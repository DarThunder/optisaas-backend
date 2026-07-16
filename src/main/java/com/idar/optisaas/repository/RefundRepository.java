package com.idar.optisaas.repository;

import com.idar.optisaas.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findBySale_IdOrderByCreatedAtDesc(Long saleId);

    Optional<Refund> findByIdAndBranchId(Long id, Long branchId);

    /** Reembolsos de una ventana de tiempo, por sucursal: el arqueo de caja los resta. */
    List<Refund> findByBranchIdAndCreatedAtBetween(Long branchId, LocalDateTime start, LocalDateTime end);
}
