package com.idar.optisaas.entity;

import com.idar.optisaas.util.RegistrationStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Solicitud de acceso enviada desde la página pública.
 *
 * NO extiende BaseEntity: una solicitud todavía no pertenece a ninguna sucursal — es alguien
 * preguntando desde fuera, antes de que exista óptica alguna.
 *
 * Contiene datos personales (nombre, correo, teléfono). Por eso se guarda el momento del
 * consentimiento y no un simple "aceptó": el día que haya que demostrarlo, un booleano no
 * dice nada y una fecha sí.
 */
@Entity
@Table(name = "registration_request")
@Data
public class RegistrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "contact_name", nullable = false, length = 150)
    private String contactName;

    @Column(length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 100)
    private String city;

    @Column(columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(name = "consent_at")
    private LocalDateTime consentAt;

    /** Para poder cortar abuso desde el panel, no para perfilar a nadie. */
    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    /** Dueño creado al aprobar: rastro de qué solicitud se volvió qué cliente. */
    @Column(name = "created_owner_id")
    private Long createdOwnerId;
}
