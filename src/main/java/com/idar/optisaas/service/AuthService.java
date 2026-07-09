package com.idar.optisaas.service;

import com.idar.optisaas.dto.AuthResponse;
import com.idar.optisaas.dto.BranchDTO;
import com.idar.optisaas.dto.BranchSelectRequest;
import com.idar.optisaas.dto.LoginRequest;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.JwtUtils;
import com.idar.optisaas.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;

    public ResponseCookie login(LoginRequest request) {
        User user = userRepository.findByEmailOrUsername(request.getIdentifier(), request.getIdentifier())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado (Email o ID inválido)"));

        // Cuenta creada pero aún sin credenciales propias: debe activarse primero.
        if (!user.isCredentialsSet() || user.getPassword() == null) {
            throw new RuntimeException("Cuenta no activada. Usa 'Primer ingreso' con tu código de activación.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Credenciales inválidas");
        }

        String principal = (user.getEmail() != null && !user.getEmail().isEmpty())
                ? user.getEmail()
                : user.getUsername();

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

        if (request.getBranchId() == null) {
            Role globalRole = getGlobalAdminRole(user);

            return new AuthResponse(
                    "Login global completado",
                    "FULL",
                    user.getId(),
                    "Global Hub",
                    globalRole.name()
            );
        }

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

    public AuthResponse accessHub(String identifier, String pin) {
        User user = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role globalRole = getGlobalAdminRole(user);

        String expectedPin = user.getQuickPin() != null ? user.getQuickPin() : "1234";

        if (!expectedPin.equals(pin)) {
            throw new RuntimeException("Clave maestra incorrecta.");
        }

        return new AuthResponse(
                "Acceso global concedido",
                "FULL",
                user.getId(),
                "Global Hub",
                globalRole.name()
        );
    }

    private Role getGlobalAdminRole(User user) {
        boolean isOwner = user.getBranchRoles().stream()
                .anyMatch(r -> r.getRole() == Role.OWNER);

        if (isOwner) {
            return Role.OWNER;
        }

        boolean isManager = user.getBranchRoles().stream()
                .anyMatch(r -> r.getRole() == Role.MANAGER);

        if (isManager) {
            return Role.MANAGER;
        }

        throw new RuntimeException("Acceso denegado: No tienes permisos de administrador.");
    }
}