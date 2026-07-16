package com.idar.optisaas.service;

import com.idar.optisaas.entity.AuditLog;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.AuditLogRepository;
import com.idar.optisaas.repository.UserBranchRoleRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.Role;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lectura de la bitácora. Separado de {@link AuditService} (que solo escribe) para que
 * la escritura no arrastre dependencias de consulta.
 *
 * Aislamiento: los resultados se acotan SIEMPRE a las sucursales donde quien consulta
 * es OWNER, más sus propias acciones de Hub (que no tienen sucursal).
 */
@Service
public class AuditQueryService {

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserBranchRoleRepository roleRepository;

    @Transactional(readOnly = true)
    public Page<AuditLog> search(String username, AuditAction action, LocalDate from, LocalDate to,
                                 int page, int size) {
        User owner = userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Set<Long> ownedBranchIds = roleRepository.findByUser_IdAndRole(owner.getId(), Role.OWNER).stream()
                .map(r -> r.getBranch().getId())
                .collect(Collectors.toSet());

        var pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        if (ownedBranchIds.isEmpty()) {
            return Page.empty(pageable);
        }

        LocalDateTime fromTs = from != null ? from.atStartOfDay() : null;
        // 'to' es inclusivo para el usuario: llega hasta el final de ese día.
        LocalDateTime toTs = to != null ? to.plusDays(1).atStartOfDay() : null;

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Alcance obligatorio: sus sucursales, o sus propias acciones de Hub (sin sucursal).
            predicates.add(cb.or(
                    root.get("branchId").in(ownedBranchIds),
                    cb.and(cb.isNull(root.get("branchId")),
                           cb.equal(root.get("actorUserId"), owner.getId()))
            ));

            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (fromTs != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromTs));
            }
            if (toTs != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), toTs));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditLogRepository.findAll(spec, pageable);
    }

    /** Límite duro para que una petición no pueda pedir toda la bitácora de golpe. */
    private int normalizeSize(int size) {
        if (size < 1) return 50;
        return Math.min(size, 200);
    }
}
