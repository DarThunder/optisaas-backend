package com.idar.optisaas.repository;

import com.idar.optisaas.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Cubre la implementación default de {@link UserRepository#findByEmailOrUsername}, donde
 * vivía el fallo: los criterios vacíos deben descartarse antes de tocar la BD y el
 * resultado acotarse a uno, para no volver a lanzar IncorrectResultSizeDataAccessException.
 */
class UserRepositoryFindByEmailOrUsernameTest {

    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // CALLS_REAL_METHODS ejecuta el default method real; las derived queries siguen mockeadas.
        userRepository = mock(UserRepository.class, Mockito.withSettings().defaultAnswer(CALLS_REAL_METHODS));
    }

    private User user(String email, String username) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        return user;
    }

    // Regresión: (null, null) se traducía a "email IS NULL OR username IS NULL", que empataba
    // con los 6 usuarios sin correo de la BD de desarrollo y reventaba por multiplicidad.
    @Test
    void nullIdentifierNeverReachesTheDatabase() {
        Optional<User> found = userRepository.findByEmailOrUsername(null, null);

        assertTrue(found.isEmpty());
        verify(userRepository, never()).findAllByEmailOrUsername(any(), any());
        verify(userRepository, never()).findAllByEmail(any());
        verify(userRepository, never()).findAllByUsername(any());
    }

    @Test
    void blankIdentifierNeverReachesTheDatabase() {
        assertTrue(userRepository.findByEmailOrUsername("  ", "  ").isEmpty());

        verify(userRepository, never()).findAllByEmailOrUsername(any(), any());
        verify(userRepository, never()).findAllByEmail(any());
        verify(userRepository, never()).findAllByUsername(any());
    }

    // Con solo uno de los dos criterios, el vacío no debe entrar a la consulta como IS NULL:
    // se consulta únicamente por el criterio presente.
    @Test
    void searchesOnlyByUsernameWhenEmailIsAbsent() {
        User admin = user(null, "admin");
        when(userRepository.findAllByUsername("admin")).thenReturn(List.of(admin));

        Optional<User> found = userRepository.findByEmailOrUsername(null, "admin");

        assertEquals(admin, found.orElse(null));
        verify(userRepository, never()).findAllByEmailOrUsername(any(), any());
    }

    @Test
    void searchesOnlyByEmailWhenUsernameIsAbsent() {
        User owner = user("owner@mogar.com", "owner");
        when(userRepository.findAllByEmail("owner@mogar.com")).thenReturn(List.of(owner));

        Optional<User> found = userRepository.findByEmailOrUsername("owner@mogar.com", null);

        assertEquals(owner, found.orElse(null));
        verify(userRepository, never()).findAllByEmailOrUsername(any(), any());
    }

    // Varias coincidencias ya no revientan: el mismo texto puede ser el correo de alguien
    // y el usuario de otra persona, y debe ganar el correo de forma determinista.
    @Test
    void multipleMatchesResolveToTheEmailMatch() {
        User byUsername = user("otro@mogar.com", "admin@mogar.com");
        User byEmail = user("admin@mogar.com", "admin");
        when(userRepository.findAllByEmailOrUsername("admin@mogar.com", "admin@mogar.com"))
                .thenReturn(List.of(byUsername, byEmail));

        Optional<User> found = userRepository.findByEmailOrUsername("admin@mogar.com", "admin@mogar.com");

        assertEquals(byEmail, found.orElse(null));
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        when(userRepository.findAllByEmailOrUsername("nadie", "nadie")).thenReturn(List.of());

        assertTrue(userRepository.findByEmailOrUsername("nadie", "nadie").isEmpty());
    }
}
