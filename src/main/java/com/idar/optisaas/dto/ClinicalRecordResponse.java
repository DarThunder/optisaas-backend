package com.idar.optisaas.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ClinicalRecordResponse {
    private Long id;
    private Long clientId;
    private String clientName;      
    private Long optometristId;
    private String optometristName; 
    private LocalDate date;
    private String notes;

    private Double sphereRight;
    private Double cylinderRight;
    private Integer axisRight;
    private Double additionRight;
    private Double sphereLeft;
    private Double cylinderLeft;
    private Integer axisLeft;
    private Double additionLeft;
    private Double pupillaryDistance;
    private Double height;

    private boolean diabetes;
    private boolean hypertension;
    private boolean familyHistory;
    private boolean tearing;
    private boolean burning;
    private boolean itching;
    private boolean secretion;
    private boolean photophobiaSolar;
    private boolean photophobiaArtificial;
    private boolean usesGlasses;
    private boolean usesContacts;
    private LocalDate lastRxDate;

    private String avScOd;
    private String avScOi;
    private String avScAo;
    private String avScNear;
    private String avCcOd;
    private String avCcOi;
    private String avCcAo;
    private String avCcNear;
    private String avPhOd;
    private String avPhOi;
}