package com.mienmien.business.management.crypto.interfaces.rest;

import com.mienmien.business.management.infrastructure.crypto.RsaAsymmetricCryptoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/business/crypto")
public class CryptoPublicKeyController {

    private final RsaAsymmetricCryptoService rsaAsymmetricCryptoService;

    public CryptoPublicKeyController(RsaAsymmetricCryptoService rsaAsymmetricCryptoService) {
        this.rsaAsymmetricCryptoService = rsaAsymmetricCryptoService;
    }

    @GetMapping("/public-key")
    public CryptoPublicKeyResponse publicKey() {
        return new CryptoPublicKeyResponse(
                RsaAsymmetricCryptoService.ALGORITHM_LABEL,
                RsaAsymmetricCryptoService.CIPHER_PREFIX,
                rsaAsymmetricCryptoService.getPublicKeySpkiBase64(),
                rsaAsymmetricCryptoService.isEphemeralKeypair());
    }

    public record CryptoPublicKeyResponse(
            String algorithm, String cipherPrefix, String publicKeySpkiBase64, boolean ephemeralKeypair) {}
}
