package com.idar.optisaas.util;

/**
 * Estado de una solicitud de acceso.
 *
 * Se guarda como texto (EnumType.STRING): no renombres constantes sin migrar los datos.
 */
public enum RegistrationStatus {
    /** Recién llegada del formulario público. Espera decisión. */
    PENDING,
    /** Aprobada: se creó la óptica y su dueño (ver createdOwnerId). */
    APPROVED,
    /** Descartada. No se borra: saber a quién ya se dijo que no también es información. */
    REJECTED
}
