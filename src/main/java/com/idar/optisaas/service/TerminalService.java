package com.idar.optisaas.service;

import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.BranchRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.AttemptLimiter;
import com.idar.optisaas.security.PinEncoder;
import com.idar.optisaas.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vinculación de terminal: permite marcar un dispositivo como "terminal" de una sucursal
 * para que los empleados inicien turno solo con su PIN, sin las credenciales de la cuenta.
 */
@Service
public class TerminalService {

    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AttemptLimiter attemptLimiter;
    @Autowired private PinEncoder pinEncoder;

    /**
     * Verifica que el usuario sea dueño o gerente de esa sucursal (permiso para
     * configurar su terminal). Devuelve la sucursal.
     */
    @Transactional(readOnly = true)
    public Branch assertCanManageBranch(String callerUsername, Long branchId) {
        User caller = userRepository.findByEmailOrUsername(callerUsername, callerUsername)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        boolean canManage = caller.getBranchRoles().stream().anyMatch(r ->
                r.getBranch() != null && r.getBranch().getId().equals(branchId)
                        && (r.getRole() == Role.OWNER || r.getRole() == Role.MANAGER));
        if (!canManage) {
            throw new RuntimeException("Solo el dueño o un gerente de esta sucursal puede configurar la terminal.");
        }
        return branch;
    }

    /**
     * Verifica permiso (dueño/gerente) y que el PIN de sucursal sea correcto. Para vincular.
     */
    @Transactional(readOnly = true)
    public Branch verifyBindAccess(String callerUsername, Long branchId, String branchPin) {
        String key = "bind:" + callerUsername + ":" + branchId;
        attemptLimiter.assertNotBlocked(key);

        Branch branch = assertCanManageBranch(callerUsername, branchId);
        if (branch.getSecurityPin() == null || branchPin == null
                || !passwordEncoder.matches(branchPin, branch.getSecurityPin())) {
            attemptLimiter.recordFailure(key);
            throw new RuntimeException("PIN de sucursal incorrecto.");
        }

        attemptLimiter.reset(key);
        return branch;
    }

    @Transactional(readOnly = true)
    public Branch getBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));
    }

    /** Empleados activos que pueden iniciar turno en la sucursal (solo id, nombre y rol). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> employeesForBranch(Long branchId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            if (!u.isActive()) continue;
            UserBranchRole ubr = u.getBranchRoles().stream()
                    .filter(r -> r.getBranch() != null && r.getBranch().getId().equals(branchId))
                    .findFirst().orElse(null);
            if (ubr == null) continue;
            // Solo listamos a quienes ya definieron su PIN (cuenta activada y lista para fichar).
            if (u.getQuickPin() == null || u.getQuickPin().isBlank()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("fullName", u.getFullName());
            m.put("role", ubr.getRole().name());
            out.add(m);
        }
        return out;
    }

    /**
     * Ficha (inicia turno) validando el PIN del empleado contra la sucursal de la terminal.
     * Devuelve el usuario y su rol en esa sucursal para emitir la sesión.
     */
    @Transactional
    public Map<String, Object> clockIn(Long branchId, Long userId, String pin) {
        String key = "clock-in:" + branchId + ":" + userId;
        attemptLimiter.assertNotBlocked(key);

        if (pin == null || pin.isBlank()) throw new RuntimeException("PIN requerido");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));
        if (!user.isActive()) throw new RuntimeException("Empleado inactivo");

        UserBranchRole ubr = user.getBranchRoles().stream()
                .filter(r -> r.getBranch() != null && r.getBranch().getId().equals(branchId))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Este empleado no pertenece a esta sucursal"));

        if (!pinEncoder.matches(pin, user.getQuickPin())) {
            attemptLimiter.recordFailure(key);
            throw new RuntimeException("PIN incorrecto");
        }

        // Migración perezosa de PIN heredado en texto plano.
        if (pinEncoder.needsUpgrade(user.getQuickPin())) {
            user.setQuickPin(pinEncoder.encode(pin));
            userRepository.save(user);
        }

        attemptLimiter.reset(key);

        String principal = (user.getEmail() != null && !user.getEmail().isBlank())
                ? user.getEmail() : user.getUsername();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("user", user);
        res.put("principal", principal);
        res.put("role", ubr.getRole().name());
        return res;
    }
}
