package com.opspilot.incident.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler — catches exceptions from ALL controllers.
 * 
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * It intercepts exceptions before they reach the client and converts them 
 * into clean, structured JSON error responses.
 * 
 * WITHOUT THIS: Client sees a raw 500 error with a Java stack trace.
 * WITH THIS:    Client sees { "error": "Incident not found", "status": 404 }
 * 
 * Each @ExceptionHandler method handles a specific exception type.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles: Incident not found (404)
     * Triggered when: IncidentNotFoundException is thrown from service layer
     */
    @ExceptionHandler(IncidentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleIncidentNotFound(IncidentNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles: Invalid status transition (409 Conflict)
     * Triggered when: Trying an invalid state change like OPEN → MITIGATING
     */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles: Bean Validation failures (400 Bad Request)
     * Triggered when: @Valid finds @NotBlank/@NotNull violations on request DTOs
     * 
     * Collects ALL validation errors into a map so the client sees all problems at once,
     * not one at a time.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        body.put("fieldErrors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles: Any unexpected exception (500 Internal Server Error)
     * This is the safety net. If nothing else catches the exception, this does.
     * In production, you'd log the full stack trace and return a generic message
     * (never expose internal errors to clients).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, 
                "An unexpected error occurred: " + ex.getMessage());
    }

    /** Helper to build consistent error response structure */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
