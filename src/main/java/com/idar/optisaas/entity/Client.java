package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;

import jakarta.persistence.*;
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
    public String getFullName() {
        return fullName;
    }
}