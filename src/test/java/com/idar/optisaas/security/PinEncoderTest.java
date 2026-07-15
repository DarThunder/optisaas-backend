package com.idar.optisaas.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PinEncoderTest {

    private final PinEncoder pinEncoder = new PinEncoder(new BCryptPasswordEncoder());

    @Test
    void encodeProducesBcryptHash() {
        String hash = pinEncoder.encode("1234");
        assertTrue(pinEncoder.isHashed(hash), "el PIN codificado debe ser un hash BCrypt");
        assertNotEquals("1234", hash);
    }

    @Test
    void matchesAgainstHash() {
        String hash = pinEncoder.encode("1234");
        assertTrue(pinEncoder.matches("1234", hash));
        assertFalse(pinEncoder.matches("0000", hash));
    }

    @Test
    void matchesLegacyPlaintext() {
        // PIN heredado en texto plano: debe seguir validando (tolerancia a datos previos).
        assertTrue(pinEncoder.matches("1234", "1234"));
        assertFalse(pinEncoder.matches("9999", "1234"));
    }

    @Test
    void needsUpgradeOnlyForLegacyPlaintext() {
        assertTrue(pinEncoder.needsUpgrade("1234"), "texto plano debe requerir re-hash");
        assertFalse(pinEncoder.needsUpgrade(pinEncoder.encode("1234")), "un hash ya no requiere upgrade");
        assertFalse(pinEncoder.needsUpgrade(null));
        assertFalse(pinEncoder.needsUpgrade(""));
    }

    @Test
    void matchesReturnsFalseForNullOrBlank() {
        assertFalse(pinEncoder.matches(null, "1234"));
        assertFalse(pinEncoder.matches("1234", null));
        assertFalse(pinEncoder.matches("1234", ""));
    }
}
