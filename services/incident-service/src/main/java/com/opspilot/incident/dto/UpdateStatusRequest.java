package com.opspilot.incident.dto;

import com.opspilot.incident.enums.IncidentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * DTO for updating incident status.
 * Uses PATCH semantics — only changes the status field, not the entire incident.
 */
public class UpdateStatusRequest {

    @NotNull(message = "Status is required")
    private IncidentStatus status;

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }
}
