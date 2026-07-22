package com.idar.optisaas.service;

import com.idar.optisaas.dto.ActivationDelivery;
import com.idar.optisaas.dto.CreateTenantRequest;
import com.idar.optisaas.dto.RegistrationRequestSubmission;
import com.idar.optisaas.entity.RegistrationRequest;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.RegistrationRequestRepository;
import com.idar.optisaas.security.AttemptLimiter;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.RegistrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationRequestServiceTest {

    private RegistrationRequestRepository repository;
    private TenantService tenantService;
    private AttemptLimiter attemptLimiter;
    private AuditService auditService;
    private RegistrationRequestService service;

    @BeforeEach
    void setUp() throws Exception {
        repository = mock(RegistrationRequestRepository.class);
        tenantService = mock(TenantService.class);
        attemptLimiter = mock(AttemptLimiter.class);
        auditService = mock(AuditService.class);

        service = new RegistrationRequestService();
        setField("repository", repository);
        setField("tenantService", tenantService);
        setField("attemptLimiter", attemptLimiter);
        setField("auditService", auditService);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findFirstByEmailIgnoreCaseAndStatus(any(), any())).thenReturn(Optional.empty());
    }

    private void setField(String name, Object value) throws Exception {
        var field = RegistrationRequestService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private RegistrationRequestSubmission submission() {
        RegistrationRequestSubmission s = new RegistrationRequestSubmission();
        s.setBusinessName("Óptica del Valle");
        s.setContactName("Ana López");
        s.setEmail("ana@opticadelvalle.mx");
        s.setConsent(true);
        return s;
    }

    // ---------------------------------------------------------------
    // Recepción desde el formulario público
    // ---------------------------------------------------------------

    @Test
    void unaSolicitudValidaSeGuardaComoPendiente() {
        service.submit(submission(), "189.203.1.1");

        ArgumentCaptor<RegistrationRequest> captor = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(repository).save(captor.capture());
        RegistrationRequest saved = captor.getValue();

        assertEquals("Óptica del Valle", saved.getBusinessName());
        assertEquals(RegistrationStatus.PENDING, saved.getStatus());
        assertEquals("189.203.1.1", saved.getSourceIp());
        assertNotNull(saved.getConsentAt(), "hay que poder demostrar CUÁNDO aceptó, no solo que aceptó");
    }

    @Test
    void seCuentaElEnvioContraElLimitePorIp() {
        service.submit(submission(), "189.203.1.1");

        verify(attemptLimiter).assertNotBlocked("regreq:189.203.1.1");
        verify(attemptLimiter).recordFailure("regreq:189.203.1.1");
    }

    @Test
    void siLaIpEstaBloqueadaNoSeGuardaNada() {
        doThrow(new RuntimeException("Demasiados intentos"))
                .when(attemptLimiter).assertNotBlocked(anyString());

        assertThrows(RuntimeException.class, () -> service.submit(submission(), "1.2.3.4"));
        verify(repository, never()).save(any());
    }

    /** Un bot rellena todo lo que encuentra; una persona no ve este campo. */
    @Test
    void elCampoTrampaDescartaLaSolicitudEnSilencio() {
        RegistrationRequestSubmission s = submission();
        s.setWebsite("http://spam.example");

        service.submit(s, "1.2.3.4");

        verify(repository, never()).save(any());
    }

    @Test
    void sinConsentimientoNoSeGuardanSusDatos() {
        RegistrationRequestSubmission s = submission();
        s.setConsent(false);

        assertThrows(IllegalArgumentException.class, () -> service.submit(s, "1.2.3.4"));
        verify(repository, never()).save(any());
    }

    @Test
    void sinCorreoNiTelefonoLaSolicitudNoSirve() {
        RegistrationRequestSubmission s = submission();
        s.setEmail(null);
        s.setPhone("  ");

        assertThrows(IllegalArgumentException.class, () -> service.submit(s, "1.2.3.4"));
        verify(repository, never()).save(any());
    }

    @Test
    void soloConTelefonoEsSuficiente() {
        RegistrationRequestSubmission s = submission();
        s.setEmail(null);
        s.setPhone("228 212 2440");

        service.submit(s, "1.2.3.4");

        verify(repository).save(any());
    }

    /** Reenviar el formulario porque no se vio la confirmación no debe llenar el panel. */
    @Test
    void unDuplicadoPendienteNoCreaOtraFila() {
        when(repository.findFirstByEmailIgnoreCaseAndStatus(anyString(), eq(RegistrationStatus.PENDING)))
                .thenReturn(Optional.of(new RegistrationRequest()));

        service.submit(submission(), "1.2.3.4");

        verify(repository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Revisión desde el panel
    // ---------------------------------------------------------------

    private RegistrationRequest pending() {
        RegistrationRequest r = new RegistrationRequest();
        r.setId(7L);
        r.setBusinessName("Óptica del Valle");
        r.setContactName("Ana López");
        r.setEmail("ana@opticadelvalle.mx");
        r.setStatus(RegistrationStatus.PENDING);
        return r;
    }

    private ActivationDelivery delivery() {
        User owner = new User();
        owner.setId(99L);
        owner.setUsername("valle");
        return new ActivationDelivery(owner, null);
    }

    @Test
    void aprobarDaDeAltaLaOpticaYCierraLaSolicitud() {
        when(repository.findById(7L)).thenReturn(Optional.of(pending()));
        when(tenantService.createOptica(any())).thenReturn(delivery());

        CreateTenantRequest overrides = new CreateTenantRequest();
        overrides.setOwnerUsername("valle");
        overrides.setTrialDays(30);

        service.approve(7L, overrides, "vlk");

        ArgumentCaptor<RegistrationRequest> captor = ArgumentCaptor.forClass(RegistrationRequest.class);
        verify(repository).save(captor.capture());
        RegistrationRequest saved = captor.getValue();

        assertEquals(RegistrationStatus.APPROVED, saved.getStatus());
        assertEquals("vlk", saved.getReviewedBy());
        assertEquals(99L, saved.getCreatedOwnerId(), "queda el rastro de qué solicitud fue qué cliente");
        assertNotNull(saved.getReviewedAt());
    }

    /** El administrador no debería recapturar lo que el prospecto ya escribió. */
    @Test
    void loQueNoSeSobrescribeSeTomaDeLaSolicitud() {
        when(repository.findById(7L)).thenReturn(Optional.of(pending()));
        when(tenantService.createOptica(any())).thenReturn(delivery());

        CreateTenantRequest overrides = new CreateTenantRequest();
        overrides.setOwnerUsername("valle");

        service.approve(7L, overrides, "vlk");

        ArgumentCaptor<CreateTenantRequest> captor = ArgumentCaptor.forClass(CreateTenantRequest.class);
        verify(tenantService).createOptica(captor.capture());
        CreateTenantRequest creation = captor.getValue();

        assertEquals("Óptica del Valle", creation.getBusinessName());
        assertEquals("Ana López", creation.getOwnerFullName());
        assertEquals("ana@opticadelvalle.mx", creation.getOwnerEmail());
    }

    @Test
    void noSePuedeAprobarDosVeces() {
        RegistrationRequest already = pending();
        already.setStatus(RegistrationStatus.APPROVED);
        when(repository.findById(7L)).thenReturn(Optional.of(already));

        assertThrows(RuntimeException.class,
                () -> service.approve(7L, new CreateTenantRequest(), "vlk"));
        verify(tenantService, never()).createOptica(any());
    }

    @Test
    void rechazarNoBorraLaSolicitud() {
        when(repository.findById(7L)).thenReturn(Optional.of(pending()));

        RegistrationRequest rejected = service.reject(7L, "Fuera de cobertura", "vlk");

        assertEquals(RegistrationStatus.REJECTED, rejected.getStatus());
        assertEquals("Fuera de cobertura", rejected.getReviewNote());
        verify(repository, never()).delete(any());
    }

    @Test
    void aprobarYRechazarQuedanEnLaBitacora() {
        when(repository.findById(7L)).thenReturn(Optional.of(pending()));
        when(tenantService.createOptica(any())).thenReturn(delivery());

        CreateTenantRequest overrides = new CreateTenantRequest();
        overrides.setOwnerUsername("valle");
        service.approve(7L, overrides, "vlk");
        verify(auditService).log(eq(AuditAction.REGISTRATION_APPROVED), eq("RegistrationRequest"),
                eq(7L), anyString(), isNull());

        when(repository.findById(8L)).thenReturn(Optional.of(pending()));
        service.reject(8L, null, "vlk");
        verify(auditService).log(eq(AuditAction.REGISTRATION_REJECTED), eq("RegistrationRequest"),
                any(), anyString(), isNull());
    }
}
