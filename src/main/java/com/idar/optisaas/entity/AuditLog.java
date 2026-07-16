package com.idar.optisaas.entity;

import com.idar.optisaas.util.AuditAction;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Registro append-only de acciones sensibles (dinero, acceso, gestión de empleados).
 *
 * Deliberadamente NO extiende BaseEntity:
 *  - `branchId` debe poder ser null (acciones del Hub global no tienen sucursal).
 *  - No debe quedar sujeto al filtro multi-tenant de Hibernate: la bitácora se acota
 *    explícitamente en el servicio a las sucursales del dueño que consulta.
 *
 * Nunca se actualiza ni se borra: solo se inserta y se consulta.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_log_branch_created", columnList = "branch_id, created_at"),
        @Index(name = "idx_audit_log_action", columnList = "action")
})
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    /** Tipo de entidad afectada (p. ej. "Sale", "CashSession"). */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    /** Id de la entidad afectada. */
    @Column(name = "entity_id")
    private Long entityId;

    /** Sucursal donde ocurrió; null si fue una acción del Hub global. */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 255)
    private String actorUsername;

    /** Contexto legible: valores antes/después, montos, motivo, etc. */
    @Column(length = 2000)
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
