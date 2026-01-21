package com.idar.optisaas.controller;

import com.idar.optisaas.entity.ClinicalRecord;
import com.idar.optisaas.repository.ClinicalRecordRepository;
import com.idar.optisaas.service.GraduationPricingService;
import com.idar.optisaas.util.LensDesignType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    @Autowired private GraduationPricingService pricingService;
    @Autowired private ClinicalRecordRepository rxRepository;

    @PostMapping("/calculate-lens")
    public ResponseEntity<PriceResponse> calculateLensPrice(@RequestBody CalculationRequest request) {
        
        ClinicalRecord rx = rxRepository.findById(request.getRxId())
                .orElseThrow(() -> new RuntimeException("Receta no encontrada"));

        BigDecimal price = pricingService.calculateLensPrice(rx, request.getLensType());

        // Ahora .getDate() funcionará porque actualizamos la entidad
        String details = "Cálculo basado en Rx del " + rx.getDate();
        
        return ResponseEntity.ok(new PriceResponse(price, details));
    }

    // --- DTOs Estáticos y Públicos ---

    @Data
    public static class CalculationRequest {
        private Long rxId;
        private LensDesignType lensType;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PriceResponse {
        private BigDecimal price;
        private String details;
    }
}