package com.idar.optisaas.repository;

import com.idar.optisaas.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Los filtros dinámicos del historial de ajustes se construyen con Specifications
 * (ver InventoryAdjustmentService): en PostgreSQL el patrón ":param IS NULL OR col = :param"
 * falla al no poder inferir el tipo del parámetro.
 */
@Repository
public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, Long>,
        JpaSpecificationExecutor<InventoryAdjustment> {
}
