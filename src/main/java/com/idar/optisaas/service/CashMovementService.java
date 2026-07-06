package com.idar.optisaas.service;

import com.idar.optisaas.dto.*;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.CashMovementType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CashMovementService {

    @Autowired private CashMovementRepository movementRepository;
    @Autowired private UserRepository userRepository;

    @Transactional
    public CashMovementResponse createMovement(CashMovementRequest request, String username) {
        User user = userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        CashMovement movement = new CashMovement();
        movement.setType(CashMovementType.valueOf(request.getType()));
        movement.setAmount(request.getAmount());
        movement.setReason(request.getReason());
        movement.setRegisteredBy(user);

        CashMovement saved = movementRepository.save(movement);
        return mapToResponse(saved);
    }

    public List<CashMovementResponse> getTodaysMovements() {
        Long branchId = TenantContext.getCurrentBranch();
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        
        return movementRepository.findByBranchIdAndCreatedAtBetweenOrderByCreatedAtDesc(branchId, start, end)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private CashMovementResponse mapToResponse(CashMovement m) {
        CashMovementResponse r = new CashMovementResponse();
        r.setId(m.getId());
        r.setType(m.getType().name());
        r.setAmount(m.getAmount());
        r.setReason(m.getReason());
        r.setRegisteredByName(m.getRegisteredBy().getFullName());
        r.setCreatedAt(m.getCreatedAt());
        return r;
    }
}