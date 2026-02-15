package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "price_rules")
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, exclude = "matrix") // Excluir del hash para evitar recursión
public class PriceRule extends BaseEntity {

    private String conditionType; 
    private Double minVal;
    private Double maxVal;
    private BigDecimal adjustment;
    private String materialIndex;   

    // --- RELACIÓN CON LA MATRIZ (CORREGIDA) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matrix_id")
    @JsonBackReference // Evita recursión infinita al serializar
    private PriceMatrix matrix;

    // --- CONSTRUCTOR DE COMPATIBILIDAD ---
    public PriceRule(String type, Double min, Double max, BigDecimal price, String materialIndex) {
        this.conditionType = type;
        this.minVal = min;
        this.maxVal = max;
        this.adjustment = price;
        this.materialIndex = materialIndex;
    }

    // --- GETTERS DE COMPATIBILIDAD ---
    public String getType() { return conditionType; }
    public Double getMin() { return minVal; }
    public Double getMax() { return maxVal; }
    public BigDecimal getPrice() { return adjustment; }
}