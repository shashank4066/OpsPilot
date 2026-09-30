package com.opspilot.incident.enums;

/**
 * IncidentStatus represents the lifecycle states of an incident.
 * 
 * State Machine:
 *   OPEN → INVESTIGATING → MITIGATING → RESOLVED
 * 
 * Valid transitions:
 *   OPEN → INVESTIGATING    (RCA begins gathering evidence)
 *   OPEN → RESOLVED         (false alarm, duplicate, or auto-resolved)
 *   INVESTIGATING → MITIGATING  (remediation approved and being applied)
 *   INVESTIGATING → RESOLVED    (issue resolved during investigation)
 *   MITIGATING → INVESTIGATING  (remediation didn't work, back to investigating)
 *   MITIGATING → RESOLVED       (recovery verified, incident done)
 * 
 * Why an enum? Because status values are fixed and known at compile time.
 * Using strings like "open" or "Open" would lead to bugs from typos.
 */
public enum IncidentStatus {
    OPEN,           // Just detected, no one investigating yet
    INVESTIGATING,  // RCA service is gathering evidence
    MITIGATING,     // Remediation approved and being applied
    RESOLVED        // Recovery verified, incident closed
}
