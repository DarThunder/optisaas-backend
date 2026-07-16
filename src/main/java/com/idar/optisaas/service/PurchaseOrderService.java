package com.idar.optisaas.service;

import com.idar.optisaas.dto.PurchaseOrderItemRequest;
import com.idar.optisaas.dto.PurchaseOrderRequest;
import com.idar.optisaas.dto.ReceiveItemRequest;
import com.idar.optisaas.dto.ReceiveRequest;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.repository.ProductRepository;
import com.idar.optisaas.repository.PurchaseOrderRepository;
import com.idar.optisaas.repository.SupplierRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.PurchaseOrderStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Órdenes de compra y recepción de mercancía.
 *
 * Regla central: **pedir no es tener**. Crear o confirmar una orden no toca el inventario;
 * solo {@link #receive} suma stock, y es también el único punto donde se recalcula el costo
 * promedio ponderado del producto, que es lo que sostiene la valuación del inventario.
 */
@Service
public class PurchaseOrderService {

    @Autowired private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;

    @Transactional(readOnly = true)
    public List<PurchaseOrder> getAll(PurchaseOrderStatus status) {
        Long branchId = TenantContext.getCurrentBranch();
        return status != null
                ? purchaseOrderRepository.findByBranchIdAndStatusOrderByCreatedAtDesc(branchId, status)
                : purchaseOrderRepository.findByBranchIdOrderByCreatedAtDesc(branchId);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getById(Long id) {
        return requireOwn(id);
    }

    // ------------------------- Alta y ciclo de vida -------------------------

    @Transactional
    public PurchaseOrder create(PurchaseOrderRequest request, String username) {
        Long branchId = TenantContext.getCurrentBranch();

        Supplier supplier = supplierRepository.findByIdAndBranchId(request.getSupplierId(), branchId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado o no pertenece a esta sucursal"));
        if (!supplier.isActive()) {
            throw new RuntimeException("El proveedor " + supplier.getName() + " está desactivado");
        }

        User creator = userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        PurchaseOrder order = new PurchaseOrder();
        order.setSupplier(supplier);
        order.setCreatedBy(creator);
        order.setReferenceCode(request.getReferenceCode());
        order.setExpectedDate(request.getExpectedDate());
        order.setNotes(request.getNotes());
        order.setStatus(request.isConfirm() ? PurchaseOrderStatus.ORDERED : PurchaseOrderStatus.DRAFT);

        Set<Long> seenProducts = new HashSet<>();
        List<PurchaseOrderItem> items = new ArrayList<>();

        for (PurchaseOrderItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + itemReq.getProductId()));
            if (!product.getBranchId().equals(branchId)) {
                throw new RuntimeException("El producto " + product.getModel() + " no pertenece a esta sucursal");
            }
            if (!seenProducts.add(product.getId())) {
                throw new RuntimeException("El producto " + product.getModel()
                        + " viene repetido: júntalo en un solo renglón");
            }

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPurchaseOrder(order);
            item.setProduct(product);
            item.setQuantityOrdered(itemReq.getQuantity());
            item.setQuantityReceived(0);
            item.setUnitCost(itemReq.getUnitCost());
            items.add(item);
        }
        order.setItems(items);

        PurchaseOrder saved = purchaseOrderRepository.save(order);

        auditService.log(AuditAction.PURCHASE_ORDER_CREATED, "PurchaseOrder", saved.getId(),
                "proveedor: " + supplier.getName()
                        + "; renglones: " + items.size()
                        + "; total pedido: " + saved.getOrderedTotal()
                        + "; estado: " + saved.getStatus());

        return saved;
    }

    /** Manda la orden al proveedor (DRAFT -> ORDERED). Sigue sin tocar inventario. */
    @Transactional
    public PurchaseOrder confirm(Long id) {
        PurchaseOrder order = requireOwn(id);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw new RuntimeException("Solo se puede confirmar una orden en borrador (está en " + order.getStatus() + ")");
        }
        order.setStatus(PurchaseOrderStatus.ORDERED);
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        auditService.log(AuditAction.PURCHASE_ORDER_CONFIRMED, "PurchaseOrder", saved.getId(),
                "proveedor: " + saved.getSupplier().getName() + "; total: " + saved.getOrderedTotal());
        return saved;
    }

    /**
     * Cancela lo que falta por llegar. La mercancía ya recibida NO se retira del stock:
     * eso sería un ajuste de inventario o una devolución al proveedor, no una cancelación.
     */
    @Transactional
    public PurchaseOrder cancel(Long id, String reason) {
        PurchaseOrder order = requireOwn(id);
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new RuntimeException("No se puede cancelar una orden ya recibida por completo");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new RuntimeException("Esta orden ya estaba cancelada");
        }

        PurchaseOrderStatus previous = order.getStatus();
        order.setStatus(PurchaseOrderStatus.CANCELLED);
        PurchaseOrder saved = purchaseOrderRepository.save(order);

        auditService.log(AuditAction.PURCHASE_ORDER_CANCELLED, "PurchaseOrder", saved.getId(),
                "estado: " + previous + " -> CANCELLED"
                        + "; recibido antes de cancelar: " + saved.getReceivedTotal()
                        + (reason != null && !reason.isBlank() ? "; motivo: " + reason.trim() : ""));
        return saved;
    }

    // ------------------------- Recepción -------------------------

    /**
     * Entrada de mercancía: suma stock y recalcula el costo promedio ponderado.
     * Admite recepciones parciales; la orden se cierra sola cuando llega todo.
     */
    @Transactional
    public PurchaseOrder receive(Long id, ReceiveRequest request, String username) {
        Long branchId = TenantContext.getCurrentBranch();

        // Bloqueo de la orden: dos recepciones simultáneas no deben sumar lo mismo dos veces.
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RuntimeException("Orden de compra no encontrada"));
        if (!order.getBranchId().equals(branchId)) {
            throw new RuntimeException("No tienes permiso para recibir órdenes de otra sucursal");
        }
        if (order.getStatus() == PurchaseOrderStatus.DRAFT) {
            throw new RuntimeException("Confirma la orden antes de recibir mercancía");
        }
        if (order.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new RuntimeException("No se puede recibir mercancía de una orden cancelada");
        }
        if (order.getStatus() == PurchaseOrderStatus.RECEIVED) {
            throw new RuntimeException("Esta orden ya se recibió por completo");
        }

        userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Map<Long, PurchaseOrderItem> itemsById = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));

        Set<Long> seen = new HashSet<>();
        List<String> received = new ArrayList<>();

        for (ReceiveItemRequest itemReq : request.getItems()) {
            PurchaseOrderItem item = itemsById.get(itemReq.getPurchaseOrderItemId());
            if (item == null) {
                throw new RuntimeException("El renglón " + itemReq.getPurchaseOrderItemId() + " no pertenece a esta orden");
            }
            if (!seen.add(item.getId())) {
                throw new RuntimeException("El renglón de " + item.getProduct().getModel()
                        + " viene repetido: junta las piezas en uno solo");
            }

            int quantity = itemReq.getQuantity();
            if (quantity > item.getPendingQuantity()) {
                throw new RuntimeException("De " + item.getProduct().getModel()
                        + " solo faltan " + item.getPendingQuantity() + " pieza(s) por recibir");
            }

            // La factura manda: si el costo real llegó distinto al pactado, ese es el que se
            // debe y el que entra al promedio.
            BigDecimal unitCost = itemReq.getUnitCost() != null ? itemReq.getUnitCost() : item.getUnitCost();

            Product product = productRepository.findByIdForUpdate(item.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + item.getProduct().getId()));

            int currentStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;
            BigDecimal newCost = weightedAverageCost(currentStock, product.getCost(), quantity, unitCost);

            product.setStockQuantity(currentStock + quantity);
            product.setCost(newCost);
            productRepository.save(product);

            item.setQuantityReceived(item.getQuantityReceived() + quantity);
            item.setReceivedCostTotal(item.getReceivedCostTotal()
                    .add(unitCost.multiply(BigDecimal.valueOf(quantity))));
            // El unitario queda como referencia del último precio conocido (lo que costarían las
            // piezas que faltan); lo que se debe se lleva aparte, recepción por recepción.
            item.setUnitCost(unitCost);

            received.add(quantity + "x " + product.getModel() + " @ " + unitCost + " (costo prom.: " + newCost + ")");
        }

        if (request.getReferenceCode() != null && !request.getReferenceCode().isBlank()) {
            order.setReferenceCode(request.getReferenceCode());
        }
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            order.setNotes(request.getNotes());
        }

        boolean complete = order.isFullyReceived();
        order.setStatus(complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (complete) {
            order.setReceivedAt(LocalDateTime.now());
        }

        PurchaseOrder saved = purchaseOrderRepository.save(order);

        auditService.log(AuditAction.PURCHASE_ORDER_RECEIVED, "PurchaseOrder", saved.getId(),
                "proveedor: " + saved.getSupplier().getName()
                        + "; recibido: " + String.join(", ", received)
                        + "; estado: " + saved.getStatus()
                        + "; total recibido acumulado: " + saved.getReceivedTotal());

        return saved;
    }

    /**
     * Costo promedio ponderado: mezcla el valor del inventario que ya estaba con el de lo que entra.
     *
     * Si no hay stock previo (o su costo era cero, como en los productos que nacieron antes de
     * capturar costos), no hay nada que promediar y el costo del inventario pasa a ser el de
     * esta compra: es la única cifra real que tenemos.
     */
    private BigDecimal weightedAverageCost(int currentStock, BigDecimal currentCost, int incomingQuantity, BigDecimal incomingCost) {
        if (currentStock <= 0 || currentCost == null || currentCost.compareTo(BigDecimal.ZERO) <= 0) {
            return incomingCost.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal currentValue = currentCost.multiply(BigDecimal.valueOf(currentStock));
        BigDecimal incomingValue = incomingCost.multiply(BigDecimal.valueOf(incomingQuantity));
        return currentValue.add(incomingValue)
                .divide(BigDecimal.valueOf(currentStock + incomingQuantity), 2, RoundingMode.HALF_UP);
    }

    /** Acota por sucursal SIEMPRE de forma explícita: el filtro de Hibernate no es confiable. */
    private PurchaseOrder requireOwn(Long id) {
        Long branchId = TenantContext.getCurrentBranch();
        return purchaseOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new RuntimeException("Orden de compra no encontrada o no pertenece a esta sucursal"));
    }
}
