package com.idar.optisaas.model;

import java.io.Serializable;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class PriceRule implements Serializable {
    private Double minSphere;
    private Double maxSphere;
    private Double minCylinder;
    private Double maxCylinder;
    private BigDecimal price;
    private String material;
}