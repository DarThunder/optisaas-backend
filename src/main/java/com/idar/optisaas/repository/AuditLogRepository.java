package com.idar.optisaas.repository;

import com.idar.optisaas.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Los filtros dinámicos de la bitácora se construyen con Specifications
 * (ver AuditQueryService): así solo se generan los predicados realmente usados y se evita
 * el patrón ":param IS NULL OR col = :param", que en PostgreSQL falla al no poder inferir
 * el tipo del parámetro.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
}
