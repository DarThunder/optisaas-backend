package com.idar.optisaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PurchaseOrderItemRequest {

    @NotNull(message = "Indica qué producto se pide")
    private Long productId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer quantity;

    /** Costo unitario pactado con el proveedor (sin IVA), no el precio de venta. */
    @NotNull(message = "El costo unitario es obligatorio")
    @PositiveOrZero(message = "El costo no puede ser negativo")
    private BigDecimal unitCost;
}
