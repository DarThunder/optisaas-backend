package com.idar.optisaas.service;

import com.idar.optisaas.dto.ActivationDelivery;
import com.idar.optisaas.dto.CreateTenantRequest;
import com.idar.optisaas.entity.*;
import com.idar.optisaas.mail.EmployeeActivationMailer;
import com.idar.optisaas.repository.*;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.Role;
import com.idar.optisaas.util.SubscriptionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantServiceTest {

    private UserRepository userRepository;
    private BranchRepository branchRepository;
    private BranchSettingsRepository branchSettingsRepository;
    private SubscriptionRepository subscriptionRepository;
    private PasswordEncoder passwordEncoder;
    private EmployeeActivationMailer activationMailer;
    private AuditService auditService;
    private TenantService service;

    @BeforeEach
    void setUp() throws Exception {
        userRepository = mock(UserRepository.class);
        branchRepository = mock(BranchRepository.class);
        branchSettingsRepository = mock(BranchSettingsRepository.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        activationMailer = mock(EmployeeActivationMailer.class);
        auditService = mock(AuditService.class);

        service = new TenantService();
        setField("userRepository", userRepository);
        setField("branchRepository", branchRepository);
        setField("branchSettingsRepository", branchSettingsRepository);
        setField("subscriptionRepository", subscriptionRepository);
        setField("passwordEncoder", passwordEncoder);
        setField("activationMailer", activationMailer);
        setField("auditService", auditService);

        when(userRepository.findByEmailOrUsername(any(), any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
        when(branchRepository.save(any())).thenAnswer(inv -> {
            Branch b = inv.getArgument(0);
            b.setId(10L);
            return b;
        });
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(20L);
            return u;
        });
        when(subscriptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void setField(String name, Object value) throws Exception {
        var field = TenantService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private CreateTenantRequest request() {
        CreateTenantRequest r = new CreateTenantRequest();
        r.setBusinessName("Óptica Mogar");
        r.setOwnerFullName("Dueño de Mogar");
        r.setOwnerUsername("mogar");
        return r;
    }

    // ---------------------------------------------------------------
    // El alta crea el cliente COMPLETO
    // ---------------------------------------------------------------

    @Test
    void elAltaCreaSucursalDuenoYVinculoDePropiedad() {
        service.createOptica(request());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User owner = captor.getValue();

        assertEquals("mogar", owner.getUsername());
        assertEquals(1, owner.getBranchRoles().size());

        UserBranchRole role = owner.getBranchRoles().iterator().next();
        assertEquals(Role.OWNER, role.getRole(), "el usuario creado debe ser DUEÑO de su sucursal");
        assertEquals(10L, role.getBranch().getId());
    }

    /**
     * De businessName salen el ticket impreso, el mensaje de WhatsApp y la identidad del correo
     * de activación. Si el alta no creara los ajustes, el correo que sale a continuación se
     * firmaría con un genérico y el primer ticket saldría sin nombre de negocio.
     */
    @Test
    void elAltaCreaLosAjustesConElNombreDelNegocio() {
        service.createOptica(request());

        ArgumentCaptor<BranchSettings> captor = ArgumentCaptor.forClass(BranchSettings.class);
        verify(branchSettingsRepository).save(captor.capture());

        assertEquals("Óptica Mogar", captor.getValue().getBusinessName());
        assertEquals(10L, captor.getValue().getBranchId(),
                "branch_id se fija a mano: el administrador de plataforma no tiene TenantContext");
    }

    @Test
    void elDuenoNaceSinContrasenaYConCodigoDeActivacion() {
        ActivationDelivery result = service.createOptica(request());
        User owner = result.user();

        assertNull(owner.getPassword(), "nadie debe fijar ni conocer la contraseña del dueño");
        assertFalse(owner.isCredentialsSet());
        assertNotNull(owner.getActivationCode());
        assertEquals(6, owner.getActivationCode().length());
        assertNotNull(owner.getActivationCodeExpiresAt());
    }

    /**
     * El camino manual sigue vivo: si el dueño no tiene correo, el código se dicta.
     * Guardar "" en vez de null rompería la columna única en cuanto hubiera dos ópticas sin correo.
     */
    @Test
    void sinCorreoElDuenoSeGuardaConNullNoConCadenaVacia() {
        CreateTenantRequest r = request();
        r.setOwnerEmail("   ");

        ActivationDelivery result = service.createOptica(r);

        assertNull(result.user().getEmail());
        assertFalse(result.wasEmailed(), "sin correo, el administrador tiene que dictar el código");
    }

    @Test
    void conCorreoSeEnviaElCodigo() {
        when(activationMailer.sendActivationCode(any(), anyLong(), anyBoolean()))
                .thenReturn("dueno@mogar.com");

        CreateTenantRequest r = request();
        r.setOwnerEmail("dueno@mogar.com");

        ActivationDelivery result = service.createOptica(r);

        assertTrue(result.wasEmailed());
        assertEquals("dueno@mogar.com", result.sentTo());
    }

    @Test
    void noSePuedeDarDeAltaUnUsuarioQueYaExiste() {
        when(userRepository.findByEmailOrUsername(any(), any()))
                .thenReturn(Optional.of(new User()));

        RuntimeException e = assertThrows(RuntimeException.class, () -> service.createOptica(request()));
        assertTrue(e.getMessage().contains("Ya existe"));
        verify(branchRepository, never()).save(any());
    }

    @Test
    void faltarElNombreDelNegocioAbortaAntesDeCrearNada() {
        CreateTenantRequest r = request();
        r.setBusinessName("  ");

        assertThrows(RuntimeException.class, () -> service.createOptica(r));
        verify(branchRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Suscripción
    // ---------------------------------------------------------------

    @Test
    void conDiasDePruebaSeFijaLaVigencia() {
        CreateTenantRequest r = request();
        r.setTrialDays(30);

        service.createOptica(r);

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());

        assertEquals(SubscriptionStatus.TRIAL, captor.getValue().getStatus());
        assertEquals(LocalDate.now().plusDays(30), captor.getValue().getValidUntil());
    }

    /** Un cliente en acompañamiento (Mogar afinando el sistema) no debe vencer solo. */
    @Test
    void sinDiasDePruebaLaSuscripcionNoVence() {
        service.createOptica(request());

        ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository).save(captor.capture());

        assertNull(captor.getValue().getValidUntil());
        assertFalse(captor.getValue().isExpired());
    }

    @Test
    void actualizarSoloElEstadoNoBorraLaVigencia() {
        Subscription existing = new Subscription();
        existing.setId(5L);
        existing.setStatus(SubscriptionStatus.TRIAL);
        existing.setValidUntil(LocalDate.of(2026, 12, 31));
        when(subscriptionRepository.findById(5L)).thenReturn(Optional.of(existing));

        Subscription updated = service.updateSubscription(5L, SubscriptionStatus.ACTIVE, null, null);

        assertEquals(SubscriptionStatus.ACTIVE, updated.getStatus());
        assertEquals(LocalDate.of(2026, 12, 31), updated.getValidUntil(),
                "un nulo significa 'no lo cambies', no 'bórralo'");
    }

    @Test
    void elAltaQuedaEnLaBitacora() {
        service.createOptica(request());

        verify(auditService).log(eq(AuditAction.TENANT_CREATED), eq("Branch"), eq(10L),
                contains("Óptica Mogar"), eq(10L));
    }
}
