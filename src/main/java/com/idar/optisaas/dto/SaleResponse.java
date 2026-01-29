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
    
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal remainingBalance;
    
    private List<SaleItemResponse> items; 
}