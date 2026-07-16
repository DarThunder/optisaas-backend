package com.idar.optisaas.service;

import com.idar.optisaas.dto.SupplierRequest;
import com.idar.optisaas.entity.Supplier;
import com.idar.optisaas.repository.SupplierRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AuditAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Catálogo de proveedores de la sucursal.
 *
 * No se borran: se desactivan. Las órdenes de compra históricas tienen que seguir diciendo
 * a quién se le compró.
 */
@Service
public class SupplierService {

    @Autowired private SupplierRepository supplierRepository;
    @Autowired private AuditService auditService;

    @Transactional(readOnly = true)
    public List<Supplier> getAll(boolean onlyActive) {
        Long branchId = TenantContext.getCurrentBranch();
        return onlyActive
                ? supplierRepository.findByBranchIdAndActiveOrderByNameAsc(branchId, true)
                : supplierRepository.findByBranchIdOrderByNameAsc(branchId);
    }

    @Transactional(readOnly = true)
    public Supplier getById(Long id) {
        return requireOwn(id);
    }

    @Transactional
    public Supplier create(SupplierRequest request) {
        Supplier supplier = new Supplier();
        apply(supplier, request);
        Supplier saved = supplierRepository.save(supplier);

        auditService.log(AuditAction.SUPPLIER_CREATED, "Supplier", saved.getId(), "nombre: " + saved.getName());
        return saved;
    }

    @Transactional
    public Supplier update(Long id, SupplierRequest request) {
        Supplier supplier = requireOwn(id);
        String previousName = supplier.getName();
        apply(supplier, request);
        Supplier saved = supplierRepository.save(supplier);

        auditService.log(AuditAction.SUPPLIER_UPDATED, "Supplier", saved.getId(),
                "nombre: " + previousName + " -> " + saved.getName());
        return saved;
    }

    /** Alta/baja lógica: deja de ofrecerse al crear órdenes, pero conserva su historial. */
    @Transactional
    public Supplier setActive(Long id, boolean active) {
        Supplier supplier = requireOwn(id);
        supplier.setActive(active);
        Supplier saved = supplierRepository.save(supplier);

        auditService.log(AuditAction.SUPPLIER_UPDATED, "Supplier", saved.getId(),
                (active ? "reactivado" : "desactivado") + ": " + saved.getName());
        return saved;
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.getName().trim());
        supplier.setContactName(request.getContactName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setNotes(request.getNotes());
    }

    /** Acota por sucursal SIEMPRE de forma explícita: el filtro de Hibernate no es confiable. */
    private Supplier requireOwn(Long id) {
        Long branchId = TenantContext.getCurrentBranch();
        return supplierRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado o no pertenece a esta sucursal"));
    }
}
