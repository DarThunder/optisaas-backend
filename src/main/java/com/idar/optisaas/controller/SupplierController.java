package com.idar.optisaas.controller;

import com.idar.optisaas.dto.SupplierRequest;
import com.idar.optisaas.entity.Supplier;
import com.idar.optisaas.service.SupplierService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Catálogo de proveedores. Restringido a Dueño/Gerente en SecurityConfig.
 */
@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    @Autowired private SupplierService supplierService;

    @GetMapping
    public ResponseEntity<List<Supplier>> getAll(@RequestParam(defaultValue = "false") boolean onlyActive) {
        return ResponseEntity.ok(supplierService.getAll(onlyActive));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getById(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getById(id));
    }

    @PostMapping
    public ResponseEntity<Supplier> create(@Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        return ResponseEntity.ok(supplierService.update(id, request));
    }

    /** Baja lógica: el proveedor deja de ofrecerse, pero su historial de compras se conserva. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Supplier> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.setActive(id, false));
    }

    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<Supplier> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.setActive(id, true));
    }
}
