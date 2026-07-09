package com.idar.optisaas.repository;

import com.idar.optisaas.entity.BranchSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BranchSettingsRepository extends JpaRepository<BranchSettings, Long> {
    Optional<BranchSettings> findByBranchId(Long branchId);
}
