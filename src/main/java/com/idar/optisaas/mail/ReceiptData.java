package com.idar.optisaas.mail;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos de un comprobante, ya resueltos y listos para pintar.
 *
 * Existe para que la plantilla no dependa de las entidades: así se puede probar el correo
 * sin construir una venta con su sucursal, su cliente y su vendedor. El mapeo desde las
 * entidades vive en {@link SaleReceiptMailer}.
 */
public record ReceiptData(
        Long folio,
        LocalDateTime date,
        String clientName,
        String sellerName,
        List<Line> lines,
        BigDecimal discount,
        String discountName,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal balance,
        Business business
) {
    /** Un renglón del comprobante. La descripción es la del momento de la venta, no la actual. */
    public record Line(String description, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}

    /**
     * Identidad del negocio que emite. Sale de los ajustes de la sucursal: es lo que el cliente
     * reconoce, y lo que hace que el correo se vea de su óptica y no de la plataforma.
     */
    public record Business(
            String name,
            String addressLine,
            String phone,
            String email,
            String taxId,
            String footerMessage,
            String legalNote
    ) {}

    public boolean hasBalance() {
        return balance != null && balance.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasDiscount() {
        return discount != null && discount.compareTo(BigDecimal.ZERO) > 0;
    }
}
