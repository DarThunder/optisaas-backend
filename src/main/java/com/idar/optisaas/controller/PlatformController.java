package com.idar.optisaas.controller;

import com.idar.optisaas.dto.ActivationDelivery;
import com.idar.optisaas.dto.CreateTenantRequest;
import com.idar.optisaas.entity.Subscription;
import com.idar.optisaas.entity.User;
import com.idar.optisaas.service.RegistrationRequestService;
import com.idar.optisaas.service.TenantService;
import com.idar.optisaas.util.RegistrationStatus;
import com.idar.optisaas.util.SubscriptionStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel de plataforma: alta de ópticas cliente y gestión de sus suscripciones.
 *
 * Protegido con hasRole("PLATFORM") en SecurityConfig. Quien lo alcanza no pertenece a ninguna
 * óptica, así que este controlador es su ÚNICA superficie útil: cualquier otro endpoint del
 * sistema le devolvería vacío por no tener sucursales.
 *
 * Aquí NO hay nada para leer datos de una óptica (ventas, clientes, expedientes) y no debe
 * haberlo. Poder dar de alta a un cliente no implica poder mirar dentro de su negocio.
 */
@RestController
@RequestMapping("/api/platform")
public class PlatformController {

    @Autowired private TenantService tenantService;
    @Autowired private RegistrationRequestService registrationRequestService;

    /** Alta de una óptica: sucursal, ajustes, dueño y suscripción, en una transacción. */
    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody CreateTenantRequest request) {
        try {
            ActivationDelivery result = tenantService.createOptica(request);
            User owner = result.user();

            // El código se devuelve SIEMPRE, se haya enviado o no por correo. Si el correo del
            // dueño se capturó mal o cae en spam, sin este respaldo el cliente nuevo se queda
            // sin poder entrar y sin nadie que pueda ayudarlo. `activationCodeSentTo` le dice
            // al administrador si tiene que dictarlo. HashMap porque Map.of no admite nulos.
            Map<String, Object> body = new HashMap<>();
            body.put("ownerId", owner.getId());
            body.put("ownerUsername", owner.getUsername());
            body.put("activationCode", owner.getActivationCode());
            body.put("activationCodeSentTo", result.sentTo());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/tenants")
    public ResponseEntity<?> listTenants() {
        List<Subscription> subscriptions = tenantService.listSubscriptions();
        return ResponseEntity.ok(subscriptions);
    }

    // ------------------- Solicitudes de acceso -------------------

    /** Sin filtro: todas, las más nuevas primero. Con ?status=PENDING: la bandeja de trabajo. */
    @GetMapping("/registration-requests")
    public ResponseEntity<?> listRequests(@RequestParam(required = false) String status) {
        try {
            RegistrationStatus filter = (status != null && !status.isBlank())
                    ? RegistrationStatus.valueOf(status) : null;
            return ResponseEntity.ok(registrationRequestService.list(filter));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Estado inválido"));
        }
    }

    /**
     * Aprobar = dar de alta la óptica. El cuerpo trae lo que el formulario público no pide
     * (usuario del dueño, PIN, días de prueba); lo demás se toma de la solicitud.
     */
    @PostMapping("/registration-requests/{id}/approve")
    public ResponseEntity<?> approveRequest(@PathVariable Long id, @RequestBody CreateTenantRequest overrides) {
        try {
            ActivationDelivery result = registrationRequestService.approve(id, overrides, currentUser());
            User owner = result.user();

            Map<String, Object> body = new HashMap<>();
            body.put("ownerId", owner.getId());
            body.put("ownerUsername", owner.getUsername());
            body.put("activationCode", owner.getActivationCode());
            body.put("activationCodeSentTo", result.sentTo());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/registration-requests/{id}/reject")
    public ResponseEntity<?> rejectRequest(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload) {
        try {
            String note = payload != null ? payload.get("note") : null;
            return ResponseEntity.ok(registrationRequestService.reject(id, note, currentUser()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    /** Ajusta estado y/o vigencia. Lo que no venga en el cuerpo no se toca. */
    @PatchMapping("/tenants/{id}")
    public ResponseEntity<?> updateTenant(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            SubscriptionStatus status = payload.get("status") != null
                    ? SubscriptionStatus.valueOf(payload.get("status")) : null;
            LocalDate validUntil = payload.get("validUntil") != null && !payload.get("validUntil").isBlank()
                    ? LocalDate.parse(payload.get("validUntil")) : null;

            Subscription updated = tenantService.updateSubscription(id, status, validUntil, payload.get("notes"));
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Estado o fecha inválidos"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
