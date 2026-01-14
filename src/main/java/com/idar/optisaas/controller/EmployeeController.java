package com.idar.optisaas.controller;

import com.idar.optisaas.dto.EmployeeRequest;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.service.UserService;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class EmployeeController {

    @Autowired private UserService userService;

    @PostMapping("/create")
    public ResponseEntity<?> createEmployee(@RequestBody EmployeeRequest request) {
        User user = userService.createEmployee(request);
        return ResponseEntity.ok("Empleado creado ID: " + user.getId());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateEmployee(@PathVariable Long id, @RequestBody EmployeeRequest request) {
        userService.updateEmployee(id, request);
        return ResponseEntity.ok("Empleado actualizado");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deactivateEmployee(@PathVariable Long id) {
        userService.toggleUserStatus(id, false);
        return ResponseEntity.ok("Empleado desactivado");
    }

    @GetMapping("/by-owner")
    public ResponseEntity<List<User>> getMyEmployees() {
        Long currentBranchId = TenantContext.getCurrentBranch();
        
        // Si estamos en modo global (Admin Hub) y no hay sucursal seleccionada,
        // devolvemos todos los empleados.
        if (currentBranchId == null) {
            return ResponseEntity.ok(userService.getAllEmployees());
        }

        List<User> employees = userService.getEmployeesByBranch(currentBranchId);
        return ResponseEntity.ok(employees);
    }
}