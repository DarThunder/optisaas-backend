package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "clients")
@Data
@EqualsAndHashCode(callSuper = true)
public class Client extends BaseEntity {
    
    private String fullName;
    private String phone;
    private String email;
    
    // NUEVOS CAMPOS
    private String occupation;
    private Integer age;

    private Long branchId; 
}