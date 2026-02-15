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
// NOTA: Eliminamos @CrossOrigin aquí para usar la configuración global de SecurityConfig
public class EmployeeController {

    @Autowired private UserService userService;

    // --- SOLUCIÓN: Mapeo manual para evitar Error 400 (Recursión) ---
    @GetMapping("/by-owner")
    public ResponseEntity<?> getEmployeesByOwner() {
        try {
            Long currentBranchId = TenantContext.getCurrentBranch();
            List<User> users;
            
            if (currentBranchId == null) {
                users = userService.getAllEmployees();
            } else {
                users = userService.getEmployeesByBranch(currentBranchId);
            }

            // Transformamos la entidad a un Map limpio
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
            e.printStackTrace(); // Ver error en consola si falla
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/validate-pin")
    public ResponseEntity<?> validatePin(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userService.getAllEmployees().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst()
                .orElse(null);

        if (user == null) return ResponseEntity.notFound().build();
        
        String inputPin = body.get("pin");
        if (user.getQuickPin() != null && user.getQuickPin().equals(inputPin)) {
             Map<String, Object> operator = new HashMap<>();
             operator.put("id", user.getId());
             operator.put("fullName", user.getFullName());
             if (user.getBranchRoles() != null && !user.getBranchRoles().isEmpty()) {
                 operator.put("role", user.getBranchRoles().iterator().next().getRole());
             }
             return ResponseEntity.ok(operator);
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("PIN Incorrecto");
    }

    @PostMapping("/create")
    public ResponseEntity<?> createEmployee(@RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(userService.createEmployee(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id, @RequestBody EmployeeRequest request) {
        return ResponseEntity.ok(userService.updateEmployee(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivateEmployee(@PathVariable Long id) {
        userService.toggleUserStatus(id, false);
        return ResponseEntity.ok("Empleado desactivado");
    }
}