package com.idar.optisaas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * La cuenta de plataforma solo puede tocar su panel.
 *
 * Sin este filtro se midió que recibía HTTP 200 (con cuerpo vacío) en /api/products y
 * /api/sales, y un 500 en /api/clinical-records. Que llegara vacío dependía de que cada
 * consulta se acotara por sucursal; aquí el rechazo es explícito y no depende de eso.
 */
class PlatformScopeGuardFilterTest {

    private final PlatformScopeGuardFilter filter = new PlatformScopeGuardFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("u", null,
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    /** @return true si el filtro dejó pasar la petición. */
    private boolean passesThrough(String uri) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilter(request, response, chain);

        try {
            verify(chain).doFilter(any(), any());
            return true;
        } catch (AssertionError e) {
            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
    }

    @Test
    void laCuentaDePlataformaAlcanzaSuPanel() throws Exception {
        authenticateAs("ROLE_PLATFORM");
        assertTrue(passesThrough("/api/platform/tenants"));
    }

    @Test
    void laCuentaDePlataformaPuedeEntrarYSalir() throws Exception {
        authenticateAs("ROLE_PLATFORM");
        assertTrue(passesThrough("/api/auth/logout"));
        assertTrue(passesThrough("/actuator/health"));
    }

    @Test
    void laCuentaDePlataformaNoAlcanzaDatosDeNingunaOptica() throws Exception {
        authenticateAs("ROLE_PLATFORM");
        for (String uri : List.of("/api/products", "/api/sales", "/api/clients",
                "/api/clinical-records", "/api/users/by-owner", "/api/audit",
                "/api/settings", "/api/reports/global/summary", "/api/branches")) {
            assertFalse(passesThrough(uri), "debería estar bloqueado: " + uri);
        }
    }

    /**
     * La lista es de PERMITIDOS, no de prohibidos: un endpoint que no existe todavía nace
     * bloqueado para esta cuenta en vez de nacer alcanzable.
     */
    @Test
    void unEndpointFuturoNaceBloqueadoParaLaPlataforma() throws Exception {
        authenticateAs("ROLE_PLATFORM");
        assertFalse(passesThrough("/api/loque-sea-que-se-agregue-manana"));
    }

    // --- Regresión: los usuarios de óptica no se ven afectados ---

    @Test
    void elDuenoDeUnaOpticaPasaSinEstorbo() throws Exception {
        authenticateAs("ROLE_OWNER");
        assertTrue(passesThrough("/api/products"));
        assertTrue(passesThrough("/api/clinical-records"));
        assertTrue(passesThrough("/api/users/by-owner"));
    }

    @Test
    void elVendedorPasaSinEstorbo() throws Exception {
        authenticateAs("ROLE_SELLER");
        assertTrue(passesThrough("/api/products"));
        assertTrue(passesThrough("/api/sales"));
    }

    @Test
    void sinAutenticarNoInterfiere() throws Exception {
        // El filtro no autentica: eso es de Spring Security. Solo acota a quien ya es plataforma.
        assertTrue(passesThrough("/api/products"));
    }
}
