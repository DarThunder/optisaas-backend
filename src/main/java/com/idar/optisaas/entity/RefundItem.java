package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Partida devuelta: qué renglón de la venta se devolvió, cuántas piezas y por cuánto valor.
 *
 * Es la fuente de verdad de cuánto queda por devolver de cada renglón: la suma de las
 * cantidades devueltas nunca puede pasar de la cantidad vendida.
 */
@Entity
@Table(name = "refund_items")
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"refund", "saleItem"})
public class RefundItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "refund_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Refund refund;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sale_item_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private SaleItem saleItem;

    @Column(nullable = false)
    private Integer quantity;

    /** Valor devuelto de esta partida, con el descuento prorrateado. */
    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    /** Si la pieza volvió al anaquel. Falso para mercancía dañada o no inventariable. */
    @Column(nullable = false)
    private boolean restocked = false;
}
