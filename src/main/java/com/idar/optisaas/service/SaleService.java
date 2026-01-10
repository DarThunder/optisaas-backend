package com.idar.optisaas.service;

import com.idar.optisaas.entity.*;
import com.idar.optisaas.util.*;
import com.idar.optisaas.dto.*;
import com.idar.optisaas.repository.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SaleService {

    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClinicalRecordRepository clinicalRepository;

    @Transactional
    public SaleResponse createSale(SaleRequest request, String sellerEmail) {
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new RuntimeException("Vendedor no encontrado"));

        Sale sale = new Sale();
        sale.setClient(client);
        sale.setSeller(seller);
        sale.setCreatedAt(LocalDateTime.now());
        
        if (request.isQuotation()) {
            sale.setStatus(SaleStatus.QUOTATION);
        } else if (request.isParkSale()) {
            sale.setStatus(SaleStatus.PENDING);
        } else {
            sale.setStatus(SaleStatus.PENDING); 
        }

        List<SaleItem> itemsEntities = new ArrayList<>();
        BigDecimal totalSaleAmount = BigDecimal.ZERO;
        boolean hasManufacturingItems = false;

        for (SaleItemRequest itemReq : request.getItems()) {
            Product product = productRepository.findByIdForUpdate(itemReq.getProductId())
            .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + itemReq.getProductId()));

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

        if (!request.isQuotation() && !request.isParkSale()) {
            BigDecimal remaining = totalSaleAmount.subtract(totalPaid);
            
            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                sale.setStatus(SaleStatus.IN_PROCESS);
            } else {
                if (hasManufacturingItems) {
                    sale.setStatus(SaleStatus.IN_PROCESS); 
                } else {
                    sale.setStatus(SaleStatus.COMPLETED);
                }
            }
        }

        Sale savedSale = saleRepository.save(sale);

        return mapToResponse(savedSale);
    }
    
    private SaleResponse mapToResponse(Sale sale) {
        SaleResponse response = new SaleResponse();
        response.setSaleId(sale.getId());
        response.setStatus(sale.getStatus().name());
        response.setDate(sale.getCreatedAt());
        response.setClientName(sale.getClient().getFullName());
        response.setTotalAmount(sale.getTotalAmount());
        response.setPaidAmount(sale.getPaidAmount());
        response.setRemainingBalance(sale.getRemainingBalance());
        
        return response;
    }

    public SaleResponse getSaleById(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
        
        return mapToResponse(sale);
    }

    @Transactional
    public SaleResponse addPayment(Long saleId, PaymentRequest paymentRequest) {
        Sale sale = saleRepository.findByIdForUpdate(saleId)
            .orElseThrow(() -> new RuntimeException("Venta no encontrada"));

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new RuntimeException("No se pueden agregar pagos a una venta cancelada");
        }
        
        if (sale.getStatus() == SaleStatus.QUOTATION) {
            throw new RuntimeException("Una cotización debe convertirse en venta antes de pagar");
        }

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

        if (sale.getRemainingBalance().compareTo(BigDecimal.ZERO) == 0) {
            
            boolean hasManufacturing = sale.getItems().stream()
                .anyMatch(item -> item.getProduct().getType() == ProductType.LENS);

            if (!hasManufacturing) {
                sale.setStatus(SaleStatus.COMPLETED);
            } 
        }
        else if (sale.getStatus() == SaleStatus.PENDING) {
            sale.setStatus(SaleStatus.IN_PROCESS);
        }

        Sale savedSale = saleRepository.save(sale);
        return mapToResponse(savedSale);
    }
}