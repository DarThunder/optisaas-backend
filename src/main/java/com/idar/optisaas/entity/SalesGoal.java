package com.idar.optisaas.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Meta de ventas mensual del DUEÑO (global, consolidada de todas sus sucursales).
 * Es independiente del filtro multi-tenant por sucursal: la meta pertenece al dueño,
 * identificado por ownerId, para un (año, mes) dado.
 */
@Entity
@Table(name = "sales_goals",
        uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "period_year", "period_month"}))
@Data
public class SalesGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "period_year", nullable = false)
    private Integer year;

    @Column(name = "period_month", nullable = false)
    private Integer month;

    @Column(nullable = false)
    private BigDecimal targetAmount = BigDecimal.ZERO;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
