package com.idar.optisaas.util;

/**
 * Ciclo de vida de una orden de compra.
 *
 * Solo la recepción mueve inventario: pedir no es tener. Se guarda como texto
 * (EnumType.STRING): NO renombres constantes existentes sin migrar los datos.
 */
public enum PurchaseOrderStatus {
    /** En captura: aún no se manda al proveedor y puede cancelarse sin consecuencias. */
    DRAFT,
    /** Enviada al proveedor; se espera la mercancía. */
    ORDERED,
    /** Llegó parte de lo pedido. */
    PARTIALLY_RECEIVED,
    /** Llegó todo lo pedido. */
    RECEIVED,
    CANCELLED
}
