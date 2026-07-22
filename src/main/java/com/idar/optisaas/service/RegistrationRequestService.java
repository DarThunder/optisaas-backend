package com.idar.optisaas.service;

import com.idar.optisaas.dto.ActivationDelivery;
import com.idar.optisaas.dto.CreateTenantRequest;
import com.idar.optisaas.dto.RegistrationRequestSubmission;
import com.idar.optisaas.entity.RegistrationRequest;
import com.idar.optisaas.repository.RegistrationRequestRepository;
import com.idar.optisaas.security.AttemptLimiter;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.RegistrationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Solicitudes de acceso: recepción desde la página pública y revisión desde el panel.
 *
 * Aprobar una solicitud y dar de alta la óptica son la misma operación: delega en
 * {@link TenantService#createOptica}, que ya lo hace todo en una transacción.
 */
@Service
public class RegistrationRequestService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationRequestService.class);

    @Autowired private RegistrationRequestRepository repository;
    @Autowired private TenantService tenantService;
    @Autowired private AttemptLimiter attemptLimiter;
    @Autowired private AuditService auditService;

    /**
     * Recibe una solicitud del formulario público.
     *
     * NUNCA revela nada al que envía: responda lo que responda este método, el controlador
     * contesta siempre lo mismo. Si variara según si el correo ya tiene cuenta, el formulario
     * se convertiría en un detector de clientes — la misma razón por la que
     * {@code forgot-password} responde igual exista o no la cuenta.
     */
    @Transactional
    public void submit(RegistrationRequestSubmission submission, String sourceIp) {
        // El limitador cuenta eventos por clave y bloquea al llegar al tope; aquí el "evento"
        // es un envío, no un fallo. 5 envíos por IP cada 15 minutos: de sobra para una persona
        // real y un techo para quien automatice. Ojo: es en memoria y por instancia, así que
        // se reinicia con cada despliegue (ver Fase 7, Redis).
        String key = "regreq:" + (sourceIp != null ? sourceIp : "desconocida");
        attemptLimiter.assertNotBlocked(key);
        attemptLimiter.recordFailure(key);

        // Campo trampa relleno: es un bot. Se descarta sin guardar y sin decírselo.
        if (submission.getWebsite() != null && !submission.getWebsite().isBlank()) {
            log.info("Solicitud descartada por campo trampa (IP {})", sourceIp);
            return;
        }

        if (!submission.isConsent()) {
            throw new IllegalArgumentException("Debes aceptar el aviso de privacidad");
        }

        String email = blankToNull(submission.getEmail());
        String phone = blankToNull(submission.getPhone());
        if (email == null && phone == null) {
            throw new IllegalArgumentException("Deja un correo o un teléfono para poder contactarte");
        }

        // Reenviar el formulario porque no se vio la confirmación no debe llenar el panel de
        // filas repetidas. Se responde igual, sin crear otra.
        if (email != null && repository.findFirstByEmailIgnoreCaseAndStatus(
                email, RegistrationStatus.PENDING).isPresent()) {
            log.info("Solicitud duplicada ignorada (ya hay una pendiente con ese correo)");
            return;
        }

        RegistrationRequest request = new RegistrationRequest();
        request.setBusinessName(submission.getBusinessName().trim());
        request.setContactName(submission.getContactName().trim());
        request.setEmail(email);
        request.setPhone(phone);
        request.setCity(blankToNull(submission.getCity()));
        request.setMessage(blankToNull(submission.getMessage()));
        request.setStatus(RegistrationStatus.PENDING);
        request.setConsentAt(LocalDateTime.now());
        request.setSourceIp(sourceIp);

        repository.save(request);
        log.info("Nueva solicitud de acceso: {}", request.getBusinessName());
    }

    @Transactional(readOnly = true)
    public List<RegistrationRequest> list(RegistrationStatus status) {
        return status != null
                ? repository.findByStatusOrderByCreatedAtAsc(status)
                : repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Aprueba una solicitud y da de alta la óptica.
     *
     * Los datos que faltan (usuario del dueño, PIN, días de prueba) los pone el administrador
     * al aprobar: el formulario público no los pide, y no debería — nadie elige su propio
     * nombre de usuario en el sistema de otro.
     */
    @Transactional
    public ActivationDelivery approve(Long id, CreateTenantRequest overrides, String reviewer) {
        RegistrationRequest request = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new RuntimeException("Esa solicitud ya fue revisada (" + request.getStatus() + ")");
        }

        // Lo que no venga en overrides se toma de la solicitud: el administrador no debería
        // recapturar lo que el prospecto ya escribió.
        CreateTenantRequest creation = new CreateTenantRequest();
        creation.setBusinessName(firstNonBlank(overrides.getBusinessName(), request.getBusinessName()));
        creation.setBranchName(overrides.getBranchName());
        creation.setAddress(overrides.getAddress());
        creation.setPin(overrides.getPin());
        creation.setOwnerFullName(firstNonBlank(overrides.getOwnerFullName(), request.getContactName()));
        creation.setOwnerUsername(overrides.getOwnerUsername());
        creation.setOwnerEmail(firstNonBlank(overrides.getOwnerEmail(), request.getEmail()));
        creation.setTrialDays(overrides.getTrialDays());
        creation.setNotes(overrides.getNotes());

        ActivationDelivery delivery = tenantService.createOptica(creation);

        request.setStatus(RegistrationStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(reviewer);
        request.setCreatedOwnerId(delivery.user().getId());
        repository.save(request);

        auditService.log(AuditAction.REGISTRATION_APPROVED, "RegistrationRequest", request.getId(),
                "óptica: " + creation.getBusinessName() + "; dueño: " + creation.getOwnerUsername(), null);

        return delivery;
    }

    /** No borra: saber a quién ya se le dijo que no también es información. */
    @Transactional
    public RegistrationRequest reject(Long id, String note, String reviewer) {
        RegistrationRequest request = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Solicitud no encontrada"));

        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new RuntimeException("Esa solicitud ya fue revisada (" + request.getStatus() + ")");
        }

        request.setStatus(RegistrationStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewedBy(reviewer);
        request.setReviewNote(note);
        RegistrationRequest saved = repository.save(request);

        auditService.log(AuditAction.REGISTRATION_REJECTED, "RegistrationRequest", saved.getId(),
                "óptica: " + saved.getBusinessName(), null);

        return saved;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred.trim() : fallback;
    }
}
