package com.idar.optisaas.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementación por defecto: escribe el correo en el log en vez de enviarlo.
 *
 * Es lo que permite construir y verificar los flujos de correo de punta a punta sin tener
 * todavía un dominio verificado. En desarrollo el enlace de recuperación se toma del log
 * del contenedor. Al configurar `app.mail.provider=smtp` deja de registrarse y se envía de verdad.
 *
 * IMPORTANTE: el cuerpo del correo lleva el enlace con el token de recuperación. Por eso esta
 * implementación NO debe usarse en producción: equivale a dejar en los logs una llave para
 * entrar a la cuenta. El arranque avisa de ello.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "log", matchIfMissing = true)
public class LogMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(LogMailSender.class);

    public LogMailSender() {
        log.warn("=== CORREO EN MODO 'log': los correos NO se envían, se escriben aquí. " +
                "Configura app.mail.provider=smtp para enviarlos de verdad. ===");
    }

    @Override
    public void send(EmailMessage message) {
        log.info("""

                ===================== CORREO (no enviado) =====================
                Para:     {}
                De:       {}
                Responder a: {}
                Asunto:   {}
                ---------------------------------------------------------------
                {}
                ===============================================================
                """,
                message.to(),
                message.fromName() != null ? message.fromName() : "OptiSaaS",
                message.replyTo() != null ? message.replyTo() : "(sin respuesta)",
                message.subject(),
                message.textBody());
    }
}
