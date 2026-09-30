package com.opspilot.incident.event;

/**
 * Abstraction for event publishing — Dependency Inversion Principle.
 * 
 * Business logic depends on this INTERFACE, not a concrete implementation.
 * This allows swapping:
 *   - InMemoryEventPublisher (local dev)
 *   - AzureServiceBusEventPublisher (production)
 * 
 * Zero business logic changes required when switching implementations.
 * Spring injects the correct one based on the active profile.
 */
public interface EventPublisher {

    /**
     * Publish a domain event to the event bus.
     * 
     * In local dev: logs the event and stores in memory.
     * In production: sends to Azure Service Bus topic.
     * 
     * @param event the domain event to publish
     */
    void publish(DomainEvent event);
}
