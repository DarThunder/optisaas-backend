package com.idar.optisaas.controller;

import com.idar.optisaas.dto.EmployeeRequest;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.service.UserService;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.JwtUtils;
import com.idar.optisaas.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class EmployeeController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * Obtiene la lista de empleados para el dueño o gerente.
     * Si TenantContext.getCurrentBranch() es null, el sistema asume "Modo Global"
     * (Dueño en el hub) y devuelve los empleados de TODAS sus sucursales.
     */
    @GetMapping("/by-owner")
    public ResponseEntity<?> getEmployeesByOwner() {
        try {
            Long currentBranchId = TenantContext.getCurrentBranch();
            List<User> users;

            if (currentBranchId == null) {
                // Modo global (Dueño en el hub): acotado a SUS sucursales.
                users = userService.getEmployeesForOwner(currentUsername());
            } else {
                // Sucursal activa: solo empleados de esa sucursal.
                users = userService.getEmployeesByBranch(currentBranchId);
            }

            // Un Gerente no debe ver el perfil del Dueño: lo excluimos de la lista.
            if ("MANAGER".equals(currentRole())) {
                users = users.stream()
                        .filter(u -> u.getBranchRoles().stream().noneMatch(r -> r.getRole() == Role.OWNER))
                        .collect(Collectors.toList());
            }

            // MAPEO MANUAL: evita el bucle infinito (User -> Role -> User) y NO expone
            // el PIN de autorización (quickPin) en la respuesta de listado.
            List<Map<String, Object>> response = users.stream().map(u -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("fullName", u.getFullName());
                map.put("username", u.getUsername());
                map.put("email", u.getEmail());
                map.put("active", u.isActive());
                // Estado de credenciales: false = aún no ha activado su cuenta.
                map.put("credentialsSet", u.isCredentialsSet());

                if (u.getBranchRoles() != null) {
                    List<Map<String, Object>> roles = u.getBranchRoles().stream().map(r -> {
                        Map<String, Object> rMap = new HashMap<>();
                        rMap.put("role", r.getRole());
                        if (r.getBranch() != null) {
                            Map<String, Object> bMap = new HashMap<>();
                            bMap.put("id", r.getBranch().getId());
                            bMap.put("name", r.getBranch().getName());
                            rMap.put("branch", bMap);
                        }
                        return rMap;
                    }).collect(Collectors.toList());
                    map.put("branchRoles", roles);
                }
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener empleados: " + e.getMessage()));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createEmployee(@RequestBody EmployeeRequest request) {
        try {
            User created = userService.createEmployee(request, currentUsername(), currentRole());
            // El código de activación se devuelve UNA sola vez, para entregárselo al empleado.
            return ResponseEntity.ok(Map.of(
                    "id", created.getId(),
                    "fullName", created.getFullName(),
                    "username", created.getUsername(),
                    "activationCode", created.getActivationCode()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id, @RequestBody EmployeeRequest request) {
        try {
            User updated = userService.updateEmployee(id, request, currentUsername(), currentRole());
            return ResponseEntity.ok(Map.of(
                    "id", updated.getId(),
                    "fullName", updated.getFullName(),
                    "username", updated.getUsername()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Resetear acceso: genera un nuevo código de activación (devuelto una vez) y deja
    // la cuenta pendiente de que el empleado defina de nuevo su contraseña y PIN.
    @PostMapping("/{id}/reset-credentials")
    public ResponseEntity<?> resetCredentials(@PathVariable Long id) {
        try {
            User user = userService.resetCredentials(id, currentUsername(), currentRole());
            return ResponseEntity.ok(Map.of(
                    "id", user.getId(),
                    "fullName", user.getFullName(),
                    "username", user.getUsername(),
                    "activationCode", user.getActivationCode()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // Cambiar de operador NO es solo cosmético: reemitimos el JWT a nombre del empleado
    // seleccionado con SU rol real en esta sucursal, para que los permisos del backend
    // (cortes, reportes, gestión, etc.) sigan a quien está realmente operando la caja.
    @PostMapping("/{id}/validate-pin")
    public ResponseEntity<?> validateEmployeePin(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            User user = userService.validateEmployeePin(id, payload.get("pin"));

            Long branchId = TenantContext.getCurrentBranch();
            String role = user.getBranchRoles().stream()
                    .filter(r -> r.getBranch().getId().equals(branchId))
                    .map(r -> r.getRole().name())
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Este empleado no tiene acceso a esta sucursal"));

            String principal = (user.getEmail() != null && !user.getEmail().isBlank()) ? user.getEmail() : user.getUsername();
            ResponseCookie cookie = jwtUtils.generateFullAccessCookie(principal, branchId, role);

            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("fullName", user.getFullName());
            response.put("username", user.getUsername());
            response.put("active", user.isActive());
            response.put("role", role);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivateEmployee(@PathVariable Long id) {
        try {
            userService.deactivateEmployee(id, currentUsername(), currentRole());
            return ResponseEntity.ok(Map.of("message", "Empleado desactivado exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // ------------------------- Helpers -------------------------

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    // Devuelve "OWNER" o "MANAGER" según la autoridad efectiva de la sesión.
    private String currentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "OTHER";
        boolean isOwner = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OWNER"));
        return isOwner ? "OWNER" : "MANAGER";
    }
}
