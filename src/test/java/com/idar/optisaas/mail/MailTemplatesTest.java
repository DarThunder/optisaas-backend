package com.idar.optisaas.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El nombre del producto viene de configuración, no escrito en las plantillas. Estas pruebas
 * existen para que si alguien vuelve a fijarlo a mano, se note enseguida.
 */
class MailTemplatesTest {

    private static final String LINK = "https://app.fovea.com.mx/?reset-token=abc";

    @Test
    void usesTheConfiguredBrandEverywhere() {
        MailTemplates templates = new MailTemplates("Fóvea", "VLK");

        EmailMessage reset = templates.passwordReset("due@optica.com", "Edwin", LINK, 60);

        assertTrue(reset.subject().contains("Fóvea"), "el asunto debe llevar la marca");
        assertTrue(reset.textBody().contains("Fóvea"));
        assertTrue(reset.htmlBody().contains("Fóvea"));
        assertFalse(reset.textBody().contains("OptiSaaS"), "no debe quedar el nombre viejo");
    }

    // Cambiar de marca debe ser cambiar una variable: si algo quedara fijo en el código,
    // aquí seguiría apareciendo "Fóvea".
    @Test
    void adaptsToADifferentBrand() {
        MailTemplates templates = new MailTemplates("OtraMarca", "OtroEstudio");

        EmailMessage reset = templates.passwordReset("due@optica.com", "Edwin", LINK, 60);

        assertTrue(reset.subject().contains("OtraMarca"));
        assertFalse(reset.textBody().contains("Fóvea"));
        assertTrue(reset.textBody().contains("hecho por OtroEstudio"));
    }

    // El estudio va en el pie, no en el remitente: la relación del cliente es con el producto.
    @Test
    void showsTheMakerOnlyInTheSignature() {
        MailTemplates templates = new MailTemplates("Fóvea", "VLK");

        EmailMessage reset = templates.passwordReset("due@optica.com", "Edwin", LINK, 60);

        assertTrue(reset.textBody().contains("Fóvea — hecho por VLK"));
        assertNull(reset.fromName(), "un correo de plataforma no lleva nombre de negocio");
    }

    @Test
    void omitsTheMakerWhenItIsNotConfigured() {
        MailTemplates templates = new MailTemplates("Fóvea", "");

        EmailMessage reset = templates.passwordReset("due@optica.com", "Edwin", LINK, 60);

        assertFalse(reset.textBody().contains("hecho por"));
        assertTrue(reset.textBody().contains("Fóvea"));
    }

    // El correo del empleado nombra al negocio Y a la marca: solo el negocio tendría forma de
    // phishing, y solo la marca dejaría al empleado sin saber quién lo dio de alta.
    @Test
    void employeeActivationNamesBothTheBusinessAndTheBrand() {
        MailTemplates templates = new MailTemplates("Fóvea", "VLK");

        EmailMessage activation = templates.employeeActivation(
                "ana@correo.com", "Ana", "ana.vend", "482913",
                "Óptica Mogar Centro", "hola@mogar.mx", false);

        assertEquals("Óptica Mogar Centro vía Fóvea", activation.fromName());
        assertEquals("hola@mogar.mx", activation.replyTo());
        assertTrue(activation.textBody().contains("482913"));
    }
}
