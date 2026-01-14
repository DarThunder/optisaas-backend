package com.idar.optisaas.entity;

import com.idar.optisaas.util.Role;
import com.fasterxml.jackson.annotation.JsonIgnore; // <--- IMPORTANTE

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Entity
@Table(name = "user_branch_roles")
@Data
public class UserBranchRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore // <--- ESTO SOLUCIONA LA RECURSIÓN INFINITA
    private User user;

    @ManyToOne
    @JoinColumn(name = "branch_id")
    private Branch branch;

    @Enumerated(EnumType.STRING)
    private Role role;

    public Branch getBranch() {
        return branch;
    }
}