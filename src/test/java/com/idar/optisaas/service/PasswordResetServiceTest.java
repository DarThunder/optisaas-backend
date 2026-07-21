package com.idar.optisaas.service;

import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.PasswordResetToken;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.mail.EmailMessage;
import com.idar.optisaas.mail.MailSender;
import com.idar.optisaas.mail.MailTemplates;
import com.idar.optisaas.repository.PasswordResetTokenRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.AttemptLimiter;
import com.idar.optisaas.util.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private UserRepository userRepository;
    private PasswordResetTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private MailSender mailSender;
    private AttemptLimiter attemptLimiter;
    private AuditService auditService;
    private PasswordResetService service;

    @BeforeEach
    void setUp() throws Exception {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(PasswordResetTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        mailSender = mock(MailSender.class);
        attemptLimiter = mock(AttemptLimiter.class);
        auditService = mock(AuditService.class);

        service = new PasswordResetService();
        // Plantillas reales (no mock): las pruebas verifican el CONTENIDO del correo.
        setField("mailTemplates", new MailTemplates("Fóvea", "VLK"));
        setField("userRepository", userRepository);
        setField("tokenRepository", tokenRepository);
        setField("passwordEncoder", passwordEncoder);
        setField("mailSender", mailSender);
        setField("attemptLimiter", attemptLimiter);
        setField("auditService", auditService);
        setField("frontendPublicUrl", "http://localhost:3000");

        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void setField(String name, Object value) throws Exception {
        var field = PasswordResetService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(service, value);
    }

    private User user(String email, Role role, boolean credentialsSet) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setFullName("Edwin Dueño");
        user.setCredentialsSet(credentialsSet);

        Branch branch = new Branch();
        branch.setId(10L);
        UserBranchRole ubr = new UserBranchRole();
        ubr.setUser(user);
        ubr.setBranch(branch);
        ubr.setRole(role);
        user.setBranchRoles(Set.of(ubr));
        return user;
    }

    // ---------- Solicitud ----------

    @Test
    void sendsLinkToOwner() {
        User owner = user("dueno@optica.com", Role.OWNER, true);
        when(userRepository.findByEmail("dueno@optica.com")).thenReturn(Optional.of(owner));

        service.requestReset("  Dueno@Optica.com  ");

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mailSender).send(captor.capture());
        EmailMessage sent = captor.getValue();

        assertEquals("dueno@optica.com", sent.to());
        // Es un correo de la plataforma sobre su cuenta: NO debe llevar la identidad de la óptica.
        assertNull(sent.fromName());
        assertNull(sent.replyTo());
        // El enlace usa app.frontend.publicUrl, NO la lista de CORS: usar esa última hacía que
        // el correo apuntara a un puerto viejo (5500) donde la app ya no vive.
        assertTrue(sent.textBody().contains("http://localhost:3000/?reset-token="),
                "el enlace debe usar la URL pública del frontend");
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    // El token viaja en el correo, pero en la base solo debe quedar su hash: si se filtrara,
    // los registros no deben servir para tomar la cuenta.
    @Test
    void storesOnlyTheTokenHash() {
        User owner = user("dueno@optica.com", Role.OWNER, true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(owner));

        service.requestReset("dueno@optica.com");

        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        ArgumentCaptor<EmailMessage> mailCaptor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mailSender).send(mailCaptor.capture());

        String stored = tokenCaptor.getValue().getTokenHash();
        assertEquals(64, stored.length(), "un SHA-256 en hexadecimal son 64 caracteres");
        assertFalse(mailCaptor.getValue().textBody().contains(stored),
                "el hash guardado no debe coincidir con el token que viaja en el correo");
    }

    @Test
    void doesNotSendToEmployees() {
        User seller = user("vendedor@optica.com", Role.SELLER, true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(seller));

        service.requestReset("vendedor@optica.com");

        verify(mailSender, never()).send(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void doesNotSendWhenEmailIsUnknown() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        // No debe lanzar: para el cliente, un correo desconocido se ve igual que uno válido.
        assertDoesNotThrow(() -> service.requestReset("nadie@ejemplo.com"));
        verify(mailSender, never()).send(any());
    }

    @Test
    void doesNotSendToAccountsPendingActivation() {
        User pending = user("dueno@optica.com", Role.OWNER, false);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(pending));

        service.requestReset("dueno@optica.com");

        verify(mailSender, never()).send(any());
    }

    // Pedir un enlace nuevo debe anular los anteriores: solo el último puede servir.
    @Test
    void invalidatesPreviousTokensWhenIssuingANewOne() {
        User owner = user("dueno@optica.com", Role.OWNER, true);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(owner));

        service.requestReset("dueno@optica.com");

        verify(tokenRepository).invalidateActiveTokens(eq(owner), any(LocalDateTime.class));
    }

    @Test
    void blockedRequestsDoNotSendMail() {
        doThrow(new RuntimeException("Demasiados intentos")).when(attemptLimiter).assertNotBlocked(anyString());

        assertDoesNotThrow(() -> service.requestReset("dueno@optica.com"));
        verify(mailSender, never()).send(any());
        verify(userRepository, never()).findByEmail(anyString());
    }

    // ---------- Canje ----------

    private PasswordResetToken usableToken(User user) {
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        return token;
    }

    @Test
    void completesResetWithAValidToken() {
        User owner = user("dueno@optica.com", Role.OWNER, true);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usableToken(owner)));
        when(passwordEncoder.encode("nuevaClave123")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User updated = service.completeReset("token-que-sea", "nuevaClave123");

        assertEquals("hashed", updated.getPassword());
        verify(userRepository).save(owner);
        // Tras un cambio efectivo, ningún enlace pendiente debe seguir sirviendo.
        verify(tokenRepository).invalidateActiveTokens(eq(owner), any(LocalDateTime.class));
    }

    @Test
    void rejectsAnAlreadyUsedToken() {
        User owner = user("dueno@optica.com", Role.OWNER, true);
        PasswordResetToken used = usableToken(owner);
        used.setUsedAt(LocalDateTime.now().minusMinutes(5));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.completeReset("token", "nuevaClave123"));
        assertTrue(ex.getMessage().contains("expiró o ya se usó"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAnExpiredToken() {
        User owner = user("dueno@optica.com", Role.OWNER, true);
        PasswordResetToken expired = usableToken(owner);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThrows(RuntimeException.class, () -> service.completeReset("token", "nuevaClave123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAnUnknownToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> service.completeReset("inventado", "nuevaClave123"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsAShortPassword() {
        assertThrows(RuntimeException.class, () -> service.completeReset("token", "corta"));
        // Ni siquiera debe consultarse el token si la contraseña no cumple.
        verify(tokenRepository, never()).findByTokenHash(anyString());
    }

    // Si la cuenta dejó de ser Dueño entre que se pidió el enlace y que se usó, ya no vale.
    @Test
    void rejectsWhenTheUserIsNoLongerAnOwner() {
        User demoted = user("exdueno@optica.com", Role.MANAGER, true);
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(usableToken(demoted)));

        assertThrows(RuntimeException.class, () -> service.completeReset("token", "nuevaClave123"));
        verify(userRepository, never()).save(any());
    }
}
