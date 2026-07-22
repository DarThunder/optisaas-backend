package com.idar.optisaas.service;

import com.idar.optisaas.entity.AuditLog;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.AuditLogRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.ClientIp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Registra acciones sensibles en la bitácora de auditoría.
 *
 * Se ejecuta dentro de la transacción del llamador (REQUIRED): si la operación de negocio
 * se confirma, su registro de auditoría también; y si el registro falla, la operación se
 * revierte. Es intencional: en un sistema de dinero no queremos acciones sin rastro.
 */
@Service
public class AuditService {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(AuditAction action, String entityType, Long entityId, String details) {
        log(action, entityType, entityId, details, TenantContext.getCurrentBranch());
    }

    /** Variante con sucursal explícita (p. ej. cuando la acción no corre bajo TenantContext). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(AuditAction action, String entityType, Long entityId, String details, Long branchId) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBranchId(branchId);
        entry.setDetails(truncate(details));
        entry.setIpAddress(currentIp());

        String username = currentUsername();
        entry.setActorUsername(username);
        if (username != null) {
            userRepository.findByEmailOrUsername(username, username)
                    .map(User::getId)
                    .ifPresent(entry::setActorUserId);
        }

        auditLogRepository.save(entry);
    }

    /** Variante para acciones donde el actor aún no está en el SecurityContext (p. ej. login/hub). */
    @Transactional(propagation = Propagation.REQUIRED)
    public void logAs(AuditAction action, User actor, String entityType, Long entityId, String details, Long branchId) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBranchId(branchId);
        entry.setDetails(truncate(details));
        entry.setIpAddress(currentIp());
        if (actor != null) {
            entry.setActorUserId(actor.getId());
            entry.setActorUsername(actor.getUsername() != null ? actor.getUsername() : actor.getEmail());
        }
        auditLogRepository.save(entry);
    }

    // ------------------------- Helpers -------------------------

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
    }

    /** IP del solicitante; respeta X-Forwarded-For si hay un reverse proxy delante. */
    private String currentIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes servletAttrs)) return null;
            return ClientIp.from(servletAttrs.getRequest());
        } catch (Exception e) {
            return null;
        }
    }

    private String truncate(String details) {
        if (details == null) return null;
        return details.length() <= 2000 ? details : details.substring(0, 2000);
    }
}
