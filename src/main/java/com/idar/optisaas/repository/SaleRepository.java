package com.idar.optisaas.repository;

import com.idar.optisaas.entity.Sale;
import com.idar.optisaas.util.SaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Sale s WHERE s.id = :id")
    Optional<Sale> findByIdForUpdate(Long id);

    // --- MÉTODOS NUEVOS AGREGADOS ---
    
    // 1. Para getAllSales() en el Controller
    List<Sale> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    // 2. Para getSaleById() con seguridad
    Optional<Sale> findByIdAndBranchId(Long id, Long branchId);

    // 3. Para getSalesByClient()
    List<Sale> findByClientIdAndBranchIdOrderByCreatedAtDesc(Long clientId, Long branchId);

    // 4. Para expirar cotizaciones viejas (job programado)
    List<Sale> findByStatusAndCreatedAtBefore(SaleStatus status, LocalDateTime cutoff);

    // 5. Para reportes por periodo (Reporte de Ventas)
    List<Sale> findByBranchIdAndCreatedAtBetween(Long branchId, LocalDateTime start, LocalDateTime end);
}