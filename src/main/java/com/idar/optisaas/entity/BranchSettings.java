package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * Configuración por sucursal: perfil del negocio, datos fiscales, preferencias
 * de operación y personalización del ticket. Una fila por sucursal.
 */
@Entity
@Table(name = "branch_settings")
@Data
@EqualsAndHashCode(callSuper = true)
public class BranchSettings extends BaseEntity {

    // --- Perfil del negocio ---
    private String businessName;
    private String phone;
    private String email;
    private String website;
    private String addressLine;

    @Column(columnDefinition = "text")
    private String logo; // data URI base64 (opcional)

    // --- Datos fiscales ---
    private String legalName;   // Razón social
    private String taxId;       // RFC
    private BigDecimal taxRate = new BigDecimal("16.00"); // IVA %
    private boolean pricesIncludeTax = true;

    // --- Preferencias de operación ---
    private String currencySymbol = "$";
    private Integer lowStockThreshold = 5;
    private Integer quotationValidityDays = 7;

    // --- Personalización del ticket ---
    private Integer paperWidth = 80; // 58 o 80 (mm)
    private boolean showLogo = true;
    private boolean showFolio = true;
    private boolean showDateTime = true;
    private boolean showCashier = true;
    private boolean showClient = true;
    private boolean showPaymentMethod = true;
    private boolean showDiscount = true;

    private String headerNote;   // línea extra bajo el nombre

    @Column(columnDefinition = "text")
    private String footerMessage = "¡Gracias por su preferencia!";

    @Column(columnDefinition = "text")
    private String legalNote;    // políticas de garantía / devolución
}
