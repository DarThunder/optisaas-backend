package com.idar.optisaas.security;

import com.idar.optisaas.entity.Branch;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.entity.UserBranchRole;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.JwtUtils;
import com.idar.optisaas.util.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * El administrador de plataforma no debe poder ver los datos de ninguna óptica.
 *
 * La garantía es ESTRUCTURAL, no una lista de chequeos: se le crea con cero filas en
 * user_branch_roles, y como todas las consultas del sistema se acotan a las sucursales del
 * usuario, no hay nada que devolverle. Estas pruebas fijan las dos condiciones de las que
 * depende esa garantía, para que nadie las rompa sin enterarse:
 *
 *   1. Recibe ROLE_PLATFORM y NINGUNA autoridad de óptica.
 *   2. No se le fija TenantContext, así que no queda "dentro" de ninguna sucursal.
 *
 * Si alguien algún día le diera un UserBranchRole para "que pueda ayudar a un cliente", estas
 * pruebas no bastarían — por eso el aislamiento se apoya en no pertenecer, no en filtrar.
 */
class PlatformAdminIsolationTest {

    private JwtUtils jwtUtils;
    private UserRepository userRepository;
    private AuthTokenFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtils = mock(JwtUtils.class);
        userRepository = mock(UserRepository.class);

        filter = new AuthTokenFilter();
        setField("jwtUtils", jwtUtils);
        setField("userRepository", userRepository);

        when(jwtUtils.getJwtFromCookies(any())).thenReturn("token");
        when(jwtUtils.validateJwtToken("token")).thenReturn(true);
        when(jwtUtils.getUserNameFromJwtToken("token")).thenReturn("plataforma");

        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private void setField(String name, Object value) throws Exception {
        var field = AuthTokenFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(filter, value);
    }

    private Set<String> runFilter() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Set.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    private User platformAdmin() {
        User user = new User();
        user.setId(1L);
        user.setUsername("plataforma");
        user.setPlatformAdmin(true);
        user.setBranchRoles(Set.of()); // Cero vínculos: no pertenece a ninguna óptica.
        return user;
    }

    private User opticaOwner() {
        User user = new User();
        user.setId(2L);
        user.setUsername("mogar");
        user.setPlatformAdmin(false);

        Branch branch = new Branch();
        branch.setId(10L);
        UserBranchRole role = new UserBranchRole();
        role.setUser(user);
        role.setBranch(branch);
        role.setRole(Role.OWNER);
        user.setBranchRoles(Set.of(role));
        return user;
    }

    @Test
    void elAdministradorDePlataformaRecibeSoloRolePlatform() throws Exception {
        when(userRepository.findByEmailOrUsername(anyString(), anyString()))
                .thenReturn(Optional.of(platformAdmin()));
        when(jwtUtils.isFullToken("token")).thenReturn(false);

        Set<String> authorities = runFilter();

        assertEquals(Set.of("ROLE_PLATFORM"), authorities);
        assertFalse(authorities.contains("ROLE_OWNER"), "no debe heredar permisos de ninguna óptica");
    }

    @Test
    void elAdministradorDePlataformaNoQuedaDentroDeNingunaSucursal() throws Exception {
        when(userRepository.findByEmailOrUsername(anyString(), anyString()))
                .thenReturn(Optional.of(platformAdmin()));
        when(jwtUtils.isFullToken("token")).thenReturn(false);

        runFilter();

        assertNull(TenantContext.getCurrentBranch(),
                "sin contexto de sucursal, las consultas acotadas por inquilino no le devuelven nada");
    }

    /**
     * Aunque llegara con un token que dice traer sucursal, sigue sin ser de ninguna óptica:
     * el caso se resuelve antes de mirar el token, así que un token manipulado no lo mete
     * dentro de un inquilino.
     */
    @Test
    void niConUnTokenQueDiceTraerSucursal() throws Exception {
        when(userRepository.findByEmailOrUsername(anyString(), anyString()))
                .thenReturn(Optional.of(platformAdmin()));
        when(jwtUtils.isFullToken("token")).thenReturn(true);
        when(jwtUtils.getBranchIdFromToken("token")).thenReturn(10L);

        Set<String> authorities = runFilter();

        assertEquals(Set.of("ROLE_PLATFORM"), authorities);
        assertNull(TenantContext.getCurrentBranch());
    }

    // --- Regresión: el camino de siempre no cambió ---

    @Test
    void elDuenoDeUnaOpticaSigueRecibiendoRoleOwner() throws Exception {
        when(userRepository.findByEmailOrUsername(anyString(), anyString()))
                .thenReturn(Optional.of(opticaOwner()));
        when(jwtUtils.isFullToken("token")).thenReturn(false);

        Set<String> authorities = runFilter();

        assertEquals(Set.of("ROLE_OWNER"), authorities);
        assertFalse(authorities.contains("ROLE_PLATFORM"),
                "un dueño de óptica NUNCA debe alcanzar el panel de plataforma");
    }

    @Test
    void unUsuarioSinRolesNiPlataformaSigueSiendoPreAuth() throws Exception {
        User nobody = new User();
        nobody.setId(3L);
        nobody.setUsername("nadie");
        nobody.setBranchRoles(Set.of());
        when(userRepository.findByEmailOrUsername(anyString(), anyString()))
                .thenReturn(Optional.of(nobody));
        when(jwtUtils.isFullToken("token")).thenReturn(false);

        assertEquals(Set.of("ROLE_PRE_AUTH"), runFilter());
    }
}
