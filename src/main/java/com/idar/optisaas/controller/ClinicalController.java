package com.idar.optisaas.controller;

import com.idar.optisaas.dto.ClinicalRecordRequest;
import com.idar.optisaas.entity.ClinicalRecord;
import com.idar.optisaas.service.ClinicalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clinical-records")
public class ClinicalController {

    @Autowired private ClinicalService clinicalService;

    @PostMapping
    public ResponseEntity<ClinicalRecord> createRecord(@RequestBody ClinicalRecordRequest request) {
        return ResponseEntity.ok(clinicalService.createRecord(request));
    }

    // NUEVO: Editar
    @PutMapping("/{id}")
    public ResponseEntity<ClinicalRecord> updateRecord(@PathVariable Long id, @RequestBody ClinicalRecordRequest request) {
        return ResponseEntity.ok(clinicalService.updateRecord(id, request));
    }

    // NUEVO: Eliminar
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRecord(@PathVariable Long id) {
        clinicalService.deleteRecord(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<List<ClinicalRecord>> getHistory(@PathVariable Long clientId) {
        return ResponseEntity.ok(clinicalService.getRecordsByClient(clientId));
    }
}