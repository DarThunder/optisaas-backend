package com.idar.optisaas.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttemptLimiterTest {

    @Test
    void allowsAttemptsUnderTheLimit() {
        AttemptLimiter limiter = new AttemptLimiter();
        // 4 fallos no deben bloquear (el máximo es 5).
        for (int i = 0; i < 4; i++) {
            limiter.recordFailure("k");
        }
        assertDoesNotThrow(() -> limiter.assertNotBlocked("k"));
    }

    @Test
    void blocksAfterReachingTheLimit() {
        AttemptLimiter limiter = new AttemptLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("k");
        }
        RuntimeException ex = assertThrows(RuntimeException.class, () -> limiter.assertNotBlocked("k"));
        assertTrue(ex.getMessage().toLowerCase().contains("intentos"));
    }

    @Test
    void resetClearsTheCounter() {
        AttemptLimiter limiter = new AttemptLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("k");
        }
        limiter.reset("k");
        assertDoesNotThrow(() -> limiter.assertNotBlocked("k"));
    }

    @Test
    void keysAreIndependent() {
        AttemptLimiter limiter = new AttemptLimiter();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("a");
        }
        // Bloquear "a" no debe afectar a "b".
        assertThrows(RuntimeException.class, () -> limiter.assertNotBlocked("a"));
        assertDoesNotThrow(() -> limiter.assertNotBlocked("b"));
    }
}
