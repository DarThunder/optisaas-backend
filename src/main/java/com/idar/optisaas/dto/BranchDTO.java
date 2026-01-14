package com.idar.optisaas.dto;

import lombok.Data;

@Data
public class BranchDTO {
    private Long id;
    private String name;
    private String role;
    private String address;
    private String pin;

    public String getName() {
        return name;
    }
     public String getAdress() {
        return address;
    }
}