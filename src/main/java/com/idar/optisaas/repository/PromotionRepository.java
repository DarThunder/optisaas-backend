package com.idar.optisaas.repository;

import com.idar.optisaas.entity.Promotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    // Aquí podríamos agregar validaciones como "existsByCode" si quisieras códigos únicos
}