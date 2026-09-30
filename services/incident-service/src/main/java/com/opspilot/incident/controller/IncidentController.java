package com.opspilot.incident.controller;

import com.opspilot.incident.dto.CreateIncidentRequest;
import com.opspilot.incident.dto.IncidentResponse;
import com.opspilot.incident.dto.UpdateStatusRequest;
import com.opspilot.incident.enums.IncidentStatus;
import com.opspilot.incident.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for incident management.
 * 
 * KEY ANNOTATIONS:
 * @RestController    — handles HTTP requests, auto-converts to JSON
 * @RequestMapping    — base URL prefix for all endpoints in this controller
 * @PostMapping       — handles POST requests (create)
 * @GetMapping        — handles GET requests (read)
 * @PatchMapping      — handles PATCH requests (partial update)
 * @PathVariable      — extracts values from the URL path
 * @RequestBody       — deserializes JSON request body into a Java object
 * @Valid             — triggers Bean Validation on the request body
 * 
 * CONTROLLER RESPONSIBILITIES:
 * 1. Receive HTTP request
 * 2. Validate input (@Valid)
 * 3. Delegate to service
 * 4. Return appropriate HTTP status code
 * 
 * The controller does NOT contain business logic.
 * All logic is in IncidentService.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    /** Constructor injection */
    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * POST /api/v1/incidents
     * 
     * Creates a new incident.
     * Returns 201 Created with the created incident.
     * 
     * @Valid triggers validation of @NotBlank/@NotNull on CreateIncidentRequest.
     * If validation fails, Spring throws MethodArgumentNotValidException
     * which our GlobalExceptionHandler catches and returns 400.
     */
    @PostMapping
    public ResponseEntity<IncidentResponse> createIncident(
            @Valid @RequestBody CreateIncidentRequest request) {
        IncidentResponse response = incidentService.createIncident(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * GET /api/v1/incidents
     * 
     * Lists all incidents. Optional filter by status.
     * Examples:
     *   GET /api/v1/incidents              → all incidents
     *   GET /api/v1/incidents?status=OPEN   → only OPEN incidents
     * 
     * @RequestParam with required=false makes the parameter optional.
     */
    @GetMapping
    public ResponseEntity<List<IncidentResponse>> getAllIncidents(
            @RequestParam(required = false) IncidentStatus status) {
        List<IncidentResponse> incidents;
        if (status != null) {
            incidents = incidentService.getIncidentsByStatus(status);
        } else {
            incidents = incidentService.getAllIncidents();
        }
        return ResponseEntity.ok(incidents);
    }

    /**
     * GET /api/v1/incidents/{id}
     * 
     * Gets a single incident by its UUID.
     * Returns 404 if not found (handled by GlobalExceptionHandler).
     * 
     * @PathVariable extracts {id} from the URL.
     */
    @GetMapping("/{id}")
    public ResponseEntity<IncidentResponse> getIncident(@PathVariable UUID id) {
        IncidentResponse response = incidentService.getIncident(id);
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/incidents/{id}/status
     * 
     * Changes incident status. Validates the transition.
     * Returns 409 Conflict if the transition is invalid.
     * 
     * WHY PATCH AND NOT PUT?
     * PUT replaces the entire resource. PATCH updates part of it.
     * We're only changing the status field, so PATCH is correct.
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<IncidentResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        IncidentResponse response = incidentService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(response);
    }
}
