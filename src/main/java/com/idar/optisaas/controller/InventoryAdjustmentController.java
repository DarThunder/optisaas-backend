package com.idar.optisaas.controller;

import com.idar.optisaas.dto.InventoryAdjustmentRequest;
import com.idar.optisaas.entity.InventoryAdjustment;
import com.idar.optisaas.service.InventoryAdjustmentService;
import com.idar.optisaas.util.AdjustmentReason;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ajustes de inventario (mermas y correcciones). Restringido a Dueño/Gerente en SecurityConfig.
 */
@RestController
@RequestMapping("/api/inventory-adjustments")
public class InventoryAdjustmentController {

    @Autowired private InventoryAdjustmentService adjustmentService;

    @PostMapping
    public ResponseEntity<InventoryAdjustment> adjust(@Valid @RequestBody InventoryAdjustmentRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(adjustmentService.adjust(request, auth.getName()));
    }

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) AdjustmentReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Page<InventoryAdjustment> result = adjustmentService.search(productId, reason, from, to, page, size);

        List<Map<String, Object>> items = result.getContent().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("productId", a.getProduct().getId());
            m.put("productName", a.getProduct().getBrand() + " " + a.getProduct().getModel());
            m.put("reason", a.getReason());
            m.put("note", a.getNote());
            m.put("previousQuantity", a.getPreviousQuantity());
            m.put("newQuantity", a.getNewQuantity());
            m.put("delta", a.getDelta());
            m.put("unitCostSnapshot", a.getUnitCostSnapshot());
            m.put("adjustedByName", a.getAdjustedBy() != null ? a.getAdjustedBy().getFullName() : null);
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    /** Catálogo de motivos, para poblar el selector en el frontend. */
    @GetMapping("/reasons")
    public ResponseEntity<?> reasons() {
        return ResponseEntity.ok(AdjustmentReason.values());
    }
}
