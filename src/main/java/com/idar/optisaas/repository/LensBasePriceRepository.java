package com.idar.optisaas.repository;

import com.idar.optisaas.entity.LensBasePrice;
import com.idar.optisaas.util.LensDesignType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface LensBasePriceRepository extends JpaRepository<LensBasePrice, Long> {
    Optional<LensBasePrice> findByDesignTypeAndBranchId(LensDesignType type, Long branchId);
    List<LensBasePrice> findByBranchId(Long branchId);
}