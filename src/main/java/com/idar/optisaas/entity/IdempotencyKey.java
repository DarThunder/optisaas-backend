package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.IdempotencyScope;
import com.idar.optisaas.util.IdempotencyStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Llave de idempotencia: hace que reintentar una operación de dinero (red inestable,
 * doble clic) devuelva el resultado original en lugar de crear un duplicado.
 *
 * La unicidad es (branch_id, scope, key_value): la llave la genera el cliente, así que
 * solo tiene sentido dentro de su sucursal y de la operación para la que se emitió.
 */
@Entity
@Table(name = "idempotency_key",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_key",
                columnNames = {"branch_id", "scope", "key_value"}))
@Data
@EqualsAndHashCode(callSuper = true)
public class IdempotencyKey extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IdempotencyScope scope;

    /** Valor que envió el cliente en el header Idempotency-Key. */
    @Column(name = "key_value", nullable = false, length = 100)
    private String keyValue;

    /**
     * SHA-256 del cuerpo de la petición. Permite distinguir un reintento legítimo
     * (mismo contenido) de un cliente que reusa la llave para otra cosa.
     */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdempotencyStatus status;

    /** Id de la venta resultante; se usa para reconstruir la respuesta en un reintento. */
    @Column(name = "resource_id")
    private Long resourceId;
}
