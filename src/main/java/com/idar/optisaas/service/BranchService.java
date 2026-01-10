package com.idar.optisaas.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.idar.optisaas.dto.BranchDTO;
import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.repository.BranchRepository;

@Service
public class BranchService {

    @Autowired private BranchRepository branchRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    public Branch createBranch(BranchDTO branchDTO, String rawPin) {
        Branch branch = new Branch();
        branch.setName(branchDTO.getName());
        branch.setAddress(branchDTO.getAddress());
        
        branch.setSecurityPin(passwordEncoder.encode(rawPin));
        
        return branchRepository.save(branch);
    }
}