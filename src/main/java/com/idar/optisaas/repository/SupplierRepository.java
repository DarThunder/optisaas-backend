package com.idar.optisaas.repository;

import com.idar.optisaas.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    List<Supplier> findByBranchIdOrderByNameAsc(Long branchId);

    List<Supplier> findByBranchIdAndActiveOrderByNameAsc(Long branchId, boolean active);

    Optional<Supplier> findByIdAndBranchId(Long id, Long branchId);
}
