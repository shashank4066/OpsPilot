package com.opspilot.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * DTO for recording a deployment event.
 */
public class CreateDeploymentRequest {

    @NotBlank(message = "Service name is required")
    private String serviceName;

    @NotBlank(message = "Environment is required")
    private String environment;

    @NotBlank(message = "Version is required")
    private String version;

    private String commitSha;

    @NotNull(message = "Deployment time is required")
    private LocalDateTime deployedAt;

    private String deployedBy;

    @NotBlank(message = "Deployment status is required")
    private String deploymentStatus;

    // Getters and Setters
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
}
