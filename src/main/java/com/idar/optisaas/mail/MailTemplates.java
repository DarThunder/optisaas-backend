package com.idar.optisaas.mail;

/**
 * Plantillas de los correos. Se arman aquí, en un solo lugar, para que el texto que ve el
 * cliente no quede disperso entre los servicios.
 *
 * Cada correo lleva versión HTML y versión en texto plano: hay clientes que no muestran HTML,
 * y un correo sin alternativa de texto puntúa peor en los filtros de spam.
 */
public final class MailTemplates {

    private MailTemplates() {}

    public static EmailMessage passwordReset(String to, String fullName, String link, int validMinutes) {
        String name = (fullName == null || fullName.isBlank()) ? "Hola" : "Hola " + fullName;

        String text = """
                %s,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de OptiSaaS.

                Abre este enlace para elegir una nueva contraseña:
                %s

                El enlace vence en %d minutos y solo se puede usar una vez.

                Si no fuiste tú, ignora este correo: tu contraseña actual sigue funcionando.

                — OptiSaaS
                """.formatted(name, link, validMinutes);

        String html = """
                <div style="font-family:system-ui,-apple-system,'Segoe UI',sans-serif;max-width:520px;margin:0 auto;padding:24px;color:#334155">
                  <h2 style="color:#0f172a;font-size:20px;margin:0 0 16px">Restablecer tu contraseña</h2>
                  <p style="margin:0 0 12px">%s,</p>
                  <p style="margin:0 0 20px">Recibimos una solicitud para restablecer la contraseña de tu cuenta de OptiSaaS.</p>
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
                  <p style="margin:0;font-size:12px;color:#94a3b8">
                    Si el botón no funciona, copia y pega esta dirección en tu navegador:<br>
                    <span style="word-break:break-all">%s</span>
                  </p>
                </div>
                """.formatted(name, link, validMinutes, link);

        return EmailMessage.platform(to, "Restablece tu contraseña de OptiSaaS", html, text);
    }
}
