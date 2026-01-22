package com.idar.optisaas.service;

import com.idar.optisaas.dto.PriceCalculationRequest;
import com.idar.optisaas.dto.PriceResponse;
import com.idar.optisaas.entity.ClinicalRecord;
import com.idar.optisaas.entity.LensBasePrice;
import com.idar.optisaas.entity.PriceMatrix;
// IMPORTANTE: Usamos la entidad, NO el modelo
import com.idar.optisaas.entity.PriceRule; 
import com.idar.optisaas.repository.ClinicalRecordRepository;
import com.idar.optisaas.repository.LensBasePriceRepository;
import com.idar.optisaas.repository.PriceMatrixRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.LensDesignType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class GraduationPricingService {

    @Autowired private ClinicalRecordRepository recordRepository;
    @Autowired private LensBasePriceRepository lensBasePriceRepository;
    @Autowired private PriceMatrixRepository priceMatrixRepository;

    public PriceResponse calculatePrice(PriceCalculationRequest request) {
        // 1. Obtener Receta
        ClinicalRecord rx = recordRepository.findById(request.getRxId())
                .orElseThrow(() -> new RuntimeException("Receta no encontrada"));

        LensDesignType type = request.getLensType();
        StringBuilder details = new StringBuilder();

        // 2. OBTENER PRECIO BASE POR TIPO (Desde Configuración de Precios)
        BigDecimal baseTypePrice = getBasePriceFromDB(type);
        details.append("Base ").append(translateType(type)).append(": $").append(baseTypePrice);

        // 3. BUSCAR EN MATRIZ DE PRECIOS (Por Graduación)
        BigDecimal matrixPrice = BigDecimal.ZERO;
        
        // Tomamos la graduación más alta de los dos ojos
        Double worstSphere = getWorstValue(rx.getSphereRight(), rx.getSphereLeft());
        Double worstCyl = getWorstValue(rx.getCylinderRight(), rx.getCylinderLeft());

        // Buscamos la matriz activa de la sucursal
        Optional<PriceMatrix> activeMatrix = priceMatrixRepository.findByBranchIdAndActiveTrue(TenantContext.getCurrentBranch());

        if (activeMatrix.isPresent()) {
            // Buscamos una regla que coincida
            for (PriceRule rule : activeMatrix.get().getRules()) {
                if (isRxInRule(worstSphere, worstCyl, rule)) {
                    matrixPrice = rule.getPrice();
                    details.append(" | Regla Matriz detectada: $").append(matrixPrice);
                    break; // Encontramos la regla, dejamos de buscar
                }
            }
        }

        // 4. LÓGICA DE NEGOCIO: El precio es el MAYOR entre el Base y la Matriz.
        BigDecimal finalPrice = baseTypePrice.max(matrixPrice);
        
        return new PriceResponse(finalPrice, details.toString());
    }

    // --- MÉTODOS AUXILIARES ---

    private BigDecimal getBasePriceFromDB(LensDesignType type) {
        if (type == null) return BigDecimal.ZERO;
        Long branchId = TenantContext.getCurrentBranch();
        return lensBasePriceRepository.findByDesignTypeAndBranchId(type, branchId)
                .map(LensBasePrice::getPrice)
                .orElse(BigDecimal.ZERO);
    }

    private boolean isRxInRule(Double sphere, Double cyl, PriceRule rule) {
        // Convertimos nulls a 0.0 para evitar errores
        double s = sphere == null ? 0.0 : sphere;
        double c = cyl == null ? 0.0 : cyl;

        // Validamos usando los getters genéricos (asegúrate que tu entidad PriceRule tenga estos campos)
        // Si tu entidad PriceRule usa 'minSphere' en vez de 'min' y 'type', ajusta aquí.
        // Asumiendo la versión moderna con 'type', 'min', 'max':
        
        if ("SPHERE".equals(rule.getType())) {
             double min = rule.getMin() != null ? rule.getMin() : -999;
             double max = rule.getMax() != null ? rule.getMax() : 999;
             return s >= min && s <= max;
        }
        
        // Si tu entidad PriceRule es la VIEJA (con minSphere, maxSphere):
        /*
        boolean sphereMatch = s >= (rule.getMinSphere() != null ? rule.getMinSphere() : -999) 
                           && s <= (rule.getMaxSphere() != null ? rule.getMaxSphere() : 999);
        
        boolean cylMatch = c >= (rule.getMinCylinder() != null ? rule.getMinCylinder() : -999) 
                        && c <= (rule.getMaxCylinder() != null ? rule.getMaxCylinder() : 0); // Cilindro suele ser negativo, max 0

        return sphereMatch && cylMatch;
        */
        
        return false; // Por defecto si no coincide tipo
    }

    private Double getWorstValue(Double v1, Double v2) {
        if (v1 == null) v1 = 0.0;
        if (v2 == null) v2 = 0.0;
        return Math.abs(v1) > Math.abs(v2) ? v1 : v2;
    }

    private String translateType(LensDesignType type) {
        if (type == null) return "Lente";
        return switch (type) {
            case MONOFOCAL -> "Monofocal";
            case BIFOCAL_FLAT_TOP -> "Bifocal FT";
            case BIFOCAL_INVISIBLE -> "Blended";
            case PROGRESSIVE -> "Progresivo";
        };
    }
}