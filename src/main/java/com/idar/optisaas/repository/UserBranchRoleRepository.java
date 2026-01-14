package com.idar.optisaas.repository;

import com.idar.optisaas.entity.UserBranchRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserBranchRoleRepository extends JpaRepository<UserBranchRole, Long> {
}