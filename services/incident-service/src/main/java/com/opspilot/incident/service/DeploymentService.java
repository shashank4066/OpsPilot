package com.opspilot.incident.service;

import com.opspilot.incident.dto.CreateDeploymentRequest;
import com.opspilot.incident.dto.DeploymentResponse;
import com.opspilot.incident.entity.Deployment;
import com.opspilot.incident.repository.DeploymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing deployment records.
 * 
 * Deployments are recorded here and later queried by the RCA correlation engine
 * to check if a deployment preceded an incident.
 */
@Service
public class DeploymentService {

    private final DeploymentRepository deploymentRepository;

    public DeploymentService(DeploymentRepository deploymentRepository) {
        this.deploymentRepository = deploymentRepository;
    }

    @Transactional
    public DeploymentResponse createDeployment(CreateDeploymentRequest request) {
        Deployment deployment = new Deployment();
        deployment.setServiceName(request.getServiceName());
        deployment.setEnvironment(request.getEnvironment());
        deployment.setVersion(request.getVersion());
        deployment.setCommitSha(request.getCommitSha());
        deployment.setDeployedAt(request.getDeployedAt());
        deployment.setDeployedBy(request.getDeployedBy());
        deployment.setDeploymentStatus(request.getDeploymentStatus());

        Deployment saved = deploymentRepository.save(deployment);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getRecentDeployments(String serviceName) {
        return deploymentRepository.findTop5ByServiceNameOrderByDeployedAtDesc(serviceName)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Find deployments that happened in a time window around an incident.
     * Used by the RCA correlation engine.
     * 
     * Example: incident at 14:10, look back 30 minutes → find deployments between 13:40 and 14:10
     */
    @Transactional(readOnly = true)
    public List<DeploymentResponse> getDeploymentsInWindow(
            String serviceName, LocalDateTime start, LocalDateTime end) {
        return deploymentRepository.findByServiceNameAndDeployedAtBetween(serviceName, start, end)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> getAllDeployments() {
        return deploymentRepository.findAll().stream().map(this::toResponse).toList();
    }

    private DeploymentResponse toResponse(Deployment d) {
        DeploymentResponse r = new DeploymentResponse();
        r.setId(d.getId());
        r.setServiceName(d.getServiceName());
        r.setEnvironment(d.getEnvironment());
        r.setVersion(d.getVersion());
        r.setCommitSha(d.getCommitSha());
        r.setDeployedAt(d.getDeployedAt());
        r.setDeployedBy(d.getDeployedBy());
        r.setDeploymentStatus(d.getDeploymentStatus());
        r.setCreatedAt(d.getCreatedAt());
        return r;
    }
}
