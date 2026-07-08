package com.idar.optisaas.service;

import com.idar.optisaas.entity.*;
import com.idar.optisaas.util.*;
import com.idar.optisaas.dto.*;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.security.TenantContext; 

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors; 

@Service
public class SaleService {

    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClinicalRecordRepository clinicalRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserBranchRoleRepository roleRepository;

    // --- OBTENER TODAS LAS VENTAS ---
    public List<SaleResponse> getAllSales() {
        Long currentBranchId = TenantContext.getCurrentBranch();
        List<Sale> sales = saleRepository.findByBranchIdOrderByCreatedAtDesc(currentBranchId);
        return sales.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // --- CREAR VENTA ---
    @Transactional
    public SaleResponse createSale(SaleRequest request, String sellerEmail) {
        Long branchId = TenantContext.getCurrentBranch();
        
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        Client client = null;
        if (request.getClientId() != null) {
            client = clientRepository.findById(request.getClientId())
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

            // El cliente puede pertenecer a cualquier sucursal DEL MISMO DUEÑO (reconocimiento
            // entre sucursales de la cadena), pero no a la cuenta de otro cliente de OptiSaaS.
            Long branchOwnerId = roleRepository.findFirstByBranch_IdAndRole(branchId, Role.OWNER)
                    .map(r -> r.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("La sucursal no tiene OWNER asignado"));

            if (client.getOwnerId() == null || !branchOwnerId.equals(client.getOwnerId())) {
                throw new RuntimeException("El cliente no pertenece a tu cuenta");
            }
        } else if (request.isQuotation()) {
            throw new RuntimeException("Las cotizaciones requieren un cliente");
        }

        User seller = userRepository.findByEmailOrUsername(sellerEmail, sellerEmail)
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado en BD. El sistema intentó buscar esto exactamente: [" + sellerEmail + "]"));

        Sale sale = new Sale();
        sale.setBranch(branch); 
        sale.setClient(client);
        sale.setSeller(seller);
        sale.setCreatedAt(LocalDateTime.now());
        
        if (request.isQuotation()) {
            sale.setStatus(SaleStatus.QUOTATION);
        } else {
            sale.setStatus(SaleStatus.PENDING); 
        }

        List<SaleItem> itemsEntities = new ArrayList<>();
        BigDecimal totalSaleAmount = BigDecimal.ZERO;
        boolean hasManufacturingItems = false;

        for (SaleItemRequest itemReq : request.getItems()) {
            Integer quantity = itemReq.getQuantity() != null ? itemReq.getQuantity() : 1;
            Product product;

            if (itemReq.getProductId() == null) {
                if (itemReq.getManualPrice() == null) {
                    throw new RuntimeException("Los items manuales requieren precio manual");
                }
                product = getManualSaleProduct(branch, branchId);
            } else {
                product = productRepository.findByIdForUpdate(itemReq.getProductId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + itemReq.getProductId()));

                if (!product.getBranch().getId().equals(branchId)) {
                    throw new RuntimeException("El producto " + product.getModel() + " no pertenece a esta sucursal");
                }

                if (shouldManageStock(product) && !request.isQuotation()) {
                    if (product.getStockQuantity() < quantity) {
                        throw new RuntimeException("Stock insuficiente para: " + product.getModel());
                    }
                    product.setStockQuantity(product.getStockQuantity() - quantity);
                    productRepository.save(product);
                }
            }

            if (product.getType() == ProductType.LENS) {
                hasManufacturingItems = true;
            }

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(quantity);

            BigDecimal finalUnitPrice = product.getBasePrice();
            if (itemReq.getManualPrice() != null && itemReq.getManualPrice().compareTo(BigDecimal.ZERO) >= 0) {
                finalUnitPrice = itemReq.getManualPrice();
            }

            item.setUnitPrice(finalUnitPrice);
            BigDecimal subtotal = finalUnitPrice.multiply(new BigDecimal(quantity));
            item.setSubtotal(subtotal);
            item.setProductNameSnapshot(itemReq.getItemName() != null && !itemReq.getItemName().isBlank() ? itemReq.getItemName() : product.getBrand() + " " + product.getModel());

            if (itemReq.getClinicalRecordId() != null) {
                ClinicalRecord record = clinicalRepository.findById(itemReq.getClinicalRecordId())
                        .orElseThrow(() -> new RuntimeException("Receta no encontrada"));
                item.setClinicalRecord(record);
            }

            itemsEntities.add(item);
            totalSaleAmount = totalSaleAmount.add(subtotal);
        }

        sale.setItems(itemsEntities);

        // --- APLICAR DESCUENTO/PROMOCIÓN ---
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getDiscountValue() != null && request.getDiscountValue().compareTo(BigDecimal.ZERO) > 0) {
            if ("PERCENTAGE".equalsIgnoreCase(request.getDiscountType())) {
                discountAmount = totalSaleAmount.multiply(request.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                discountAmount = request.getDiscountValue();
            }
            if (discountAmount.compareTo(totalSaleAmount) > 0) {
                discountAmount = totalSaleAmount;
            }
        }

        BigDecimal finalTotal = totalSaleAmount.subtract(discountAmount);
        sale.setTotalAmount(finalTotal);
        sale.setDiscountAmount(discountAmount);
        sale.setDiscountName(request.getDiscountName());

        // Procesar Pagos Iniciales
        BigDecimal totalPaid = BigDecimal.ZERO;
        List<Payment> paymentEntities = new ArrayList<>();

        if (!request.isQuotation() && request.getPayments() != null) {
            for (PaymentRequest payReq : request.getPayments()) {
                Payment payment = new Payment();
                payment.setSale(sale);
                payment.setAmount(payReq.getAmount());
                payment.setMethod(PaymentMethod.valueOf(payReq.getMethod()));
                payment.setReferenceCode(payReq.getReferenceCode());
                
                paymentEntities.add(payment);
                totalPaid = totalPaid.add(payReq.getAmount());
            }
        }
        
        sale.setPayments(paymentEntities);
        sale.setPaidAmount(totalPaid);

        // --- LÓGICA DE ESTADO INICIAL ---
        if (!request.isQuotation() && !request.isParkSale()) {
            BigDecimal remaining = finalTotal.subtract(totalPaid);
            
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                // Si debe dinero
                if (totalPaid.compareTo(BigDecimal.ZERO) > 0 && hasManufacturingItems) {
                    // Dio anticipo y es lente -> Taller
                    sale.setStatus(SaleStatus.IN_PROCESS);
                } else {
                    // No ha pagado nada o es accesorio -> Pendiente
                    sale.setStatus(SaleStatus.PENDING);
                }
            } else {
                // Pagado completo
                if (hasManufacturingItems) {
                    sale.setStatus(SaleStatus.IN_PROCESS); // Lente pagado va a taller
                } else {
                    sale.setStatus(SaleStatus.COMPLETED); // Accesorio pagado se entrega
                }
            }
        }

        Sale savedSale = saleRepository.save(sale);
        return mapToResponse(savedSale);
    }
    
    private boolean shouldManageStock(Product product) {
        return product.getType() == ProductType.FRAME || product.getType() == ProductType.ACCESSORY;
    }
    private Product getManualSaleProduct(Branch branch, Long branchId) {
        String sku = "MANUAL-SALE-" + branchId;
        return productRepository.findBySku(sku).orElseGet(() -> {
            Product product = new Product();
            product.setBranchId(branchId);
            product.setBranch(branch);
            product.setSku(sku);
            product.setBrand("Manual");
            product.setModel("Venta comun");
            product.setCategory("Venta rapida");
            product.setType(ProductType.SERVICE);
            product.setBasePrice(BigDecimal.ZERO);
            product.setStockQuantity(0);
            return productRepository.save(product);
        });
    }
    // --- MAPPER ---
    private SaleResponse mapToResponse(Sale sale) {
        SaleResponse response = new SaleResponse();
        response.setSaleId(sale.getId());
        response.setStatus(sale.getStatus().name());
        response.setDate(sale.getCreatedAt());
        
        if (sale.getClient() != null) {
            response.setClientName(sale.getClient().getFullName());
            response.setClientPhone(sale.getClient().getPhone());
        } else {
            response.setClientName("Cliente General");
        }
        
        response.setTotalAmount(sale.getTotalAmount());
        response.setPaidAmount(sale.getPaidAmount());
        response.setDiscountAmount(sale.getDiscountAmount());
        response.setDiscountName(sale.getDiscountName());
        
        if (sale.getRemainingBalance() != null) {
             response.setRemainingBalance(sale.getRemainingBalance());
        } else {
             response.setRemainingBalance(sale.getTotalAmount().subtract(sale.getPaidAmount()));
        }
        
        // --- MAPEO DE ITEMS PARA VER DETALLES ---
        if (sale.getItems() != null) {
            List<SaleItemResponse> itemResponses = sale.getItems().stream()
                .map(item -> {
                    SaleItemResponse ir = new SaleItemResponse();
                    ir.setProductId(item.getProduct().getId());
                    ir.setProductNameSnapshot(item.getProductNameSnapshot());
                    ir.setQuantity(item.getQuantity());
                    ir.setUnitPrice(item.getUnitPrice());
                    ir.setSubtotal(item.getSubtotal());
                    return ir;
                })
                .collect(Collectors.toList());
            response.setItems(itemResponses);
        }
        
        return response;
    }

    public SaleResponse getSaleById(Long id) {
        Long currentBranchId = TenantContext.getCurrentBranch();
        Sale sale = saleRepository.findByIdAndBranchId(id, currentBranchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada o no pertenece a esta sucursal"));
        
        return mapToResponse(sale);
    }

    // --- ACTUALIZAR ESTADO (PATCH) ---
    @Transactional
    public SaleResponse updateStatus(Long saleId, String newStatusName) {
        Long currentBranchId = TenantContext.getCurrentBranch();
        
        Sale sale = saleRepository.findByIdAndBranchId(saleId, currentBranchId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada o sin permisos"));

        try {
            SaleStatus status = SaleStatus.valueOf(newStatusName);
            sale.setStatus(status);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Estado inválido: " + newStatusName);
        }

        Sale savedSale = saleRepository.save(sale);
        return mapToResponse(savedSale);
    }

    // --- AGREGAR PAGO ---
    @Transactional
    public SaleResponse addPayment(Long saleId, PaymentRequest paymentRequest) {
        Long currentBranchId = TenantContext.getCurrentBranch();
        
        Sale sale = saleRepository.findByIdForUpdate(saleId)
            .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (!sale.getBranch().getId().equals(currentBranchId)) {
             throw new RuntimeException("No tienes permiso para modificar ventas de otra sucursal");
        }

        if (sale.getStatus() == SaleStatus.CANCELLED) throw new RuntimeException("No se pueden agregar pagos a una venta cancelada");
        if (sale.getStatus() == SaleStatus.QUOTATION) throw new RuntimeException("Una cotización debe convertirse en venta antes de pagar");

        BigDecimal newBalance = sale.getRemainingBalance().subtract(paymentRequest.getAmount());
        
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("El pago excede la deuda actual: $" + sale.getRemainingBalance());
        }

        Payment payment = new Payment();
        payment.setSale(sale);
        payment.setAmount(paymentRequest.getAmount());
        payment.setMethod(PaymentMethod.valueOf(paymentRequest.getMethod()));
        payment.setReferenceCode(paymentRequest.getReferenceCode());

        sale.getPayments().add(payment);
        sale.setPaidAmount(sale.getPaidAmount().add(paymentRequest.getAmount()));

        // --- LÓGICA DE ESTADO AL PAGAR ---
        if (sale.getRemainingBalance().compareTo(BigDecimal.ZERO) == 0) {
            // Pagado al 100%
            boolean hasManufacturing = sale.getItems().stream()
                .anyMatch(item -> item.getProduct().getType() == ProductType.LENS);

            if (hasManufacturing) {
                // Si tiene lentes y estaba pendiente, pasa a taller
                if (sale.getStatus() == SaleStatus.PENDING) {
                    sale.setStatus(SaleStatus.IN_PROCESS);
                }
            } else {
                // Solo accesorios -> Entregado
                sale.setStatus(SaleStatus.COMPLETED);
            } 
        } else {
            // Pago parcial
            boolean hasManufacturing = sale.getItems().stream()
                .anyMatch(item -> item.getProduct().getType() == ProductType.LENS);
                
            if (hasManufacturing && sale.getStatus() == SaleStatus.PENDING) {
                sale.setStatus(SaleStatus.IN_PROCESS);
            }
        }

        Sale savedSale = saleRepository.save(sale);
        return mapToResponse(savedSale);
    }
    
    // --- EXPIRAR COTIZACIONES VIEJAS (JOB PROGRAMADO) ---
    @Transactional
    public int expireOldQuotations(int daysOld) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysOld);
        List<Sale> expired = saleRepository.findByStatusAndCreatedAtBefore(SaleStatus.QUOTATION, cutoff);

        for (Sale sale : expired) {
            sale.setStatus(SaleStatus.CANCELLED);
        }
        saleRepository.saveAll(expired);
        return expired.size();
    }

    public List<SaleResponse> getSalesByClient(Long clientId) {
        Long currentBranchId = TenantContext.getCurrentBranch();
        
        // Buscamos las entidades
        List<Sale> sales = saleRepository.findByClientIdAndBranchIdOrderByCreatedAtDesc(clientId, currentBranchId);
        
        // Las convertimos a DTOs usando el mapper que ya tienes
        return sales.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
}
