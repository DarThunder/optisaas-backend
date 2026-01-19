package com.idar.optisaas.service;

import com.idar.optisaas.entity.Promotion;
import com.idar.optisaas.repository.PromotionRepository;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PromotionService {

    @Autowired private PromotionRepository promotionRepository;

    public List<Promotion> getAllPromotions() {
        return promotionRepository.findAll(); // TenantAspect filtra por sucursal
    }

    @Transactional
    public Promotion savePromotion(Promotion promotion) {
        if (promotion.getId() == null) {
            // CREAR
            promotion.setBranchId(TenantContext.getCurrentBranch());
            return promotionRepository.save(promotion);
        } else {
            // EDITAR
            Promotion existing = promotionRepository.findById(promotion.getId())
                    .orElseThrow(() -> new RuntimeException("Promoción no encontrada"));
            
            if (!existing.getBranchId().equals(TenantContext.getCurrentBranch())) {
                throw new RuntimeException("No tienes permiso para editar esta promoción");
            }

            // Actualizar campos
            existing.setName(promotion.getName());
            existing.setDescription(promotion.getDescription());
            existing.setType(promotion.getType());
            existing.setValue(promotion.getValue());
            existing.setCode(promotion.getCode());
            existing.setStartDate(promotion.getStartDate());
            existing.setEndDate(promotion.getEndDate());
            existing.setActive(promotion.isActive());

            return promotionRepository.save(existing);
        }
    }

    @Transactional
    public void deletePromotion(Long id) {
        Promotion existing = promotionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Promoción no encontrada"));
        
        if (!existing.getBranchId().equals(TenantContext.getCurrentBranch())) {
            throw new RuntimeException("No tienes permiso para eliminar esta promoción");
        }
        promotionRepository.delete(existing);
    }
}