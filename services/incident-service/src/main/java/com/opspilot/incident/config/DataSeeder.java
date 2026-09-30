package com.opspilot.incident.config;

import com.opspilot.incident.entity.Deployment;
import com.opspilot.incident.entity.Incident;
import com.opspilot.incident.enums.IncidentStatus;
import com.opspilot.incident.enums.Severity;
import com.opspilot.incident.repository.DeploymentRepository;
import com.opspilot.incident.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Seeds the database with demo data on startup.
 * 
 * Only runs in the 'dev' profile (or default).
 * In production, data comes from real telemetry and API calls.
 * 
 * CommandLineRunner: Spring calls run() after the application starts.
 * This is a common pattern for initialization tasks.
 */
@Component
@Profile({"dev", "default"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;

    public DataSeeder(IncidentRepository incidentRepository,
                      DeploymentRepository deploymentRepository) {
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
    }

    @Override
    public void run(String... args) {
        if (incidentRepository.count() > 0) {
            log.info("Database already seeded, skipping.");
            return;
        }

        log.info("Seeding database with demo data...");

        // === Deployments ===
        LocalDateTime now = LocalDateTime.now();

        Deployment deploy1 = createDeployment(
                "checkout-service", "production", "v1.8.3",
                "a1b2c3d", now.minusMinutes(45), "ci-pipeline", "SUCCEEDED");

        Deployment deploy2 = createDeployment(
                "checkout-service", "production", "v1.8.2",
                "e4f5g6h", now.minusHours(6), "ci-pipeline", "SUCCEEDED");

        Deployment deploy3 = createDeployment(
                "payment-service", "production", "v2.1.0",
                "i7j8k9l", now.minusHours(2), "ci-pipeline", "SUCCEEDED");

        Deployment deploy4 = createDeployment(
                "user-service", "production", "v3.0.1",
                "m0n1o2p", now.minusDays(1), "manual", "SUCCEEDED");

        Deployment deploy5 = createDeployment(
                "inventory-service", "staging", "v1.5.0-rc1",
                "q3r4s5t", now.minusMinutes(15), "ci-pipeline", "ROLLING_BACK");

        // === Incidents ===

        // Active SEV2 incident — the main demo scenario
        Incident inc1 = createIncident(
                "INC-1001", "Checkout Service Error Rate Spike",
                "Error rate increased from 0.8% to 18.5% following deployment v1.8.3. "
                + "174 instances of DatabaseConnectionTimeoutException in the last 10 minutes.",
                "checkout-service", "production", Severity.SEV2, IncidentStatus.INVESTIGATING,
                now.minusMinutes(37), now.minusMinutes(35), null,
                "HIGH_ERROR_RATE_RULE");

        // Resolved incident — shows complete lifecycle
        Incident inc2 = createIncident(
                "INC-1002", "Payment Gateway Timeout",
                "P95 latency exceeded 3000ms for payment-service. "
                + "Root cause: upstream payment gateway rate limiting.",
                "payment-service", "production", Severity.SEV2, IncidentStatus.RESOLVED,
                now.minusHours(4), now.minusHours(3).minusMinutes(55),
                now.minusHours(3).minusMinutes(10),
                "HIGH_LATENCY_RULE");

        // Open SEV1 incident
        Incident inc3 = createIncident(
                "INC-1003", "User Service Complete Outage",
                "Health check returning 503 for user-service. All login and registration "
                + "endpoints returning 500. Impact: 100% of authentication requests failing.",
                "user-service", "production", Severity.SEV1, IncidentStatus.OPEN,
                now.minusMinutes(5), null, null,
                "HEALTH_CHECK_FAILURE");

        // Mitigating incident — remediation in progress
        Incident inc4 = createIncident(
                "INC-1004", "Inventory Service Memory Leak",
                "Heap usage at 94% and climbing. GC pause times exceeding 2 seconds. "
                + "Rollback to v1.4.9 approved and in progress.",
                "inventory-service", "staging", Severity.SEV3, IncidentStatus.MITIGATING,
                now.minusHours(1), now.minusMinutes(55), null,
                "HIGH_MEMORY_USAGE_RULE");

        // Resolved minor incident
        Incident inc5 = createIncident(
                "INC-1005", "Cart Service Slow Response Time",
                "P99 latency increased to 800ms (baseline: 200ms). "
                + "Caused by missing database index on cart_items.user_id.",
                "cart-service", "production", Severity.SEV4, IncidentStatus.RESOLVED,
                now.minusDays(2), now.minusDays(2).plusMinutes(10),
                now.minusDays(2).plusMinutes(45),
                "HIGH_LATENCY_RULE");

        log.info("Seeded {} incidents and {} deployments.",
                incidentRepository.count(), deploymentRepository.count());
    }

    private Deployment createDeployment(String serviceName, String environment,
            String version, String commitSha, LocalDateTime deployedAt,
            String deployedBy, String status) {
        Deployment d = new Deployment();
        d.setServiceName(serviceName);
        d.setEnvironment(environment);
        d.setVersion(version);
        d.setCommitSha(commitSha);
        d.setDeployedAt(deployedAt);
        d.setDeployedBy(deployedBy);
        d.setDeploymentStatus(status);
        return deploymentRepository.save(d);
    }

    private Incident createIncident(String number, String title, String description,
            String serviceName, String environment, Severity severity,
            IncidentStatus status, LocalDateTime detectedAt,
            LocalDateTime acknowledgedAt, LocalDateTime resolvedAt,
            String source) {
        Incident inc = new Incident();
        inc.setIncidentNumber(number);
        inc.setTitle(title);
        inc.setDescription(description);
        inc.setServiceName(serviceName);
        inc.setEnvironment(environment);
        inc.setSeverity(severity);
        inc.setStatus(status);
        inc.setDetectedAt(detectedAt);
        inc.setAcknowledgedAt(acknowledgedAt);
        inc.setResolvedAt(resolvedAt);
        inc.setSource(source);
        inc.setCorrelationId(UUID.randomUUID().toString());
        return incidentRepository.save(inc);
    }
}
