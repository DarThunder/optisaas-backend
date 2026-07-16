package com.idar.optisaas.service;

import com.idar.optisaas.entity.IdempotencyKey;
import com.idar.optisaas.repository.IdempotencyKeyRepository;
import com.idar.optisaas.util.IdempotencyScope;
import com.idar.optisaas.util.IdempotencyStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Acceso a las llaves de idempotencia en transacciones propias (REQUIRES_NEW).
 *
 * Está separado de {@link IdempotencyService} a propósito: cada método tiene que confirmarse
 * por su cuenta, independientemente de la transacción de negocio. La reserva debe ser visible
 * para otros intentos ANTES de que la operación termine, y la liberación debe sobrevivir al
 * rollback de la operación que falló. Si estos métodos vivieran en el mismo bean que los llama,
 * la llamada interna se saltaría el proxy de Spring y no habría transacción nueva.
 */
@Service
public class IdempotencyStore {

    @Autowired private IdempotencyKeyRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<IdempotencyKey> find(Long branchId, IdempotencyScope scope, String keyValue) {
        return repository.findByBranchIdAndScopeAndKeyValue(branchId, scope, keyValue);
    }

    /**
     * Reserva la llave. Si otro intento se adelantó, el índice único hace fallar el INSERT
     * y la excepción sale de este método (con su transacción ya revertida) para que el
     * llamador vuelva a leer la fila ganadora.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserve(Long branchId, IdempotencyScope scope, String keyValue, String requestHash) {
        IdempotencyKey entry = new IdempotencyKey();
        entry.setBranchId(branchId);
        entry.setScope(scope);
        entry.setKeyValue(keyValue);
        entry.setRequestHash(requestHash);
        entry.setStatus(IdempotencyStatus.IN_PROGRESS);
        repository.saveAndFlush(entry);
    }

    /** La operación terminó bien: la llave queda ligada al recurso creado para poder repetir la respuesta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long branchId, IdempotencyScope scope, String keyValue, Long resourceId) {
        repository.findByBranchIdAndScopeAndKeyValue(branchId, scope, keyValue).ifPresent(entry -> {
            entry.setStatus(IdempotencyStatus.COMPLETED);
            entry.setResourceId(resourceId);
            repository.save(entry);
        });
    }

    /**
     * La operación falló: se borra la reserva para que el cliente pueda reintentar con la
     * misma llave. Una venta rechazada por stock insuficiente no debe quemar la llave.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long branchId, IdempotencyScope scope, String keyValue) {
        repository.deleteByBranchIdAndScopeAndKeyValue(branchId, scope, keyValue);
    }
}
