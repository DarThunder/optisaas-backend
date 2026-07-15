package com.idar.optisaas.controller;

import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.service.TerminalService;
import com.idar.optisaas.util.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Vinculación de terminal + fichaje por PIN. Rutas bajo /api/auth/** (públicas a nivel de
 * SecurityConfig); la autorización real se valida aquí con las cookies correspondientes:
 *  - bind/unbind: exigen una sesión de dueño/gerente (cookie de sesión FULL).
 *  - status/clock-in: se apoyan en la cookie de TERMINAL del dispositivo (sin sesión de usuario).
 */
@RestController
@RequestMapping("/api/auth/terminal")
public class TerminalController {

    @Autowired private TerminalService terminalService;
    @Autowired private JwtUtils jwtUtils;

    // Vincular este dispositivo a una sucursal (setup único, hecho por dueño/gerente).
    @PostMapping("/bind")
    public ResponseEntity<?> bind(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            String jwt = jwtUtils.getJwtFromCookies(request);
            if (jwt == null || !jwtUtils.validateJwtToken(jwt)) {
                return ResponseEntity.status(401).body(Map.of("message", "Inicia sesión como dueño o gerente para vincular la terminal."));
            }
            String username = jwtUtils.getUserNameFromJwtToken(jwt);
            Long branchId = Long.valueOf(String.valueOf(body.get("branchId")));
            String branchPin = body.get("branchPin") != null ? String.valueOf(body.get("branchPin")) : null;

            Branch branch = terminalService.verifyBindAccess(username, branchId, branchPin);
            ResponseCookie cookie = jwtUtils.generateTerminalCookie(branchId);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("branchId", branch.getId());
            resp.put("branchName", branch.getName());
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Estado de la terminal: ¿está vinculada? ¿a qué sucursal? ¿quiénes pueden fichar?
    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest request) {
        try {
            String t = jwtUtils.getTerminalTokenFromCookies(request);
            if (t == null || !jwtUtils.validateJwtToken(t) || !jwtUtils.isTerminalToken(t)) {
                return ResponseEntity.ok(Map.of("bound", false));
            }
            Long branchId = jwtUtils.getBranchIdFromTerminal(t);
            Branch branch;
            try {
                branch = terminalService.getBranch(branchId);
            } catch (Exception notFound) {
                // La sucursal ya no existe: la terminal quedó huérfana → se trata como no vinculada.
                return ResponseEntity.ok(Map.of("bound", false));
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("bound", true);
            resp.put("branchId", branch.getId());
            resp.put("branchName", branch.getName());
            resp.put("employees", terminalService.employeesForBranch(branchId));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("bound", false));
        }
    }

    // Iniciar turno: el dispositivo está vinculado y el empleado teclea su PIN. Sin credenciales de cuenta.
    @PostMapping("/clock-in")
    public ResponseEntity<?> clockIn(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            String t = jwtUtils.getTerminalTokenFromCookies(request);
            if (t == null || !jwtUtils.validateJwtToken(t) || !jwtUtils.isTerminalToken(t)) {
                return ResponseEntity.status(401).body(Map.of("message", "Esta terminal no está vinculada a ninguna sucursal."));
            }
            Long branchId = jwtUtils.getBranchIdFromTerminal(t);
            Long userId = Long.valueOf(String.valueOf(body.get("userId")));
            String pin = body.get("pin") != null ? String.valueOf(body.get("pin")) : null;

            Map<String, Object> res = terminalService.clockIn(branchId, userId, pin);
            User user = (User) res.get("user");
            String principal = (String) res.get("principal");
            String role = (String) res.get("role");

            ResponseCookie cookie = jwtUtils.generateFullAccessCookie(principal, branchId, role);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", user.getId());
            resp.put("fullName", user.getFullName());
            resp.put("role", role);
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(resp);
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", e.getMessage()));
        }
    }

    // Desvincular la terminal: exige que quien lo pide (dueño/gerente de la sucursal vinculada)
    // esté autenticado. Acción administrativa.
    @PostMapping("/unbind")
    public ResponseEntity<?> unbind(HttpServletRequest request) {
        try {
            String jwt = jwtUtils.getJwtFromCookies(request);
            if (jwt == null || !jwtUtils.validateJwtToken(jwt)) {
                return ResponseEntity.status(401).body(Map.of("message", "Inicia sesión como dueño o gerente para desvincular la terminal."));
            }
            String username = jwtUtils.getUserNameFromJwtToken(jwt);

            // Si hay una terminal vinculada, validamos que el usuario administre esa sucursal.
            String t = jwtUtils.getTerminalTokenFromCookies(request);
            if (t != null && jwtUtils.validateJwtToken(t) && jwtUtils.isTerminalToken(t)) {
                Long branchId = jwtUtils.getBranchIdFromTerminal(t);
                terminalService.assertCanManageBranch(username, branchId); // lanza si no tiene permiso
            }

            ResponseCookie clean = jwtUtils.getCleanTerminalCookie();
            return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, clean.toString()).body(Map.of("message", "Terminal desvinculada"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
