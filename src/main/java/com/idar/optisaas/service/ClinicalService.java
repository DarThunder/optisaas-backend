package com.idar.optisaas.service;

import com.idar.optisaas.dto.ClinicalRecordRequest;
import com.idar.optisaas.dto.ClinicalRecordResponse;
import com.idar.optisaas.entity.Client;
import com.idar.optisaas.entity.ClinicalRecord;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.ClientRepository;
import com.idar.optisaas.repository.ClinicalRecordRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClinicalService {

    @Autowired private ClinicalRecordRepository recordRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;

    @Transactional
    public ClinicalRecordResponse createRecord(ClinicalRecordRequest request) {
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        
        String identifier = SecurityContextHolder.getContext().getAuthentication().getName();
        User optometrist = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        ClinicalRecord record = new ClinicalRecord();
        record.setBranchId(TenantContext.getCurrentBranch());
        record.setClient(client);
        record.setOptometrist(optometrist);
        
        if (record.getDate() == null) record.setDate(java.time.LocalDate.now());
        
        ClinicalRecord saved = saveFromRequest(record, request);
        return mapToResponse(saved);
    }

    @Transactional
    public ClinicalRecordResponse updateRecord(Long id, ClinicalRecordRequest request) {
        ClinicalRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Consulta no encontrada"));

        if (!record.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso");
        }

        ClinicalRecord saved = saveFromRequest(record, request);
        return mapToResponse(saved);
    }

    @Transactional
    public void deleteRecord(Long id) {
        ClinicalRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Consulta no encontrada"));
        if (!record.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso");
        }
        recordRepository.delete(record);
    }

    // Importante: @Transactional(readOnly = true) asegura que la conexión siga abierta
    // para leer los datos del cliente/optometrista antes de convertirlos a DTO.
    @Transactional(readOnly = true)
    public List<ClinicalRecordResponse> getRecordsByClient(Long clientId) {
        List<ClinicalRecord> records = recordRepository.findByClient_IdOrderByCreatedAtDesc(clientId);
        return records.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ClinicalRecord saveFromRequest(ClinicalRecord record, ClinicalRecordRequest request) {
        BeanUtils.copyProperties(request, record, "clientId", "optometristId"); 
        
        record.setPupillaryDistance(parseSafeDouble(request.getPupillaryDistance()));
        record.setHeight(parseSafeDouble(request.getHeight()));
        
        if (record.getDate() == null) record.setDate(java.time.LocalDate.now());

        return recordRepository.save(record);
    }

    private ClinicalRecordResponse mapToResponse(ClinicalRecord record) {
        ClinicalRecordResponse response = new ClinicalRecordResponse();
        BeanUtils.copyProperties(record, response);
        
        if (record.getClient() != null) {
            response.setClientId(record.getClient().getId());
            response.setClientName(record.getClient().getFullName());
        }
        
        if (record.getOptometrist() != null) {
            response.setOptometristId(record.getOptometrist().getId());
            response.setOptometristName(record.getOptometrist().getFullName()); 
        }

        return response;
    }

    private Double parseSafeDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0.0; }
    }
}