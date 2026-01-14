package com.idar.optisaas.controller;

import com.idar.optisaas.dto.BranchDTO;
import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.service.BranchService;
import com.idar.optisaas.util.JwtUtils; // <--- Importante para sacar el usuario
import jakarta.servlet.http.HttpServletRequest; // <--- Necesario
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    @Autowired private BranchService branchService;
    @Autowired private JwtUtils jwtUtils; // <--- Inyectamos JwtUtils

    @PostMapping
    public ResponseEntity<?> createBranch(@RequestBody BranchDTO request, HttpServletRequest httpRequest) {
        // 1. Obtenemos el usuario del token
        String username = getAuthenticatedUser(httpRequest);

        String pin = (request.getPin() != null && !request.getPin().isEmpty()) ? request.getPin() : "0000";
        
        // 2. Pasamos el username al servicio
        Branch newBranch = branchService.createBranch(request, pin, username);
        
        return ResponseEntity.ok(newBranch);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBranch(@PathVariable Long id, @RequestBody BranchDTO request) {
        Branch updatedBranch = branchService.updateBranch(id, request);
        return ResponseEntity.ok(updatedBranch);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBranch(@PathVariable Long id) {
        branchService.deleteBranch(id);
        return ResponseEntity.ok("Sucursal eliminada");
    }
    
    // Método auxiliar privado para sacar el usuario del request
    private String getAuthenticatedUser(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromCookies(request);
        if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
            return jwtUtils.getUserNameFromJwtToken(jwt);
        }
        throw new RuntimeException("Usuario no autenticado");
    }
}