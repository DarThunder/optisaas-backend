package com.idar.optisaas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InventoryAdjustmentRequest {

    @NotNull(message = "Indica qué producto se ajusta")
    private Long productId;

    /**
     * Las dos formas naturales de ajustar, y se manda EXACTAMENTE una:
     *  - `newQuantity`: "conté 7 piezas" (conteo físico; el sistema calcula la diferencia).
     *  - `delta`: "se rompieron 2" (movimiento; negativo para bajar, positivo para subir).
     */
    private Integer newQuantity;
    private Integer delta;

    @NotBlank(message = "El motivo es obligatorio (SHRINKAGE, THEFT, COUNT_CORRECTION, EXPIRATION, SUPPLIER_RETURN)")
    private String reason;

    @Size(max = 500)
    private String note;
}
