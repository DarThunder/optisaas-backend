package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.PromotionType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "promotions")
@Data
@EqualsAndHashCode(callSuper = true)
public class Promotion extends BaseEntity {

    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private PromotionType type; // PERCENTAGE o FIXED

    private BigDecimal value; // El valor del descuento (ej. 15.00 o 500.00)
    
    private String code; // Código de cupón (opcional)

    private LocalDate startDate;
    private LocalDate endDate;
    
    private boolean active;
}