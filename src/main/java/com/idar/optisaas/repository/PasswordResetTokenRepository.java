package com.idar.optisaas.repository;

import com.idar.optisaas.entity.PasswordResetToken;
import com.idar.optisaas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalida los tokens vivos de un usuario. Se llama al emitir uno nuevo (pedir otro enlace
     * anula el anterior) y al completar el cambio, para que un enlace viejo en la bandeja de
     * entrada no siga siendo una llave válida.
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user = :user AND t.usedAt IS NULL")
    int invalidateActiveTokens(@Param("user") User user, @Param("now") LocalDateTime now);

    /** Limpieza de tokens ya vencidos o usados; no aportan nada después de un tiempo. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);
}
