package com.idar.optisaas.repository;

import com.idar.optisaas.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Pagos registrados (cobrados) dentro de una ventana de tiempo, por sucursal.
    List<Payment> findByBranchIdAndCreatedAtBetween(Long branchId, LocalDateTime start, LocalDateTime end);
}
