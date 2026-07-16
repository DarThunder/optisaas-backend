package com.idar.optisaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReceiveItemRequest {

    @NotNull(message = "Indica qué renglón de la orden se recibe")
    private Long purchaseOrderItemId;

    @NotNull(message = "La cantidad recibida es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer quantity;

    /**
     * Costo unitario real de la factura, si llegó distinto al pactado. Si se omite, se usa el
     * de la orden. Es el costo que entra al promedio ponderado del producto.
     */
    @PositiveOrZero(message = "El costo no puede ser negativo")
    private BigDecimal unitCost;
}
