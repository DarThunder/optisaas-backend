package com.idar.optisaas.repository;

import com.idar.optisaas.entity.IdempotencyKey;
import com.idar.optisaas.util.IdempotencyScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByBranchIdAndScopeAndKeyValue(Long branchId, IdempotencyScope scope, String keyValue);

    void deleteByBranchIdAndScopeAndKeyValue(Long branchId, IdempotencyScope scope, String keyValue);

    /** Purga de llaves viejas: una vez pasada la ventana de reintentos ya no sirven de nada. */
    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
