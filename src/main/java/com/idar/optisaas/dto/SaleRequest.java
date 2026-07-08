package com.idar.optisaas.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import jakarta.validation.constraints.NotEmpty;

@Data
public class SaleRequest {

    // Puede ser null: venta de mostrador sin cliente registrado.
    // Las cotizaciones sí lo exigen; esa regla se valida en SaleService, no aquí.
    private Long clientId;

    @NotEmpty(message = "La venta debe tener al menos un producto")
    private List<SaleItemRequest> items;

    private List<PaymentRequest> payments;

    // Lombok genera setQuotation() (no setIsQuotation) para un campo que ya empieza con "is",
    // por lo que Jackson espera la clave "quotation" en el JSON sin esta anotación.
    @JsonProperty("isQuotation")
    private boolean isQuotation = false;

    private boolean parkSale = false;

    private String discountType; // "PERCENTAGE" o "FIXED"
    private BigDecimal discountValue;
    private String discountName;
}