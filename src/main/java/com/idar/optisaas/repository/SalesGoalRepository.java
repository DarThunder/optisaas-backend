package com.idar.optisaas.repository;

import com.idar.optisaas.entity.SalesGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesGoalRepository extends JpaRepository<SalesGoal, Long> {
    Optional<SalesGoal> findByOwnerIdAndYearAndMonth(Long ownerId, Integer year, Integer month);
}
