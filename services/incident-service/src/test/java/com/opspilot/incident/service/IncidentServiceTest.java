package com.opspilot.incident.service;

import com.opspilot.incident.dto.CreateIncidentRequest;
import com.opspilot.incident.dto.IncidentResponse;
import com.opspilot.incident.entity.Incident;
import com.opspilot.incident.enums.IncidentStatus;
import com.opspilot.incident.enums.Severity;
import com.opspilot.incident.event.InMemoryEventPublisher;
import com.opspilot.incident.exception.IncidentNotFoundException;
import com.opspilot.incident.exception.InvalidStatusTransitionException;
import com.opspilot.incident.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for IncidentService.
 * 
 * KEY TESTING CONCEPTS:
 * - @Mock creates a fake (mock) repository — no database needed
 * - We test BUSINESS LOGIC only, not Spring wiring or database behavior
 * - Each test is independent — setUp() runs before each test
 * - @DisplayName makes test names readable in reports
 * 
 * TEST STRUCTURE: Arrange → Act → Assert
 * - Arrange: set up test data and mock behavior
 * - Act: call the method being tested
 * - Assert: verify the result and side effects
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    private InMemoryEventPublisher eventPublisher;
    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        eventPublisher = new InMemoryEventPublisher();
        incidentService = new IncidentService(incidentRepository, eventPublisher);
    }

    // ========== Nested test class for createIncident ==========
    @Nested
    @DisplayName("createIncident")
    class CreateIncident {

        @Test
        @DisplayName("should create incident with OPEN status")
        void shouldCreateWithOpenStatus() {
            // Arrange
            CreateIncidentRequest request = buildRequest("Checkout error spike",
                    "checkout-service", "production", Severity.SEV2);

            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> {
                        Incident saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            // Act
            IncidentResponse response = incidentService.createIncident(request);

            // Assert
            assertThat(response.getStatus()).isEqualTo(IncidentStatus.OPEN);
            assertThat(response.getTitle()).isEqualTo("Checkout error spike");
            assertThat(response.getServiceName()).isEqualTo("checkout-service");
            assertThat(response.getSeverity()).isEqualTo(Severity.SEV2);
            assertThat(response.getIncidentNumber()).startsWith("INC-");
            assertThat(response.getCorrelationId()).isNotNull();
            assertThat(response.getDetectedAt()).isNotNull();
        }

        @Test
        @DisplayName("should auto-generate incident number")
        void shouldGenerateIncidentNumber() {
            // Arrange
            CreateIncidentRequest request = buildRequest("Test", "svc", "prod", Severity.SEV3);
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> {
                        Incident saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            // Act
            IncidentResponse response = incidentService.createIncident(request);

            // Assert
            assertThat(response.getIncidentNumber()).matches("INC-\\d+");
        }

        @Test
        @DisplayName("should set source to MANUAL when not specified")
        void shouldDefaultSourceToManual() {
            // Arrange
            CreateIncidentRequest request = buildRequest("Test", "svc", "prod", Severity.SEV3);
            // source is not set → should default to MANUAL
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> {
                        Incident saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            // Act
            IncidentResponse response = incidentService.createIncident(request);

            // Assert
            assertThat(response.getSource()).isEqualTo("MANUAL");
        }

        @Test
        @DisplayName("should publish INCIDENT_CREATED event")
        void shouldPublishEvent() {
            // Arrange
            CreateIncidentRequest request = buildRequest("Test", "svc", "prod", Severity.SEV2);
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> {
                        Incident saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            // Act
            incidentService.createIncident(request);

            // Assert
            assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
            assertThat(eventPublisher.getPublishedEvents().get(0).getEventType())
                    .isEqualTo("INCIDENT_CREATED");
        }
    }

    // ========== Nested test class for getIncident ==========
    @Nested
    @DisplayName("getIncident")
    class GetIncident {

        @Test
        @DisplayName("should return incident when found")
        void shouldReturnWhenFound() {
            // Arrange
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.OPEN);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));

            // Act
            IncidentResponse response = incidentService.getIncident(id);

            // Assert
            assertThat(response.getId()).isEqualTo(id);
            assertThat(response.getIncidentNumber()).isEqualTo("INC-1001");
        }

        @Test
        @DisplayName("should throw IncidentNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(incidentRepository.findById(id)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> incidentService.getIncident(id))
                    .isInstanceOf(IncidentNotFoundException.class)
                    .hasMessageContaining(id.toString());
        }
    }

    // ========== Nested test class for updateStatus ==========
    @Nested
    @DisplayName("updateStatus — State Machine")
    class UpdateStatus {

        @Test
        @DisplayName("OPEN → INVESTIGATING should succeed")
        void openToInvestigating() {
            // Arrange
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.OPEN);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            IncidentResponse response = incidentService.updateStatus(id, IncidentStatus.INVESTIGATING);

            // Assert
            assertThat(response.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
            assertThat(response.getAcknowledgedAt()).isNotNull();
        }

        @Test
        @DisplayName("INVESTIGATING → MITIGATING should succeed")
        void investigatingToMitigating() {
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.INVESTIGATING);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            IncidentResponse response = incidentService.updateStatus(id, IncidentStatus.MITIGATING);

            assertThat(response.getStatus()).isEqualTo(IncidentStatus.MITIGATING);
        }

        @Test
        @DisplayName("MITIGATING → RESOLVED should set resolvedAt")
        void mitigatingToResolved() {
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.MITIGATING);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            IncidentResponse response = incidentService.updateStatus(id, IncidentStatus.RESOLVED);

            assertThat(response.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
            assertThat(response.getResolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("MITIGATING → INVESTIGATING should succeed (fix didn't work)")
        void mitigatingBackToInvestigating() {
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.MITIGATING);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            IncidentResponse response = incidentService.updateStatus(id, IncidentStatus.INVESTIGATING);

            assertThat(response.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
        }

        @Test
        @DisplayName("OPEN → MITIGATING should be rejected (invalid transition)")
        void openToMitigatingShouldFail() {
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.OPEN);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));

            assertThatThrownBy(() ->
                    incidentService.updateStatus(id, IncidentStatus.MITIGATING))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("OPEN")
                    .hasMessageContaining("MITIGATING");
        }

        @Test
        @DisplayName("RESOLVED → any should be rejected (terminal state)")
        void resolvedShouldBeTerminal() {
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.RESOLVED);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));

            assertThatThrownBy(() ->
                    incidentService.updateStatus(id, IncidentStatus.OPEN))
                    .isInstanceOf(InvalidStatusTransitionException.class);

            assertThatThrownBy(() ->
                    incidentService.updateStatus(id, IncidentStatus.INVESTIGATING))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("should publish STATUS_CHANGED event on valid transition")
        void shouldPublishEventOnStatusChange() {
            UUID id = UUID.randomUUID();
            Incident incident = buildIncident(id, "INC-1001", IncidentStatus.OPEN);
            when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            incidentService.updateStatus(id, IncidentStatus.INVESTIGATING);

            assertThat(eventPublisher.getPublishedEvents()).hasSize(1);
            assertThat(eventPublisher.getPublishedEvents().get(0).getEventType())
                    .isEqualTo("INCIDENT_STATUS_CHANGED");
        }
    }

    // ========== Helper Methods ==========

    private CreateIncidentRequest buildRequest(String title, String serviceName,
            String environment, Severity severity) {
        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setTitle(title);
        request.setServiceName(serviceName);
        request.setEnvironment(environment);
        request.setSeverity(severity);
        return request;
    }

    private Incident buildIncident(UUID id, String incidentNumber, IncidentStatus status) {
        Incident incident = new Incident();
        incident.setId(id);
        incident.setIncidentNumber(incidentNumber);
        incident.setTitle("Test Incident");
        incident.setServiceName("test-service");
        incident.setEnvironment("production");
        incident.setSeverity(Severity.SEV2);
        incident.setStatus(status);
        incident.setDetectedAt(LocalDateTime.now());
        incident.setCorrelationId(UUID.randomUUID().toString());
        return incident;
    }
}
