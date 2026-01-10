package com.idar.optisaas.dto;

import lombok.Data;

@Data
public class ClinicalRecordRequest {
    private Long clientId;
    private Double sphereRight;
    private Double sphereLeft;
    private Double cylinderRight;
    private Double cylinderLeft;
    private Integer axisRight;
    private Integer axisLeft;
    private Double addition;
    private Double pupillaryDistance;
    private String notes;
}