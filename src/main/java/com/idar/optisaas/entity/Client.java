package com.idar.optisaas.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "clients")
@Data
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fullName;
    private String phone;
    private String email;
    private String occupation;
    private Integer age;

    // Sucursal donde se registro originalmente el cliente.
    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    // Cuenta/propietario que puede ver al cliente desde todas sus sucursales.
    @Column(name = "owner_id")
    private Long ownerId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}