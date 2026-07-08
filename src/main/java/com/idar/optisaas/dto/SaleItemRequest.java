package com.idar.optisaas.dto;

import lombok.Data;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

@Data
public class SaleItemRequest {
    private Long productId;
    private String itemName;

    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer quantity;

    private Long clinicalRecordId;
    private BigDecimal manualPrice;
}