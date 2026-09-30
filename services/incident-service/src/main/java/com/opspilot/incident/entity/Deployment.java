package com.opspilot.incident.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Deployment entity — tracks service deployments.
 * 
 * WHY IS THIS CRITICAL FOR RCA?
 * Deployments are the #1 cause of production incidents.
 * When an incident occurs, the RCA engine checks:
 *   "Was there a deployment to this service shortly before the incident?"
 * 
 * Example correlation:
 *   Deployment: checkout-service v1.8.3 at 14:02
 *   Incident:   checkout-service error spike at 14:10
 *   → 8-minute gap = strong correlation signal
 * 
 * This is EVIDENCE, not PROOF. Correlation ≠ causation.
 */
@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String serviceName;

    @Column(nullable = false)
    private String environment;

    /** The version deployed (e.g., "v1.8.3") */
    @Column(nullable = false)
    private String version;

    /** Git commit SHA for traceability */
    private String commitSha;

    /** When the deployment happened */
    @Column(nullable = false)
    private LocalDateTime deployedAt;

    /** Who or what triggered the deployment */
    private String deployedBy;

    /** SUCCEEDED, FAILED, ROLLING_BACK, etc. */
    @Column(nullable = false)
    private String deploymentStatus;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public Deployment() {}

    // Getters and Setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getCommitSha() { return commitSha; }
    public void setCommitSha(String commitSha) { this.commitSha = commitSha; }

    public LocalDateTime getDeployedAt() { return deployedAt; }
    public void setDeployedAt(LocalDateTime deployedAt) { this.deployedAt = deployedAt; }

    public String getDeployedBy() { return deployedBy; }
    public void setDeployedBy(String deployedBy) { this.deployedBy = deployedBy; }

    public String getDeploymentStatus() { return deploymentStatus; }
    public void setDeploymentStatus(String deploymentStatus) { this.deploymentStatus = deploymentStatus; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
