package com.idar.optisaas.repository;

import com.idar.optisaas.entity.PriceMatrix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PriceMatrixRepository extends JpaRepository<PriceMatrix, Long> {
    Optional<PriceMatrix> findByActiveTrueAndBranchId(Long branchId);
}