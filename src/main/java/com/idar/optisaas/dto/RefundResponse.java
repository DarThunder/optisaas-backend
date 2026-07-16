package com.idar.optisaas.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class RefundResponse {
    private Long refundId;
    private Long saleId;

    /** Valor de la mercancía devuelta. */
    private BigDecimal returnedValue;
    /** Dinero reintegrado al cliente; puede ser cero si solo bajó la deuda. */
    private BigDecimal amount;
    private String method;
    private String reason;
    private String processedByName;
    private LocalDateTime createdAt;

    /** Estado en que quedó la venta: PARTIALLY_RETURNED o RETURNED. */
    private String saleStatus;
    /** Saldo de la venta ya ajustado por la devolución. */
    private BigDecimal saleRemainingBalance;

    private List<RefundItemResponse> items;
}
