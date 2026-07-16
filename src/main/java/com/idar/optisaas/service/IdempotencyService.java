package com.idar.optisaas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.idar.optisaas.entity.IdempotencyKey;
import com.idar.optisaas.exception.ConflictException;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.IdempotencyScope;
import com.idar.optisaas.util.IdempotencyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Ejecuta una operación a lo sumo una vez por llave de idempotencia.
 *
 * El cliente manda el header `Idempotency-Key` (un UUID por intención de cobro, generado al
 * abrir el modal y NO en cada clic). Si el mismo UUID vuelve a llegar:
 *  - operación ya terminada con el mismo contenido -> se repite la respuesta original;
 *  - operación aún en curso -> 409, para que el cliente espere en vez de duplicar;
 *  - mismo UUID con otro contenido -> 409, porque eso es un error del cliente, no un reintento.
 *
 * NO es transaccional a propósito: la reserva de la llave tiene que confirmarse antes de que
 * empiece la operación de negocio (ver {@link IdempotencyStore}).
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private static final int MAX_KEY_LENGTH = 100;

    @Autowired private IdempotencyStore store;
    @Autowired private ObjectMapper objectMapper;

    /**
     * @param key         valor del header; si viene vacío la operación corre sin protección
     * @param payload     cuerpo de la petición, para detectar reuso de llave con otro contenido
     * @param action      la operación de negocio (debe llamarse a través de un proxy transaccional)
     * @param resourceIdOf cómo obtener el id del recurso a partir del resultado
     * @param replay      cómo reconstruir la respuesta a partir del id, en un reintento
     */
    public <T> T run(IdempotencyScope scope,
                     String key,
                     Object payload,
                     Supplier<T> action,
                     Function<T, Long> resourceIdOf,
                     Function<Long, T> replay) {

        if (key == null || key.isBlank()) {
            // Header opcional por ahora: el frontend aún no lo manda en todas las pantallas.
            return action.get();
        }

        String keyValue = normalize(key);
        Long branchId = TenantContext.getCurrentBranch();
        if (branchId == null) {
            throw new IllegalStateException("La idempotencia requiere una sucursal seleccionada");
        }
        String requestHash = hash(payload);

        Optional<IdempotencyKey> existing = store.find(branchId, scope, keyValue);
        if (existing.isEmpty()) {
            try {
                store.reserve(branchId, scope, keyValue, requestHash);
            } catch (DataAccessException e) {
                // Carrera con un intento simultáneo: el índice único lo resolvió, releemos al ganador.
                existing = store.find(branchId, scope, keyValue);
                if (existing.isEmpty()) throw e;
            }
        }

        if (existing.isPresent()) {
            return replayExisting(existing.get(), scope, requestHash, replay);
        }

        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            store.release(branchId, scope, keyValue);
            throw e;
        }

        store.complete(branchId, scope, keyValue, resourceIdOf.apply(result));
        return result;
    }

    private <T> T replayExisting(IdempotencyKey entry, IdempotencyScope scope, String requestHash, Function<Long, T> replay) {
        if (!entry.getRequestHash().equals(requestHash)) {
            throw new ConflictException("Esta llave de idempotencia ya se usó con un contenido distinto. "
                    + "Genera una llave nueva para una operación nueva.");
        }
        if (entry.getStatus() == IdempotencyStatus.IN_PROGRESS) {
            throw new ConflictException("La operación con esta llave todavía está en curso. "
                    + "Espera el resultado antes de reintentar.");
        }
        if (entry.getResourceId() == null) {
            throw new ConflictException("La operación con esta llave ya se registró, pero no dejó un recurso consultable.");
        }

        log.info("Reintento idempotente de {}: se devuelve el recurso {} ya creado", scope, entry.getResourceId());
        return replay.apply(entry.getResourceId());
    }

    private String normalize(String key) {
        String trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("La llave de idempotencia excede " + MAX_KEY_LENGTH + " caracteres");
        }
        return trimmed;
    }

    /** SHA-256 del cuerpo serializado; Jackson escribe los campos en orden de declaración, así que es estable. */
    private String hash(Object payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la huella de la petición", e);
        }
    }
}
