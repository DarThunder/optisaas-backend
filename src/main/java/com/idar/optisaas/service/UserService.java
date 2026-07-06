package com.idar.optisaas.service;

import com.idar.optisaas.dto.EmployeeRequest;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Transactional
    public User createEmployee(EmployeeRequest request) {
        if (userRepository.findByEmailOrUsername(request.getUsername(), request.getEmail()).isPresent()) {
            throw new RuntimeException("El usuario ya existe (username o email duplicado)");
        }

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        User user = new User();
        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setQuickPin(request.getQuickPin()); // <--- Mapeo del PIN
        user.setActive(true);

        UserBranchRole role = new UserBranchRole();
        role.setUser(user);
        role.setBranch(branch);
        role.setRole(Role.valueOf(request.getRole()));

        user.setBranchRoles(Set.of(role));
        
        return userRepository.save(user);
    }

    @Transactional
    public User updateEmployee(Long id, EmployeeRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        user.setFullName(request.getFullName());
        user.setQuickPin(request.getQuickPin()); // <--- Mapeo del PIN en actualización

        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getEmail() != null) user.setEmail(request.getEmail());
        
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // Actualizar rol en la sucursal específica
        user.getBranchRoles().stream()
            .filter(r -> r.getBranch().getId().equals(request.getBranchId()))
            .findFirst()
            .ifPresent(r -> r.setRole(Role.valueOf(request.getRole())));

        return userRepository.save(user);
    }
    public User validateEmployeePin(Long id, String pin) {
        if (pin == null || pin.isBlank()) {
            throw new RuntimeException("PIN requerido");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        if (!user.isActive()) {
            throw new RuntimeException("Empleado inactivo");
        }

        if (user.getQuickPin() == null || !user.getQuickPin().equals(pin)) {
            throw new RuntimeException("PIN de autorización incorrecto");
        }

        return user;
    }

    public void toggleUserStatus(Long id, boolean active) {
        User user = userRepository.findById(id).orElseThrow();
        user.setActive(active);
        userRepository.save(user); 
    }

    public List<User> getEmployeesByBranch(Long branchId) {
        return userRepository.findAll().stream()
                .filter(u -> u.getBranchRoles().stream()
                    .anyMatch(r -> r.getBranch().getId().equals(branchId)))
                .collect(Collectors.toList());
    }

    public List<User> getAllEmployees() {
        return userRepository.findAll();
    }
}
