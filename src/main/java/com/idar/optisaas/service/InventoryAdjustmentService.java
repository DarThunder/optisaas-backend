package com.idar.optisaas.service;

import com.idar.optisaas.dto.InventoryAdjustmentRequest;
import com.idar.optisaas.entity.InventoryAdjustment;
import com.idar.optisaas.entity.Product;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.InventoryAdjustmentRepository;
import com.idar.optisaas.repository.ProductRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AdjustmentReason;
import com.idar.optisaas.util.AuditAction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ajustes manuales de inventario: mermas, robos, correcciones de conteo, caducidades y
 * devoluciones al proveedor.
 *
 * Cada ajuste deja un registro con motivo, responsable y el costo del momento: es el rastro
 * de por qué el stock del sistema dejó de coincidir con el anaquel, y lo que permite sumar
 * cuánto dinero se pierde por cada causa.
 */
@Service
public class InventoryAdjustmentService {

    @Autowired private InventoryAdjustmentRepository adjustmentRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;

    @Transactional
    public InventoryAdjustment adjust(InventoryAdjustmentRequest request, String username) {
        Long branchId = TenantContext.getCurrentBranch();

        AdjustmentReason reason = parseReason(request.getReason());
        User adjustedBy = userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Bloqueo del producto: un ajuste y una venta simultáneos no deben pisarse el stock.
        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + request.getProductId()));
        if (!product.getBranchId().equals(branchId)) {
            throw new RuntimeException("El producto no pertenece a esta sucursal");
        }

        int previousQuantity = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
        int delta = resolveDelta(request, previousQuantity);
        int newQuantity = previousQuantity + delta;

        if (newQuantity < 0) {
            throw new RuntimeException("El ajuste dejaría el stock en " + newQuantity
                    + ": no puedes descontar más piezas de las que hay (" + previousQuantity + ")");
        }

        product.setStockQuantity(newQuantity);
        productRepository.save(product);

        InventoryAdjustment adjustment = new InventoryAdjustment();
        adjustment.setProduct(product);
        adjustment.setReason(reason);
        adjustment.setNote(request.getNote());
        adjustment.setPreviousQuantity(previousQuantity);
        adjustment.setNewQuantity(newQuantity);
        adjustment.setDelta(delta);
        adjustment.setUnitCostSnapshot(product.getCost() != null ? product.getCost() : BigDecimal.ZERO);
        adjustment.setAdjustedBy(adjustedBy);

        InventoryAdjustment saved = adjustmentRepository.save(adjustment);

        auditService.log(AuditAction.INVENTORY_ADJUSTED, "Product", product.getId(),
                "producto: " + product.getModel()
                        + "; stock: " + previousQuantity + " -> " + newQuantity + " (" + (delta > 0 ? "+" : "") + delta + ")"
                        + "; motivo: " + reason
                        + "; valor: " + saved.getUnitCostSnapshot().multiply(BigDecimal.valueOf(Math.abs(delta)))
                        + (request.getNote() != null && !request.getNote().isBlank() ? "; nota: " + request.getNote().trim() : ""));

        return saved;
    }

    /**
     * Historial de ajustes de la sucursal, con filtros opcionales.
     * Se usan Specifications (no ":param IS NULL OR col = :param", que en PostgreSQL falla
     * al no poder inferir el tipo del parámetro).
     */
    @Transactional(readOnly = true)
    public Page<InventoryAdjustment> search(Long productId, AdjustmentReason reason,
                                            LocalDate from, LocalDate to, int page, int size) {
        Long branchId = TenantContext.getCurrentBranch();

        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        // 'to' es inclusivo para el usuario: llega hasta el final de ese día.
        LocalDateTime toTs = to != null ? to.plusDays(1).atStartOfDay() : null;

        Specification<InventoryAdjustment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("branchId"), branchId));

            if (productId != null) predicates.add(cb.equal(root.get("product").get("id"), productId));
            if (reason != null) predicates.add(cb.equal(root.get("reason"), reason));
            if (fromTs != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTs));
            if (toTs != null) predicates.add(cb.lessThan(root.get("createdAt"), toTs));

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        var pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return adjustmentRepository.findAll(spec, pageable);
    }

    /**
     * Las dos formas de ajustar: por conteo físico (newQuantity) o por movimiento (delta).
     * Se exige exactamente una: recibir ambas sería ambiguo y silenciosamente peligroso.
     */
    private int resolveDelta(InventoryAdjustmentRequest request, int previousQuantity) {
        boolean hasNewQuantity = request.getNewQuantity() != null;
        boolean hasDelta = request.getDelta() != null;

        if (hasNewQuantity == hasDelta) {
            throw new RuntimeException("Manda newQuantity (conteo físico) o delta (piezas a sumar/restar), pero no ambos");
        }
        if (hasNewQuantity) {
            if (request.getNewQuantity() < 0) throw new RuntimeException("El conteo no puede ser negativo");
            return request.getNewQuantity() - previousQuantity;
        }
        if (request.getDelta() == 0) throw new RuntimeException("El ajuste no puede ser de cero piezas");
        return request.getDelta();
    }

    private AdjustmentReason parseReason(String reason) {
        try {
            return AdjustmentReason.valueOf(reason.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Motivo de ajuste inválido: " + reason);
        }
    }

    /** Límite duro para que una petición no pueda pedir todo el historial de golpe. */
    private int normalizeSize(int size) {
        if (size < 1) return 50;
        return Math.min(size, 200);
    }
}
