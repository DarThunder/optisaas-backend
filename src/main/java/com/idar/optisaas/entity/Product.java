package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.ProductType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Data
@EqualsAndHashCode(callSuper = true)
public class Product extends BaseEntity {

    // --- CORRECCIÓN: Solo lectura ---
    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false, insertable = false, updatable = false)
    private Branch branch;
    // --------------------------------

    @Column(nullable = false)
    private String sku;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    private String color;
    
    private String category; 

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType type;

    @Column(nullable = false)
    private BigDecimal basePrice;

    private Integer stockQuantity = 0;
    
    private Integer duration; 
}