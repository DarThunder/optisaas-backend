package com.idar.optisaas.scheduler;

import com.idar.optisaas.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Un token de recuperación vencido no sirve para nada: solo deja en la base el rastro de que
 * alguien pidió recuperar su cuenta. Se conservan unos días por si hay que revisar un incidente
 * (la bitácora de auditoría guarda el hecho de forma permanente) y luego se borran.
 */
@Component
public class PasswordResetTokenPurgeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenPurgeScheduler.class);
    private static final int RETENTION_DAYS = 7;

    @Autowired private PasswordResetTokenRepository repository;

    // Todos los días a las 3:45 AM, después de las otras purgas.
    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = repository.deleteExpiredBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (deleted > 0) {
            log.info("Tokens de recuperación purgados: {}", deleted);
        }
    }
}
