package com.idar.optisaas.entity;

import com.idar.optisaas.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Proveedor de mercancía.
 *
 * Vive por sucursal (como los productos y su inventario): cada sucursal compra a quien le surte.
 * No se borra, se desactiva: las órdenes de compra históricas deben seguir diciendo a quién
 * se le compró.
 */
@Entity
@Table(name = "suppliers")
@Data
@EqualsAndHashCode(callSuper = true)
public class Supplier extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(length = 30)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;
}
