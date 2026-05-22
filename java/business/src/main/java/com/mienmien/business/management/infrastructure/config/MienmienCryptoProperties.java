package com.mienmien.business.management.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RSA 非对称加密：前端用公钥加密口令 / API Key，服务端私钥解密。
 * 生产环境请通过环境变量注入 PEM，勿将私钥提交仓库。
 */
@ConfigurationProperties(prefix = "mienmien.business.crypto")
public class MienmienCryptoProperties {

    /** PKCS#8 PEM 私钥（可多行，含 BEGIN/END） */
    private String rsaPrivateKeyPem = "";

    /** SPKI PEM 公钥；未配置时从私钥推导 */
    private String rsaPublicKeyPem = "";

    /**
     * 为 true 时，注册/登录口令与模型 API Key 必须带 {@code RSA1:} 前缀密文，禁止明文入站。
     */
    private boolean requireClientCipher = false;

    public String getRsaPrivateKeyPem() {
        return rsaPrivateKeyPem;
    }

    public void setRsaPrivateKeyPem(String rsaPrivateKeyPem) {
        this.rsaPrivateKeyPem = rsaPrivateKeyPem == null ? "" : rsaPrivateKeyPem;
    }

    public String getRsaPublicKeyPem() {
        return rsaPublicKeyPem;
    }

    public void setRsaPublicKeyPem(String rsaPublicKeyPem) {
        this.rsaPublicKeyPem = rsaPublicKeyPem == null ? "" : rsaPublicKeyPem;
    }

    public boolean isRequireClientCipher() {
        return requireClientCipher;
    }

    public void setRequireClientCipher(boolean requireClientCipher) {
        this.requireClientCipher = requireClientCipher;
    }
}
