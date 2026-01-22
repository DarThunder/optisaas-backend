package com.idar.optisaas.entity;

import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Embeddable
@Data
@NoArgsConstructor
public class PriceRule {
    
    private String type;        // "SPHERE", "CYLINDER", "POSITIVE"
    private Double min;
    private Double max;
    private BigDecimal price;
    private String materialIndex; // Campo extra para guardar "1.56", "1.67", etc.

    // CONSTRUCTOR MANUAL (Para que el DataSeeder no falle con "undefined constructor")
    public PriceRule(String type, Double min, Double max, BigDecimal price, String materialIndex) {
        this.type = type;
        this.min = min;
        this.max = max;
        this.price = price;
        this.materialIndex = materialIndex;
    }

    // Getters de compatibilidad (por si alguna parte vieja del código los llama)
    public Double getMinSphere() { return "SPHERE".equals(type) ? min : null; }
    public Double getMaxSphere() { return "SPHERE".equals(type) ? max : null; }
    public Double getMinCylinder() { return "CYLINDER".equals(type) ? min : null; }
    public Double getMaxCylinder() { return "CYLINDER".equals(type) ? max : null; }
}