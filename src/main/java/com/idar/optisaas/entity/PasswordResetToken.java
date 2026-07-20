package com.idar.optisaas.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Token de un solo uso para restablecer la contraseña del dueño.
 *
 * NO extiende BaseEntity a propósito: esa clase exige `branch_id` y lo toma del TenantContext,
 * que aquí no existe — la recuperación ocurre ANTES de iniciar sesión y no pertenece a ninguna
 * sucursal, sino a la cuenta.
 *
 * Del token solo se guarda su HASH, nunca el valor. Quien tenga el token puede tomar la cuenta,
 * así que es un secreto equivalente a una contraseña: si la base se filtrara, los hashes no le
 * sirven a nadie. El valor real solo viaja en el correo del dueño.
 */
@Entity
@Table(name = "password_reset_tokens")
@Data
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** Sella el token al usarse: un token vale exactamente una vez. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public boolean isUsable() {
        return usedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}
