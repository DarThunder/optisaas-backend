package com.idar.optisaas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class PurchaseOrderRequest {

    @NotNull(message = "La orden necesita un proveedor")
    private Long supplierId;

    @NotEmpty(message = "La orden debe tener al menos un producto")
    @Valid
    private List<PurchaseOrderItemRequest> items;

    @Size(max = 100)
    private String referenceCode;

    private LocalDate expectedDate;

    @Size(max = 500)
    private String notes;

    /** Si es true la orden nace como ORDERED (ya se mandó al proveedor); si no, queda en DRAFT. */
    private boolean confirm = false;
}
