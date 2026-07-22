package com.idar.optisaas.util;

/**
 * Acciones sensibles que quedan registradas en la bitácora de auditoría.
 * Se guardan como texto (EnumType.STRING): no reordenar libremente es irrelevante,
 * pero NO renombres constantes existentes sin migrar los datos históricos.
 */
public enum AuditAction {
    // --- Dinero ---
    SALE_STATUS_CHANGED,
    PAYMENT_ADDED,
    SALE_REFUNDED,
    CASH_SESSION_OPENED,
    CASH_SESSION_CLOSED,
    CASH_MOVEMENT_CREATED,

    // --- Inventario y compras ---
    PURCHASE_ORDER_CREATED,
    PURCHASE_ORDER_CONFIRMED,
    PURCHASE_ORDER_CANCELLED,
    PURCHASE_ORDER_RECEIVED,
    INVENTORY_ADJUSTED,
    SUPPLIER_CREATED,
    SUPPLIER_UPDATED,

    // --- Acceso / identidad ---
    OPERATOR_SWITCHED,
    HUB_ACCESS_GRANTED,

    // --- Plataforma (alta y gestión de ópticas cliente) ---
    // Se registran con branch_id de la óptica creada, para que quede rastro de quién la dio
    // de alta y cuándo. El actor es el administrador de plataforma, no un usuario de la óptica.
    TENANT_CREATED,
    SUBSCRIPTION_UPDATED,
    REGISTRATION_APPROVED,
    REGISTRATION_REJECTED,

    // --- Gestión de empleados ---
    EMPLOYEE_CREATED,
    EMPLOYEE_UPDATED,
    EMPLOYEE_DEACTIVATED,
    CREDENTIALS_RESET,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_COMPLETED
}
