package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
@Data
@EqualsAndHashCode(callSuper = true)
public class SaleItem extends BaseEntity {

    @ManyToOne
    @JoinColumn(name = "sale_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Sale sale;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "clinical_record_id")
    private ClinicalRecord clinicalRecord;

    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    
    private String productNameSnapshot; 
}