package com.idar.optisaas.util;

/**
 * Estado comercial de un cliente (un dueño con sus sucursales).
 *
 * Se guarda como texto (EnumType.STRING). NO renombres constantes existentes sin migrar los
 * datos: la columna guarda el nombre literal.
 */
public enum SubscriptionStatus {
    /** Prueba gratuita con fecha de vencimiento. El vencimiento aquí es esperado y legítimo. */
    TRIAL,
    /** Cliente al corriente. */
    ACTIVE,
    /**
     * Acceso cortado por falta de pago.
     *
     * Ojo al implementar el corte (queda fuera de esta fase): esto es un punto de venta.
     * Dejar a una óptica sin poder cobrar es dejarla sin operar. La política acordada es
     * solo lectura —pueden consultar todo lo suyo, no registrar ventas nuevas— y NUNCA
     * cerrarles el acceso a sus propios datos ni borrar nada.
     */
    SUSPENDED
}
