package com.idar.optisaas.repository;

import com.idar.optisaas.entity.PriceMatrix; // <--- CAMBIA 'model' POR 'entity'
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PriceMatrixRepository extends JpaRepository<PriceMatrix, Long> {
    Optional<PriceMatrix> findByBranchIdAndActiveTrue(Long branchId);
}