package com.idar.optisaas.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RefundItemResponse {
    private Long saleItemId;
    private String productNameSnapshot;
    private Integer quantity;
    private BigDecimal amount;
    private boolean restocked;
}
