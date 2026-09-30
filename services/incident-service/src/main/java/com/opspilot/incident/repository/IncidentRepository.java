package com.opspilot.incident.repository;

import com.opspilot.incident.entity.Incident;
import com.opspilot.incident.enums.IncidentStatus;
import com.opspilot.incident.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Incident entity — the data access layer.
 * 
 * WHY IS THIS JUST AN INTERFACE?
 * Spring Data JPA generates the implementation at runtime!
 * By extending JpaRepository, we get for free:
 *   - save(entity)        → INSERT or UPDATE
 *   - findById(id)        → SELECT by primary key
 *   - findAll()           → SELECT all
 *   - deleteById(id)      → DELETE by primary key
 *   - count()             → COUNT rows
 * 
 * CUSTOM QUERIES:
 * Spring Data also generates queries from method names!
 * "findByStatus" → SELECT * FROM incidents WHERE status = ?
 * "findByServiceName" → SELECT * FROM incidents WHERE service_name = ?
 * No SQL writing needed. Spring parses the method name and builds the query.
 * 
 * JpaRepository<Incident, UUID>:
 *   First type = the entity class
 *   Second type = the primary key type (UUID)
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    /** Find all incidents with a given status */
    List<Incident> findByStatus(IncidentStatus status);

    /** Find all incidents for a specific service */
    List<Incident> findByServiceName(String serviceName);

    /** Find by human-readable incident number like "INC-1042" */
    Optional<Incident> findByIncidentNumber(String incidentNumber);

    /** Find all incidents with a given severity */
    List<Incident> findBySeverity(Severity severity);

    /** Find all active (non-resolved) incidents for a service */
    List<Incident> findByServiceNameAndStatusNot(String serviceName, IncidentStatus status);

    /** Count active incidents */
    long countByStatusNot(IncidentStatus status);
}
