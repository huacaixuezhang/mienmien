package com.mienmien.business.management.infrastructure.crypto;

import com.mienmien.business.management.domain.DomainException;
import com.mienmien.business.management.infrastructure.config.MienmienCryptoProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.spec.MGF1ParameterSpec;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA-OAEP(SHA-256) 加解密。密文统一前缀 {@value #CIPHER_PREFIX} + Base64。
 */
@Service
public class RsaAsymmetricCryptoService {

    public static final String CIPHER_PREFIX = "RSA1:";
    public static final String ALGORITHM_LABEL = "RSA-OAEP-256";
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final OAEPParameterSpec OAEP_SHA256 =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
    private static final int KEY_SIZE = 2048;
    /** RSA-OAEP-256 单块最大明文长度（2048-bit 密钥） */
    private static final int MAX_PLAIN_BYTES = 190;

    private static final Logger log = LoggerFactory.getLogger(RsaAsymmetricCryptoService.class);

    private final MienmienCryptoProperties properties;
    private PrivateKey privateKey;
    private PublicKey publicKey;
    private boolean ephemeralKeypair;

    public RsaAsymmetricCryptoService(MienmienCryptoProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initKeys() {
        String privPem = normalizePem(properties.getRsaPrivateKeyPem());
        String pubPem = normalizePem(properties.getRsaPublicKeyPem());
        if (!privPem.isBlank()) {
            privateKey = parsePrivateKey(privPem);
            publicKey = pubPem.isBlank() ? derivePublicKey(privateKey) : parsePublicKey(pubPem);
            ephemeralKeypair = false;
            return;
        }
        KeyPair pair = generateEphemeralKeyPair();
        privateKey = pair.getPrivate();
        publicKey = pair.getPublic();
        ephemeralKeypair = true;
        log.warn(
                "未配置 mienmien.business.crypto.rsa-private-key-pem，已生成临时 RSA 密钥对（重启后失效）。"
                        + "生产请设置 MIENMIEN_RSA_PRIVATE_KEY_PEM / MIENMIEN_RSA_PUBLIC_KEY_PEM。");
    }

    public boolean isEphemeralKeypair() {
        return ephemeralKeypair;
    }

    public String getPublicKeySpkiBase64() {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /** 入站：解密前端密文；兼容历史明文（未强制时）。 */
    public String resolveInboundSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (isSealed(trimmed)) {
            return decryptPayload(trimmed.substring(CIPHER_PREFIX.length()));
        }
        if (properties.isRequireClientCipher()) {
            throw new DomainException("BUS-4003", "请使用 RSA 公钥加密后再提交（前缀 RSA1:）");
        }
        return trimmed;
    }

    /** 落库：RSA 加密存储。 */
    public String sealForStorage(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        return CIPHER_PREFIX + encryptToBase64(plaintext);
    }

    public String unsealFromStorage(String stored) {
        if (stored == null || stored.isBlank()) {
            return "";
        }
        String trimmed = stored.trim();
        if (isSealed(trimmed)) {
            return decryptPayload(trimmed.substring(CIPHER_PREFIX.length()));
        }
        return trimmed;
    }

    public static boolean isSealed(String value) {
        return value != null && value.startsWith(CIPHER_PREFIX);
    }

    public static boolean looksLikeMaskedPlaceholder(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String t = value.trim();
        return "********".equals(t) || "******".equals(t);
    }

    private String encryptToBase64(String plaintext) {
        byte[] plain = plaintext.getBytes(StandardCharsets.UTF_8);
        if (plain.length > MAX_PLAIN_BYTES) {
            throw new DomainException("BUS-4003", "明文过长，无法 RSA 加密（请缩短后重试）");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA256);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain));
        } catch (DomainException e) {
            throw e;
        } catch (Exception e) {
            throw new DomainException("BUS-5003", "RSA 加密失败: " + e.getMessage());
        }
    }

    private String decryptPayload(String base64Cipher) {
        if (base64Cipher == null || base64Cipher.isBlank()) {
            return "";
        }
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(base64Cipher.replaceAll("\\s+", ""));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new DomainException("BUS-4003", "RSA 解密失败，请确认使用当前公钥加密");
        }
    }

    private static KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(KEY_SIZE);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥对失败", e);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePemBody(pem);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("解析 RSA 私钥失败", e);
        }
    }

    private static PublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePemBody(pem);
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("解析 RSA 公钥失败", e);
        }
    }

    private static PublicKey derivePublicKey(PrivateKey privateKey) {
        try {
            if (privateKey instanceof java.security.interfaces.RSAPrivateCrtKey crt) {
                return KeyFactory.getInstance("RSA")
                        .generatePublic(
                                new java.security.spec.RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            }
            throw new IllegalStateException("私钥无法推导公钥");
        } catch (Exception e) {
            throw new IllegalStateException("推导 RSA 公钥失败", e);
        }
    }

    private static byte[] decodePemBody(String pem) {
        String body =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                        .replace("-----END RSA PRIVATE KEY-----", "")
                        .replace("-----BEGIN PUBLIC KEY-----", "")
                        .replace("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(body);
    }

    private static String normalizePem(String pem) {
        return pem == null ? "" : pem.trim();
    }
}
