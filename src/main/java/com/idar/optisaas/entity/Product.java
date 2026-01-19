package com.idar.optisaas.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.ProductType;

@Entity
@Table(name = "products")
@Data
@EqualsAndHashCode(callSuper = true)
public class Product extends BaseEntity {
    
    @Column(unique = true)
    private String sku; // Código único
    
    private String brand;
    private String model; // En servicios/lentes usaremos esto como el "Nombre"
    
    // Campo nuevo para armazones (ej. "Negro Mate")
    private String color; 
    
    // Campo nuevo para diferenciar Solar/Oftálmico o Clínico/Taller
    private String category; 

    // Campo nuevo para Servicios (ej. "30 min")
    private String duration;

    @Enumerated(EnumType.STRING)
    private ProductType type; // FRAME, LENS, SERVICE, ACCESSORY
    
    private BigDecimal basePrice;
    private Integer stockQuantity;
}