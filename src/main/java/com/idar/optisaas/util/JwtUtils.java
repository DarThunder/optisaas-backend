package com.idar.optisaas.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationMs}")
    private int jwtExpirationMs;

    @Value("${app.jwtCookieName}")
    private String jwtCookie;

    @Value("${app.cookie.sameSite}")
    private String sameSite;

    // En producción DEBE ser true (requiere HTTPS). Configurable por entorno para
    // permitir desarrollo local sobre HTTP sin perder la cookie.
    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    public ResponseCookie generatePreAuthCookie(String email) {
        String jwt = Jwts.builder()
                .setSubject(email)
                .claim("type", "PRE_AUTH")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 300_000))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
        
        return buildCookie(jwt);
    }

    public ResponseCookie generateFullAccessCookie(String email, Long branchId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "FULL");
        claims.put("branchId", branchId);
        claims.put("role", role);

        String jwt = Jwts.builder()
                .setSubject(email)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();

        return buildCookie(jwt);
    }

    public String getJwtFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, jwtCookie);
        return (cookie != null) ? cookie.getValue() : null;
    }

    public ResponseCookie getCleanJwtCookie() {
        // maxAge(0) indica al navegador que elimine la cookie de inmediato.
        // Debe coincidir en path/httpOnly/sameSite con la cookie original para borrarla bien.
        return ResponseCookie.from(jwtCookie, "")
                .path("/api")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .build();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
            return true;
        } catch (JwtException e) {
        }
        return false;
    }

    public String getUserNameFromJwtToken(String token) {
        return parseClaims(token).getBody().getSubject();
    }

    public Long getBranchIdFromToken(String token) {
        return parseClaims(token).getBody().get("branchId", Long.class);
    }

    public String getRoleFromToken(String token) {
    return parseClaims(token).getBody().get("role", String.class);
    }

    public boolean isFullToken(String token) {
        String type = parseClaims(token).getBody().get("type", String.class);
        return "FULL".equals(type);
    }

    // ==================== TOKEN DE TERMINAL ====================
    // Marca a un dispositivo/navegador como "terminal" de una sucursal. Vive en una cookie
    // aparte de la sesión, de larga duración y revocable: permite que los empleados inicien
    // turno solo con su PIN, sin las credenciales de la cuenta.

    private String terminalCookieName() {
        return jwtCookie + "-terminal";
    }

    public ResponseCookie generateTerminalCookie(Long branchId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "TERMINAL");
        claims.put("branchId", branchId);

        long ninetyDaysMs = 90L * 24 * 60 * 60 * 1000;
        String jwt = Jwts.builder()
                .setSubject("terminal")
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ninetyDaysMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();

        return ResponseCookie.from(terminalCookieName(), jwt)
                .path("/api")
                .maxAge(90 * 24 * 60 * 60)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .build();
    }

    public String getTerminalTokenFromCookies(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, terminalCookieName());
        return (cookie != null) ? cookie.getValue() : null;
    }

    public boolean isTerminalToken(String token) {
        try {
            return "TERMINAL".equals(parseClaims(token).getBody().get("type", String.class));
        } catch (JwtException e) {
            return false;
        }
    }

    public Long getBranchIdFromTerminal(String token) {
        return parseClaims(token).getBody().get("branchId", Long.class);
    }

    public ResponseCookie getCleanTerminalCookie() {
        return ResponseCookie.from(terminalCookieName(), "")
                .path("/api")
                .maxAge(0)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .build();
    }

    private Jws<Claims> parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build().parseClaimsJws(token);
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    
    private ResponseCookie buildCookie(String jwt) {
        return ResponseCookie.from(jwtCookie, jwt)
                .path("/api")
                .maxAge(24 * 60 * 60)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .build();
    }
}