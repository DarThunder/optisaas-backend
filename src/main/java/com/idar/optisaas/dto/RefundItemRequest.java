package com.idar.optisaas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RefundItemRequest {

    @NotNull(message = "Indica qué partida de la venta se devuelve")
    private Long saleItemId;

    @NotNull(message = "La cantidad devuelta es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer quantity;

    /**
     * Si la pieza vuelve al anaquel. Se pone en false para mercancía dañada.
     * Solo aplica a lo inventariable (armazones y accesorios); en lo demás se ignora.
     */
    private boolean restock = true;
}
