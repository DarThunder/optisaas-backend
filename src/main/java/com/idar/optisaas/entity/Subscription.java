package com.idar.optisaas.entity;

import com.idar.optisaas.util.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Estado comercial de un cliente.
 *
 * Cuelga del DUEÑO, no de la sucursal: una óptica con tres sucursales es un solo cliente con
 * una sola suscripción. Por eso NO extiende BaseEntity — esa clase exige `branch_id` y lo toma
 * del TenantContext, que aquí no aplica.
 *
 * Hoy estos campos los llena a mano el administrador desde el panel; son los mismos que
 * escribiría una pasarela de pago el día que se integre.
 */
@Entity
@Table(name = "subscription")
@Data
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SubscriptionStatus status = SubscriptionStatus.TRIAL;

    /**
     * Hasta cuándo tiene acceso. Es una fecha, no un instante: a nadie se le corta el servicio
     * a las 14:32; se le corta un día. Nulo significa sin vencimiento (útil para Mogar, que
     * está probando el sistema sin plazo mientras se afina).
     */
    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Vencida es distinto de suspendida: una es el calendario, la otra una decisión. */
    public boolean isExpired() {
        return validUntil != null && validUntil.isBefore(LocalDate.now());
    }
}
