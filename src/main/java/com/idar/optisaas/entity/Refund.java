package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.PaymentMethod;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Devolución de mercancía de una venta, con el dinero que se reintegró (si lo hubo).
 *
 * `amount` es dinero que SALE de la caja, y puede ser cero: si la venta tenía saldo pendiente,
 * devolver la mercancía baja la deuda en lugar de reembolsar. `returnedValue` es el valor de la
 * mercancía devuelta (con el descuento prorrateado), que no siempre coincide con el reembolso.
 *
 * Es un registro histórico: se inserta y se consulta, nunca se edita ni se borra.
 */
@Entity
@Table(name = "refunds")
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"sale", "items"})
public class Refund extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Sale sale;

    /** Valor de la mercancía devuelta, con el descuento de la venta ya prorrateado. */
    @Column(name = "returned_value", nullable = false)
    private BigDecimal returnedValue = BigDecimal.ZERO;

    /** Dinero efectivamente reintegrado al cliente. Cero si la devolución solo bajó la deuda. */
    @Column(nullable = false)
    private BigDecimal amount = BigDecimal.ZERO;

    /** Por dónde salió el dinero. Null cuando el reembolso fue cero. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod method;

    @Column(nullable = false, length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "processed_by", nullable = false)
    @JsonIgnoreProperties({"password", "branchRoles", "active"})
    private User processedBy;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RefundItem> items = new ArrayList<>();
}
