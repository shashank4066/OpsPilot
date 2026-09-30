package com.opspilot.incident.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public class DeploymentResponse {

    private UUID id;
    private String serviceName;
    private String environment;
    private String version;
    private String commitSha;
    private LocalDateTime deployedAt;
    private String deployedBy;
    private String deploymentStatus;
    private LocalDateTime createdAt;

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
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
