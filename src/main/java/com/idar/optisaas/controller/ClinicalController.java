package com.idar.optisaas.controller;

import com.idar.optisaas.service.ClinicalService;
import com.idar.optisaas.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clinical")
public class ClinicalController {

    @Autowired
    private ClinicalService clinicalService;

    @PostMapping("/records")
    public ResponseEntity<?> createRecord(@RequestBody ClinicalRecordRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) auth.getPrincipal();

        clinicalService.createRecord(request, email);
        return ResponseEntity.ok("Receta guardada exitosamente");
    }

    @PostMapping("/calculate-price")
    public ResponseEntity<PriceResponse> calculatePrice(@RequestBody PriceCalculationRequest request) {
        PriceResponse response = clinicalService.calculatePrice(request);
        return ResponseEntity.ok(response);
    }
}