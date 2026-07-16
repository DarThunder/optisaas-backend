package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.PurchaseOrderStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de compra a un proveedor.
 *
 * Pedir no es tener: la orden por sí sola NO mueve inventario. El stock y el costo promedio
 * solo cambian al recibir la mercancía (ver PurchaseOrderService.receive).
 */
@Entity
@Table(name = "purchase_orders")
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"items"})
public class PurchaseOrder extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "supplier_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Supplier supplier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status = PurchaseOrderStatus.DRAFT;

    /** Referencia del proveedor (número de factura o de pedido), para conciliar. */
    @Column(name = "reference_code", length = 100)
    private String referenceCode;

    /** Cuándo se espera la mercancía; sirve para saber qué pedido va tarde. */
    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(length = 500)
    private String notes;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    @JsonIgnoreProperties({"password", "branchRoles", "active"})
    private User createdBy;

    /** Cuándo se completó la recepción. Null mientras falte mercancía por llegar. */
    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    /** Costo total de lo pedido (no de lo recibido). */
    public BigDecimal getOrderedTotal() {
        return items.stream()
                .map(item -> item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantityOrdered())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Costo de lo que realmente entró, que es lo que se le debe al proveedor.
     * Suma lo acumulado en cada recepción, para respetar el precio al que llegó cada partida
     * (ver PurchaseOrderItem.receivedCostTotal).
     */
    public BigDecimal getReceivedTotal() {
        return items.stream()
                .map(PurchaseOrderItem::getReceivedCostTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean isFullyReceived() {
        return items.stream().allMatch(item -> item.getQuantityReceived() >= item.getQuantityOrdered());
    }
}
