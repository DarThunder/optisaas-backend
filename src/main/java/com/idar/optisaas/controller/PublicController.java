package com.idar.optisaas.controller;

import com.idar.optisaas.dto.RegistrationRequestSubmission;
import com.idar.optisaas.service.RegistrationRequestService;
import com.idar.optisaas.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Lo único que la página de presentación puede llamar. Sin autenticar.
 *
 * Es la única superficie de escritura anónima del sistema, así que se mantiene deliberadamente
 * mínima: un endpoint, sin lecturas y sin nada que consultar. Todo lo demás exige sesión.
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    /**
     * Misma respuesta pase lo que pase (salvo un formulario mal llenado, que sí hay que
     * corregir). Si variara según si el correo ya tiene cuenta o si la IP está bloqueada, el
     * formulario serviría para averiguar quiénes son clientes o para tantear el límite. Es la
     * misma decisión que en forgot-password.
     */
    private static final Map<String, String> ALWAYS = Map.of(
            "message", "Gracias. Recibimos tu solicitud y te contactaremos pronto.");

    @Autowired private RegistrationRequestService service;

    @PostMapping("/registration-requests")
    public ResponseEntity<?> submit(@Valid @RequestBody RegistrationRequestSubmission submission,
                                    HttpServletRequest httpRequest) {
        try {
            service.submit(submission, ClientIp.from(httpRequest));
        } catch (IllegalArgumentException e) {
            // Errores de captura del propio prospecto (falta consentimiento, falta contacto):
            // estos sí se le dicen, porque son suyos y puede corregirlos.
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            // Límite alcanzado, duplicado o fallo interno: no se distingue hacia afuera.
            // Queda en el log del servidor, que es donde sirve.
            log.warn("Solicitud no registrada: {}", e.getMessage());
        }
        return ResponseEntity.ok(ALWAYS);
    }
}
