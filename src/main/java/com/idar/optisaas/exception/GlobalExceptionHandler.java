package com.idar.optisaas.exception;

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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDatabaseError(DataIntegrityViolationException ex) {
        System.err.println(">>> DATABASE ERROR CAPTURADO:"); // LOG VISIBLE
        ex.printStackTrace(); // IMPRIMIR EN CONSOLA
        
        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now(),
            "message", "Error de integridad de datos (posible duplicado o datos faltantes).",
            "status", HttpStatus.CONFLICT.value()
        );
        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Object> handleRuntimeException(RuntimeException ex) {
        // --- ESTO ES LO QUE FALTABA ---
        System.err.println(">>> RUNTIME EXCEPTION CAPTURADA (Causante del 400):");
        ex.printStackTrace(); // ¡Aquí veremos el error real!
        // ------------------------------

        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now(),
            "message", "Error interno: " + ex.getMessage(), // Enviamos el mensaje real al frontend
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
    
    // Catch-all para cualquier otra cosa que no sea RuntimeException
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex) {
        System.err.println(">>> EXCEPTION GENERAL CAPTURADA:");
        ex.printStackTrace();
        
        Map<String, Object> body = Map.of(
            "timestamp", LocalDateTime.now(),
            "message", "Error inesperado del servidor: " + ex.getMessage(),
            "status", HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}