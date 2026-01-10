package com.idar.optisaas.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "branches")
@Data
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String address;
    
    @Column(nullable = false)
    private String securityPin;

    public void setName(String name2) {
        name = name2;
    }

    public void setAddress(String address2) {
        address = address2;
    }

    public void setSecurityPin(String encode) {
        securityPin = encode;
    }
}