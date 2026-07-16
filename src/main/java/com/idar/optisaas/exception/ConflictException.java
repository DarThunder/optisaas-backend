package com.idar.optisaas.exception;

/**
 * Regla de negocio que no es culpa del contenido de la petición, sino del estado actual:
 * la operación choca con algo que ya existe o que está en curso. Se traduce a HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
