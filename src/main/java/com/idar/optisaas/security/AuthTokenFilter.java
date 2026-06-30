package com.idar.optisaas.security;

import com.idar.optisaas.entity.User;
import com.idar.optisaas.repository.UserRepository;
import com.idar.optisaas.util.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    @Autowired private JwtUtils jwtUtils;
    @Autowired private UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = jwtUtils.getJwtFromCookies(request);
            if (jwt == null) {
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    jwt = authHeader.substring(7);
                }
            }

            if (jwt != null && jwtUtils.validateJwtToken(jwt)) {
                String username = jwtUtils.getUserNameFromJwtToken(jwt);
                Optional<User> userOp = userRepository.findByEmailOrUsername(username, username);

                if (userOp.isPresent()) {
                    User user = userOp.get();
                    List<SimpleGrantedAuthority> authorities = new ArrayList<>();

                    if (jwtUtils.isFullToken(jwt)) {
                    Long branchId = jwtUtils.getBranchIdFromToken(jwt);
                    TenantContext.setCurrentBranch(branchId);

                    String role;

                    if (branchId == null) {
                        role = jwtUtils.getRoleFromToken(jwt);
                    } else {
                        role = user.getBranchRoles().stream()
                            .filter(r -> r.getBranch().getId().equals(branchId))
                            .map(r -> r.getRole().name())
                            .findFirst()
                            .orElse("USER");
                    }

                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                    } else {
                        // MODO GLOBAL: Verificamos si es OWNER en alguna sucursal para darle el permiso global
                        boolean isOwner = user.getBranchRoles().stream()
                                .anyMatch(r -> r.getRole().name().equals("OWNER"));
                        
                        if (isOwner) {
                            authorities.add(new SimpleGrantedAuthority("ROLE_OWNER"));
                        } else {
                            authorities.add(new SimpleGrantedAuthority("ROLE_PRE_AUTH"));
                        }
                    }

                    UserDetails userDetails = org.springframework.security.core.userdetails.User
                            .withUsername(user.getUsername())
                            .password("") 
                            .authorities(authorities)
                            .build();

                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userDetails, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception e) {
            logger.error("Error en AuthTokenFilter: " + e.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}