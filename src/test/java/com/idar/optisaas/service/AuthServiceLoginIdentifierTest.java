package com.idar.optisaas.service;

import com.idar.optisaas.dto.LoginRequest;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.security.AttemptLimiter;
import com.idar.optisaas.util.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceLoginIdentifierTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtUtils jwtUtils;
    private AttemptLimiter attemptLimiter;
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtils = mock(JwtUtils.class);
        attemptLimiter = mock(AttemptLimiter.class);

        authService = new AuthService();
        setField("userRepository", userRepository);
        setField("passwordEncoder", passwordEncoder);
        setField("jwtUtils", jwtUtils);
        setField("attemptLimiter", attemptLimiter);
    }

    private void setField(String name, Object value) throws Exception {
        var field = AuthService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(authService, value);
    }

    private LoginRequest request(String identifier, String password) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setPassword(password);
        return request;
    }

    // Regresión: un cuerpo sin 'identifier' llegaba al repositorio como
    // findByEmailOrUsername(null, null), que la derived query traducía a
    // "email IS NULL OR username IS NULL". Eso empataba con todos los usuarios sin
    // correo y reventaba con IncorrectResultSizeDataAccessException, cuyo mensaje
    // interno ("Query did not return a unique result: 6 results were returned")
    // terminaba en la respuesta al cliente.
    @Test
    void rejectsNullIdentifierWithoutQueryingTheRepository() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login(request(null, "admin123")));

        assertEquals("Debes indicar tu correo o usuario", ex.getMessage());
        verify(userRepository, never()).findByEmailOrUsername(any(), any());
    }

    @Test
    void rejectsBlankIdentifierWithoutQueryingTheRepository() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> authService.login(request("   ", "admin123")));

        assertEquals("Debes indicar tu correo o usuario", ex.getMessage());
        verify(userRepository, never()).findByEmailOrUsername(any(), any());
    }

    // Un identificador vacío no debe consumir el presupuesto de intentos de la clave
    // "login:", que es compartida por todas las peticiones malformadas.
    @Test
    void blankIdentifierDoesNotConsumeAttemptBudget() {
        assertThrows(RuntimeException.class, () -> authService.login(request("", "admin123")));

        verify(attemptLimiter, never()).assertNotBlocked(anyString());
        verify(attemptLimiter, never()).recordFailure(anyString());
    }

    @Test
    void loginWithValidIdentifierStillWorks() {
        User user = new User();
        user.setUsername("admin");
        user.setPassword("hashed");
        user.setCredentialsSet(true);
        when(userRepository.findByEmailOrUsername("admin", "admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("admin123", "hashed")).thenReturn(true);
        when(jwtUtils.generatePreAuthCookie("admin"))
                .thenReturn(ResponseCookie.from("jwt", "token").build());

        ResponseCookie cookie = authService.login(request("admin", "admin123"));

        assertNotNull(cookie);
        verify(attemptLimiter).reset("login:admin");
    }
}
