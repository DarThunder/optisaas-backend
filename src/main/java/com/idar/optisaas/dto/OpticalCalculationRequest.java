package com.idar.optisaas.dto;

import com.idar.optisaas.util.LensDesignType;
import lombok.Data;
import java.util.List;

@Data
public class OpticalCalculationRequest {
    private Double sphere;      // Esfera (ej. -2.50)
    private Double cylinder;    // Cilindro (ej. -0.75)
    private Integer axis;       // Eje (no afecta precio, pero se valida)
    private LensDesignType type; // MONOFOCAL, BIFOCAL, ETC.
    private List<Long> treatmentIds; // IDs de tratamientos opcionales
}