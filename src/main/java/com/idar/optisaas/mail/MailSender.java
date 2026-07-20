package com.idar.optisaas.mail;

/**
 * Puerto de salida de correo. Existe para que el proveedor sea una decisión de CONFIGURACIÓN
 * y no de código: hoy hay una implementación que solo escribe en el log (sirve para desarrollo
 * y para verificar los flujos sin dominio verificado) y otra por SMTP, que funciona con
 * cualquier proveedor (Gmail, Brevo, SES, el del hosting). Se elige con `app.mail.provider`.
 */
public interface MailSender {

    /**
     * Entrega el correo. No lanza si el envío falla: un correo que no sale no debe tumbar la
     * operación que lo originó (recuperar una contraseña, cerrar una venta). El fallo se
     * registra en el log y el llamador sigue su curso.
     */
    void send(EmailMessage message);
}
