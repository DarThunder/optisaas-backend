package com.idar.optisaas.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SaleResponse {
    private Long saleId;
    private String status;
    private LocalDateTime date;
    
    private String clientName;
    private String clientPhone;

    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingBalance;
    private BigDecimal discountAmount;
    private String discountName;

    /** Valor de la mercancía devuelta y dinero reintegrado. Cero en una venta sin devoluciones. */
    private BigDecimal returnedAmount;
    private BigDecimal refundedAmount;

    private List<SaleItemResponse> items; 
}