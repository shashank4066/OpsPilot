package com.opspilot.incident.enums;

/**
 * Severity levels for incidents, following industry-standard SEV levels.
 * 
 * SEV1 = Critical: Complete service outage, data loss, security breach
 * SEV2 = Major: Significant degradation, affecting many users
 * SEV3 = Moderate: Partial impact, workaround available
 * SEV4 = Minor: Low impact, cosmetic or non-urgent
 * 
 * Severity determines response urgency and escalation.
 * In real companies: SEV1 pages the on-call engineer at 3 AM.
 * SEV4 goes into the backlog for next sprint.
 */
public enum Severity {
    SEV1,  // Critical
    SEV2,  // Major
    SEV3,  // Moderate
    SEV4   // Minor
}
