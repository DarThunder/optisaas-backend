package com.idar.optisaas.dto;

import lombok.Data;

/**
 * Alta de una óptica cliente: crea su primera sucursal y la cuenta de su dueño.
 *
 * El correo del dueño es opcional. Si viene, el código de activación le llega solo; si no,
 * el administrador lo ve en pantalla y se lo dicta. Es el mismo camino doble que el alta de
 * empleados, y por la misma razón: en este negocio no todo el mundo tiene correo.
 */
@Data
public class CreateTenantRequest {

    /** Nombre comercial de la óptica. Va a la sucursal y a BranchSettings.businessName. */
    private String businessName;

    /** Nombre de la sucursal. Si viene vacío, se usa businessName. */
    private String branchName;

    private String address;

    /** PIN de autorización de la sucursal. Si viene vacío, se usa 0000 (igual que createBranch). */
    private String pin;

    // --- Cuenta del dueño ---
    private String ownerFullName;
    private String ownerUsername;
    /** Opcional: si está vacío, el código de activación se dicta a mano. */
    private String ownerEmail;

    // --- Suscripción ---
    /** Días de prueba. Nulo o 0 = sin vencimiento (para clientes en acompañamiento). */
    private Integer trialDays;

    private String notes;
}
