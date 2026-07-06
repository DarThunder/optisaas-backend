package com.idar.optisaas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CashMovementRequest {
    @NotBlank(message = "El tipo de movimiento es obligatorio (INCOME o EXPENSE)")
    private String type;

    @NotNull(message = "El monto es obligatorio")
    @Positive(message = "El monto debe ser mayor a 0")
    private BigDecimal amount;

    @NotBlank(message = "El motivo es obligatorio")
    private String reason;
}