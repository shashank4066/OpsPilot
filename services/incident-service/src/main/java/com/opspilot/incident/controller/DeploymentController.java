package com.opspilot.incident.controller;

import com.opspilot.incident.dto.CreateDeploymentRequest;
import com.opspilot.incident.dto.DeploymentResponse;
import com.opspilot.incident.service.DeploymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for deployment tracking.
 * Records deployments that the RCA engine later correlates with incidents.
 */
@RestController
@RequestMapping("/api/v1/deployments")
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    @PostMapping
    public ResponseEntity<DeploymentResponse> createDeployment(
            @Valid @RequestBody CreateDeploymentRequest request) {
        DeploymentResponse response = deploymentService.createDeployment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<DeploymentResponse>> getAllDeployments() {
        return ResponseEntity.ok(deploymentService.getAllDeployments());
    }

    @GetMapping("/service/{serviceName}/recent")
    public ResponseEntity<List<DeploymentResponse>> getRecentDeployments(
            @PathVariable String serviceName) {
        return ResponseEntity.ok(deploymentService.getRecentDeployments(serviceName));
    }
}
