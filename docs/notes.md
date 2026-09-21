# OpsPilot — Study Notes

This file is updated after every phase. It contains the key concepts, principles, and patterns you need to remember.

---

## Phase 0 — Understanding OpsPilot

### What Is a Production Incident?
- An **unplanned disruption** to a live system that affects real users.
- Not every bug is an incident — incidents have **urgency**, **impact**, and **time pressure**.
- Classified by severity: **SEV1** (critical/widespread) → **SEV4** (minor/limited).

### Incident Management Lifecycle
```
Detection → Triage → Investigation → Mitigation → Resolution → Postmortem
```
- **Detection:** Something notices the problem (monitoring alerts, user reports).
- **Triage:** Assess severity, blast radius, who needs to respond.
- **Investigation:** What changed? What do metrics/logs/traces show?
- **Mitigation:** Stop the bleeding first — rollback, scale up, disable feature. Goal is to restore service, not find the perfect fix.
- **Resolution:** Confirm the fix worked, verify metrics, close incident.
- **Postmortem:** Blameless write-up — what happened, why, how to prevent it.

### Root Cause Analysis (RCA)
- Determining **why** an incident happened, not just *what* happened.
- Technique: **"5 Whys"** — keep asking why until you reach the systemic cause.
- RCA is hard because:
  - Correlation ≠ causation
  - Multiple contributing factors are common
  - Information is often incomplete
  - You're doing detective work under time pressure

### Three Pillars of Observability

| Pillar | What it tells you | Analogy |
|---|---|---|
| **Metrics** | *What* is happening (numbers over time) | Vital signs monitor |
| **Logs** | *What specifically* happened (detailed event records) | Doctor's notes |
| **Traces** | *Where* it broke (request journey across services) | Package tracking |

- **Monitoring** = watching known failure modes with predefined rules
- **Observability** = ability to understand *any* internal state from outputs, even unpredicted failures

### Detection Types
| Type | Example |
|---|---|
| Threshold-based | Error rate > 5% |
| Anomaly-based | Latency 10x higher than baseline |
| Heartbeat/Health check | `/health` returns 503 |
| Log-based | "OutOfMemoryError" appears 50 times/min |
| Synthetic monitoring | Automated checkout test fails |

OpsPilot starts with **threshold-based** (simplest to implement).

### Why Deployments Matter for RCA
- **Most common cause of production incidents = a change was deployed.**
- Temporal correlation (deployment at 14:02, incident at 14:10) is a strong signal.
- But **correlation ≠ causation** — treat deployments as *evidence*, not proof.
- Common deployment failure causes: code bugs, config regressions, dependency mismatches, resource changes, migration issues.

### OpsPilot's Core Story
```
Deploy → Failure → Detection → Incident → Correlation → AI RCA
    → Human Approval → Remediation → Recovery → Postmortem
```
Everything we build serves this story.

### OpsPilot's AI Philosophy
- AI is the **analyst**, not the **detective**.
- Backend gathers structured evidence first (metrics, deployments, errors, dependencies).
- AI receives curated evidence and produces a structured interpretation.
- AI must **never** invent logs, deployments, or metrics.
- Structured input makes AI output **verifiable**.

### Incident State Machine
```
OPEN → INVESTIGATING → MITIGATING → RESOLVED
```
Key nuances:
- Not purely linear — MITIGATING can go back to INVESTIGATING (fix didn't work).
- Any active state can go to RESOLVED (false alarm, manual close).
- RESOLVED incidents don't reopen — a new incident is created instead.
- OPEN → INVESTIGATING: automatic (anomaly detected)
- INVESTIGATING → MITIGATING: human-triggered (operator approves remediation)
- MITIGATING → RESOLVED: triggered by recovery verification

### Component Responsibilities

| Component | Owns | Responsibility |
|---|---|---|
| Incident Service | incidents, incident_events | Create, track, manage incident lifecycle |
| Detection Service | rules, telemetry, anomalies | Evaluate telemetry against rules, fire anomalies |
| RCA Service | rca_reports, rca_evidence | Gather evidence, correlate, call AI |
| Remediation Service | remediations, audit logs | Execute approved fixes, verify recovery |
| Failure Simulator | simulated metrics, failure state | Create controlled failures for demos |
| React Dashboard | — | Visualize the lifecycle, enable human decisions |

**Data sovereignty:** Each service owns its data. Services communicate through APIs or events, not by reaching into each other's databases.

### Synchronous vs. Asynchronous Communication

| Sync (HTTP/REST) | Async (Events/Messages) |
|---|---|
| Caller waits for response | Caller publishes and moves on |
| Simple to debug | More complex (eventual consistency) |
| Tight coupling (caller fails if target is down) | Loose coupling (message waits in queue) |
| Good for: user-facing APIs, read queries | Good for: fire-and-forget, long-running ops |

**Strategy:** Start synchronous (simpler), evolve to async where appropriate (Phase 6).

### Dependency Inversion Principle (DIP)
- Business logic depends on **abstractions** (interfaces), not concrete implementations.
- Enables swapping local ↔ cloud implementations without changing business logic.
- Applied to: event publishing, AI client, storage, secrets.

```
Business Logic → Interface (abstraction)
                     ↑
          ┌──────────┴──────────┐
     LocalImpl            AzureImpl
```

### Key Substitutions for Local Development

| Azure Service | Local Substitute |
|---|---|
| Azure Service Bus | `InMemoryEventPublisher` |
| Azure OpenAI | `MockRcaAiClient` |
| Azure Blob Storage | `LocalPostmortemStorage` (filesystem) |
| Azure Key Vault | Environment variables / `.env` |
| Azure Container Apps | `docker-compose` |

### Five Things to Remember Before Coding
1. **Start with the domain**, not the technology.
2. **Correlation engine > AI** — structured evidence pipeline is more important than the LLM call.
3. **Local-first development** — everything runs without Azure.
4. **Human-in-the-loop is a feature**, not a limitation.
5. **The demo scenario IS the specification** — every feature serves the incident story.

---

<!-- NEW PHASES WILL BE ADDED BELOW THIS LINE -->
