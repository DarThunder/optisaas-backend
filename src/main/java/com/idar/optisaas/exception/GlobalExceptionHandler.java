package com.idar.optisaas.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDatabaseError(DataIntegrityViolationException ex) {
        // Detalle completo solo en logs del servidor, nunca en la respuesta.
        log.error("Error de integridad de datos", ex);

        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now(),
            "message", "Error de integridad de datos (posible duplicado o datos faltantes).",
            "status", HttpStatus.CONFLICT.value()
        );
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        // Las RuntimeException del dominio llevan mensajes pensados para el usuario
        // (validaciones de negocio); se devuelven tal cual. El detalle va a los logs.
        log.warn("Regla de negocio o error controlado: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now(),
            "message", ex.getMessage() != null ? ex.getMessage() : "Solicitud inválida",
            "status", HttpStatus.BAD_REQUEST.value()
        );
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now(),
            "message", "Error de validación",
            "errors", errors,
            "status", HttpStatus.BAD_REQUEST.value()
        );

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    // Catch-all: cualquier excepción inesperada. NO se expone el mensaje interno
    // (puede contener detalles de implementación/BD) — solo un mensaje genérico.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex) {
        log.error("Error inesperado del servidor", ex);

        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now(),
            "message", "Error inesperado del servidor. Inténtalo de nuevo o contacta a soporte.",
            "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
