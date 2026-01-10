package com.idar.optisaas.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

@Data
public class SaleItemRequest {
    
    @NotNull(message = "El producto es obligatorio")
    private Long productId;
    
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer quantity;
    
    private Long clinicalRecordId; 
    
    // private BigDecimal manualPrice; 
}