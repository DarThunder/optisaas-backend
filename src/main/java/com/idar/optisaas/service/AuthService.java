package com.idar.optisaas.service;

import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.dto.*;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtils jwtUtils;

    public ResponseCookie login(LoginRequest request) {
        User user = userRepository.findByEmailOrUsername(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado (Email o ID inválido)"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }
        
        String principal = (user.getEmail() != null && !user.getEmail().isEmpty()) ? user.getEmail() : user.getUsername();
        
        return jwtUtils.generatePreAuthCookie(principal);
    }

    public List<BranchDTO> getUserBranches(String identifier) {
        User user = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + identifier));
                
        return user.getBranchRoles().stream()
                .map(role -> {
                    BranchDTO dto = new BranchDTO();
                    dto.setId(role.getBranch().getId());
                    dto.setName(role.getBranch().getName());
                    dto.setRole(role.getRole().name());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public AuthResponse selectBranch(String identifier, BranchSelectRequest request) {
        User user = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + identifier));
        UserBranchRole role = user.getBranchRoles().stream()
                .filter(r -> r.getBranch().getId().equals(request.getBranchId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No tienes acceso a esta sucursal"));

        return new AuthResponse(
            "Login completado", 
            "FULL", 
            user.getId(), 
            role.getBranch().getName(),
            role.getRole().name()
        );
    }
    
    public ResponseCookie createFullCookie(String email, Long branchId, String roleName) {
        return jwtUtils.generateFullAccessCookie(email, branchId, roleName);
    }
}