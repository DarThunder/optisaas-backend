package com.idar.optisaas.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Codificador para los PIN de 4 dígitos ({@code User.quickPin}: fichaje, cambio de
 * operador y clave maestra del hub). Antes se guardaban en texto plano; ahora se
 * hashean con BCrypt.
 *
 * {@link #matches(String, String)} tolera valores heredados en texto plano para no
 * bloquear a los usuarios que ya tenían un PIN definido antes del cambio. El llamador
 * puede migrarlos de forma perezosa con {@link #needsUpgrade(String)} tras una
 * verificación exitosa (re-hashear y guardar).
 */
@Component
public class PinEncoder {

    private final PasswordEncoder encoder;

    public PinEncoder(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /** Hashea un PIN en claro. */
    public String encode(String rawPin) {
        return encoder.encode(rawPin);
    }

    /** ¿El valor almacenado ya es un hash BCrypt? */
    public boolean isHashed(String stored) {
        return stored != null && (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$"));
    }

    /** true si el valor almacenado es heredado (texto plano) y conviene re-hashearlo. */
    public boolean needsUpgrade(String stored) {
        return stored != null && !stored.isBlank() && !isHashed(stored);
    }

    /** Compara el PIN en claro contra el almacenado (hash BCrypt o texto plano heredado). */
    public boolean matches(String rawPin, String stored) {
        if (rawPin == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (isHashed(stored)) {
            return encoder.matches(rawPin, stored);
        }
        // Valor heredado en texto plano.
        return stored.equals(rawPin);
    }
}
