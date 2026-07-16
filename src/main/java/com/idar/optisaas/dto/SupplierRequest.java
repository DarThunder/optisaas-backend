package com.idar.optisaas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SupplierRequest {

    @NotBlank(message = "El nombre del proveedor es obligatorio")
    @Size(max = 150, message = "El nombre no puede pasar de 150 caracteres")
    private String name;

    @Size(max = 150)
    private String contactName;

    @Size(max = 30)
    private String phone;

    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 150)
    private String email;

    @Size(max = 500)
    private String notes;
}
