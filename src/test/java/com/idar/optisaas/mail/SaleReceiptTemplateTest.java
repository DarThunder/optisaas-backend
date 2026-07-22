package com.idar.optisaas.mail;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SaleReceiptTemplateTest {

    private final MailTemplates templates = new MailTemplates("Fóvea", "VLK");

    private ReceiptData.Business business() {
        return new ReceiptData.Business("Óptica Mogar Centro", "Av. Xalapa 100", "2288123456",
                "hola@mogar.mx", "MOG010101ABC", "¡Gracias por su preferencia!", "Garantía de 30 días.");
    }

    private ReceiptData receipt(BigDecimal total, BigDecimal paid, BigDecimal discount) {
        return new ReceiptData(
                42L,
                LocalDateTime.of(2026, 7, 20, 15, 30),
                "Ana Cliente",
                "Gerardo Vendedor",
                List.of(new ReceiptData.Line("Armazón Ray-Ban RB2140", 1, new BigDecimal("2500.00"), new BigDecimal("2500.00")),
                        new ReceiptData.Line("Micas antirreflejante", 2, new BigDecimal("750.00"), new BigDecimal("1500.00"))),
                discount, discount.signum() > 0 ? "Promoción verano" : null,
                total, paid, total.subtract(paid),
                business());
    }

    @Test
    void includesTheItemsAndTotals() {
        EmailMessage mail = templates.saleReceipt("ana@correo.com",
                receipt(new BigDecimal("4000.00"), new BigDecimal("4000.00"), BigDecimal.ZERO));

        assertTrue(mail.textBody().contains("Armazón Ray-Ban RB2140"));
        assertTrue(mail.textBody().contains("Micas antirreflejante"));
        assertTrue(mail.textBody().contains("$4000.00"), "debe aparecer el total");
        assertTrue(mail.subject().contains("#42"), "el asunto debe llevar el folio");
    }

    // El comprobante lo emite la óptica, no la plataforma: al cliente le compró a su óptica.
    @Test
    void usesTheBusinessIdentityAndDoesNotMentionTheBrand() {
        EmailMessage mail = templates.saleReceipt("ana@correo.com",
                receipt(new BigDecimal("4000.00"), new BigDecimal("4000.00"), BigDecimal.ZERO));

        assertEquals("Óptica Mogar Centro", mail.fromName());
        assertEquals("hola@mogar.mx", mail.replyTo());
        assertFalse(mail.textBody().contains("Fóvea"), "el cliente final no debe ver la marca de la plataforma");
        assertFalse(mail.htmlBody().contains("Fóvea"));
    }

    @Test
    void showsThePendingBalanceWhenThereIsOne() {
        EmailMessage mail = templates.saleReceipt("ana@correo.com",
                receipt(new BigDecimal("4000.00"), new BigDecimal("1500.00"), BigDecimal.ZERO));

        assertTrue(mail.textBody().contains("SALDO PENDIENTE"));
        assertTrue(mail.textBody().contains("$2500.00"));
        assertTrue(mail.htmlBody().contains("Saldo pendiente"));
    }

    @Test
    void omitsTheBalanceWhenFullyPaid() {
        EmailMessage mail = templates.saleReceipt("ana@correo.com",
                receipt(new BigDecimal("4000.00"), new BigDecimal("4000.00"), BigDecimal.ZERO));

        assertFalse(mail.textBody().contains("SALDO PENDIENTE"));
        assertFalse(mail.htmlBody().contains("Saldo pendiente"));
    }

    @Test
    void showsTheDiscountWithItsName() {
        EmailMessage mail = templates.saleReceipt("ana@correo.com",
                receipt(new BigDecimal("3500.00"), new BigDecimal("3500.00"), new BigDecimal("500.00")));

        assertTrue(mail.textBody().contains("Promoción verano"));
        assertTrue(mail.textBody().contains("-$500.00"));
    }

    // No es una factura: si alguien lo confunde con un CFDI (Fase 5) se mete en un problema fiscal.
    @Test
    void statesItHasNoFiscalValue() {
        EmailMessage mail = templates.saleReceipt("ana@correo.com",
                receipt(new BigDecimal("4000.00"), new BigDecimal("4000.00"), BigDecimal.ZERO));

        assertTrue(mail.textBody().contains("no tiene validez fiscal"));
        assertTrue(mail.htmlBody().contains("no tiene validez fiscal"));
    }

    // Un '<' en el nombre de un producto rompería el HTML del correo; peor, un texto capturado
    // con marcado podría inyectar contenido en lo que recibe el cliente.
    @Test
    void escapesHtmlInUserWrittenText() {
        ReceiptData data = new ReceiptData(
                7L, LocalDateTime.now(), "Ana", "Gerardo",
                List.of(new ReceiptData.Line("Lente <script>alert(1)</script>", 1,
                        BigDecimal.TEN, BigDecimal.TEN)),
                BigDecimal.ZERO, null, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                new ReceiptData.Business("Óptica <b>X</b>", null, null, null, null, null, null));

        EmailMessage mail = templates.saleReceipt("ana@correo.com", data);

        assertFalse(mail.htmlBody().contains("<script>"), "el marcado debe quedar escapado");
        assertTrue(mail.htmlBody().contains("&lt;script&gt;"));
        assertTrue(mail.htmlBody().contains("&lt;b&gt;"));
    }

    // Una sucursal sin ajustes no debe impedir mandar el comprobante.
    @Test
    void worksWhenTheBranchHasNoSettings() {
        ReceiptData data = new ReceiptData(
                1L, LocalDateTime.now(), null, null,
                List.of(new ReceiptData.Line("Producto", 1, BigDecimal.ONE, BigDecimal.ONE)),
                BigDecimal.ZERO, null, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                new ReceiptData.Business(null, null, null, null, null, null, null));

        EmailMessage mail = templates.saleReceipt("ana@correo.com", data);

        assertTrue(mail.subject().contains("Tu óptica"));
        assertNull(mail.replyTo());
        assertTrue(mail.textBody().contains("Hola,"), "sin nombre de cliente saluda genérico");
    }
}
