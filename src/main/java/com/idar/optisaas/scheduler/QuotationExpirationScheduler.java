package com.idar.optisaas.scheduler;

import com.idar.optisaas.service.SaleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class QuotationExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(QuotationExpirationScheduler.class);
    private static final int QUOTATION_EXPIRATION_DAYS = 7;

    @Autowired private SaleService saleService;

    // Corre todos los días a las 3:00 AM
    @Scheduled(cron = "0 0 3 * * *")
    public void expireOldQuotations() {
        int expired = saleService.expireOldQuotations(QUOTATION_EXPIRATION_DAYS);
        if (expired > 0) {
            log.info("Cotizaciones expiradas automáticamente: {}", expired);
        }
    }
}
