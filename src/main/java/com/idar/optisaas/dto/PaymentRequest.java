package com.idar.optisaas.dto;

import lombok.Data;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Data
public class PaymentRequest {
    
    @NotNull
    @Positive
    private BigDecimal amount;
    
    @NotNull(message = "El método de pago es obligatorio (CASH, CARD, TRANSFER)")
    private String method;
    
    private String referenceCode; 
}