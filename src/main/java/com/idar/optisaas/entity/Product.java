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
    private String sku;
    
    private String brand;
    private String model;
    
    @Enumerated(EnumType.STRING)
    private ProductType type;
    
    private BigDecimal basePrice;
    private Integer stockQuantity;
    public ProductType getType() {
        return type;
    }
    public Integer getStockQuantity() {
        return stockQuantity;
    }
    public String getModel() {
        return model;
    }
    public void setStockQuantity(int i) {
        stockQuantity = i;
    }
    public BigDecimal getBasePrice() {
        return basePrice;
    }
    public String getBrand() {
        return brand;
    }
}