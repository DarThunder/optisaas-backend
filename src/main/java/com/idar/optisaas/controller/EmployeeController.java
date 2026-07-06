package com.idar.optisaas.controller;

import com.idar.optisaas.dto.EmployeeRequest;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.service.UserService;
import com.idar.optisaas.security.TenantContext; 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    /**
     * Obtiene la lista de empleados para el dueño o gerente.
     * Si TenantContext.getCurrentBranch() es null, el sistema asume "Modo Global"
     * y devuelve todos los empleados de todas las sucursales.
     */
    @GetMapping("/by-owner")
    public ResponseEntity<?> getEmployeesByOwner() {
        try {
            Long currentBranchId = TenantContext.getCurrentBranch();
            List<User> users;
            
            // LÓGICA DE GESTIÓN GLOBAL:
            // Si el Admin entra directamente a la pestaña sin pasar por el selector,
            // currentBranchId será null. En ese caso, traemos todo.
            if (currentBranchId == null) {
                users = userService.getAllEmployees();
            } else {
                // Si hay una sucursal activa, filtramos solo por esa sucursal
                users = userService.getEmployeesByBranch(currentBranchId);
            }

            // MAPEOMANUAL: Evita que Jackson entre en bucle infinito (User -> Role -> User)
            List<Map<String, Object>> response = users.stream().map(u -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", u.getId());
                map.put("fullName", u.getFullName());
                map.put("username", u.getUsername());
                map.put("email", u.getEmail());
                map.put("active", u.isActive());
                map.put("quickPin", u.getQuickPin());

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
            e.printStackTrace(); // Log para depuración en consola
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al obtener empleados: " + e.getMessage()));
        }
    }

    @PostMapping("/create")
    public ResponseEntity<?> createEmployee(@RequestBody EmployeeRequest request) {
        try {
            return ResponseEntity.ok(userService.createEmployee(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id, @RequestBody EmployeeRequest request) {
        try {
            return ResponseEntity.ok(userService.updateEmployee(id, request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
    @PostMapping("/{id}/validate-pin")
    public ResponseEntity<?> validateEmployeePin(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            User user = userService.validateEmployeePin(id, payload.get("pin"));
            Map<String, Object> response = new HashMap<>();
            response.put("id", user.getId());
            response.put("fullName", user.getFullName());
            response.put("username", user.getUsername());
            response.put("active", user.isActive());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivateEmployee(@PathVariable Long id) {
        try {
            userService.toggleUserStatus(id, false);
            return ResponseEntity.ok(Map.of("message", "Empleado desactivado exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
}
