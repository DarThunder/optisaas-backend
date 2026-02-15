package com.idar.optisaas.dto;

import com.idar.optisaas.util.LensDesignType;
import lombok.Data;

@Data
public class PriceCalculationRequest {
    private Long rxId;       // Debe ser Long
    private LensDesignType lensType; // Debe ser el Enum para que Jackson lo convierta bien
}