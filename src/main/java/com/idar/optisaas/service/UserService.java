package com.idar.optisaas.service;

import com.idar.optisaas.dto.ActivationDelivery;
import com.idar.optisaas.dto.EmployeeRequest;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.mail.EmployeeActivationMailer;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.security.AttemptLimiter;
import com.idar.optisaas.security.PinEncoder;
import com.idar.optisaas.security.TenantContext;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private UserBranchRoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AttemptLimiter attemptLimiter;
    @Autowired private PinEncoder pinEncoder;
    @Autowired private AuditService auditService;
    @Autowired private EmployeeActivationMailer activationMailer;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ACTIVATION_CODE_VALID_DAYS = 7;

    // =======================================================================
    // CREAR EMPLEADO
    // callerUsername / callerRole identifican a quién hace la petición, para
    // aplicar el techo de rol y el aislamiento por sucursal/cuenta.
    // =======================================================================
    @Transactional
    public ActivationDelivery createEmployee(EmployeeRequest request, String callerUsername, String callerRole) {
        if (userRepository.findByEmailOrUsername(request.getEmail(), request.getUsername()).isPresent()) {
            throw new RuntimeException("El usuario ya existe (username o email duplicado)");
        }

        Role newRole = parseRole(request.getRole());
        Long targetBranchId = request.getBranchId();
        if (targetBranchId == null) {
            throw new RuntimeException("La sucursal es obligatoria");
        }

        // Nadie crea cuentas de Dueño desde aquí (se provisionan al dar de alta la cuenta).
        if (newRole == Role.OWNER) {
            throw new RuntimeException("No se pueden crear cuentas de Dueño");
        }

        User caller = getCaller(callerUsername);

        if ("MANAGER".equals(callerRole)) {
            Long currentBranch = TenantContext.getCurrentBranch();
            if (currentBranch == null || !currentBranch.equals(targetBranchId)) {
                throw new RuntimeException("Solo puedes crear empleados en tu sucursal");
            }
            if (newRole == Role.MANAGER) {
                throw new RuntimeException("Un Gerente no puede crear otros Gerentes");
            }
            // Aquí newRole solo puede ser SELLER u OPTOMETRIST.
        } else {
            // OWNER: solo en sucursales que le pertenecen.
            if (!caller.getId().equals(resolveOwnerIdForBranch(targetBranchId))) {
                throw new RuntimeException("Esa sucursal no pertenece a tu cuenta");
            }
        }

        Branch branch = branchRepository.findById(targetBranchId)
                .orElseThrow(() -> new RuntimeException("Sucursal no encontrada"));

        User user = new User();
        user.setFullName(request.getFullName());
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setActive(true);

        // El empleado define su propia contraseña y PIN al activar la cuenta.
        // Quien lo crea NUNCA fija ni conoce esas credenciales.
        user.setCredentialsSet(false);
        user.setActivationCode(generateActivationCode());
        user.setActivationCodeExpiresAt(LocalDateTime.now().plusDays(ACTIVATION_CODE_VALID_DAYS));

        UserBranchRole role = new UserBranchRole();
        role.setUser(user);
        role.setBranch(branch);
        role.setRole(newRole);

        user.setBranchRoles(Set.of(role));

        User created = userRepository.save(user);

        auditService.log(AuditAction.EMPLOYEE_CREATED, "User", created.getId(),
                "usuario: " + created.getUsername() + "; rol: " + newRole
                        + "; sucursal: " + targetBranchId, targetBranchId);

        // Si el empleado tiene correo, el código le llega solo; si no, el administrador se lo
        // dicta como siempre. El envío ocurre al confirmarse la transacción (ver el mailer).
        String sentTo = activationMailer.sendActivationCode(created, targetBranchId, false);

        return new ActivationDelivery(created, sentTo);
    }

    // =======================================================================
    // RESETEAR ACCESO (genera un nuevo código de activación; el empleado vuelve
    // a definir su contraseña y PIN). Quien resetea NO conoce las nuevas claves.
    // =======================================================================
    @Transactional
    public ActivationDelivery resetCredentials(Long id, String callerUsername, String callerRole) {
        User caller = getCaller(callerUsername);
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        assertCanManageTarget(caller, callerRole, target, "resetear el acceso de");

        target.setCredentialsSet(false);
        target.setPassword(null);
        target.setQuickPin(null);
        target.setActivationCode(generateActivationCode());
        target.setActivationCodeExpiresAt(LocalDateTime.now().plusDays(ACTIVATION_CODE_VALID_DAYS));

        User saved = userRepository.save(target);

        auditService.log(AuditAction.CREDENTIALS_RESET, "User", saved.getId(),
                "se restableció el acceso de: " + saved.getUsername()
                        + " (contraseña y PIN quedan pendientes de activación)");

        String sentTo = activationMailer.sendActivationCode(
                saved, activationMailer.primaryBranchId(saved), true);

        return new ActivationDelivery(saved, sentTo);
    }

    // =======================================================================
    // ACTIVAR CUENTA (auto-servicio): el empleado define su contraseña y PIN
    // usando el código de un solo uso. Es un flujo público (aún no puede iniciar sesión).
    // =======================================================================
    @Transactional
    public void activateAccount(String identifier, String code, String newPassword, String newPin) {
        String attemptKey = "activate:" + (identifier == null ? "" : identifier.trim().toLowerCase());
        attemptLimiter.assertNotBlocked(attemptKey);

        User user = userRepository.findByEmailOrUsername(identifier, identifier)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (user.isCredentialsSet()) {
            throw new RuntimeException("Esta cuenta ya fue activada. Inicia sesión normalmente.");
        }
        if (user.getActivationCode() == null || !user.getActivationCode().equals(code)) {
            attemptLimiter.recordFailure(attemptKey);
            throw new RuntimeException("Código de activación inválido");
        }
        if (user.getActivationCodeExpiresAt() != null && user.getActivationCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("El código de activación expiró. Pide uno nuevo a tu administrador.");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new RuntimeException("La contraseña debe tener al menos 8 caracteres");
        }
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new RuntimeException("El PIN debe ser de 4 dígitos");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setQuickPin(pinEncoder.encode(newPin));
        user.setCredentialsSet(true);
        user.setActivationCode(null);
        user.setActivationCodeExpiresAt(null);

        userRepository.save(user);
        attemptLimiter.reset(attemptKey);
    }

    // =======================================================================
    // ACTUALIZAR EMPLEADO
    // =======================================================================
    @Transactional
    public User updateEmployee(Long id, EmployeeRequest request, String callerUsername, String callerRole) {
        User caller = getCaller(callerUsername);
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Role newRole = parseRole(request.getRole());
        if (newRole == Role.OWNER) {
            throw new RuntimeException("No se puede asignar el rol de Dueño");
        }

        Long branchToUpdate;

        if ("MANAGER".equals(callerRole)) {
            Long currentBranch = TenantContext.getCurrentBranch();
            UserBranchRole targetRole = target.getBranchRoles().stream()
                    .filter(r -> r.getBranch().getId().equals(currentBranch))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Este empleado no pertenece a tu sucursal"));

            if (targetRole.getRole() == Role.OWNER || targetRole.getRole() == Role.MANAGER) {
                throw new RuntimeException("No puedes editar a un Gerente o Dueño");
            }
            if (newRole == Role.MANAGER) {
                throw new RuntimeException("Un Gerente no puede ascender empleados a Gerente");
            }
            branchToUpdate = currentBranch;
        } else {
            // OWNER: el empleado debe pertenecer a una sucursal de su cuenta.
            boolean sameOwner = target.getBranchRoles().stream()
                    .anyMatch(r -> caller.getId().equals(resolveOwnerIdForBranch(r.getBranch().getId())));
            if (!sameOwner) {
                throw new RuntimeException("Este empleado no pertenece a tu cuenta");
            }
            branchToUpdate = request.getBranchId();
        }

        target.setFullName(request.getFullName());

        if (request.getUsername() != null) target.setUsername(request.getUsername());
        if (request.getEmail() != null) target.setEmail(request.getEmail());

        // Nota: la contraseña y el PIN NO se tocan aquí. Solo el propio empleado los
        // define (al activar su cuenta); para renovarlos se usa resetCredentials().

        // Actualizar el rol en la sucursal correspondiente.
        Role previousRole = target.getBranchRoles().stream()
                .filter(r -> r.getBranch().getId().equals(branchToUpdate))
                .findFirst()
                .map(UserBranchRole::getRole)
                .orElse(null);

        target.getBranchRoles().stream()
                .filter(r -> r.getBranch().getId().equals(branchToUpdate))
                .findFirst()
                .ifPresent(r -> r.setRole(newRole));

        User saved = userRepository.save(target);

        auditService.log(AuditAction.EMPLOYEE_UPDATED, "User", saved.getId(),
                "usuario: " + saved.getUsername() + "; rol: " + previousRole + " -> " + newRole
                        + "; sucursal: " + branchToUpdate, branchToUpdate);

        return saved;
    }

    public User validateEmployeePin(Long id, String pin) {
        String key = "emp-pin:" + id;
        attemptLimiter.assertNotBlocked(key);

        if (pin == null || pin.isBlank()) {
            throw new RuntimeException("PIN requerido");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        if (!user.isActive()) {
            throw new RuntimeException("Empleado inactivo");
        }

        if (!pinEncoder.matches(pin, user.getQuickPin())) {
            attemptLimiter.recordFailure(key);
            throw new RuntimeException("PIN de autorización incorrecto");
        }

        // Migración perezosa: si el PIN estaba en texto plano, lo re-hasheamos.
        if (pinEncoder.needsUpgrade(user.getQuickPin())) {
            user.setQuickPin(pinEncoder.encode(pin));
            userRepository.save(user);
        }

        attemptLimiter.reset(key);

        // El cambio de operador reemite la sesión con el rol del empleado destino:
        // queda registrado quién cedió el turno a quién.
        auditService.log(AuditAction.OPERATOR_SWITCHED, "User", user.getId(),
                "operador entrante: " + user.getUsername() + " (" + user.getFullName() + ")");

        return user;
    }

    // =======================================================================
    // DESACTIVAR EMPLEADO
    // =======================================================================
    @Transactional
    public void deactivateEmployee(Long id, String callerUsername, String callerRole) {
        User caller = getCaller(callerUsername);
        User target = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        assertCanManageTarget(caller, callerRole, target, "desactivar");

        target.setActive(false);
        userRepository.save(target);

        auditService.log(AuditAction.EMPLOYEE_DEACTIVATED, "User", target.getId(),
                "usuario desactivado: " + target.getUsername());
    }

    public List<User> getEmployeesByBranch(Long branchId) {
        return userRepository.findAll().stream()
                .filter(u -> u.getBranchRoles().stream()
                    .anyMatch(r -> r.getBranch().getId().equals(branchId)))
                .collect(Collectors.toList());
    }

    // Lista global para el Dueño, acotada SOLO a los empleados de sus propias
    // sucursales (antes devolvía todos los usuarios de toda la plataforma).
    public List<User> getEmployeesForOwner(String ownerUsername) {
        User owner = getCaller(ownerUsername);
        Set<Long> ownedBranchIds = roleRepository.findByUser_IdAndRole(owner.getId(), Role.OWNER).stream()
                .map(r -> r.getBranch().getId())
                .collect(Collectors.toSet());

        if (ownedBranchIds.isEmpty()) {
            return List.of();
        }

        return userRepository.findAll().stream()
                .filter(u -> u.getBranchRoles().stream()
                        .anyMatch(r -> ownedBranchIds.contains(r.getBranch().getId())))
                .collect(Collectors.toList());
    }

    // =======================================================================
    // AUTO-SERVICIO DE PERFIL (el usuario autenticado gestiona SU propia cuenta)
    // =======================================================================

    /** Datos del perfil propio. Nunca expone contraseña ni PIN; solo si el PIN está definido. */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getMyProfile(String username) {
        User me = getCaller(username);
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", me.getId());
        map.put("fullName", me.getFullName());
        map.put("username", me.getUsername());
        map.put("email", me.getEmail());
        map.put("hasMasterPin", me.getQuickPin() != null && !me.getQuickPin().isBlank());
        return map;
    }

    /** Actualiza nombre y correo del propio usuario, cuidando que el correo no choque con otro. */
    @Transactional
    public User updateMyProfile(String username, String fullName, String email) {
        User me = getCaller(username);

        if (fullName != null) {
            if (fullName.isBlank()) throw new RuntimeException("El nombre no puede estar vacío");
            me.setFullName(fullName.trim());
        }
        if (email != null && !email.equals(me.getEmail())) {
            String clean = email.trim();
            if (clean.isBlank() || !clean.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new RuntimeException("Correo electrónico inválido");
            }
            userRepository.findByEmailOrUsername(clean, clean).ifPresent(other -> {
                if (!other.getId().equals(me.getId())) throw new RuntimeException("Ese correo ya está en uso");
            });
            me.setEmail(clean);
        }
        return userRepository.save(me);
    }

    /** Cambia la contraseña propia verificando la actual. */
    @Transactional
    public void changeMyPassword(String username, String currentPassword, String newPassword) {
        User me = getCaller(username);
        if (me.getPassword() == null || currentPassword == null
                || !passwordEncoder.matches(currentPassword, me.getPassword())) {
            throw new RuntimeException("La contraseña actual es incorrecta");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new RuntimeException("La nueva contraseña debe tener al menos 8 caracteres");
        }
        me.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(me);
    }

    /** Actualiza el PIN maestro (quickPin) propio, confirmando con la contraseña de la cuenta. */
    @Transactional
    public void updateMyMasterPin(String username, String currentPassword, String newPin) {
        User me = getCaller(username);
        if (me.getPassword() == null || currentPassword == null
                || !passwordEncoder.matches(currentPassword, me.getPassword())) {
            throw new RuntimeException("La contraseña actual es incorrecta");
        }
        if (newPin == null || !newPin.matches("\\d{4}")) {
            throw new RuntimeException("El PIN maestro debe ser de 4 dígitos");
        }
        me.setQuickPin(pinEncoder.encode(newPin));
        userRepository.save(me);
    }

    // ------------------------- Helpers -------------------------

    private User getCaller(String username) {
        return userRepository.findByEmailOrUsername(username, username)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado"));
    }

    private Long resolveOwnerIdForBranch(Long branchId) {
        return roleRepository.findFirstByBranch_IdAndRole(branchId, Role.OWNER)
                .map(r -> r.getUser().getId())
                .orElseThrow(() -> new RuntimeException("La sucursal no tiene Dueño asignado"));
    }

    private Role parseRole(String role) {
        if (role == null) throw new RuntimeException("El rol es obligatorio");
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Rol inválido: " + role);
        }
    }

    // Valida que 'caller' pueda administrar a 'target' (desactivar / resetear acceso):
    // - Gerente: solo Vendedores/Optometristas de SU sucursal.
    // - Dueño: cualquier empleado (no Dueño) de sus propias sucursales.
    // - Nadie sobre su propia cuenta.
    private void assertCanManageTarget(User caller, String callerRole, User target, String actionPhrase) {
        if (caller.getId().equals(target.getId())) {
            throw new RuntimeException("No puedes " + actionPhrase + " tu propia cuenta");
        }

        if ("MANAGER".equals(callerRole)) {
            Long currentBranch = TenantContext.getCurrentBranch();
            UserBranchRole targetRole = target.getBranchRoles().stream()
                    .filter(r -> r.getBranch().getId().equals(currentBranch))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Este empleado no pertenece a tu sucursal"));
            if (targetRole.getRole() == Role.OWNER || targetRole.getRole() == Role.MANAGER) {
                throw new RuntimeException("No puedes " + actionPhrase + " a un Gerente o Dueño");
            }
        } else {
            boolean sameOwner = target.getBranchRoles().stream()
                    .anyMatch(r -> caller.getId().equals(resolveOwnerIdForBranch(r.getBranch().getId())));
            if (!sameOwner) {
                throw new RuntimeException("Este empleado no pertenece a tu cuenta");
            }
            boolean targetIsOwner = target.getBranchRoles().stream()
                    .anyMatch(r -> r.getRole() == Role.OWNER);
            if (targetIsOwner) {
                throw new RuntimeException("No puedes " + actionPhrase + " una cuenta de Dueño");
            }
        }
    }

    // Código de activación de un solo uso: 6 dígitos, fácil de dictar al empleado.
    private String generateActivationCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
