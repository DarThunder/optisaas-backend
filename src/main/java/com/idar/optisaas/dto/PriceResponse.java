package com.idar.optisaas.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PriceResponse {
    private BigDecimal calculatedPrice;
    private String breakdown;
}