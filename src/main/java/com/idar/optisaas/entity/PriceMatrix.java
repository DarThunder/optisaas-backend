package com.idar.optisaas.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.idar.optisaas.model.BaseEntity;
import com.idar.optisaas.model.PriceRule;

@Entity
@Table(name = "price_matrices")
@Data
@EqualsAndHashCode(callSuper = true)
public class PriceMatrix extends BaseEntity {

    private String name;
    private boolean active;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<PriceRule> rules = new ArrayList<>();

    public List<PriceRule> getRules() {
        return rules;
    }
}