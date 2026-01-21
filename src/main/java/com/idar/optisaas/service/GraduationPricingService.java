package com.idar.optisaas.service;

import com.idar.optisaas.entity.ClinicalRecord;
import com.idar.optisaas.util.LensDesignType;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;

@Service
public class GraduationPricingService {

    // --- PRECIOS BASE CONFIGURABLES (Idealmente vendrían de DB) ---
    private static final BigDecimal BASE_MONOFOCAL = new BigDecimal("500.00");
    private static final BigDecimal BASE_BIFOCAL_FT = new BigDecimal("800.00");
    private static final BigDecimal BASE_BIFOCAL_INV = new BigDecimal("1200.00");
    private static final BigDecimal BASE_PROGRESSIVE = new BigDecimal("1800.00");

    // --- RECARGOS (SURCHARGES) ---
    private static final BigDecimal SURCHARGE_HIGH_CYLINDER = new BigDecimal("250.00"); // Cil > 2.00
    private static final BigDecimal SURCHARGE_HIGH_SPHERE = new BigDecimal("200.00");   // Esfera > 4.00
    private static final BigDecimal SURCHARGE_EXTRA_HIGH = new BigDecimal("500.00");    // Esfera > 6.00 o Cil > 4.00

    public BigDecimal calculateLensPrice(ClinicalRecord rx, LensDesignType type) {
        if (rx == null) return BigDecimal.ZERO;

        // 1. Obtener Precio Base según el Diseño
        BigDecimal finalPrice = getBasePriceByType(type);

        // 2. Analizar la Graduación (Tomamos el "peor" ojo para calcular el rango)
        double maxSphere = Math.max(Math.abs(rx.getSphereRight()), Math.abs(rx.getSphereLeft()));
        double maxCylinder = Math.max(Math.abs(rx.getCylinderRight()), Math.abs(rx.getCylinderLeft()));
        // La adición solo importa si es bifocal/progresivo
        double maxAdd = (type != LensDesignType.MONOFOCAL) 
        ? Math.max(rx.getAdditionRight() != null ? rx.getAdditionRight() : 0.0, 
                   rx.getAdditionLeft() != null ? rx.getAdditionLeft() : 0.0) 
        : 0.0;

        // 3. Aplicar Reglas de la Matriz (Lógica de Negocio)
        
        // REGLA A: Cilindro Alto (Astigmatismo)
        // Si el cilindro pasa de 2.00, se cobra extra.
        if (maxCylinder > 2.00 && maxCylinder <= 4.00) {
            finalPrice = finalPrice.add(SURCHARGE_HIGH_CYLINDER);
        } else if (maxCylinder > 4.00) {
            finalPrice = finalPrice.add(SURCHARGE_EXTRA_HIGH); // Fabricación especial
        }

        // REGLA B: Esfera Alta (Miopía/Hipermetropía fuerte)
        if (maxSphere > 4.00 && maxSphere <= 6.00) {
            finalPrice = finalPrice.add(SURCHARGE_HIGH_SPHERE);
        } else if (maxSphere > 6.00) {
            finalPrice = finalPrice.add(SURCHARGE_EXTRA_HIGH);
        }

        // REGLA C: Adición Alta (Opcional, común en progresivos complejos)
        if (maxAdd > 3.00) {
            finalPrice = finalPrice.add(new BigDecimal("150.00"));
        }

        return finalPrice;
    }

    private BigDecimal getBasePriceByType(LensDesignType type) {
        switch (type) {
            case MONOFOCAL: return BASE_MONOFOCAL;
            case BIFOCAL_FLAT_TOP: return BASE_BIFOCAL_FT;
            case BIFOCAL_INVISIBLE: return BASE_BIFOCAL_INV;
            case PROGRESSIVE: return BASE_PROGRESSIVE;
            default: return BASE_MONOFOCAL;
        }
    }
}