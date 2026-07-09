package com.idar.optisaas.controller;

import com.idar.optisaas.entity.BranchSettings;
import com.idar.optisaas.service.BranchSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    @Autowired private BranchSettingsService settingsService;

    @GetMapping
    public ResponseEntity<?> get() {
        try {
            return ResponseEntity.ok(settingsService.getForCurrentBranch());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestBody BranchSettings request) {
        try {
            return ResponseEntity.ok(settingsService.save(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
