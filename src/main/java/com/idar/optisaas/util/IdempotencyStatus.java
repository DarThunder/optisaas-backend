package com.idar.optisaas.util;

/**
 * Estado de una llave de idempotencia.
 *
 * IN_PROGRESS se escribe ANTES de ejecutar la operación y se confirma de inmediato,
 * para que un segundo intento simultáneo (doble clic) lo vea y no duplique.
 */
public enum IdempotencyStatus {
    IN_PROGRESS,
    COMPLETED
}
