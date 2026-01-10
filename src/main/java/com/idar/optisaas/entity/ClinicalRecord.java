package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "clinical_records")
@Data
@EqualsAndHashCode(callSuper = true)
public class ClinicalRecord extends BaseEntity {
    
    @ManyToOne
    private Client client;

    @ManyToOne
    private User optometrist;

    private Double sphereRight;
    private Double sphereLeft;
    private Double cylinderRight;
    private Double cylinderLeft;
    private Integer axisRight;
    private Integer axisLeft;
    private Double addition;
    private Double pupillaryDistance;
    
    @Column(columnDefinition = "TEXT")
    private String notes;

    public void setClient(Client client2) {
        client = client2;
    }

    public void setOptometrist(User optometrist2) {
        optometrist = optometrist2;
    }

    public void setSphereRight(Double sphereRight2) {
        sphereRight = sphereRight2;
    }

    public void setSphereLeft(Double sphereLeft2) {
        sphereLeft = sphereLeft2;
    }

    public void setCylinderRight(Double cylinderRight2) {
        cylinderRight = cylinderRight2;
    }

    public void setCylinderLeft(Double cylinderLeft2) {
        cylinderLeft = cylinderLeft2;
    }

    public void setAxisRight(Integer axisRight2) {
        axisRight = axisRight2;
    }

    public void setAxisLeft(Integer axisLeft2) {
        axisLeft = axisLeft2;
    }

    public void setAddition(Double addition2) {
        addition = addition2;
    }

    public void setPupillaryDistance(Double pupillaryDistance2) {
        pupillaryDistance = pupillaryDistance2;
    }

    public void setNotes(String notes2) {
        notes = notes2;
    }

    public Double getCylinderRight() {
        return cylinderRight;
    }

    public Double getCylinderLeft() {
        return cylinderLeft;
    }

    public Double getSphereRight() {
        return sphereRight;
    }

    public Double getSphereLeft() {
        return sphereLeft;
    }
}