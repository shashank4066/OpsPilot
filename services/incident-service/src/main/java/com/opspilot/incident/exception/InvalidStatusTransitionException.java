package com.opspilot.incident.exception;

/**
 * Thrown when an invalid status transition is attempted.
 * Example: trying to go from OPEN directly to MITIGATING (must investigate first).
 * 
 * This produces HTTP 409 Conflict — the request conflicts with the current state.
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
