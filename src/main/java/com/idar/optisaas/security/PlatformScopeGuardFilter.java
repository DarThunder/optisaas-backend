package com.idar.optisaas.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Guardián de alcance del administrador de plataforma.
 *
 * El administrador de plataforma no pertenece a ninguna óptica, así que las consultas acotadas
 * por sucursal ya le devuelven vacío. Eso es defensa en profundidad, no una garantía: depende
 * de que TODA consulta futura recuerde acotarse. Este filtro convierte el vacío accidental en
 * un rechazo explícito.
 *
 * A diferencia de {@link HubScopeGuardFilter}, que enumera lo prohibido, aquí la lista es de lo
 * PERMITIDO. La diferencia importa: con una lista de prohibidos, cada endpoint nuevo nace
 * alcanzable y hay que acordarse de añadirlo; con una de permitidos, nace bloqueado. Para una
 * cuenta que está por encima de todos los clientes, el valor por defecto tiene que ser "no".
 *
 * Se midió antes de escribirlo: sin este filtro, la cuenta de plataforma recibía HTTP 200 en
 * /api/products y /api/sales (con cuerpo vacío) y un 500 en /api/clinical-records.
 */
@Component
public class PlatformScopeGuardFilter extends OncePerRequestFilter {

    /** Lo único que una cuenta de plataforma puede tocar. */
    private static final List<String> ALLOWED_PREFIXES = List.of(
            "/api/platform",   // su panel
            "/api/auth",       // entrar, salir, activar su propia cuenta
            "/actuator/health"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (isPlatformAdmin() && !isAllowed(request.getRequestURI())) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"message\":\"La cuenta de plataforma administra el servicio, no opera ópticas.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if ("ROLE_PLATFORM".equals(authority.getAuthority())) return true;
        }
        return false;
    }

    private boolean isAllowed(String uri) {
        if (uri == null) return false;
        for (String prefix : ALLOWED_PREFIXES) {
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) return true;
        }
        return false;
    }
}
