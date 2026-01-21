package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // <--- ESTA IMPORTACIÓN ES VITAL
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Entity
@Table(name = "clinical_records")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClinicalRecord extends BaseEntity {

    // VINCULAMOS LA ANOTACIÓN PARA EVITAR EL ERROR DE JSON/SERIALIZACIÓN
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"}) 
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optometrist_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User optometrist;

    @Column(nullable = false)
    private LocalDate date;

    // --- REFRACCIÓN (Rx) ---
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

    // --- ANAMNESIS ---
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

    @Column(length = 1000)
    private String notes;
    
    // Métodos de compatibilidad
    public Double getAddRight() { return additionRight; }
    public Double getAddLeft() { return additionLeft; }
}