package com.idar.optisaas.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Lo que manda el formulario público de la página de presentación.
 *
 * Todos los campos llevan tope de longitud. Es el único endpoint de escritura sin autenticar
 * expuesto a internet: sin límites, un solo envío puede meter megabytes en la base.
 */
@Data
public class RegistrationRequestSubmission {

    @NotBlank(message = "El nombre de la óptica es obligatorio")
    @Size(max = 150, message = "El nombre de la óptica es demasiado largo")
    private String businessName;

    @NotBlank(message = "Tu nombre es obligatorio")
    @Size(max = 150, message = "El nombre es demasiado largo")
    private String contactName;

    // Ni el correo ni el teléfono son obligatorios por separado, pero uno de los dos sí:
    // sin forma de contactar, la solicitud no sirve de nada. Se valida en el servicio.
    @Email(message = "El correo no parece válido")
    @Size(max = 150, message = "El correo es demasiado largo")
    private String email;

    @Size(max = 30, message = "El teléfono es demasiado largo")
    private String phone;

    @Size(max = 100, message = "La ciudad es demasiado larga")
    private String city;

    @Size(max = 2000, message = "El mensaje es demasiado largo")
    private String message;

    /** El prospecto acepta el aviso de privacidad. Sin esto no se guardan sus datos. */
    private boolean consent;

    /**
     * Campo trampa: invisible en el formulario, así que una persona SIEMPRE lo deja vacío.
     * Los bots rellenan todo lo que encuentran. Si viene con algo, la solicitud se descarta
     * en silencio — al bot se le responde igual que a todos, para que no aprenda nada.
     */
    private String website;
}
