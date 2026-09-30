package com.opspilot.incident.service;

import com.opspilot.incident.dto.CreateIncidentRequest;
import com.opspilot.incident.dto.IncidentResponse;
import com.opspilot.incident.entity.Incident;
import com.opspilot.incident.enums.IncidentStatus;
import com.opspilot.incident.exception.IncidentNotFoundException;
import com.opspilot.incident.exception.InvalidStatusTransitionException;
import com.opspilot.incident.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IncidentService — the business logic layer.
 * 
 * KEY DESIGN DECISIONS:
 * 
 * 1. STATUS TRANSITIONS are validated here, not in the controller.
 *    The controller just passes the request. The service decides if it's valid.
 *    This means the same rules apply whether the request comes from an API,
 *    an event, or a scheduled job.
 * 
 * 2. ENTITY-TO-DTO CONVERSION happens here.
 *    The controller never sees the entity. This prevents JPA lazy-loading 
 *    issues and keeps the API contract separate from the database schema.
 * 
 * 3. @Transactional ensures database operations are atomic.
 *    If anything fails during a save, all changes roll back.
 * 
 * CONSTRUCTOR INJECTION:
 * We inject IncidentRepository via the constructor, not @Autowired on a field.
 * Why? Constructor injection:
 *   - Makes dependencies explicit
 *   - Allows the class to be tested without Spring (just pass a mock)
 *   - Prevents the class from being instantiated with missing dependencies
 */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AtomicLong incidentCounter = new AtomicLong(1000);

    /**
     * VALID STATUS TRANSITIONS — the state machine rules.
     * 
     * This map defines which transitions are allowed:
     *   OPEN → [INVESTIGATING, RESOLVED]
     *   INVESTIGATING → [MITIGATING, RESOLVED, OPEN]
     *   MITIGATING → [INVESTIGATING, RESOLVED]
     *   RESOLVED → [] (terminal state — no transitions out)
     * 
     * If a transition isn't in this map, it's rejected with 409 Conflict.
     */
    private static final Map<IncidentStatus, Set<IncidentStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(IncidentStatus.class);
        VALID_TRANSITIONS.put(IncidentStatus.OPEN,
                EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.RESOLVED));
        VALID_TRANSITIONS.put(IncidentStatus.INVESTIGATING,
                EnumSet.of(IncidentStatus.MITIGATING, IncidentStatus.RESOLVED, IncidentStatus.OPEN));
        VALID_TRANSITIONS.put(IncidentStatus.MITIGATING,
                EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.RESOLVED));
        VALID_TRANSITIONS.put(IncidentStatus.RESOLVED,
                EnumSet.noneOf(IncidentStatus.class)); // Terminal state
    }

    /** Constructor injection — Spring automatically provides the repository */
    public IncidentService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    /**
     * CREATE a new incident.
     * 
     * Business rules:
     * - Status always starts as OPEN (client cannot choose)
     * - Incident number is auto-generated (INC-1001, INC-1002, ...)
     * - detectedAt is set to now (server decides, not client — prevents clock manipulation)
     * - correlationId is generated for distributed tracing
     */
    @Transactional
    public IncidentResponse createIncident(CreateIncidentRequest request) {
        Incident incident = new Incident();
        incident.setIncidentNumber("INC-" + incidentCounter.incrementAndGet());
        incident.setTitle(request.getTitle());
        incident.setDescription(request.getDescription());
        incident.setServiceName(request.getServiceName());
        incident.setEnvironment(request.getEnvironment());
        incident.setSeverity(request.getSeverity());
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDetectedAt(LocalDateTime.now());
        incident.setSource(request.getSource() != null ? request.getSource() : "MANUAL");
        incident.setCorrelationId(UUID.randomUUID().toString());

        Incident saved = incidentRepository.save(incident);
        return toResponse(saved);
    }

    /** GET a single incident by ID */
    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));
        return toResponse(incident);
    }

    /** LIST all incidents */
    @Transactional(readOnly = true)
    public List<IncidentResponse> getAllIncidents() {
        return incidentRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /** LIST incidents by status */
    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidentsByStatus(IncidentStatus status) {
        return incidentRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * CHANGE incident status — the most important business logic method.
     * 
     * This enforces the state machine:
     * 1. Find the incident (or throw 404)
     * 2. Check if the transition is valid (or throw 409)
     * 3. Update status and set relevant timestamps
     * 4. Save and return
     * 
     * WHY @Transactional?
     * If setting the status succeeds but saving fails, we don't want the 
     * in-memory object to be in a different state than the database.
     * @Transactional ensures the entire operation is atomic.
     */
    @Transactional
    public IncidentResponse updateStatus(UUID id, IncidentStatus newStatus) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new IncidentNotFoundException(id));

        IncidentStatus currentStatus = incident.getStatus();

        // Validate the transition using our state machine map
        Set<IncidentStatus> allowed = VALID_TRANSITIONS.getOrDefault(
                currentStatus, EnumSet.noneOf(IncidentStatus.class));

        if (!allowed.contains(newStatus)) {
            throw new InvalidStatusTransitionException(
                    String.format("Cannot transition from %s to %s. Allowed transitions: %s",
                            currentStatus, newStatus, allowed));
        }

        // Update status
        incident.setStatus(newStatus);

        // Set timestamps based on the new status
        if (newStatus == IncidentStatus.INVESTIGATING && incident.getAcknowledgedAt() == null) {
            incident.setAcknowledgedAt(LocalDateTime.now());
        }
        if (newStatus == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(LocalDateTime.now());
        }

        Incident saved = incidentRepository.save(incident);
        return toResponse(saved);
    }

    /**
     * Convert entity to response DTO.
     * 
     * In larger projects, you'd use a mapping library like MapStruct.
     * For learning purposes, manual mapping makes the conversion explicit.
     */
    private IncidentResponse toResponse(Incident incident) {
        IncidentResponse response = new IncidentResponse();
        response.setId(incident.getId());
        response.setIncidentNumber(incident.getIncidentNumber());
        response.setTitle(incident.getTitle());
        response.setDescription(incident.getDescription());
        response.setServiceName(incident.getServiceName());
        response.setEnvironment(incident.getEnvironment());
        response.setSeverity(incident.getSeverity());
        response.setStatus(incident.getStatus());
        response.setDetectedAt(incident.getDetectedAt());
        response.setAcknowledgedAt(incident.getAcknowledgedAt());
        response.setResolvedAt(incident.getResolvedAt());
        response.setSource(incident.getSource());
        response.setCorrelationId(incident.getCorrelationId());
        response.setCreatedAt(incident.getCreatedAt());
        response.setUpdatedAt(incident.getUpdatedAt());
        return response;
    }
}
