package com.idar.optisaas.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private AuthTokenFilter authTokenFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 1. Configuración de CORS y CSRF
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            
            // 2. Gestión de sesión Stateless (para JWT)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // 3. Reglas de Autorización
            .authorizeHttpRequests(auth -> auth
                // Permitir pre-flight requests (OPTIONS)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Endpoints públicos (Auth y Actuator)
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                
                // REGLA CLAVE: Acceso a gestión de empleados
                // .hasAnyRole busca automáticamente autoridades con prefijo "ROLE_"
                // Permitimos a OWNER y MANAGER gestionar esta sección globalmente
                .requestMatchers("/api/users/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/api/branches/**").hasRole("OWNER")
                
                // Cualquier otra ruta requiere estar autenticado
                .anyRequest().authenticated()
            );

        // 4. Inyectar nuestro filtro de JWT antes del filtro de autenticación de Spring
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración de CORS centralizada para permitir la comunicación con el Frontend
     * y el intercambio de Cookies (credentials).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Orígenes permitidos (ajusta según tu entorno de desarrollo/producción)
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://127.0.0.1:3000"));
        
        // Métodos permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        
        // Cabeceras permitidas
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control"));
        
        // IMPORTANTE: Permitir el envío de la Cookie 'optisaas-auth-token'
        configuration.setAllowCredentials(true); 
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}