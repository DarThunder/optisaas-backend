package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.util.LensDesignType;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@Entity
@Table(name = "lens_base_prices")
@Data
@EqualsAndHashCode(callSuper = true)
public class LensBasePrice extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LensDesignType designType; // MONOFOCAL, BIFOCAL_INVISIBLE, etc.

    @Column(nullable = false)
    private BigDecimal price;

    private String description; // "Lente Visión Sencilla", "Redondo Invisible", etc.
}