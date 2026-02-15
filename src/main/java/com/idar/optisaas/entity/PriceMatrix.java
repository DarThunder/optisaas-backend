package com.idar.optisaas.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "price_matrices")
@Data
@EqualsAndHashCode(callSuper = true, exclude = "rules") // Excluir del hash para evitar recursión
public class PriceMatrix extends BaseEntity {

    private String name;
    private boolean active;
    private Long branchId;

    // --- RELACIÓN CON REGLAS (CORREGIDA) ---
    @OneToMany(mappedBy = "matrix", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference // Indica que esta es la parte que "manda" en el JSON
    private List<PriceRule> rules = new ArrayList<>();
}