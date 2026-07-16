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
import org.springframework.beans.factory.annotation.Value;
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

    @Autowired
    private HubScopeGuardFilter hubScopeGuardFilter;

    // Orígenes permitidos para CORS. Configurable por entorno (coma-separado).
    // En producción, fijar al dominio real del frontend.
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private List<String> allowedOrigins;

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
                
                // Endpoints públicos (Auth). Salud del sistema pública para health-checks;
                // el resto de actuator queda restringido al Dueño.
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/**").hasRole("OWNER")
                
                // Cambio de operador (fichaje por PIN): cualquier autenticado puede intentarlo,
                // el PIN de 4 dígitos del empleado destino es la propia validación de esta acción.
                // Debe ir ANTES de la regla general de /api/users/** (que sí es solo Dueño/Gerente).
                .requestMatchers(HttpMethod.POST, "/api/users/*/validate-pin").authenticated()

                // REGLA CLAVE: Acceso a gestión de empleados
                // .hasAnyRole busca automáticamente autoridades con prefijo "ROLE_"
                // Permitimos a OWNER y MANAGER gestionar esta sección globalmente
                .requestMatchers("/api/users/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/api/branches/**").hasRole("OWNER")

                // Bitácora de auditoría: solo el Dueño (el servicio la acota a SUS sucursales).
                .requestMatchers("/api/audit/**").hasRole("OWNER")

                // Devoluciones: registrarlas reingresa mercancía y saca dinero de la caja,
                // así que son de Dueño/Gerente. Consultarlas las necesita el vendedor para
                // ver el detalle de la venta, y el servicio las acota a su sucursal.
                // Debe ir ANTES de la regla general de /api/sales/**.
                .requestMatchers(HttpMethod.POST, "/api/sales/*/refunds").hasAnyRole("OWNER", "MANAGER")

                // Cortes / movimientos de caja y sesiones de caja: solo Dueño y Gerente
                .requestMatchers("/api/cash-movements/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/api/cash-sessions/**").hasAnyRole("OWNER", "MANAGER")

                // Reporte consolidado de todas las sucursales: SOLO el Dueño.
                // Debe ir ANTES de la regla general de /api/reports/**.
                .requestMatchers("/api/reports/global/**").hasRole("OWNER")

                // Reportes por sucursal (ventas, cuentas por cobrar, valuación): Dueño y Gerente
                .requestMatchers("/api/reports/**").hasAnyRole("OWNER", "MANAGER")

                // Configuración: leerla la necesita cualquiera (para imprimir el ticket),
                // pero editarla es solo del Dueño y el Gerente.
                .requestMatchers(HttpMethod.PUT, "/api/settings").hasAnyRole("OWNER", "MANAGER")

                // Inventario: cualquier rol autenticado puede consultar (lo necesita el POS),
                // pero solo Dueño/Gerente pueden crear, editar o borrar productos.
                .requestMatchers(HttpMethod.GET, "/api/products/**").authenticated()
                .requestMatchers("/api/products/**").hasAnyRole("OWNER", "MANAGER")

                // Compras e inventario: proveedores, órdenes de compra (recibir mercancía cambia
                // stock y costo) y ajustes (mermas). Todo esto es gestión, no mostrador: Dueño/Gerente.
                .requestMatchers("/api/suppliers/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/api/purchase-orders/**").hasAnyRole("OWNER", "MANAGER")
                .requestMatchers("/api/inventory-adjustments/**").hasAnyRole("OWNER", "MANAGER")

                // Precios: el cálculo y la consulta los usa cualquiera al vender;
                // solo Dueño/Gerente configuran las tablas de precios.
                .requestMatchers(HttpMethod.GET, "/api/pricing/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/pricing/calculate-lens").authenticated()
                .requestMatchers("/api/pricing/**").hasAnyRole("OWNER", "MANAGER")

                // Promociones: cualquiera puede verlas/aplicarlas en el POS,
                // solo Dueño/Gerente las crean, editan o eliminan.
                .requestMatchers(HttpMethod.GET, "/api/promotions/**").authenticated()
                .requestMatchers("/api/promotions/**").hasAnyRole("OWNER", "MANAGER")

                // Expedientes clínicos: cualquiera puede consultarlos (Vendedor necesita ver la Rx),
                // pero solo Dueño/Gerente/Optometrista pueden crearlos, editarlos o borrarlos.
                .requestMatchers(HttpMethod.GET, "/api/clinical-records/**").authenticated()
                .requestMatchers("/api/clinical-records/**").hasAnyRole("OWNER", "MANAGER", "OPTOMETRIST")

                // Cualquier otra ruta requiere estar autenticado
                .anyRequest().authenticated()
            );

        // 4. Inyectar nuestro filtro de JWT antes del filtro de autenticación de Spring,
        // y el guardián de alcance de Hub justo después (ya hay contexto de sesión).
        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(hubScopeGuardFilter, AuthTokenFilter.class);

        return http.build();
    }

    /**
     * Configuración de CORS centralizada para permitir la comunicación con el Frontend
     * y el intercambio de Cookies (credentials).
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Orígenes permitidos (configurables por entorno: app.cors.allowed-origins)
        configuration.setAllowedOrigins(allowedOrigins);
        
        // Métodos permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        
        // Cabeceras permitidas. Idempotency-Key va aquí: sin listarla, el navegador la bloquea
        // en el preflight y la protección contra duplicados nunca llegaría al backend.
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Cache-Control", "Idempotency-Key"));
        
        // IMPORTANTE: Permitir el envío de la Cookie 'optisaas-auth-token'
        configuration.setAllowCredentials(true); 
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}