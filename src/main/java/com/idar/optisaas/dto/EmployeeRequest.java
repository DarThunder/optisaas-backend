package com.idar.optisaas.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class EmployeeRequest {
    @NotBlank(message = "El nombre es obligatorio")
    private String fullName;
    
    @NotBlank(message = "El username/teléfono es obligatorio")
    private String username;
    
    private String email;
    
    private String password;
    
    @NotNull(message = "El rol es obligatorio (MANAGER, SELLER, OPTOMETRIST)")
    private String role;
    
    @NotNull(message = "La sucursal es obligatoria")
    private Long branchId;
}