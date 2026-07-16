package com.idar.optisaas.controller;

import com.idar.optisaas.entity.AuditLog;
import com.idar.optisaas.service.AuditQueryService;
import com.idar.optisaas.util.AuditAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consulta de la bitácora de auditoría. Solo el Dueño (regla en SecurityConfig) y
 * acotada en el servicio a SUS sucursales.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired private AuditQueryService auditQueryService;

    @GetMapping
    public ResponseEntity<?> search(
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Page<AuditLog> result = auditQueryService.search(auth.getName(), action, from, to, page, size);

        List<Map<String, Object>> items = result.getContent().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("action", a.getAction());
            m.put("entityType", a.getEntityType());
            m.put("entityId", a.getEntityId());
            m.put("branchId", a.getBranchId());
            m.put("actor", a.getActorUsername());
            m.put("details", a.getDetails());
            m.put("ipAddress", a.getIpAddress());
            m.put("createdAt", a.getCreatedAt());
            return m;
        }).toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("page", result.getNumber());
        body.put("size", result.getSize());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages", result.getTotalPages());
        return ResponseEntity.ok(body);
    }

    /** Catálogo de acciones, para poblar el filtro en el frontend. */
    @GetMapping("/actions")
    public ResponseEntity<?> actions() {
        return ResponseEntity.ok(AuditAction.values());
    }
}
