package com.idar.optisaas.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String identifier;
    private String password;
}