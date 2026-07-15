package com.idar.optisaas.controller;

import com.idar.optisaas.dto.*;
import com.idar.optisaas.util.JwtUtils;
import com.idar.optisaas.service.AuthService;
import com.idar.optisaas.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private AuthService authService;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private UserService userService;

    // Activación de cuenta (primer ingreso): el empleado define su propia contraseña
    // y PIN con el código de un solo uso. Público: aún no tiene credenciales para login.
    @PostMapping("/activate")
    public ResponseEntity<?> activate(@RequestBody ActivationRequest request) {
        try {
            userService.activateAccount(
                    request.getIdentifier(),
                    request.getActivationCode(),
                    request.getNewPassword(),
                    request.getNewPin());
            return ResponseEntity.ok(Map.of("message", "Cuenta activada. Ya puedes iniciar sesión."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Verificación de sesión: el front la llama al cargar para restaurar la sesión
    // sin volver a iniciar sesión. Solo lee y valida la cookie httpOnly ya existente.
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromCookies(request);
        if (jwt == null || !jwtUtils.validateJwtToken(jwt) || !jwtUtils.isFullToken(jwt)) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }
        // branchId presente = sesión dentro de una sucursal; null = sesión del Hub global
        // (panel de administrador). Ambas son sesiones válidas; el frontend enruta según cuál.
        Long branchId = jwtUtils.getBranchIdFromToken(jwt);

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("authenticated", true);
        body.put("username", jwtUtils.getUserNameFromJwtToken(jwt));
        body.put("role", jwtUtils.getRoleFromToken(jwt));
        body.put("branchId", branchId);
        body.put("scope", branchId != null ? "BRANCH" : "HUB");
        return ResponseEntity.ok(body);
    }

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
        String userIdentifier = getAuthenticatedUser(request);
        
        return ResponseEntity.ok(authService.getUserBranches(userIdentifier));
    }

    @PostMapping("/select-branch")
    public ResponseEntity<?> selectBranch(@RequestBody BranchSelectRequest request, HttpServletRequest httpRequest) {
        try {
            String userIdentifier = getAuthenticatedUser(httpRequest);
            AuthResponse response = authService.selectBranch(userIdentifier, request);
            ResponseCookie cookie = authService.createFullCookie(
                userIdentifier, 
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

    private String getAuthenticatedUser(HttpServletRequest request) {
        String jwt = jwtUtils.getJwtFromCookies(request);
        if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
            return jwtUtils.getUserNameFromJwtToken(jwt);
        }
        throw new RuntimeException("Token inválido o expirado. Vuelva a hacer login.");
    }

    @PostMapping("/hub-access")
    public ResponseEntity<?> accessHub(@RequestBody Map<String, String> payload, HttpServletRequest request) {
        try {
            // Extraer quién está intentando entrar desde la cookie PRE_AUTH
            String jwt = jwtUtils.getJwtFromCookies(request);
            if (jwt == null || !jwtUtils.validateJwtToken(jwt)) {
                return ResponseEntity.status(401).body(Map.of("error", "Sesión expirada"));
            }
            
            String identifier = jwtUtils.getUserNameFromJwtToken(jwt);
            String pin = payload.get("pin");

            // Validar
            AuthResponse response = authService.accessHub(identifier, pin);
            
            // Crear el token FULL. IMPORTANTE: branchId es 'null' porque es el Hub Global
            ResponseCookie fullCookie = authService.createFullCookie(identifier, null, response.getRole());

            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.SET_COOKIE, fullCookie.toString())
                    .body(response);
                    
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}