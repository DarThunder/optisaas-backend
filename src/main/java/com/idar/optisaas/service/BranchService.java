package com.idar.optisaas.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.idar.optisaas.dto.BranchDTO;
import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.BranchRepository;
import com.idar.optisaas.repository.UserBranchRoleRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.Role;

import java.util.List;

@Service
public class BranchService {

    @Autowired private BranchRepository branchRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserBranchRoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    // Crear Sucursal y Asignar Dueño
    @Transactional
    public Branch createBranch(BranchDTO branchDTO, String rawPin, String identifier) {
        // CORRECCIÓN: Buscamos por Email O Username (para coincidir con el token)
        User owner = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + identifier));

        Branch branch = new Branch();
        branch.setName(branchDTO.getName());
        branch.setAddress(branchDTO.getAddress());
        branch.setSecurityPin(passwordEncoder.encode(rawPin));
        
        Branch savedBranch = branchRepository.save(branch);

        UserBranchRole role = new UserBranchRole();
        role.setUser(owner);
        role.setBranch(savedBranch);
        role.setRole(Role.OWNER);

        roleRepository.save(role);

        return savedBranch;
    }

    public Branch updateBranch(Long id, BranchDTO branchDTO) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
        
        branch.setName(branchDTO.getName());
        branch.setAddress(branchDTO.getAddress());
        
        if (branchDTO.getPin() != null && !branchDTO.getPin().isBlank()) {
            branch.setSecurityPin(passwordEncoder.encode(branchDTO.getPin()));
        }

        return branchRepository.save(branch);
    }

    @Transactional
    public void deleteBranch(Long id) {
        if (!branchRepository.existsById(id)) throw new RuntimeException("Sucursal no encontrada");
        branchRepository.deleteById(id);
    }
    
    public List<Branch> getAllBranches() {
        return branchRepository.findAll();
    }
}