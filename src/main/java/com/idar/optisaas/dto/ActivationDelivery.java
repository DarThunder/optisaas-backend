package com.idar.optisaas.dto;

import com.idar.optisaas.entity.User;

/**
 * Resultado de dar de alta o restablecer a un empleado: el usuario y cómo le llegó su código.
 *
 * {@code sentTo} es el correo al que se envió, o null si el empleado no tiene correo capturado
 * y el administrador tiene que dictarle el código. Se devuelve explícitamente en vez de dejarlo
 * implícito para que el controlador pueda decirle al administrador qué pasó realmente.
 */
public record ActivationDelivery(User user, String sentTo) {

    public boolean wasEmailed() {
        return sentTo != null && !sentTo.isBlank();
    }
}
