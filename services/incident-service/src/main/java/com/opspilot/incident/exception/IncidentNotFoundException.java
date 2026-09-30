package com.opspilot.incident.exception;

import java.util.UUID;

/**
 * Custom exception thrown when an incident is not found.
 * 
 * WHY A CUSTOM EXCEPTION?
 * Instead of returning null and checking everywhere, we throw this exception.
 * The GlobalExceptionHandler catches it and returns a proper 404 response.
 * This keeps error handling centralized and controller code clean.
 */
public class IncidentNotFoundException extends RuntimeException {

    public IncidentNotFoundException(UUID id) {
        super("Incident not found with id: " + id);
    }

    public IncidentNotFoundException(String incidentNumber) {
        super("Incident not found: " + incidentNumber);
    }
}
