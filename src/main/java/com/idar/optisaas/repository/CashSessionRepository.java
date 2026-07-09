package com.idar.optisaas.repository;

import com.idar.optisaas.entity.CashSession;
import com.idar.optisaas.util.CashSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CashSessionRepository extends JpaRepository<CashSession, Long> {

    Optional<CashSession> findFirstByBranchIdAndStatusOrderByCreatedAtDesc(Long branchId, CashSessionStatus status);

    List<CashSession> findByBranchIdAndStatusOrderByCreatedAtDesc(Long branchId, CashSessionStatus status);
}
