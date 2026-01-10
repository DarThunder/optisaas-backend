package com.idar.optisaas.controller;

import com.idar.optisaas.dto.*;
import com.idar.optisaas.util.JwtUtils;
import com.idar.optisaas.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private JwtUtils jwtUtils;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            ResponseCookie cookie = authService.login(loginRequest);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body("Paso 1 completado. Seleccione sucursal.");
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @GetMapping("/my-branches")
    public ResponseEntity<?> getMyBranches(HttpServletRequest request) {
        String email = getEmailFromCookie(request);
        return ResponseEntity.ok(authService.getUserBranches(email));
    }

    @PostMapping("/select-branch")
    public ResponseEntity<?> selectBranch(@RequestBody BranchSelectRequest request, HttpServletRequest httpRequest) {
        try {
            String email = getEmailFromCookie(httpRequest);
            AuthResponse response = authService.selectBranch(email, request);
            
            ResponseCookie cookie = authService.createFullCookie(
                email, 
                request.getBranchId(), 
                response.getRole()
            );

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie cookie = jwtUtils.getCleanJwtCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body("Sesión cerrada");
    }

    private String getEmailFromCookie(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromCookies(request);
        if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
            return jwtUtils.getUserNameFromJwtToken(jwt);
        }
        throw new RuntimeException("Token inválido o expirado. Vuelva a hacer login.");
    }
}