package com.idar.optisaas.dto;

import lombok.Data;

@Data
public class ActivationRequest {
    private String identifier;      // username o email del empleado
    private String activationCode;  // código de un solo uso entregado por el administrador
    private String newPassword;     // contraseña que el empleado define
    private String newPin;          // PIN de 4 dígitos que el empleado define
}
