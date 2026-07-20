package com.idar.optisaas.mail;

/**
 * Un correo listo para enviar, independiente de quién lo entregue.
 *
 * Sobre el remitente: NO se puede poner el correo del cliente (p. ej. su Gmail) en el campo
 * "De". El dominio declara por DNS (SPF/DKIM/DMARC) quién puede enviar en su nombre, y nuestro
 * servidor no está en esa lista: el correo acabaría en spam o rechazado. La forma correcta de
 * que un correo "se vea" de la óptica es:
 *   - "De" = nuestro dominio verificado (lo pone el MailSender, es infraestructura),
 *   - {@code fromName} = el nombre del negocio, que es lo que el cliente lee en su bandeja,
 *   - {@code replyTo} = el correo de la óptica, para que las respuestas le lleguen a ella.
 *
 * Los correos de la plataforma al dueño (recuperar contraseña) van sin fromName ni replyTo:
 * son sobre su cuenta de OptiSaaS y deben verse como OptiSaaS, no como su tienda.
 */
public record EmailMessage(
        String to,
        String subject,
        String htmlBody,
        String textBody,
        String fromName,
        String replyTo
) {
    /** Correo de la plataforma: se identifica como OptiSaaS. */
    public static EmailMessage platform(String to, String subject, String htmlBody, String textBody) {
        return new EmailMessage(to, subject, htmlBody, textBody, null, null);
    }

    /** Correo de una óptica a su cliente: lleva el nombre y el correo de respuesta del negocio. */
    public static EmailMessage fromBusiness(String to, String subject, String htmlBody, String textBody,
                                            String businessName, String businessEmail) {
        return new EmailMessage(to, subject, htmlBody, textBody, businessName, businessEmail);
    }
}
