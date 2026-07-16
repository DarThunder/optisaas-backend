package com.idar.optisaas.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class SaleItemResponse {
    /** Id del renglón de la venta: es lo que identifica la partida al devolverla. */
    private Long saleItemId;
    private Long productId;
    private String productNameSnapshot; // El nombre del producto en el momento de la venta
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}