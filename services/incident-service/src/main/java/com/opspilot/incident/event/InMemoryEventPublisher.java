package com.opspilot.incident.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory event publisher for local development.
 * 
 * In production, this would be replaced by AzureServiceBusEventPublisher.
 * This implementation:
 * 1. Logs the event (so we can see events in the console)
 * 2. Stores events in memory (so we can inspect them via the API)
 * 
 * WHY NOT JUST SKIP EVENTS IN DEV?
 * Because we want to validate the event flow works end-to-end.
 * If we skip events locally, we might ship broken event code to production.
 * The in-memory implementation lets us test the full flow without Azure.
 */
@Component
public class InMemoryEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryEventPublisher.class);

    /** Thread-safe list of all published events (for debugging/inspection) */
    private final List<DomainEvent> publishedEvents = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void publish(DomainEvent event) {
        publishedEvents.add(event);
        log.info("📢 Event published: type={}, correlationId={}, eventId={}",
                event.getEventType(), event.getCorrelationId(), event.getEventId());
    }

    /** Returns all published events (useful for testing and debugging) */
    public List<DomainEvent> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

    /** Clear all events (useful for testing) */
    public void clear() {
        publishedEvents.clear();
    }
}
