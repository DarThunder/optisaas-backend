package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.CashSessionStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_sessions")
@Data
@EqualsAndHashCode(callSuper = true)
public class CashSession extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CashSessionStatus status = CashSessionStatus.OPEN;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "opened_by_id", nullable = false)
    @JsonIgnoreProperties({"password", "quickPin", "branchRoles", "activationCode"})
    private User openedBy;

    private LocalDateTime openedAt;
    private BigDecimal openingFloat = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "closed_by_id")
    @JsonIgnoreProperties({"password", "quickPin", "branchRoles", "activationCode"})
    private User closedBy;

    private LocalDateTime closedAt;

    // Datos del corte (se llenan al cerrar)
    private BigDecimal expectedCash;   // lo que debería haber en el cajón
    private BigDecimal countedCash;    // lo que el cajero contó físicamente
    private BigDecimal difference;     // countedCash - expectedCash (sobrante/faltante)
}
