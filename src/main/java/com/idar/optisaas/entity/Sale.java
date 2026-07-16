package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.SaleStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales")
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"items", "payments", "branch"})
public class Sale extends BaseEntity {

    // Cambiamos insertable/updatable a false para que no choque con branchId de BaseEntity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", insertable = false, updatable = false)
    @JsonIgnore // Evita recursión en JSON
    private Branch branch;

    // Nullable: una venta de mostrador (sin cliente registrado) no tiene client.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id", nullable = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "branch"})
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seller_id", nullable = false)
    @JsonIgnoreProperties({"password", "branchRoles", "active"})
    private User seller;

    @Enumerated(EnumType.STRING)
    private SaleStatus status;

    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private String discountName;

    /**
     * Valor de la mercancía devuelta (descuento ya prorrateado). Baja lo que el cliente debe,
     * pero NO toca totalAmount: la venta original se conserva tal como ocurrió.
     */
    private BigDecimal returnedAmount = BigDecimal.ZERO;

    /** Dinero reintegrado al cliente. paidAmount sigue siendo el bruto que entró en su momento. */
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<SaleItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Payment> payments = new ArrayList<>();
    
    private boolean isParked = false; 

    /**
     * Lo que el cliente todavía debe: lo que se queda menos lo que pagó en neto.
     *
     * totalAmount y paidAmount son historia bruta e inmutable; las devoluciones se expresan
     * restando por separado, de modo que el saldo se ajusta solo y las cuentas por cobrar
     * (ver ReportService) nunca cobran mercancía que ya regresó.
     */
    public BigDecimal getRemainingBalance() {
        if (totalAmount == null) return BigDecimal.ZERO;
        BigDecimal kept = totalAmount.subtract(nz(returnedAmount));
        BigDecimal netPaid = nz(paidAmount).subtract(nz(refundedAmount));
        return kept.subtract(netPaid);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}