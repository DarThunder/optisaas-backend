package com.idar.optisaas.config;

import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Crea la cuenta de administrador de plataforma al arrancar, si no existe.
 *
 * Resuelve el problema del huevo y la gallina: en producción SEED_ENABLED=false, así que una
 * base recién desplegada no tiene NINGÚN usuario. Sin esto no habría forma de entrar a dar de
 * alta la primera óptica sin meter SQL a mano (incluido un hash de BCrypt generado aparte).
 *
 * No fija contraseña: emite un código de activación, igual que cualquier alta del sistema. El
 * código sale por el log del servidor, que en el arranque solo puede leer quien tiene acceso
 * al servidor — es decir, quien ya podría hacer cualquier cosa de todos modos. Así ninguna
 * credencial vive en el repositorio, ni en el .env, ni en una variable de entorno.
 *
 * Es idempotente: si la cuenta ya existe no toca nada, así que reiniciar no regenera códigos
 * ni pisa al usuario. Para volver a emitir uno, se usa el flujo normal de reseteo.
 */
@Component
public class PlatformAdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformAdminBootstrap.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ACTIVATION_CODE_VALID_DAYS = 7;

    private final UserRepository userRepository;

    @Value("${app.platform.admin.username:}")
    private String adminUsername;

    @Value("${app.platform.admin.email:}")
    private String adminEmail;

    @Value("${app.platform.admin.fullName:Administrador de plataforma}")
    private String adminFullName;

    public PlatformAdminBootstrap(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (adminUsername == null || adminUsername.isBlank()) {
            return; // No configurado: en desarrollo normalmente no hace falta.
        }

        String email = (adminEmail == null || adminEmail.isBlank()) ? null : adminEmail.trim();

        if (userRepository.findByEmailOrUsername(email, adminUsername).isPresent()) {
            return; // Ya existe: no se toca.
        }

        User admin = new User();
        admin.setUsername(adminUsername.trim());
        admin.setEmail(email);
        admin.setFullName(adminFullName);
        admin.setActive(true);
        admin.setPlatformAdmin(true);

        // CERO filas en user_branch_roles: es lo que le impide ver datos de cualquier óptica.
        admin.setBranchRoles(Set.of());

        admin.setCredentialsSet(false);
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        admin.setActivationCode(code);
        admin.setActivationCodeExpiresAt(LocalDateTime.now().plusDays(ACTIVATION_CODE_VALID_DAYS));

        userRepository.save(admin);

        log.warn("=== Cuenta de administrador de plataforma creada: '{}' ===", admin.getUsername());
        log.warn("=== Código de activación: {} (válido {} días). Actívala y este mensaje no vuelve a salir. ===",
                code, ACTIVATION_CODE_VALID_DAYS);
    }
}
