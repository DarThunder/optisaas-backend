package com.idar.optisaas.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
}
