package com.idar.optisaas.service;

import com.idar.optisaas.dto.RefundItemRequest;
import com.idar.optisaas.dto.RefundItemResponse;
import com.idar.optisaas.dto.RefundRequest;
import com.idar.optisaas.dto.RefundResponse;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.PaymentMethod;
import com.idar.optisaas.util.SaleStatus;
import com.idar.optisaas.util.StockPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Devoluciones de mercancía y reembolsos.
 *
 * Reglas del modelo (ver también {@link Sale#getRemainingBalance()}):
 *  - La venta original nunca se reescribe: total y pagado son historia bruta.
 *  - El valor devuelto lleva el descuento de la venta prorrateado, o se devolvería más
 *    dinero del que el cliente pagó por esa pieza.
 *  - Solo se reembolsa lo que el cliente pagó de más respecto de lo que se queda. Si la venta
 *    traía saldo, la devolución baja la deuda y no sale dinero de la caja.
 */
@Service
public class RefundService {

    @Autowired private SaleRepository saleRepository;
    @Autowired private RefundRepository refundRepository;
    @Autowired private RefundItemRepository refundItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditService auditService;

    @Transactional
    public RefundResponse createRefund(Long saleId, RefundRequest request, String username) {
        Long branchId = TenantContext.getCurrentBranch();

        // Bloqueo de la venta: dos devoluciones simultáneas no deben poder devolver
        // la misma pieza dos veces.
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (!sale.getBranch().getId().equals(branchId)) {
            throw new RuntimeException("No tienes permiso para devolver ventas de otra sucursal");
        }
        if (sale.getStatus() == SaleStatus.QUOTATION) {
            throw new RuntimeException("Una cotización no se devuelve: no hubo entrega ni cobro");
        }
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new RuntimeException("No se puede devolver una venta cancelada");
        }
        if (sale.getStatus() == SaleStatus.RETURNED) {
            throw new RuntimeException("Esta venta ya se devolvió por completo");
        }

        User processedBy = userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Map<Long, Integer> alreadyReturned = returnedQuantitiesBySaleItem(saleId);
        Map<Long, SaleItem> saleItems = sale.getItems().stream()
                .collect(Collectors.toMap(SaleItem::getId, item -> item));

        Refund refund = new Refund();
        refund.setSale(sale);
        refund.setReason(request.getReason().trim());
        refund.setProcessedBy(processedBy);

        Map<Long, Integer> returningNow = new HashMap<>();
        BigDecimal returnedValue = BigDecimal.ZERO;

        for (RefundItemRequest itemReq : request.getItems()) {
            SaleItem saleItem = saleItems.get(itemReq.getSaleItemId());
            if (saleItem == null) {
                throw new RuntimeException("La partida " + itemReq.getSaleItemId() + " no pertenece a esta venta");
            }
            if (returningNow.containsKey(saleItem.getId())) {
                throw new RuntimeException("La partida " + saleItem.getProductNameSnapshot()
                        + " viene repetida en la devolución: junta las piezas en un solo renglón");
            }

            int quantity = itemReq.getQuantity();
            int available = saleItem.getQuantity() - alreadyReturned.getOrDefault(saleItem.getId(), 0);
            if (quantity > available) {
                throw new RuntimeException("De " + saleItem.getProductNameSnapshot()
                        + " solo quedan " + available + " pieza(s) por devolver");
            }

            BigDecimal amount = netUnitValue(sale, saleItem).multiply(BigDecimal.valueOf(quantity));
            boolean restocked = itemReq.isRestock() && StockPolicy.managesStock(saleItem.getProduct().getType());
            if (restocked) {
                Product product = productRepository.findByIdForUpdate(saleItem.getProduct().getId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + saleItem.getProduct().getId()));
                product.setStockQuantity(product.getStockQuantity() + quantity);
                productRepository.save(product);
            }

            RefundItem refundItem = new RefundItem();
            refundItem.setRefund(refund);
            refundItem.setSaleItem(saleItem);
            refundItem.setQuantity(quantity);
            refundItem.setAmount(amount);
            refundItem.setRestocked(restocked);
            refund.getItems().add(refundItem);

            returningNow.put(saleItem.getId(), quantity);
            returnedValue = returnedValue.add(amount);
        }

        boolean fullyReturned = isFullyReturned(sale, alreadyReturned, returningNow);

        BigDecimal newReturnedAmount;
        if (fullyReturned) {
            // Al devolver la última pieza, el valor devuelto tiene que cuadrar EXACTO con el total:
            // el prorrateo del descuento redondea a centavos y podría dejar una diferencia.
            newReturnedAmount = sale.getTotalAmount();
            returnedValue = newReturnedAmount.subtract(sale.getReturnedAmount());
        } else {
            newReturnedAmount = sale.getReturnedAmount().add(returnedValue);
        }

        BigDecimal refundAmount = resolveRefundAmount(sale, request, newReturnedAmount);
        PaymentMethod method = resolveMethod(request, refundAmount);

        refund.setReturnedValue(returnedValue);
        refund.setAmount(refundAmount);
        refund.setMethod(method);

        sale.setReturnedAmount(newReturnedAmount);
        sale.setRefundedAmount(sale.getRefundedAmount().add(refundAmount));
        sale.setStatus(fullyReturned ? SaleStatus.RETURNED : SaleStatus.PARTIALLY_RETURNED);

        Refund savedRefund = refundRepository.save(refund);
        saleRepository.save(sale);

        auditService.log(AuditAction.SALE_REFUNDED, "Sale", sale.getId(),
                "devolución " + savedRefund.getId()
                        + "; mercancía: " + returnedValue
                        + "; reembolso: " + refundAmount + (method != null ? " por " + method : " (sin salida de caja)")
                        + "; piezas: " + describeItems(savedRefund)
                        + "; motivo: " + savedRefund.getReason()
                        + "; estado: " + sale.getStatus()
                        + "; saldo: " + sale.getRemainingBalance());

        return mapToResponse(savedRefund);
    }

    @Transactional(readOnly = true)
    public RefundResponse getRefundById(Long refundId) {
        Long branchId = TenantContext.getCurrentBranch();
        Refund refund = refundRepository.findByIdAndBranchId(refundId, branchId)
                .orElseThrow(() -> new RuntimeException("Devolución no encontrada o no pertenece a esta sucursal"));
        return mapToResponse(refund);
    }

    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsBySale(Long saleId) {
        Long branchId = TenantContext.getCurrentBranch();
        // Acota por sucursal antes de listar: el id de venta por sí solo no autoriza nada.
        saleRepository.findByIdAndBranchId(saleId, branchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada o no pertenece a esta sucursal"));

        return refundRepository.findBySale_IdOrderByCreatedAtDesc(saleId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ------------------------- Reglas de cálculo -------------------------

    /**
     * Valor unitario de una partida con el descuento de la venta prorrateado según su peso
     * en el subtotal. Sin esto, devolver una pieza de una venta con 20% de descuento
     * reembolsaría el precio de lista, es decir, más de lo que el cliente pagó.
     */
    private BigDecimal netUnitValue(Sale sale, SaleItem item) {
        BigDecimal subtotal = item.getSubtotal();
        BigDecimal quantity = BigDecimal.valueOf(item.getQuantity());
        BigDecimal discount = sale.getDiscountAmount() != null ? sale.getDiscountAmount() : BigDecimal.ZERO;

        if (discount.compareTo(BigDecimal.ZERO) <= 0) {
            return subtotal.divide(quantity, 2, RoundingMode.HALF_UP);
        }

        BigDecimal grossTotal = sale.getItems().stream()
                .map(SaleItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grossTotal.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal itemDiscount = discount.multiply(subtotal)
                .divide(grossTotal, 2, RoundingMode.HALF_UP);
        return subtotal.subtract(itemDiscount).divide(quantity, 2, RoundingMode.HALF_UP);
    }

    /**
     * Tope del reembolso: lo que el cliente pagó en neto menos lo que se queda.
     * Si pagó $1000 de una venta de $1000 y devuelve $300 de mercancía, se le regresan $300.
     * Si solo había dado $200 de anticipo, no sale dinero: baja su deuda.
     */
    private BigDecimal resolveRefundAmount(Sale sale, RefundRequest request, BigDecimal newReturnedAmount) {
        BigDecimal keptValue = sale.getTotalAmount().subtract(newReturnedAmount);
        BigDecimal netPaid = sale.getPaidAmount().subtract(sale.getRefundedAmount());
        BigDecimal maxRefund = netPaid.subtract(keptValue).max(BigDecimal.ZERO);

        if (request.getAmount() == null) {
            return maxRefund;
        }
        if (request.getAmount().compareTo(maxRefund) > 0) {
            throw new RuntimeException("El reembolso máximo para esta devolución es $" + maxRefund
                    + " (el cliente pagó $" + netPaid + " y se queda con mercancía por $" + keptValue + ")");
        }
        return request.getAmount();
    }

    private PaymentMethod resolveMethod(RefundRequest request, BigDecimal refundAmount) {
        if (refundAmount.compareTo(BigDecimal.ZERO) == 0) {
            return null; // No salió dinero: no hay medio que registrar.
        }
        if (request.getMethod() == null || request.getMethod().isBlank()) {
            throw new RuntimeException("Indica por dónde se reembolsa el dinero (CASH, CARD, TRANSFER...)");
        }
        try {
            return PaymentMethod.valueOf(request.getMethod().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Método de reembolso inválido: " + request.getMethod());
        }
    }

    private boolean isFullyReturned(Sale sale, Map<Long, Integer> alreadyReturned, Map<Long, Integer> returningNow) {
        return sale.getItems().stream().allMatch(item -> {
            int returned = alreadyReturned.getOrDefault(item.getId(), 0) + returningNow.getOrDefault(item.getId(), 0);
            return returned >= item.getQuantity();
        });
    }

    private Map<Long, Integer> returnedQuantitiesBySaleItem(Long saleId) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (RefundItem item : refundItemRepository.findBySaleId(saleId)) {
            quantities.merge(item.getSaleItem().getId(), item.getQuantity(), Integer::sum);
        }
        return quantities;
    }

    // ------------------------- Mapeo -------------------------

    private String describeItems(Refund refund) {
        return refund.getItems().stream()
                .map(item -> item.getQuantity() + "x " + item.getSaleItem().getProductNameSnapshot()
                        + (item.isRestocked() ? " (reingresado)" : " (sin reingreso)"))
                .collect(Collectors.joining(", "));
    }

    private RefundResponse mapToResponse(Refund refund) {
        RefundResponse response = new RefundResponse();
        response.setRefundId(refund.getId());
        response.setSaleId(refund.getSale().getId());
        response.setReturnedValue(refund.getReturnedValue());
        response.setAmount(refund.getAmount());
        response.setMethod(refund.getMethod() != null ? refund.getMethod().name() : null);
        response.setReason(refund.getReason());
        response.setProcessedByName(refund.getProcessedBy() != null ? refund.getProcessedBy().getFullName() : null);
        response.setCreatedAt(refund.getCreatedAt());
        response.setSaleStatus(refund.getSale().getStatus().name());
        response.setSaleRemainingBalance(refund.getSale().getRemainingBalance());

        List<RefundItemResponse> items = new ArrayList<>();
        for (RefundItem item : refund.getItems()) {
            RefundItemResponse ir = new RefundItemResponse();
            ir.setSaleItemId(item.getSaleItem().getId());
            ir.setProductNameSnapshot(item.getSaleItem().getProductNameSnapshot());
            ir.setQuantity(item.getQuantity());
            ir.setAmount(item.getAmount());
            ir.setRestocked(item.isRestocked());
            items.add(ir);
        }
        response.setItems(items);
        return response;
    }
}
