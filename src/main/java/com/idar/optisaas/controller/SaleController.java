package com.idar.optisaas.controller;

import com.idar.optisaas.service.IdempotencyService;
import com.idar.optisaas.service.RefundService;
import com.idar.optisaas.service.SaleService;
import com.idar.optisaas.dto.*;
import com.idar.optisaas.util.IdempotencyScope;

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

    @Autowired
    private RefundService refundService;

    @Autowired
    private IdempotencyService idempotencyService;

    /**
     * La envoltura de idempotencia vive aquí y no dentro de SaleService porque una llamada
     * interna (this.createSale()) se saltaría el proxy de Spring y la operación de negocio
     * se quedaría sin su @Transactional. El controlador sí tiene el proxy del servicio.
     */
    @PostMapping
    public ResponseEntity<SaleResponse> createSale(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SaleRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String sellerEmail = auth.getName();

        SaleResponse response = idempotencyService.run(
                IdempotencyScope.SALE_CREATE,
                idempotencyKey,
                request,
                () -> saleService.createSale(request, sellerEmail),
                SaleResponse::getSaleId,
                saleService::getSaleById);

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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {

        // La huella incluye la venta: la misma llave aplicada a otra venta no es un reintento,
        // es un error del cliente, y debe rechazarse en vez de repetir la respuesta ajena.
        SaleResponse response = idempotencyService.run(
                IdempotencyScope.PAYMENT_ADD,
                idempotencyKey,
                List.of(id, request),
                () -> saleService.addPayment(id, request),
                SaleResponse::getSaleId,
                saleService::getSaleById);

        return ResponseEntity.ok(response);
    }

    /**
     * Devolución de mercancía de una venta (total o por partidas).
     * Restringido a Dueño/Gerente en SecurityConfig: reingresa stock y saca dinero de la caja.
     */
    @PostMapping("/{id}/refunds")
    public ResponseEntity<RefundResponse> createRefund(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RefundRequest request) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        RefundResponse response = idempotencyService.run(
                IdempotencyScope.SALE_REFUND,
                idempotencyKey,
                List.of(id, request),
                () -> refundService.createRefund(id, request, username),
                RefundResponse::getRefundId,
                refundService::getRefundById);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/refunds")
    public ResponseEntity<List<RefundResponse>> getRefunds(@PathVariable Long id) {
        return ResponseEntity.ok(refundService.getRefundsBySale(id));
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

    /**
     * Envía el comprobante de la venta por correo, a petición de quien atiende.
     * Si el cuerpo trae `email` se usa ese; si no, el que tenga registrado el cliente.
     */
    @PostMapping("/{id}/send-receipt")
    public ResponseEntity<?> sendReceipt(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload) {
        try {
            String email = payload != null ? payload.get("email") : null;
            String sentTo = saleService.sendReceipt(id, email);
            return ResponseEntity.ok(Map.of(
                    "message", "Comprobante enviado a " + sentTo,
                    "sentTo", sentTo));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<SaleResponse>> getSalesByClient(@PathVariable Long clientId) {
        // Ahora devolvemos SaleResponse, que es seguro para JSON
        return ResponseEntity.ok(saleService.getSalesByClient(clientId));
    }
}