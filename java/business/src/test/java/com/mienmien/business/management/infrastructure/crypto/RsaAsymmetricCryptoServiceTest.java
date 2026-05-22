package com.mienmien.business.management.infrastructure.crypto;

import com.mienmien.business.management.infrastructure.config.MienmienCryptoProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsaAsymmetricCryptoServiceTest {

    private RsaAsymmetricCryptoService service;

    @BeforeEach
    void setUp() {
        MienmienCryptoProperties props = new MienmienCryptoProperties();
        service = new RsaAsymmetricCryptoService(props);
        service.initKeys();
    }

    @Test
    void roundTrip_inbound_and_storage() {
        String plain = "MyP@ssw0rd-测试";
        String wire = service.sealForStorage(plain);
        assertTrue(wire.startsWith(RsaAsymmetricCryptoService.CIPHER_PREFIX));
        assertEquals(plain, service.resolveInboundSecret(wire));
        assertEquals(plain, service.unsealFromStorage(wire));
    }
}
