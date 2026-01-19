package com.idar.optisaas.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class ClinicalRecordRequest {
    private Long clientId;
    private String notes;

    // --- REFRACCIÓN (Rx) ---
    private Double sphereRight;
    private Double sphereLeft;
    private Double cylinderRight;
    private Double cylinderLeft;
    private Integer axisRight;
    private Integer axisLeft;
    private Double additionRight; // Separado OD/OI
    private Double additionLeft;
    private String pupillaryDistance; // String para flexibilidad
    private String height;            // Altura focal

    // --- ANAMNESIS (Booleanos) ---
    private boolean diabetes;
    private boolean hypertension;
    private boolean familyHistory;
    
    private boolean tearing;        // Lagrimeo
    private boolean burning;        // Ardor
    private boolean itching;        // Comezón
    private boolean secretion;      // Lagaña
    private boolean photophobiaSolar;
    private boolean photophobiaArtificial;
    
    private boolean usesGlasses;
    private boolean usesContacts;
    private LocalDate lastRxDate;

    // --- AGUDEZA VISUAL ---
    // SC = Sin Corrección
    private String avScOd;
    private String avScOi;
    private String avScAo;
    private String avScNear;

    // CC = Con Corrección
    private String avCcOd;
    private String avCcOi;
    private String avCcAo;
    private String avCcNear;

    // PH = Estenopeico
    private String avPhOd;
    private String avPhOi;
}