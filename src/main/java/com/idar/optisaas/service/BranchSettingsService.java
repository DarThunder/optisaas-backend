package com.idar.optisaas.service;

import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.BranchSettings;
import com.idar.optisaas.repository.BranchRepository;
import com.idar.optisaas.repository.BranchSettingsRepository;
import com.idar.optisaas.security.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchSettingsService {

    @Autowired private BranchSettingsRepository settingsRepository;
    @Autowired private BranchRepository branchRepository;

    // Devuelve la configuración de la sucursal actual, creándola con valores por
    // defecto (tomados de la sucursal) la primera vez.
    @Transactional
    public BranchSettings getForCurrentBranch() {
        Long branchId = TenantContext.getCurrentBranch();
        if (branchId == null) throw new RuntimeException("Selecciona una sucursal");

        return settingsRepository.findByBranchId(branchId).orElseGet(() -> {
            BranchSettings s = new BranchSettings();
            s.setBranchId(branchId);
            branchRepository.findById(branchId).ifPresent((Branch b) -> {
                s.setBusinessName(b.getName());
                s.setAddressLine(b.getAddress());
            });
            return settingsRepository.save(s);
        });
    }

    // Guarda (upsert) solo los campos editables sobre la configuración de la sucursal.
    @Transactional
    public BranchSettings save(BranchSettings incoming) {
        BranchSettings s = getForCurrentBranch();

        s.setBusinessName(incoming.getBusinessName());
        s.setPhone(incoming.getPhone());
        s.setEmail(incoming.getEmail());
        s.setWebsite(incoming.getWebsite());
        s.setAddressLine(incoming.getAddressLine());
        s.setLogo(incoming.getLogo());

        s.setLegalName(incoming.getLegalName());
        s.setTaxId(incoming.getTaxId());
        if (incoming.getTaxRate() != null) s.setTaxRate(incoming.getTaxRate());
        s.setPricesIncludeTax(incoming.isPricesIncludeTax());

        if (incoming.getCurrencySymbol() != null) s.setCurrencySymbol(incoming.getCurrencySymbol());
        if (incoming.getLowStockThreshold() != null) s.setLowStockThreshold(incoming.getLowStockThreshold());
        if (incoming.getQuotationValidityDays() != null) s.setQuotationValidityDays(incoming.getQuotationValidityDays());

        if (incoming.getPaperWidth() != null) s.setPaperWidth(incoming.getPaperWidth());
        s.setShowLogo(incoming.isShowLogo());
        s.setShowFolio(incoming.isShowFolio());
        s.setShowDateTime(incoming.isShowDateTime());
        s.setShowCashier(incoming.isShowCashier());
        s.setShowClient(incoming.isShowClient());
        s.setShowPaymentMethod(incoming.isShowPaymentMethod());
        s.setShowDiscount(incoming.isShowDiscount());
        s.setHeaderNote(incoming.getHeaderNote());
        s.setFooterMessage(incoming.getFooterMessage());
        s.setLegalNote(incoming.getLegalNote());

        return settingsRepository.save(s);
    }

    // Umbral de stock bajo configurable (usado por el reporte de valuación).
    public int getLowStockThreshold(Long branchId) {
        return settingsRepository.findByBranchId(branchId)
                .map(s -> s.getLowStockThreshold() != null ? s.getLowStockThreshold() : 5)
                .orElse(5);
    }
}
