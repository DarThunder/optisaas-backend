package com.idar.optisaas.dto;

import lombok.Data;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;

@Data
public class SaleRequest {
    
    @NotNull(message = "El cliente es obligatorio")
    private Long clientId;
    
    @NotEmpty(message = "La venta debe tener al menos un producto")
    private List<SaleItemRequest> items;
    
    private List<PaymentRequest> payments;
    
    private boolean isQuotation = false; 
    
    private boolean parkSale = false;
}