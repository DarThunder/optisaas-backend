package com.idar.optisaas.mail;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Envío real por SMTP. Sirve con cualquier proveedor (Gmail, Brevo, SES, el del hosting):
 * el host, usuario y contraseña se configuran con las propiedades estándar `spring.mail.*`.
 *
 * El "De" es SIEMPRE nuestra dirección verificada; lo que cambia por óptica es el nombre que
 * se muestra y el correo de respuesta (ver EmailMessage). Poner el correo del cliente en el
 * "De" haría fallar SPF/DKIM y el correo acabaría en spam.
 */
@Component
@ConditionalOnProperty(name = "app.mail.provider", havingValue = "smtp")
public class SmtpMailSender implements MailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpMailSender.class);

    private final JavaMailSender javaMailSender;
    private final String fromAddress;
    private final String defaultFromName;

    public SmtpMailSender(JavaMailSender javaMailSender,
                          @Value("${app.mail.from}") String fromAddress,
                          @Value("${app.mail.fromName:OptiSaaS}") String defaultFromName) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        this.defaultFromName = defaultFromName;
    }

    @Override
    public void send(EmailMessage message) {
        try {
            MimeMessage mime = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());

            helper.setTo(message.to());
            helper.setSubject(message.subject());
            // Cuerpo en HTML con alternativa en texto plano: algunos clientes (y filtros de spam)
            // esperan las dos versiones.
            helper.setText(message.textBody(), message.htmlBody());
            helper.setFrom(new InternetAddress(
                    fromAddress,
                    message.fromName() != null ? message.fromName() : defaultFromName,
                    StandardCharsets.UTF_8.name()));

            if (message.replyTo() != null && !message.replyTo().isBlank()) {
                helper.setReplyTo(message.replyTo());
            }

            javaMailSender.send(mime);
            log.info("Correo enviado a {} — {}", message.to(), message.subject());

        } catch (UnsupportedEncodingException | jakarta.mail.MessagingException | org.springframework.mail.MailException e) {
            // Un correo que no sale no debe tumbar la operación que lo originó.
            log.error("No se pudo enviar el correo a {} ({})", message.to(), message.subject(), e);
        }
    }
}
