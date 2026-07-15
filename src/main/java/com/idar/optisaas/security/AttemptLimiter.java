package com.idar.optisaas.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de intentos en memoria para flujos sensibles a fuerza bruta:
 * login por contraseña y validaciones por PIN de 4 dígitos (fichaje, cambio de
 * operador, clave maestra del hub). Bloquea una clave (identificador/usuario)
 * durante {@link #LOCK_DURATION} tras {@link #MAX_ATTEMPTS} fallos.
 *
 * Nota: el estado es por-instancia (no distribuido). Es suficiente para un
 * despliegue de una sola instancia; para escalar horizontalmente conviene
 * mover el conteo a Redis/BD.
 */
@Component
public class AttemptLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private static final class Entry {
        int failures;
        Instant blockedUntil;
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /** Lanza si la clave está bloqueada por exceso de intentos fallidos. */
    public void assertNotBlocked(String key) {
        Entry e = entries.get(key);
        if (e != null && e.blockedUntil != null && Instant.now().isBefore(e.blockedUntil)) {
            long mins = Math.max(1, Duration.between(Instant.now(), e.blockedUntil).toMinutes());
            throw new RuntimeException("Demasiados intentos fallidos. Intenta de nuevo en ~" + mins + " min.");
        }
    }

    /** Registra un fallo; bloquea la clave al alcanzar el máximo de intentos. */
    public void recordFailure(String key) {
        entries.compute(key, (k, e) -> {
            if (e == null) e = new Entry();
            // Si el bloqueo previo ya venció, reiniciamos el conteo.
            if (e.blockedUntil != null && Instant.now().isAfter(e.blockedUntil)) {
                e.failures = 0;
                e.blockedUntil = null;
            }
            e.failures++;
            if (e.failures >= MAX_ATTEMPTS) {
                e.blockedUntil = Instant.now().plus(LOCK_DURATION);
            }
            return e;
        });
    }

    /** Limpia el registro tras un intento exitoso. */
    public void reset(String key) {
        entries.remove(key);
    }
}
