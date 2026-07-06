package com.idar.optisaas.repository;

import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.util.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBranchRoleRepository extends JpaRepository<UserBranchRole, Long> {
    Optional<UserBranchRole> findFirstByBranch_IdAndRole(Long branchId, Role role);
    List<UserBranchRole> findByUser_IdAndRole(Long userId, Role role);
}