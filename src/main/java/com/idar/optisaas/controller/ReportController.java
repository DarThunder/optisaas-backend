package com.idar.optisaas.controller;

import com.idar.optisaas.service.ReportService;
import com.idar.optisaas.service.OwnerReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired private ReportService reportService;
    @Autowired private OwnerReportService ownerReportService;

    // Resumen consolidado de TODAS las sucursales del dueño (solo las suyas).
    @GetMapping("/global/summary")
    public ResponseEntity<?> globalSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return ResponseEntity.ok(ownerReportService.globalSummary(auth.getName(), from, to));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/sales")
    public ResponseEntity<?> salesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return ResponseEntity.ok(reportService.salesReport(from, to));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/receivables")
    public ResponseEntity<?> receivablesReport() {
        try {
            return ResponseEntity.ok(reportService.receivablesReport());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/inventory-valuation")
    public ResponseEntity<?> inventoryValuation() {
        try {
            return ResponseEntity.ok(reportService.inventoryValuation());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
