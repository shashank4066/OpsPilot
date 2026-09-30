package com.opspilot.incident.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base event structure for all domain events in OpsPilot.
 * 
 * Every event carries:
 * - eventId: unique identifier for this specific event instance
 * - eventType: what happened (INCIDENT_CREATED, STATUS_CHANGED, etc.)
 * - timestamp: when the event was created
 * - correlationId: traces the business operation across services
 * - sourceService: which service produced this event
 * - payload: the event-specific data (as JSON string)
 * 
 * WHY A COMMON EVENT STRUCTURE?
 * Standardization. Every consumer knows where to find the correlationId,
 * every log entry can include the eventType, and monitoring can track
 * event throughput by type.
 */
public class DomainEvent {

    private final String eventId;
    private final String eventType;
    private final LocalDateTime timestamp;
    private final String correlationId;
    private final String sourceService;
    private final Object payload;

    public DomainEvent(String eventType, String correlationId, Object payload) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = LocalDateTime.now();
        this.correlationId = correlationId;
        this.sourceService = "incident-service";
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getCorrelationId() { return correlationId; }
    public String getSourceService() { return sourceService; }
    public Object getPayload() { return payload; }

    @Override
    public String toString() {
        return String.format("DomainEvent{type='%s', correlationId='%s', timestamp=%s}",
                eventType, correlationId, timestamp);
    }
}
