package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Renglón de una orden de compra: qué producto, cuántas piezas y a qué costo unitario.
 *
 * `quantityReceived` es la fuente de verdad de lo que falta por llegar y crece con cada
 * recepción parcial; nunca puede pasar de `quantityOrdered`.
 */
@Entity
@Table(name = "purchase_order_items")
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"purchaseOrder", "product"})
public class PurchaseOrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "branch"})
    @ToString.Exclude
    private Product product;

    @Column(name = "quantity_ordered", nullable = false)
    private Integer quantityOrdered;

    @Column(name = "quantity_received", nullable = false)
    private Integer quantityReceived = 0;

    /** Costo unitario pactado. Al recibir puede corregirse si la factura llegó con otro precio. */
    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost = BigDecimal.ZERO;

    /**
     * Costo acumulado de lo que realmente llegó, sumando cada recepción a su propio precio.
     *
     * No se puede calcular como `quantityReceived * unitCost`: si la segunda parte del pedido
     * llega facturada más cara, ese multiplicador repreciaría también las piezas que ya habían
     * entrado, e inflaría lo que se le debe al proveedor.
     */
    @Column(name = "received_cost_total", nullable = false)
    private BigDecimal receivedCostTotal = BigDecimal.ZERO;

    public int getPendingQuantity() {
        return quantityOrdered - quantityReceived;
    }
}
