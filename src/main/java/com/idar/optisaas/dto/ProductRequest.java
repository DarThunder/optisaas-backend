package com.idar.optisaas.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductRequest {
    private Long id; // Para ediciones
    
    private String sku;
    private String brand;
    private String model;
    private String category;
    private String type; // FRAME, LENS, SERVICE, ACCESSORY
    
    private BigDecimal basePrice;
    private Integer stockQuantity;
    private String color;
    
    // CLAVE: Recibimos String para limpiar "15 min" en el servicio
    private String duration; 
}