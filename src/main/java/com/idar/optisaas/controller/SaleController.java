package com.idar.optisaas.controller;

import com.idar.optisaas.service.SaleService;
import com.idar.optisaas.dto.*;
// Importamos Sale entidad solo si es necesario para el metodo legacy getSalesByClient
import com.idar.optisaas.entity.Sale; 

import java.util.List;
import java.util.Map; // Importante para PATCH

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    @Autowired
    private SaleService saleService;

    @PostMapping
    public ResponseEntity<SaleResponse> createSale(@Valid @RequestBody SaleRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String sellerEmail = (String) auth.getPrincipal();

        SaleResponse response = saleService.createSale(request, sellerEmail);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleResponse> getSale(@PathVariable Long id) {
        SaleResponse response = saleService.getSaleById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SaleResponse>> getAllSales() {
        return ResponseEntity.ok(saleService.getAllSales());
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<SaleResponse> addPayment(
            @PathVariable Long id, 
            @Valid @RequestBody PaymentRequest request) {
            
        SaleResponse response = saleService.addPayment(id, request);
        return ResponseEntity.ok(response);
    }

    // --- NUEVO: ENDPOINT PATCH PARA CAMBIAR ESTADO ---
    @PatchMapping("/{id}/status")
    public ResponseEntity<SaleResponse> updateStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> payload) {
        
        String newStatus = payload.get("status");
        if (newStatus == null) {
            return ResponseEntity.badRequest().build();
        }

        SaleResponse response = saleService.updateStatus(id, newStatus);
        return ResponseEntity.ok(response);
    }
    // -------------------------------------------------

    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<Sale>> getSalesByClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(saleService.getSalesByClient(clientId));
    }
}