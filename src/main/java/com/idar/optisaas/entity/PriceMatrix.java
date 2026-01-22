package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

// IMPORTANTE: No importes com.idar.optisaas.model.PriceRule si estás usando entity.PriceRule
// O importa explícitamente el que acabas de editar en el Paso 1.

@Entity
@Table(name = "price_matrices")
@Data
@EqualsAndHashCode(callSuper = true)
public class PriceMatrix extends BaseEntity {

    private String name;
    private boolean active;
    
    private Long branchId;

    @ElementCollection(fetch = FetchType.EAGER) // Eager para que al cargar la matriz traiga las reglas
    @CollectionTable(name = "price_matrix_rules", joinColumns = @JoinColumn(name = "matrix_id"))
    private List<PriceRule> rules; // Asegúrate que este PriceRule sea el del Paso 1
}