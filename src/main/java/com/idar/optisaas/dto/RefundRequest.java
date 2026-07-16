package com.idar.optisaas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RefundRequest {

    @NotEmpty(message = "La devolución debe tener al menos una partida")
    @Valid
    private List<RefundItemRequest> items;

    /**
     * Dinero a reintegrar. Si se omite, el servicio devuelve el máximo permitido
     * (lo que el cliente pagó de más una vez descontada la mercancía que se queda).
     * Cero es válido: la devolución solo baja la deuda.
     */
    @PositiveOrZero(message = "El reembolso no puede ser negativo")
    private BigDecimal amount;

    /** Por dónde sale el dinero (CASH, CARD, TRANSFER...). Obligatorio si el reembolso es mayor a cero. */
    private String method;

    @NotBlank(message = "El motivo de la devolución es obligatorio")
    private String reason;
}
