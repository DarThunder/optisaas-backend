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

        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

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
            Product product = productRepository.findByIdForUpdate(itemReq.getProductId())
            .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + itemReq.getProductId()));

            // Validar Sucursal
            if (!product.getBranch().getId().equals(branchId)) {
                throw new RuntimeException("El producto " + product.getModel() + " no pertenece a esta sucursal");
            }

            // Descontar Stock
            if (product.getType() != ProductType.SERVICE && !request.isQuotation()) {
                if (product.getStockQuantity() < itemReq.getQuantity()) {
                    throw new RuntimeException("Stock insuficiente para: " + product.getModel());
                }
                product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());
                productRepository.save(product);
            }
            
            if (product.getType() == ProductType.LENS) {
                hasManufacturingItems = true;
            }

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(product.getBasePrice());
            
            BigDecimal subtotal = product.getBasePrice().multiply(new BigDecimal(itemReq.getQuantity()));
            item.setSubtotal(subtotal);
            item.setProductNameSnapshot(product.getBrand() + " " + product.getModel());

            if (itemReq.getClinicalRecordId() != null) {
                ClinicalRecord record = clinicalRepository.findById(itemReq.getClinicalRecordId())
                        .orElseThrow(() -> new RuntimeException("Receta no encontrada"));
                item.setClinicalRecord(record);
            }

            itemsEntities.add(item);
            totalSaleAmount = totalSaleAmount.add(subtotal);
        }

        sale.setItems(itemsEntities);
        sale.setTotalAmount(totalSaleAmount);

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
            BigDecimal remaining = totalSaleAmount.subtract(totalPaid);
            
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
    
    // --- MAPPER ---
    private SaleResponse mapToResponse(Sale sale) {
        SaleResponse response = new SaleResponse();
        response.setSaleId(sale.getId());
        response.setStatus(sale.getStatus().name());
        response.setDate(sale.getCreatedAt());
        
        if (sale.getClient() != null) {
            response.setClientName(sale.getClient().getFullName());
        } else {
            response.setClientName("Cliente General");
        }
        
        response.setTotalAmount(sale.getTotalAmount());
        response.setPaidAmount(sale.getPaidAmount());
        
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
    
    public List<Sale> getSalesByClient(Long clientId) {
        Long currentBranchId = TenantContext.getCurrentBranch();
        return saleRepository.findByClientIdAndBranchIdOrderByCreatedAtDesc(clientId, currentBranchId);
    }
}