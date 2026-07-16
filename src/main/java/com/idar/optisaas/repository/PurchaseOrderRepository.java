package com.idar.optisaas.repository;

import com.idar.optisaas.entity.PurchaseOrder;
import com.idar.optisaas.util.PurchaseOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    List<PurchaseOrder> findByBranchIdOrderByCreatedAtDesc(Long branchId);

    List<PurchaseOrder> findByBranchIdAndStatusOrderByCreatedAtDesc(Long branchId, PurchaseOrderStatus status);

    Optional<PurchaseOrder> findByIdAndBranchId(Long id, Long branchId);

    /** Bloqueo al recibir: dos recepciones simultáneas no deben sumar la misma mercancía dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT po FROM PurchaseOrder po WHERE po.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(Long id);
}
