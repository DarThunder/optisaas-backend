package com.idar.optisaas.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP del solicitante, respetando el reverse proxy.
 *
 * Vive aquí y no duplicada en cada llamador porque es lógica de seguridad: la usa la bitácora
 * de auditoría (queda registrada como la IP responsable de una acción sensible) y el limitador
 * del formulario público de solicitudes.
 *
 * IMPORTANTE: se confía en X-Forwarded-For, que un cliente puede escribir a mano. Eso es
 * correcto SOLO si a la aplicación no se llega más que por el proxy, que la sobrescribe con la
 * IP de la conexión real. Por eso el puerto 8080 no se publica en producción (ver
 * docker-compose.prod.yml y el Caddyfile): si se pudiera llegar directo, cualquiera podría
 * falsificar la IP que queda en la bitácora o saltarse el límite del formulario.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String from(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Puede venir como "cliente, proxy1, proxy2": nos quedamos con el primero.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
