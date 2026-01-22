package com.idar.optisaas.dto;
import com.idar.optisaas.util.LensDesignType;
import lombok.Data;

@Data
public class PriceCalculationRequest {
    private Long rxId;
    private LensDesignType lensType;
}