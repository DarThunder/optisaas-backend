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
    @Column(length = 4)
    private String quickPin; 
    // -------------------------------

    private String fullName;
    private boolean active = true;

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