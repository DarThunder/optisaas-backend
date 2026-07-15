package com.idar.optisaas.security;

import com.idar.optisaas.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Guardián de alcance para sesiones de Hub (panel de administrador global).
 *
 * Una sesión de Hub tiene un token FULL con {@code branchId == null}. En ese modo,
 * el filtro multi-tenant de Hibernate ({@code branchFilter}) queda desactivado, por
 * lo que los endpoints que se apoyan únicamente en él devolverían datos de TODAS las
 * sucursales de TODOS los tenants (fuga cross-tenant, p. ej. expedientes clínicos).
 *
 * El Hub es solo para administración global (reportes consolidados, usuarios,
 * sucursales, configuración). Los datos operativos por-sucursal no tienen sentido
 * ahí, así que se rechazan explícitamente en sesión de Hub. La operación normal
 * (POS, clínica, caja) ocurre siempre dentro de una sucursal (branchId presente).
 */
@Component
public class HubScopeGuardFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils;

    // Prefijos de datos operativos por-sucursal, prohibidos en una sesión de Hub.
    private static final List<String> BRANCH_SCOPED_PREFIXES = List.of(
            "/api/sales",
            "/api/clients",
            "/api/clinical-records",
            "/api/cash-sessions",
            "/api/cash-movements",
            "/api/promotions"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isHubSession(request) && isBranchScoped(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"message\":\"Esta operación requiere una sesión dentro de una sucursal, no el panel de administrador.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isHubSession(HttpServletRequest request) {
        try {
            String jwt = jwtUtils.getJwtFromCookies(request);
            if (jwt == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    jwt = authHeader.substring(7);
                }
            }
            if (jwt == null || !jwtUtils.validateJwtToken(jwt) || !jwtUtils.isFullToken(jwt)) {
                return false;
            }
            return jwtUtils.getBranchIdFromToken(jwt) == null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBranchScoped(String uri) {
        if (uri == null) return false;
        for (String prefix : BRANCH_SCOPED_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }
}
