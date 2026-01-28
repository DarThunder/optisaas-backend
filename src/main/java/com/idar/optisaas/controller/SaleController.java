package com.idar.optisaas.controller;

import com.idar.optisaas.service.SaleService;
import com.idar.optisaas.dto.*;
// Eliminamos import com.idar.optisaas.entity.Sale; si ya no se usa directamente en los retornos

import java.util.List;

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

    // --- CORRECCIÓN AQUÍ: Cambiamos List<Sale> a List<SaleResponse> ---
    @GetMapping
    public ResponseEntity<List<SaleResponse>> getAllSales() {
        return ResponseEntity.ok(saleService.getAllSales());
    }
    // ------------------------------------------------------------------

    @PostMapping("/{id}/payments")
    public ResponseEntity<SaleResponse> addPayment(
            @PathVariable Long id, 
            @Valid @RequestBody PaymentRequest request) {
            
        SaleResponse response = saleService.addPayment(id, request);
        return ResponseEntity.ok(response);
    }

    // --- NOTA: Si getSalesByClient en el servicio devuelve List<Sale> (entidades)
    // debes decidir si cambiar el servicio a DTOs o mantenerlo así.
    // Si el servicio devuelve List<Sale>, este método debe quedar así:
    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<com.idar.optisaas.entity.Sale>> getSalesByClient(@PathVariable Long clientId) {
        return ResponseEntity.ok(saleService.getSalesByClient(clientId));
    }
    // Si prefieres DTOs, tendrías que mapearlo en el servicio primero.
}