package com.idar.optisaas.repository;

import com.idar.optisaas.entity.ClinicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClinicalRecordRepository extends JpaRepository<ClinicalRecord, Long> {
    // Usamos el guion bajo para navegar explícitamente a la propiedad ID dentro de la entidad Client
    List<ClinicalRecord> findByClient_IdOrderByCreatedAtDesc(Long clientId);
}