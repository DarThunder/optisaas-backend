package com.idar.optisaas.util;

/**
 * Por qué se corrigió el stock a mano.
 *
 * Es una lista cerrada a propósito: un ajuste de inventario es una pérdida (o una corrección)
 * y el dueño necesita poder sumar cuánto se va por cada causa. La nota libre acompaña, no sustituye.
 * Se guarda como texto (EnumType.STRING): NO renombres constantes existentes sin migrar los datos.
 */
public enum AdjustmentReason {
    /** Merma: producto dañado o inservible. */
    SHRINKAGE,
    THEFT,
    /** El conteo físico no coincidía con el sistema. */
    COUNT_CORRECTION,
    EXPIRATION,
    SUPPLIER_RETURN
}
