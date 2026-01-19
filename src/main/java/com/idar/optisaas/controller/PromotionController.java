package com.idar.optisaas.controller;

import com.idar.optisaas.entity.Promotion;
import com.idar.optisaas.service.PromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    @Autowired private PromotionService promotionService;

    @GetMapping
    public ResponseEntity<List<Promotion>> getAll() {
        return ResponseEntity.ok(promotionService.getAllPromotions());
    }

    @PostMapping
    public ResponseEntity<Promotion> create(@RequestBody Promotion promotion) {
        return ResponseEntity.ok(promotionService.savePromotion(promotion));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Promotion> update(@PathVariable Long id, @RequestBody Promotion promotion) {
        promotion.setId(id);
        return ResponseEntity.ok(promotionService.savePromotion(promotion));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        promotionService.deletePromotion(id);
        return ResponseEntity.ok().build();
    }
}