package com.idar.optisaas.controller;

import com.idar.optisaas.dto.CashMovementRequest;
import com.idar.optisaas.dto.CashMovementResponse;
import com.idar.optisaas.service.CashMovementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cash-movements")
public class CashMovementController {

    @Autowired
    private CashMovementService movementService;

    @PostMapping
    public ResponseEntity<CashMovementResponse> create(@Valid @RequestBody CashMovementRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok(movementService.createMovement(request, auth.getName()));
    }

    @GetMapping("/today")
    public ResponseEntity<List<CashMovementResponse>> getToday() {
        return ResponseEntity.ok(movementService.getTodaysMovements());
    }
}