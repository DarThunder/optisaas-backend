package com.idar.optisaas.dto;

import lombok.Data;

@Data
public class PriceCalculationRequest {
    private Long clinicalRecordId;
    private String material;
    private String treatment;
}