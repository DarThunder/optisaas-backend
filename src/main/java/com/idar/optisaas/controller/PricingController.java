package com.idar.optisaas.controller;

import com.idar.optisaas.dto.OpticalCalculationRequest;
import com.idar.optisaas.dto.PriceCalculationRequest;
import com.idar.optisaas.dto.PriceResponse;
import com.idar.optisaas.entity.ClinicalRecord;
import com.idar.optisaas.entity.LensBasePrice;
import com.idar.optisaas.entity.PriceMatrix; // Asegúrate de importar la Entidad
import com.idar.optisaas.repository.ClinicalRecordRepository;
import com.idar.optisaas.repository.LensBasePriceRepository;
import com.idar.optisaas.repository.PriceMatrixRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.service.OpticalCalculationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    @Autowired private OpticalCalculationService opticalService;
    @Autowired private ClinicalRecordRepository rxRepository;
    @Autowired private LensBasePriceRepository lensBasePriceRepository;
    @Autowired private PriceMatrixRepository priceMatrixRepository;

    // =================================================================================
    // 1. CÁLCULO DE PRECIO (Para el Asistente de Micas en el POS)
    // =================================================================================
    
    @PostMapping("/calculate-lens")
    public ResponseEntity<PriceResponse> calculateLensPrice(@RequestBody PriceCalculationRequest request) {
        ClinicalRecord rx = rxRepository.findById(request.getRxId())
                .orElseThrow(() -> new RuntimeException("Receta no encontrada"));

        OpticalCalculationRequest calcReq = new OpticalCalculationRequest();
        
        // Usamos la graduación más alta ("peor ojo") para determinar el precio
        Double sphere = getWorstValue(rx.getSphereRight(), rx.getSphereLeft());
        Double cylinder = getWorstValue(rx.getCylinderRight(), rx.getCylinderLeft());

        calcReq.setSphere(sphere);
        calcReq.setCylinder(cylinder);
        calcReq.setType(request.getLensType());

        return ResponseEntity.ok(opticalService.calculateSmartPrice(calcReq));
    }

    // =================================================================================
    // 2. CONFIGURACIÓN: PRECIOS BASE POR TIPO (Monofocal, Bifocal, etc.)
    // =================================================================================

    @GetMapping("/base-prices")
    public ResponseEntity<List<LensBasePrice>> getAllBasePrices() {
        return ResponseEntity.ok(lensBasePriceRepository.findByBranchId(TenantContext.getCurrentBranch()));
    }

    @PutMapping("/base-prices")
    public ResponseEntity<?> updateBasePrices(@RequestBody List<LensBasePrice> updates) {
        Long branchId = TenantContext.getCurrentBranch();
        
        for (LensBasePrice update : updates) {
            LensBasePrice target = null;

            if (update.getId() != null) {
                target = lensBasePriceRepository.findById(update.getId())
                        .filter(p -> p.getBranchId().equals(branchId))
                        .orElse(null);
            } else if (update.getDesignType() != null) {
                target = lensBasePriceRepository.findByDesignTypeAndBranchId(update.getDesignType(), branchId)
                        .orElse(null);
            }

            if (target != null) {
                target.setPrice(update.getPrice());
                lensBasePriceRepository.save(target);
            } else if (update.getDesignType() != null) {
                LensBasePrice newPrice = new LensBasePrice();
                newPrice.setBranchId(branchId);
                newPrice.setDesignType(update.getDesignType());
                newPrice.setPrice(update.getPrice());
                newPrice.setDescription(update.getDescription());
                lensBasePriceRepository.save(newPrice);
            }
        }
        return ResponseEntity.ok().build();
    }

    // =================================================================================
    // 3. CONFIGURACIÓN: MATRIZ DE PRECIOS (Reglas por Graduación)
    // =================================================================================

    @GetMapping("/matrix")
    public ResponseEntity<PriceMatrix> getActiveMatrix() {
        return ResponseEntity.of(priceMatrixRepository.findByBranchIdAndActiveTrue(TenantContext.getCurrentBranch()));
    }

    @PostMapping("/matrix")
    public ResponseEntity<PriceMatrix> saveMatrix(@RequestBody PriceMatrix matrix) {
        Long branchId = TenantContext.getCurrentBranch();
        
        priceMatrixRepository.findByBranchIdAndActiveTrue(branchId).ifPresent(existing -> {
            matrix.setId(existing.getId()); 
        });

        matrix.setBranchId(branchId);
        matrix.setActive(true);
        
        return ResponseEntity.ok(priceMatrixRepository.save(matrix));
    }

    // =================================================================================
    // UTILIDADES
    // =================================================================================

    private Double getWorstValue(Double v1, Double v2) {
        if (v1 == null) v1 = 0.0;
        if (v2 == null) v2 = 0.0;
        return Math.abs(v1) > Math.abs(v2) ? v1 : v2;
    }
}