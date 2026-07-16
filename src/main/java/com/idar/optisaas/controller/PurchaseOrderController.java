package com.idar.optisaas.controller;

import com.idar.optisaas.dto.PurchaseOrderRequest;
import com.idar.optisaas.dto.ReceiveRequest;
import com.idar.optisaas.entity.PurchaseOrder;
import com.idar.optisaas.service.IdempotencyService;
import com.idar.optisaas.service.PurchaseOrderService;
import com.idar.optisaas.util.IdempotencyScope;
import com.idar.optisaas.util.PurchaseOrderStatus;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Órdenes de compra. Restringido a Dueño/Gerente en SecurityConfig.
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    @Autowired private PurchaseOrderService purchaseOrderService;
    @Autowired private IdempotencyService idempotencyService;

    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> getAll(@RequestParam(required = false) PurchaseOrderStatus status) {
        return ResponseEntity.ok(purchaseOrderService.getAll(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrder> getById(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.getById(id));
    }

    @PostMapping
    public ResponseEntity<PurchaseOrder> create(@Valid @RequestBody PurchaseOrderRequest request) {
        return ResponseEntity.ok(purchaseOrderService.create(request, currentUsername()));
    }

    @PatchMapping("/{id}/confirm")
    public ResponseEntity<PurchaseOrder> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseOrderService.confirm(id));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<PurchaseOrder> cancel(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, String> payload) {
        String reason = payload != null ? payload.get("reason") : null;
        return ResponseEntity.ok(purchaseOrderService.cancel(id, reason));
    }

    /**
     * Entrada de mercancía: suma stock y recalcula el costo promedio. Es la operación que un
     * reintento duplicaría, así que va protegida con llave de idempotencia (ver SaleController
     * para el porqué de envolverla aquí y no dentro del servicio).
     */
    @PostMapping("/{id}/receive")
    public ResponseEntity<PurchaseOrder> receive(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReceiveRequest request) {

        String username = currentUsername();
        PurchaseOrder result = idempotencyService.run(
                IdempotencyScope.PURCHASE_RECEIPT,
                idempotencyKey,
                List.of(id, request),
                () -> purchaseOrderService.receive(id, request, username),
                PurchaseOrder::getId,
                purchaseOrderService::getById);

        return ResponseEntity.ok(result);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }
}
