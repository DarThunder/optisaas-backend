package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

@Entity
@Table(name = "clinical_records")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClinicalRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    // CORRECCIÓN: Ignoramos propiedades de Hibernate y la relación circular con el cliente
    @JsonIgnore
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "optometrist_id")
    @JsonIgnore
    private User optometrist;

    @Column(nullable = false)
    private LocalDate date;

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

    @Column(length = 1000)
    private String notes;
    
    public Double getAddRight() { return additionRight; }
    public Double getAddLeft() { return additionLeft; }
}