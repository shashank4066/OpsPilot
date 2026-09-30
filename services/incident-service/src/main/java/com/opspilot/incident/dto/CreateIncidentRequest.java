package com.opspilot.incident.dto;

import com.opspilot.incident.enums.Severity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for creating a new incident.
 * 
 * WHY DTOs INSTEAD OF USING THE ENTITY?
 * 1. The entity has fields the client shouldn't set (id, createdAt, status)
 * 2. Changing the entity would change the API (coupling)
 * 3. DTOs let you validate input without polluting the entity
 * 4. Different operations need different fields (create vs update)
 * 
 * @NotBlank = not null AND not empty AND not just whitespace
 * @NotNull  = not null (but can be empty string — use for non-strings)
 */
public class CreateIncidentRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotBlank(message = "Environment is required")
    private String environment;

    @NotNull(message = "Severity is required")
    private Severity severity;

    /** What triggered this (e.g., "HIGH_ERROR_RATE_RULE", "MANUAL") */
    private String source;

    // Getters and Setters

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = severity;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
