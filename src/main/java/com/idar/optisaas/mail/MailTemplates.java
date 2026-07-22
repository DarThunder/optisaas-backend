package com.idar.optisaas.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Plantillas de los correos. Se arman aquí, en un solo lugar, para que el texto que ve el
 * cliente no quede disperso entre los servicios.
 *
 * El nombre del producto viene de `app.brand.name` en vez de estar escrito en cada plantilla:
 * cambiarlo es editar una variable, no perseguirlo por el código y sus pruebas. El estudio que
 * lo hace (`app.brand.maker`) aparece solo en el pie, que es donde corresponde: la relación del
 * cliente es con el producto, no con quien lo programó.
 *
 * Cada correo lleva versión HTML y versión en texto plano: hay clientes que no muestran HTML,
 * y un correo sin alternativa de texto puntúa peor en los filtros de spam.
 */
@Component
public class MailTemplates {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String brand;
    private final String maker;

    public MailTemplates(@Value("${app.brand.name}") String brand,
                         @Value("${app.brand.maker:}") String maker) {
        this.brand = brand;
        this.maker = maker;
    }

    public String brand() {
        return brand;
    }

    /** Pie de firma: "Fóvea — hecho por VLK", o solo la marca si no hay estudio configurado. */
    private String signature() {
        return (maker == null || maker.isBlank()) ? brand : brand + " — hecho por " + maker;
    }

    public EmailMessage passwordReset(String to, String fullName, String link, int validMinutes) {
        String name = (fullName == null || fullName.isBlank()) ? "Hola" : "Hola " + fullName;

        String text = """
                %s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de %s.

                Abre este enlace para elegir una nueva contraseña:
                %s

                El enlace vence en %d minutos y solo se puede usar una vez.

                Si no fuiste tú, ignora este correo: tu contraseña actual sigue funcionando.

                — %s
                """.formatted(name, brand, link, validMinutes, signature());

        String html = """
                <div style="font-family:system-ui,-apple-system,'Segoe UI',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#334155">
                  <h2 style="color:#0f172a;font-size:20px;margin:0 0 16px">Restablecer tu contraseña</h2>
                  <p style="margin:0 0 12px">%s,</p>
                  <p style="margin:0 0 20px">Recibimos una solicitud para restablecer la contraseña de tu cuenta de %s.</p>
                  <p style="margin:0 0 24px">
                    <a href="%s" style="background:#0d9488;color:#ffffff;text-decoration:none;padding:12px 24px;border-radius:8px;display:inline-block;font-weight:600">
                      Elegir nueva contraseña
                    </a>
                  </p>
                  <p style="margin:0 0 12px;font-size:14px;color:#64748b">
                    El enlace vence en %d minutos y solo se puede usar una vez.
                  </p>
                  <p style="margin:0 0 12px;font-size:14px;color:#64748b">
                    Si no fuiste tú, ignora este correo: tu contraseña actual sigue funcionando.
                  </p>
                  <hr style="border:0;border-top:1px solid #e2e8f0;margin:24px 0">
                  <p style="margin:0 0 8px;font-size:12px;color:#94a3b8">
                    Si el botón no funciona, copia y pega esta dirección en tu navegador:<br>
                    <span style="word-break:break-all">%s</span>
                  </p>
                  <p style="margin:0;font-size:12px;color:#94a3b8">%s</p>
                </div>
                """.formatted(name, brand, link, validMinutes, link, signature());

        return EmailMessage.platform(to, "Restablece tu contraseña de " + brand, html, text);
    }

    /**
     * Código de activación del empleado, o su reenvío tras un reseteo de acceso.
     *
     * Identidad: el nombre visible es "«Negocio» vía «Marca»". Solo el negocio se prestaría a
     * confusión (un correo que pide definir una contraseña, aparentando venir de la tienda, tiene
     * forma de phishing); solo la marca dejaría al empleado sin saber quién lo dio de alta —
     * puede trabajar en varias ópticas. Nombrar a ambos es lo que hacen las plataformas que
     * invitan en nombre de terceros, y el Reply-To lleva a la óptica, que es quien puede ayudarlo.
     */
    public EmailMessage employeeActivation(String to, String fullName, String username,
                                           String activationCode, String businessName,
                                           String businessEmail, boolean isReset) {
        String name = (fullName == null || fullName.isBlank()) ? "Hola" : "Hola " + fullName;
        String business = (businessName == null || businessName.isBlank()) ? "Tu óptica" : businessName;
        String displayName = business + " vía " + brand;

        String intro = isReset
                ? "Se restableció tu acceso en %s. Tu contraseña y PIN anteriores ya no funcionan: usa este código para definirlos de nuevo.".formatted(business)
                : "%s te dio de alta en %s, el sistema con el que van a operar el punto de venta.".formatted(business, brand);

        String subject = isReset
                ? "Tu acceso se restableció — " + business
                : "Tu acceso a " + business;

        String text = """
                %s,

                %s

                Tu usuario: %s
                Tu código de activación: %s

                Entra a %s, elige "Primer ingreso" y con ese código defines tu contraseña
                y tu PIN de 4 dígitos. Nadie más los conoce, ni siquiera quien te dio de alta.

                El código vence en 7 días y solo se puede usar una vez.

                Si no esperabas este correo, avísale a tu administrador.

                — %s
                """.formatted(name, intro, username, activationCode, brand, signature());

        String html = """
                <div style="font-family:system-ui,-apple-system,'Segoe UI',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#334155">
                  <h2 style="color:#0f172a;font-size:20px;margin:0 0 16px">%s</h2>
                  <p style="margin:0 0 12px">%s,</p>
                  <p style="margin:0 0 20px">%s</p>
                  <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:16px;margin:0 0 20px">
                    <p style="margin:0 0 4px;font-size:11px;font-weight:700;color:#94a3b8;text-transform:uppercase">Tu usuario</p>
                    <p style="margin:0 0 12px;font-family:monospace;font-weight:700;color:#334155">%s</p>
                    <p style="margin:0 0 4px;font-size:11px;font-weight:700;color:#94a3b8;text-transform:uppercase">Código de activación</p>
                    <p style="margin:0;font-family:monospace;font-size:28px;font-weight:800;letter-spacing:.3em;color:#0f172a">%s</p>
                  </div>
                  <p style="margin:0 0 12px">
                    Entra a %s, elige <strong>Primer ingreso</strong> y con ese código defines tu
                    contraseña y tu PIN de 4 dígitos. Nadie más los conoce, ni siquiera quien te dio de alta.
                  </p>
                  <p style="margin:0 0 12px;font-size:14px;color:#64748b">
                    El código vence en 7 días y solo se puede usar una vez.
                  </p>
                  <hr style="border:0;border-top:1px solid #e2e8f0;margin:24px 0">
                  <p style="margin:0 0 8px;font-size:12px;color:#94a3b8">
                    Si no esperabas este correo, avísale a tu administrador.
                  </p>
                  <p style="margin:0;font-size:12px;color:#94a3b8">%s</p>
                </div>
                """.formatted(isReset ? "Tu acceso se restableció" : "Bienvenido a " + business,
                              name, intro, username, activationCode, brand, signature());

        return EmailMessage.fromBusiness(to, subject, html, text, displayName, businessEmail);
    }

    /**
     * Comprobante de compra para el cliente final.
     *
     * Este SÍ va con la identidad de la óptica y sin mencionar la marca: al cliente le compró a
     * su óptica, no a nosotros. Es la diferencia con el correo de activación, donde el empleado
     * necesita saber que hay una plataforma detrás.
     *
     * No es un comprobante fiscal: eso es la Fase 5 (CFDI). El pie lo deja claro para que nadie
     * lo confunda con una factura.
     */
    public EmailMessage saleReceipt(String to, ReceiptData r) {
        ReceiptData.Business b = r.business();
        String business = (b.name() == null || b.name().isBlank()) ? "Tu óptica" : b.name();
        String saludo = (r.clientName() == null || r.clientName().isBlank()) ? "Hola" : "Hola " + r.clientName();
        String fecha = r.date() == null ? "" : r.date().format(DATE_FORMAT);

        StringBuilder lineasTexto = new StringBuilder();
        StringBuilder lineasHtml = new StringBuilder();
        for (ReceiptData.Line line : r.lines()) {
            lineasTexto.append("  %d x %s%s%s%n".formatted(
                    line.quantity(), line.description(),
                    " ".repeat(Math.max(1, 28 - line.description().length())),
                    money(line.subtotal())));
            lineasHtml.append("""
                    <tr>
                      <td style="padding:6px 0;border-bottom:1px solid #f1f5f9">%s<br><span style="color:#94a3b8;font-size:12px">%d × %s</span></td>
                      <td style="padding:6px 0;border-bottom:1px solid #f1f5f9;text-align:right;white-space:nowrap">%s</td>
                    </tr>
                    """.formatted(esc(line.description()), line.quantity(), money(line.unitPrice()), money(line.subtotal())));
        }

        String totalesTexto = new StringBuilder()
                .append(r.hasDiscount() ? "  Descuento%s: -%s%n".formatted(
                        r.discountName() != null ? " (" + r.discountName() + ")" : "", money(r.discount())) : "")
                .append("  TOTAL: %s%n".formatted(money(r.total())))
                .append("  Pagado: %s%n".formatted(money(r.paid())))
                .append(r.hasBalance() ? "  SALDO PENDIENTE: %s%n".formatted(money(r.balance())) : "")
                .toString();

        String text = """
                %s,

                Gracias por tu compra en %s.

                Comprobante #%d
                %s

                %s
                %s
                %s%s%s

                Este comprobante es informativo y no tiene validez fiscal.
                """.formatted(saludo, business, r.folio(), fecha,
                              lineasTexto.toString().stripTrailing(), totalesTexto,
                              b.footerMessage() != null ? b.footerMessage() + "\n" : "",
                              b.phone() != null ? "Tel. " + b.phone() + "\n" : "",
                              b.legalNote() != null ? "\n" + b.legalNote() + "\n" : "");

        String html = """
                <div style="font-family:system-ui,-apple-system,'Segoe UI',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#334155">
                  <div style="text-align:center;margin:0 0 20px">
                    <h2 style="color:#0f172a;font-size:20px;margin:0 0 4px">%s</h2>
                    <p style="margin:0;color:#94a3b8;font-size:13px">%s%s</p>
                  </div>
                  <p style="margin:0 0 16px">%s, gracias por tu compra.</p>
                  <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:16px;margin:0 0 16px">
                    <p style="margin:0 0 2px;font-size:11px;font-weight:700;color:#94a3b8;text-transform:uppercase">Comprobante</p>
                    <p style="margin:0 0 12px;font-family:monospace;font-weight:700;color:#334155">#%d · %s</p>
                    <table style="width:100%%;border-collapse:collapse;font-size:14px">%s</table>
                    <table style="width:100%%;border-collapse:collapse;font-size:14px;margin-top:12px">
                      %s
                      <tr><td style="padding:4px 0;font-weight:700">Total</td><td style="padding:4px 0;text-align:right;font-weight:700">%s</td></tr>
                      <tr><td style="padding:4px 0;color:#64748b">Pagado</td><td style="padding:4px 0;text-align:right;color:#64748b">%s</td></tr>
                      %s
                    </table>
                  </div>
                  %s
                  <hr style="border:0;border-top:1px solid #e2e8f0;margin:20px 0">
                  <p style="margin:0 0 4px;font-size:12px;color:#94a3b8">%s</p>
                  <p style="margin:0;font-size:11px;color:#cbd5e1">Este comprobante es informativo y no tiene validez fiscal.</p>
                </div>
                """.formatted(
                        esc(business),
                        b.addressLine() != null ? esc(b.addressLine()) : "",
                        b.phone() != null ? " · Tel. " + esc(b.phone()) : "",
                        esc(saludo),
                        r.folio(), fecha,
                        lineasHtml.toString(),
                        r.hasDiscount()
                                ? "<tr><td style=\"padding:4px 0;color:#64748b\">Descuento%s</td><td style=\"padding:4px 0;text-align:right;color:#059669\">-%s</td></tr>"
                                    .formatted(r.discountName() != null ? " (" + esc(r.discountName()) + ")" : "", money(r.discount()))
                                : "",
                        money(r.total()),
                        money(r.paid()),
                        r.hasBalance()
                                ? "<tr><td style=\"padding:4px 0;font-weight:700;color:#b45309\">Saldo pendiente</td><td style=\"padding:4px 0;text-align:right;font-weight:700;color:#b45309\">%s</td></tr>".formatted(money(r.balance()))
                                : "",
                        b.footerMessage() != null ? "<p style=\"margin:0;text-align:center;color:#64748b\">" + esc(b.footerMessage()) + "</p>" : "",
                        b.legalNote() != null ? esc(b.legalNote()) : "");

        return EmailMessage.fromBusiness(to, "Tu comprobante de " + business + " (#" + r.folio() + ")",
                html, text, business, b.email());
    }

    private static String money(BigDecimal amount) {
        return "$" + (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Escapa el HTML de los datos que escribe el usuario (nombre del negocio, descripciones de
     * producto, notas). Sin esto, un `<` en el nombre de un producto rompería el correo, y un
     * texto malicioso podría inyectar marcado en el mensaje que recibe el cliente.
     */
    private static String esc(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
    }
}
