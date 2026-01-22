package com.idar.optisaas.service;

public enum LensIndex {
    INDEX_1_50("1.50 Standard", 0.0),
    INDEX_1_56("1.56 Thin", 200.0),
    INDEX_1_67("1.67 High Index", 600.0),
    INDEX_1_74("1.74 Ultra Thin", 1000.0);

    public final String label;
    public final double materialSurcharge; // Costo extra intrínseco del material

    LensIndex(String label, double surcharge) {
        this.label = label;
        this.materialSurcharge = surcharge;
    }
}