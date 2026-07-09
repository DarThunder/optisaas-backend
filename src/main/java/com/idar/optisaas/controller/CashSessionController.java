package com.idar.optisaas.controller;

import com.idar.optisaas.service.CashSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/cash-sessions")
public class CashSessionController {

    @Autowired private CashSessionService cashSessionService;

    @GetMapping("/current")
    public ResponseEntity<?> current() {
        try {
            return ResponseEntity.ok(cashSessionService.currentSession());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/open")
    public ResponseEntity<?> open(@RequestBody Map<String, Object> body) {
        try {
            BigDecimal openingFloat = toBigDecimal(body.get("openingFloat"));
            return ResponseEntity.ok(cashSessionService.openSession(openingFloat, username()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/close")
    public ResponseEntity<?> close(@RequestBody Map<String, Object> body) {
        try {
            BigDecimal countedCash = toBigDecimal(body.get("countedCash"));
            return ResponseEntity.ok(cashSessionService.closeSession(countedCash, username()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }
}
