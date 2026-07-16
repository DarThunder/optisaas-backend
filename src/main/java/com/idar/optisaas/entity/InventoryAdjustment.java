package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.AdjustmentReason;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Corrección manual del stock de un producto, con motivo y responsable.
 *
 * Es el rastro de por qué el inventario del sistema dejó de coincidir con el anaquel.
 * Registro histórico: se inserta y se consulta, nunca se edita ni se borra. Guarda el costo
 * del momento para poder valuar la pérdida aunque el costo del producto cambie después.
 */
@Entity
@Table(name = "inventory_adjustments")
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"product"})
public class InventoryAdjustment extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "branch"})
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdjustmentReason reason;

    @Column(length = 500)
    private String note;

    @Column(name = "previous_quantity", nullable = false)
    private Integer previousQuantity;

    @Column(name = "new_quantity", nullable = false)
    private Integer newQuantity;

    /** Diferencia aplicada: negativa en una merma, positiva en una corrección al alza. */
    @Column(nullable = false)
    private Integer delta;

    /** Costo unitario al momento del ajuste: valúa la pérdida sin depender del costo actual. */
    @Column(name = "unit_cost_snapshot", nullable = false)
    private BigDecimal unitCostSnapshot = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "adjusted_by", nullable = false)
    @JsonIgnoreProperties({"password", "branchRoles", "active"})
    private User adjustedBy;
}
