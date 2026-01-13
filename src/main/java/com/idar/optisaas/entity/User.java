package com.idar.optisaas.entity;

import java.util.Set;

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

    private String password;
    private String fullName;
    private boolean active = true;

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserBranchRole> branchRoles;

    public @Nullable String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public Set<UserBranchRole> getBranchRoles() {
        return branchRoles;
    }

    public Long getId() {
        return id;
    }
}