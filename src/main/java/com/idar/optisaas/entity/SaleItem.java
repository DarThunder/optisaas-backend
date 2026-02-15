package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"sale", "clinicalRecord"})
public class SaleItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonIgnore // <--- CORTE TOTAL HACIA LA VENTA
    private Sale sale;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore // <--- NO NECESITAMOS EL OBJETO PRODUCTO COMPLETO AQUÍ
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinical_record_id")
    @JsonIgnore // <--- CORTE TOTAL HACIA LA RECETA (Usaremos los snapshots para mostrar info)
    private ClinicalRecord clinicalRecord;

    private String productNameSnapshot;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}