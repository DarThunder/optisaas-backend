package com.idar.optisaas.util;

public enum SaleStatus {
    QUOTATION,
    PENDING,
    IN_PROCESS,
    READY_TO_PICK,
    COMPLETED,
    CANCELLED,
    /** Se devolvió parte de la mercancía; el resto sigue siendo del cliente. */
    PARTIALLY_RETURNED,
    /** Se devolvió toda la mercancía de la venta. */
    RETURNED
}
