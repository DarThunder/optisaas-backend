package com.idar.optisaas.util;

/**
 * Operaciones protegidas por llave de idempotencia.
 *
 * El alcance forma parte de la llave única junto con la sucursal y el valor de la llave:
 * así, la misma llave usada en dos operaciones distintas no se confunde.
 * Se guarda como texto (EnumType.STRING): NO renombres constantes existentes.
 */
public enum IdempotencyScope {
    SALE_CREATE,
    PAYMENT_ADD,
    SALE_REFUND,
    /** Recibir mercancía: reintentarla sin protección sumaría el mismo pedido dos veces al stock. */
    PURCHASE_RECEIPT
}
