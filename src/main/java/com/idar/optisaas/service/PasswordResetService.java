package com.idar.optisaas.service;

import com.idar.optisaas.entity.PasswordResetToken;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.mail.EmailMessage;
import com.idar.optisaas.mail.MailSender;
import com.idar.optisaas.mail.MailTemplates;
import com.idar.optisaas.repository.PasswordResetTokenRepository;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.AttemptLimiter;
import com.idar.optisaas.util.AuditAction;
import com.idar.optisaas.util.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Recuperación de contraseña self-service, solo para el DUEÑO.
 *
 * Por qué solo el dueño: es quien contrata el servicio y tiene correo propio. Los empleados no
 * controlan su cuenta — si pierden el acceso, el dueño se los restablece con el código de
 * activación que ya existe (ver UserService.resetCredentials). Abrir esto a los empleados
 * ampliaría sin necesidad quién puede tomar una cuenta desde fuera.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    /** Ventana corta a propósito: el enlace da acceso a la cuenta. */
    private static final int TOKEN_VALID_MINUTES = 60;
    private static final int MIN_PASSWORD_LENGTH = 8;

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordResetTokenRepository tokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private MailSender mailSender;
    @Autowired private MailTemplates mailTemplates;
    @Autowired private AttemptLimiter attemptLimiter;
    @Autowired private AuditService auditService;

    // URL pública del frontend, NO la lista de CORS (app.frontend.url): esa admite varios
    // orígenes y su primer valor no es necesariamente donde vive la app.
    @Value("${app.frontend.publicUrl}")
    private String frontendPublicUrl;

    /**
     * Envía el enlace de recuperación si procede.
     *
     * No revela NADA de lo que encuentra: el llamador siempre recibe la misma respuesta, exista
     * o no el correo, sea dueño o no. Si contestara distinto, cualquiera podría averiguar qué
     * correos tienen cuenta y cuáles son de dueños.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String clean = email.trim().toLowerCase();

        // Se limita por correo para que nadie use este endpoint como generador de correos.
        String key = "pwdreset:" + clean;
        try {
            attemptLimiter.assertNotBlocked(key);
        } catch (RuntimeException e) {
            log.warn("Recuperación bloqueada por exceso de intentos para {}", clean);
            return;
        }
        attemptLimiter.recordFailure(key);

        Optional<User> found = userRepository.findByEmail(clean);
        if (found.isEmpty()) {
            log.info("Recuperación solicitada para un correo sin cuenta");
            return;
        }

        User user = found.get();
        if (!isOwner(user)) {
            log.info("Recuperación solicitada por un usuario que no es Dueño (id {})", user.getId());
            return;
        }
        if (!user.isCredentialsSet()) {
            // Cuenta aún sin activar: el camino correcto es el código de activación, no esto.
            log.info("Recuperación solicitada para una cuenta sin activar (id {})", user.getId());
            return;
        }

        // Pedir un enlace nuevo anula los anteriores: solo el último debe servir.
        tokenRepository.invalidateActiveTokens(user, LocalDateTime.now());

        String rawToken = generateToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setTokenHash(hash(rawToken));
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_VALID_MINUTES));
        tokenRepository.save(token);

        String link = buildResetLink(rawToken);
        EmailMessage message = mailTemplates.passwordReset(user.getEmail(), user.getFullName(), link, TOKEN_VALID_MINUTES);
        mailSender.send(message);

        auditService.logAs(AuditAction.PASSWORD_RESET_REQUESTED, user, "User", user.getId(),
                "se envió enlace de recuperación a su correo", null);
    }

    /**
     * Consume el token y fija la contraseña nueva.
     *
     * @return el usuario actualizado
     * @throws RuntimeException si el token no sirve (inexistente, vencido o ya usado)
     */
    @Transactional
    public User completeReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new RuntimeException("Enlace de recuperación inválido");
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new RuntimeException("La contraseña debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }

        PasswordResetToken token = tokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new RuntimeException("El enlace no es válido o ya se usó"));

        if (!token.isUsable()) {
            throw new RuntimeException("El enlace expiró o ya se usó. Solicita uno nuevo.");
        }

        User user = token.getUser();
        // Se revalida el rol: si la cuenta dejó de ser Dueño entre la solicitud y el uso del
        // enlace, este ya no debe servir.
        if (!isOwner(user)) {
            throw new RuntimeException("El enlace no es válido");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setCredentialsSet(true);
        userRepository.save(user);

        // Se queman todos los tokens vivos, no solo este: si se pidieron varios enlaces, ninguno
        // debe seguir sirviendo después de un cambio efectivo.
        tokenRepository.invalidateActiveTokens(user, LocalDateTime.now());

        // El intento acumulado se limpia: ya demostró ser el dueño del buzón.
        attemptLimiter.reset("pwdreset:" + (user.getEmail() == null ? "" : user.getEmail().toLowerCase()));

        auditService.logAs(AuditAction.PASSWORD_RESET_COMPLETED, user, "User", user.getId(),
                "cambió su contraseña con un enlace de recuperación", null);

        return user;
    }

    private boolean isOwner(User user) {
        return user.getBranchRoles() != null
                && user.getBranchRoles().stream().anyMatch(r -> r.getRole() == Role.OWNER);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 basta y sobra aquí: el token es aleatorio de 256 bits, no una contraseña que
     * alguien pueda adivinar por fuerza bruta o diccionario. Lo que se busca es que un volcado
     * de la base no entregue tokens usables.
     */
    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private String buildResetLink(String rawToken) {
        String base = frontendPublicUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/?reset-token=" + rawToken;
    }
}
