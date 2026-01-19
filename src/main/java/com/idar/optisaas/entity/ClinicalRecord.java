package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Entity
@Table(name = "clinical_records")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClinicalRecord extends BaseEntity {
    
    @ManyToOne
    private Client client;

    @ManyToOne
    private User optometrist;

    // --- REFRACCIÓN (Rx) ---
    private Double sphereRight;
    private Double sphereLeft;
    private Double cylinderRight;
    private Double cylinderLeft;
    private Integer axisRight;
    private Integer axisLeft;
    private Double additionRight; 
    private Double additionLeft;
    private Double pupillaryDistance; // Lo mantenemos como Double para compatibilidad
    private Double height;            // Altura

    // --- ANAMNESIS (Nuevos campos) ---
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

    @Column(columnDefinition = "TEXT")
    private String notes;
}