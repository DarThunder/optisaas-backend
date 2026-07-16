package com.idar.optisaas.scheduler;

import com.idar.optisaas.repository.IdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Las llaves de idempotencia solo sirven durante la ventana en que un cliente puede reintentar.
 * Pasada esa ventana son basura que hace crecer la tabla sin aportar nada.
 */
@Component
public class IdempotencyKeyPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyPurgeScheduler.class);
    private static final int RETENTION_DAYS = 7;

    @Autowired private IdempotencyKeyRepository repository;

    // Corre todos los días a las 3:30 AM (después de la expiración de cotizaciones)
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpiredKeys() {
        int deleted = repository.deleteOlderThan(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("Llaves de idempotencia purgadas: {}", deleted);
        }
    }
}
