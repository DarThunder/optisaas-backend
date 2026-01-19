package com.idar.optisaas.service;

import com.idar.optisaas.dto.ClinicalRecordRequest;
import com.idar.optisaas.entity.Client;
import com.idar.optisaas.entity.ClinicalRecord;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.ClientRepository;
import com.idar.optisaas.repository.ClinicalRecordRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClinicalService {

    @Autowired private ClinicalRecordRepository recordRepository;
    @Autowired private ClientRepository clientRepository;
    @Autowired private UserRepository userRepository;

    @Transactional
    public ClinicalRecord createRecord(ClinicalRecordRequest request) {
        // ... (Tu código existente de createRecord, déjalo igual) ...
        // (Para ahorrar espacio, asumo que el código de createRecord sigue aquí igual que antes)
        Client client = clientRepository.findById(request.getClientId())
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        String identifier = SecurityContextHolder.getContext().getAuthentication().getName();
        User optometrist = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + identifier));

        ClinicalRecord record = new ClinicalRecord();
        record.setBranchId(TenantContext.getCurrentBranch());
        record.setClient(client);
        record.setOptometrist(optometrist);
        
        return mapRequestToRecord(record, request);
    }

    // --- NUEVO: ACTUALIZAR ---
    @Transactional
    public ClinicalRecord updateRecord(Long id, ClinicalRecordRequest request) {
        ClinicalRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Consulta no encontrada"));

        // Seguridad: Verificar que sea de la misma sucursal (TenantAspect lo hace, pero doble check no sobra)
        if (!record.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso para editar este registro");
        }

        // Mapeamos los nuevos datos sobre el registro existente
        return recordRepository.save(mapRequestToRecord(record, request));
    }

    // --- NUEVO: ELIMINAR ---
    @Transactional
    public void deleteRecord(Long id) {
        ClinicalRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Consulta no encontrada"));
        
        if (!record.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso para eliminar este registro");
        }
        
        recordRepository.delete(record);
    }

    public List<ClinicalRecord> getRecordsByClient(Long clientId) {
        return recordRepository.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    // --- MÉTODO AUXILIAR PARA MAPEO (Refactorizado para usarlo en Create y Update) ---
    private ClinicalRecord mapRequestToRecord(ClinicalRecord record, ClinicalRecordRequest request) {
        record.setNotes(request.getNotes());

        // Rx
        record.setSphereRight(request.getSphereRight());
        record.setSphereLeft(request.getSphereLeft());
        record.setCylinderRight(request.getCylinderRight());
        record.setCylinderLeft(request.getCylinderLeft());
        record.setAxisRight(request.getAxisRight());
        record.setAxisLeft(request.getAxisLeft());
        record.setAdditionRight(request.getAdditionRight());
        record.setAdditionLeft(request.getAdditionLeft());
        
        record.setPupillaryDistance(parseSafeDouble(request.getPupillaryDistance()));
        record.setHeight(parseSafeDouble(request.getHeight()));

        // Anamnesis
        record.setDiabetes(request.isDiabetes());
        record.setHypertension(request.isHypertension());
        record.setFamilyHistory(request.isFamilyHistory());
        record.setTearing(request.isTearing());
        record.setBurning(request.isBurning());
        record.setItching(request.isItching());
        record.setSecretion(request.isSecretion());
        record.setPhotophobiaSolar(request.isPhotophobiaSolar());
        record.setPhotophobiaArtificial(request.isPhotophobiaArtificial());
        record.setUsesGlasses(request.isUsesGlasses());
        record.setUsesContacts(request.isUsesContacts());
        record.setLastRxDate(request.getLastRxDate());

        // AV
        record.setAvScOd(request.getAvScOd());
        record.setAvScOi(request.getAvScOi());
        record.setAvScAo(request.getAvScAo());
        record.setAvScNear(request.getAvScNear());
        record.setAvCcOd(request.getAvCcOd());
        record.setAvCcOi(request.getAvCcOi());
        record.setAvCcAo(request.getAvCcAo());
        record.setAvCcNear(request.getAvCcNear());
        record.setAvPhOd(request.getAvPhOd());
        record.setAvPhOi(request.getAvPhOi());

        return recordRepository.save(record); // Nota: createRecord llama a save dos veces si no se cuida, pero aquí está bien.
    }

    private Double parseSafeDouble(String value) {
        if (value == null || value.trim().isEmpty()) return 0.0;
        try { return Double.parseDouble(value); } catch (NumberFormatException e) { return 0.0; }
    }
}