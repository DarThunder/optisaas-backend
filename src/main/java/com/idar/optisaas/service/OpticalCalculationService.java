package com.idar.optisaas.service;

import com.idar.optisaas.dto.OpticalCalculationRequest;
import com.idar.optisaas.dto.PriceResponse;
import com.idar.optisaas.entity.LensBasePrice; // Importar
import com.idar.optisaas.entity.PriceMatrix;
import com.idar.optisaas.entity.PriceRule;
import com.idar.optisaas.repository.LensBasePriceRepository; // Importar Repo
import com.idar.optisaas.repository.PriceMatrixRepository;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OpticalCalculationService {

    @Autowired private PriceMatrixRepository priceMatrixRepository;
    @Autowired private LensBasePriceRepository lensBasePriceRepository; // NUEVO

    public PriceResponse calculateSmartPrice(OpticalCalculationRequest request) {
        BigDecimal totalPrice = BigDecimal.ZERO;
        List<String> breakdown = new ArrayList<>();

        // 1. Obtener Datos de la Receta
        double sphere = request.getSphere() != null ? request.getSphere() : 0.0;
        double absSphere = Math.abs(sphere);
        double cylinder = request.getCylinder() != null ? Math.abs(request.getCylinder()) : 0.0;

        // ---------------------------------------------------------
        // PASO A: PRECIO BASE POR TIPO DE LENTE (Monofocal, Progresivo...)
        // ---------------------------------------------------------
        BigDecimal lensTypeBasePrice = BigDecimal.ZERO;
        Long branchId = TenantContext.getCurrentBranch();

        if (request.getType() != null) {
            Optional<LensBasePrice> basePriceOpt = lensBasePriceRepository
                    .findByDesignTypeAndBranchId(request.getType(), branchId);
            
            if (basePriceOpt.isPresent()) {
                lensTypeBasePrice = basePriceOpt.get().getPrice();
                // Añadimos al desglose
                breakdown.add(translateType(request.getType().name()) + ": $" + lensTypeBasePrice);
            }
        }
        
        // Sumamos el precio base inicial
        totalPrice = totalPrice.add(lensTypeBasePrice);

        // ---------------------------------------------------------
        // PASO B: SOBRECOSTOS POR GRADUACIÓN (MATRIZ)
        // ---------------------------------------------------------
        
        // Cargar Matriz Activa
        Optional<PriceMatrix> matrixOpt = priceMatrixRepository.findByBranchIdAndActiveTrue(branchId);
        
        BigDecimal sphereSurcharge = BigDecimal.ZERO;
        BigDecimal cylSurcharge = BigDecimal.ZERO;
        BigDecimal positiveSurcharge = BigDecimal.ZERO;

        if (matrixOpt.isPresent()) {
            List<PriceRule> rules = matrixOpt.get().getRules();

            // --- REGLA 1: ESFERA ---
            for (PriceRule rule : rules) {
                if ("SPHERE".equals(rule.getType()) && isInRange(absSphere, rule)) {
                    sphereSurcharge = rule.getPrice();
                    break; 
                }
            }

            // --- REGLA 2: CILINDRO ---
            for (PriceRule rule : rules) {
                if ("CYLINDER".equals(rule.getType()) && isInRange(cylinder, rule)) {
                    cylSurcharge = rule.getPrice();
                    break;
                }
            }

            // --- REGLA 3: POSITIVO ---
            if (sphere > 0) {
                for (PriceRule rule : rules) {
                    if ("POSITIVE".equals(rule.getType())) {
                        positiveSurcharge = rule.getPrice();
                        break;
                    }
                }
            }
        }

        // ---------------------------------------------------------
        // PASO C: SUMA FINAL
        // ---------------------------------------------------------
        
        // Sumamos los extras al total acumulado
        totalPrice = totalPrice.add(sphereSurcharge);
        totalPrice = totalPrice.add(cylSurcharge);
        totalPrice = totalPrice.add(positiveSurcharge);

        // Agregamos detalles al desglose solo si hay cobro extra
        if (sphereSurcharge.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.add(String.format("Extra Graduación (%.2f D): $%.2f", absSphere, sphereSurcharge));
        }
        if (positiveSurcharge.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.add("Extra por Hipermetropía (+): $" + positiveSurcharge);
        }
        if (cylSurcharge.compareTo(BigDecimal.ZERO) > 0) {
            breakdown.add(String.format("Extra Astigmatismo (%.2f D): $%.2f", cylinder, cylSurcharge));
        }
        
        return new PriceResponse(totalPrice, String.join(" | ", breakdown));
    }

    private boolean isInRange(double value, PriceRule rule) {
        double min = rule.getMin() != null ? rule.getMin() : -999;
        double max = rule.getMax() != null ? rule.getMax() : 999;
        return value >= min && value <= max;
    }
    
    // Traductor simple para nombres bonitos
    private String translateType(String typeName) {
        if (typeName == null) return "Lente";
        if (typeName.contains("MONOFOCAL")) return "Monofocal";
        if (typeName.contains("FLAT_TOP")) return "Bifocal FT";
        if (typeName.contains("INVISIBLE")) return "Bifocal Invisible";
        if (typeName.contains("PROGRESSIVE")) return "Progresivo";
        return typeName;
    }
}