package com.idar.optisaas.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

@Data
public class SaleItemRequest {
    
    @NotNull(message = "El producto es obligatorio")
    private Long productId;
    
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer quantity;
    
    private Long clinicalRecordId; 
    
    private BigDecimal manualPrice;
}