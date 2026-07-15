package com.idar.optisaas.entity;

import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;
    
    @Column(unique = true)
    private String username;

    @JsonIgnore
    private String password;
    
    // --- CAMPO NUEVO PARA EL SAAS ---
    // @JsonIgnore: el PIN de autorización nunca debe salir en ninguna respuesta
    // (se valida siempre contra el backend, jamás se compara en el cliente).
    @JsonIgnore
    // Longitud amplia: ya no guarda 4 dígitos en texto plano, sino un hash BCrypt (~60 chars).
    @Column(length = 100)
    private String quickPin;
    // -------------------------------

    private String fullName;
    private boolean active = true;

    // --- AUTO-SERVICIO DE CREDENCIALES ---
    // Un empleado nuevo se crea SIN contraseña ni PIN; define ambos él mismo en su
    // primer ingreso usando un código de activación de un solo uso. Nadie más los conoce.
    // columnDefinition "default true": las filas EXISTENTES (admin y empleados ya activos)
    // quedan como activadas; los registros nuevos que inserta la app llegan con false.
    @Column(columnDefinition = "boolean default true")
    private boolean credentialsSet = false;

    @JsonIgnore
    @Column(length = 10)
    private String activationCode;

    @JsonIgnore
    private java.time.LocalDateTime activationCodeExpiresAt;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserBranchRole> branchRoles;

    // Getters manuales para asegurar que Lombok no falle
    public @Nullable String getPassword() { return password; }
    public String getEmail() { return email; }
    public Set<UserBranchRole> getBranchRoles() { return branchRoles; }
    public Long getId() { return id; }
    public String getQuickPin() { return quickPin; }
    public void setQuickPin(String pin) { this.quickPin = pin; }
}