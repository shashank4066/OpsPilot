package com.opspilot.incident.repository;

import com.opspilot.incident.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for deployment data.
 * Key query: find recent deployments for a service around the time of an incident.
 */
@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    /** Find all deployments for a service, ordered by most recent first */
    List<Deployment> findByServiceNameOrderByDeployedAtDesc(String serviceName);

    /** Find deployments for a service within a time window — critical for RCA correlation */
    List<Deployment> findByServiceNameAndDeployedAtBetween(
            String serviceName, LocalDateTime start, LocalDateTime end);

    /** Find the most recent deployment for a service */
    List<Deployment> findTop5ByServiceNameOrderByDeployedAtDesc(String serviceName);
}
