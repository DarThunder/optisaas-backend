package com.idar.optisaas.service;

import com.idar.optisaas.dto.ActivationDelivery;
import com.idar.optisaas.dto.CreateTenantRequest;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.mail.EmployeeActivationMailer;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.Role;
import com.idar.optisaas.util.SubscriptionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Alta y gestión de ópticas cliente. Solo lo usa el administrador de plataforma.
 *
 * Por qué NO se reutiliza BranchService.createBranch: ese método da de alta una sucursal MÁS
 * a un dueño que ya existe (es como Mogar abrirá su segunda sucursal). Aquí el dueño todavía
 * no existe, y crear la sucursal sin él —o al revés— dejaría a medias un cliente nuevo. Son
 * operaciones distintas, así que es un método nuevo y el existente no se toca.
 */
@Service
public class TenantService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ACTIVATION_CODE_VALID_DAYS = 7;
    private static final String DEFAULT_PIN = "0000";

    @Autowired private UserRepository userRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private BranchSettingsRepository branchSettingsRepository;
    @Autowired private SubscriptionRepository subscriptionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmployeeActivationMailer activationMailer;
    @Autowired private AuditService auditService;

    /**
     * Da de alta una óptica completa: sucursal, ajustes, dueño, vínculo y suscripción.
     *
     * Todo en una transacción. Un alta a medias (sucursal sin dueño, o dueño sin sucursal) es
     * un cliente que no puede entrar y un registro que hay que limpiar a mano.
     */
    @Transactional
    public ActivationDelivery createOptica(CreateTenantRequest request) {
        String businessName = require(request.getBusinessName(), "El nombre de la óptica es obligatorio");
        String username = require(request.getOwnerUsername(), "El usuario del dueño es obligatorio");
        String fullName = require(request.getOwnerFullName(), "El nombre del dueño es obligatorio");

        // Un correo vacío se guarda como NULL, nunca como "": la columna es única y dos
        // cadenas vacías chocarían entre sí. Es la misma corrección que se hizo en el frontend
        // cuando fabricaba correos falsos.
        String email = blankToNull(request.getOwnerEmail());

        if (userRepository.findByEmailOrUsername(email, username).isPresent()) {
            throw new RuntimeException("Ya existe un usuario con ese correo o nombre de usuario");
        }

        // 1. Sucursal.
        Branch branch = new Branch();
        branch.setName(blankToNull(request.getBranchName()) != null ? request.getBranchName() : businessName);
        branch.setAddress(request.getAddress());
        String rawPin = blankToNull(request.getPin()) != null ? request.getPin() : DEFAULT_PIN;
        branch.setSecurityPin(passwordEncoder.encode(rawPin));
        Branch savedBranch = branchRepository.save(branch);

        // 2. Ajustes de la sucursal. NO es opcional ni cosmético: de businessName salen el
        // ticket impreso, el mensaje de WhatsApp al cliente y la identidad del correo de
        // activación («Óptica» vía Fóvea). Sin ajustes, el correo que está a punto de salir
        // en el paso 5 se firmaría con un genérico.
        //
        // branchId se fija a mano porque BaseEntity lo toma del TenantContext, y el
        // administrador de plataforma no tiene contexto de sucursal (ni debe tenerlo).
        BranchSettings settings = new BranchSettings();
        settings.setBranchId(savedBranch.getId());
        settings.setBusinessName(businessName);
        branchSettingsRepository.save(settings);

        // 3. Dueño, sin contraseña: la define él con el código de activación.
        User owner = new User();
        owner.setFullName(fullName);
        owner.setUsername(username);
        owner.setEmail(email);
        owner.setActive(true);
        owner.setCredentialsSet(false);
        owner.setActivationCode(generateActivationCode());
        owner.setActivationCodeExpiresAt(LocalDateTime.now().plusDays(ACTIVATION_CODE_VALID_DAYS));

        // 4. El vínculo que lo convierte en dueño DE esta sucursal.
        UserBranchRole role = new UserBranchRole();
        role.setUser(owner);
        role.setBranch(savedBranch);
        role.setRole(Role.OWNER);
        owner.setBranchRoles(Set.of(role));

        User savedOwner = userRepository.save(owner);

        // 5. Suscripción. Sin días de prueba = sin vencimiento, que es el caso de un cliente
        // en acompañamiento (Mogar probando el sistema mientras se afina).
        Subscription subscription = new Subscription();
        subscription.setOwnerUserId(savedOwner.getId());
        subscription.setStatus(SubscriptionStatus.TRIAL);
        Integer trialDays = request.getTrialDays();
        if (trialDays != null && trialDays > 0) {
            subscription.setValidUntil(LocalDate.now().plusDays(trialDays));
        }
        subscription.setNotes(request.getNotes());
        subscriptionRepository.save(subscription);

        auditService.log(AuditAction.TENANT_CREATED, "Branch", savedBranch.getId(),
                "óptica: " + businessName + "; dueño: " + username
                        + "; vigencia: " + (subscription.getValidUntil() != null
                                ? subscription.getValidUntil().toString() : "sin vencimiento"),
                savedBranch.getId());

        // 6. El correo sale DESPUÉS del commit (lo maneja el mailer). Si esta transacción
        // fallara más adelante, el dueño habría recibido el código de una cuenta inexistente.
        String sentTo = activationMailer.sendActivationCode(savedOwner, savedBranch.getId(), false);

        return new ActivationDelivery(savedOwner, sentTo);
    }

    @Transactional(readOnly = true)
    public List<Subscription> listSubscriptions() {
        return subscriptionRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Ajusta estado y vigencia de un cliente. Los nulos se ignoran (no borran el valor
     * actual): así el panel puede cambiar solo el estado sin tener que reenviar la fecha.
     */
    @Transactional
    public Subscription updateSubscription(Long id, SubscriptionStatus status,
                                           LocalDate validUntil, String notes) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Suscripción no encontrada"));

        if (status != null) subscription.setStatus(status);
        if (validUntil != null) subscription.setValidUntil(validUntil);
        if (notes != null) subscription.setNotes(notes);

        Subscription saved = subscriptionRepository.save(subscription);

        auditService.log(AuditAction.SUBSCRIPTION_UPDATED, "Subscription", saved.getId(),
                "estado: " + saved.getStatus() + "; vigencia: "
                        + (saved.getValidUntil() != null ? saved.getValidUntil().toString() : "sin vencimiento"),
                null);

        return saved;
    }

    private String generateActivationCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String require(String value, String message) {
        if (value == null || value.isBlank()) throw new RuntimeException(message);
        return value.trim();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
