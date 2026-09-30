# OpsPilot — Complete Interview Preparation Guide

**Study this entire document. This covers every concept you'd be asked about.**

---

## Part 1: Project Overview (Your Elevator Pitch)

### What is OpsPilot?

"OpsPilot is an event-driven incident management platform built with Spring Boot microservices. It detects production failures from telemetry data, correlates them with recent deployments and error patterns, builds a structured evidence context, and sends it to an LLM for root cause analysis. It follows a human-in-the-loop workflow — AI recommends remediation, but a human must approve before any destructive action is executed. After remediation, the system verifies recovery metrics before resolving the incident, then generates a postmortem."

### The Story (Memorize This Flow)

```
Deploy → Failure → Detection → Incident → Evidence Gathering → AI RCA
    → Human Approval → Remediation → Recovery Verification → Postmortem
```

### Why I Built This
"I wanted to demonstrate production engineering concepts — not just CRUD. This project shows how modern SRE teams handle incidents: automated detection, structured investigation, AI-assisted analysis with guardrails, and closed-loop verification."

---

## Part 2: Architecture

### Microservices

| Service | Responsibility |
|---|---|
| **Incident Service** | Creates/manages incidents, stores lifecycle state, exposes REST APIs |
| **Detection Service** | Evaluates telemetry against rules, fires anomaly events |
| **RCA Service** | Gathers evidence, runs correlation, calls AI, stores RCA reports |
| **Remediation Service** | Executes approved actions, verifies recovery |
| **Failure Simulator** | Deliberately breakable app for demos |
| **React Dashboard** | Visualizes the entire lifecycle |

### Why microservices?
"Each service has a single domain responsibility and owns its data. They can be deployed, scaled, and developed independently. For example, if the RCA service is slow, I can scale just that service without scaling everything else."

### Why NOT Kubernetes?
"Azure Container Apps gives us container orchestration without managing a K8s cluster. For a project this size, Kubernetes would add operational complexity without proportional benefit. I documented this as an Architecture Decision Record (ADR-005)."

### Communication Patterns

| Communication | Type | Why |
|---|---|---|
| Dashboard → Services | **Synchronous (REST)** | User expects immediate response |
| Detection → "Anomaly Detected" | **Asynchronous (Events)** | Detection shouldn't wait for incident creation |
| Incident Created → RCA | **Asynchronous** | Creating incident shouldn't block on analysis |
| Remediation Approved → Execute | **Asynchronous** | Approval shouldn't block on execution |

### Why async where possible?
"Asynchronous communication decouples services. If the RCA service is temporarily down, the anomaly event waits in the queue instead of causing the detection service to fail. This improves resilience and allows independent scaling."

---

## Part 3: Technology Decisions

### Why Spring Boot?
"Industry-standard for Java microservices. Built-in dependency injection, auto-configuration, embedded server, and rich ecosystem (Spring Data, Spring Security, Spring Actuator). Reduces boilerplate significantly."

### Why PostgreSQL?
"It's the most capable open-source RDBMS. We need ACID transactions for incident state management (status transitions must be atomic), complex queries for correlation (join incidents with deployments in time windows), and it supports JSON columns for flexible evidence storage."

### Why NOT MongoDB?
"Our data is highly relational — incidents reference deployments, RCA reports reference incidents, evidence references both. Document databases excel at denormalized data; our data model has clear relationships that benefit from referential integrity."

### Why Redis (where justified)?
"Only for specific use cases: caching frequently-read incident data, rate limiting API endpoints, and storing short-lived detection state (like sliding window counters for error rate calculation). Every Redis use case has a clear justification — we don't add it just for the resume."

### Why Azure Container Apps?
"Managed container platform with built-in scaling, ingress, and secrets management. Simpler than managing a Kubernetes cluster. Supports running multiple container apps in a shared environment with internal service discovery."

---

## Part 4: Incident Domain Model

### Incident Entity
```
incidents table:
  id (UUID, PK)          — universally unique, no conflicts across services
  incident_number        — human-readable: INC-1042
  title                  — "Checkout Service Error Rate Increased"
  description            — longer explanation
  service_name           — which service is affected
  environment            — production/staging
  severity               — SEV1-SEV4
  status                 — OPEN/INVESTIGATING/MITIGATING/RESOLVED
  detected_at            — when the anomaly was first detected
  acknowledged_at        — when someone started investigating
  resolved_at            — when recovery was confirmed
  source                 — what triggered it (detection rule or manual)
  correlation_id         — traces the incident across all services
  created_at             — auto-set by Hibernate
  updated_at             — auto-set by Hibernate
```

### Why UUID instead of auto-increment?
"In a microservice architecture, multiple services might generate IDs independently. UUIDs avoid conflicts. Auto-increment also leaks information — if you see INC-1042, you know INC-1043 probably exists."

### Why @Enumerated(EnumType.STRING)?
"Without STRING, JPA stores the enum's ordinal position (0, 1, 2). If you reorder the enum or add a value in the middle, all existing data breaks. STRING stores 'SEV2' as text — safe to modify the enum later."

### State Machine
```
OPEN → INVESTIGATING → MITIGATING → RESOLVED
  ↓         ↑    ↓          ↓
  RESOLVED  └────┘      RESOLVED
```
- MITIGATING → INVESTIGATING (fix didn't work)
- Any active state → RESOLVED (false alarm, manual close)
- RESOLVED is terminal (new incident for regression)

### Why enforce transitions in the service layer?
"The service layer is the single source of truth for business rules. Whether the status change comes from the REST API, an event, or a scheduled job, the same validation runs. If we put it in the controller, event-driven status changes would bypass the rules."

---

## Part 5: REST API Design

### HTTP Methods
| Method | Purpose | Idempotent? |
|---|---|---|
| GET | Read | Yes |
| POST | Create | No |
| PUT | Full replace | Yes |
| PATCH | Partial update | Depends |
| DELETE | Remove | Yes |

### Key Status Codes
| Code | Meaning | Our usage |
|---|---|---|
| 200 | OK | GET, PATCH success |
| 201 | Created | POST success |
| 400 | Bad Request | Validation failure |
| 404 | Not Found | Incident not found |
| 409 | Conflict | Invalid status transition |
| 500 | Server Error | Unexpected failure |

### Why DTOs instead of exposing entities?
"Four reasons: (1) Entities have DB-specific annotations clients don't need. (2) Changing an entity would change the API, creating tight coupling. (3) DTOs let us validate input separately. (4) We control exactly what's exposed — no risk of leaking internal fields."

### Why PATCH for status changes?
"We're updating part of the resource (just the status), not replacing the entire incident. That's the semantic difference between PUT and PATCH."

---

## Part 6: Layered Architecture

```
Controller → Service → Repository → Database
```

### Controller layer
- Receives HTTP request
- Validates input (@Valid)
- Delegates to service
- Returns HTTP response with status code
- **NEVER** contains business logic

### Service layer
- Contains all business logic
- Enforces state machine rules
- Converts between entities and DTOs
- Manages transactions (@Transactional)
- Can be called from controllers, events, or scheduled jobs

### Repository layer
- Data access only
- Extends JpaRepository for free CRUD
- Custom query methods generated from method names
- **NEVER** contains business logic

### Why this separation?
"**Testability.** I can unit-test the service with a mocked repository (no database needed). I can integration-test the repository with a test database (no HTTP needed). I can test the controller with a mocked service. Each layer is independently testable."

---

## Part 7: Event-Driven Architecture

### Core Events
```
TelemetryAnomalyDetected → IncidentCreated → RcaRequested → RcaCompleted
→ RemediationSuggested → RemediationApproved → RemediationExecuted
→ RecoveryVerified → IncidentResolved
```

### Event Structure
```json
{
  "eventId": "uuid",
  "eventType": "INCIDENT_CREATED",
  "timestamp": "2026-09-21T14:10:00Z",
  "correlationId": "corr-uuid",
  "sourceService": "incident-service",
  "payload": { ... }
}
```

### Why correlationId?
"In distributed systems, a single business operation spans multiple services. The correlationId follows the incident from detection through RCA through remediation. When debugging, I can grep all logs for one correlationId and see the entire lifecycle across services."

### Dependency Inversion for messaging
```java
public interface EventPublisher {
    void publish(Event event);
}

// Local dev:
class InMemoryEventPublisher implements EventPublisher { ... }

// Production:
class AzureServiceBusEventPublisher implements EventPublisher { ... }
```
"Business logic depends on the EventPublisher interface, not Azure SDK. I can develop and test locally with an in-memory implementation, then swap to Azure Service Bus via configuration — zero code changes."

### Important concepts

**Idempotency:** Processing the same event twice should produce the same result. Important because messages can be delivered more than once. We store processed event IDs and check before processing.

**Dead-letter queue:** When a message fails processing after retries, it goes to a dead-letter queue instead of being lost. Operators can investigate and reprocess later.

**Eventual consistency:** Services don't share a database, so they can be temporarily inconsistent. After an event propagates and is processed, they become consistent. This is a trade-off for the decoupling benefit.

---

## Part 8: RCA (Root Cause Analysis) — THE KEY DIFFERENTIATOR

### Two-phase approach

**Phase 1: Deterministic Evidence Gathering** (our backend code)
```
Incident occurs for checkout-service
    ↓
Query telemetry: error rate before (0.8%) vs after (18.5%)
    ↓
Query deployments: v1.8.3 deployed 8 minutes before incident
    ↓
Query errors: DatabaseConnectionTimeoutException × 174
    ↓
Query dependencies: Database HEALTHY, Payment API HEALTHY
    ↓
Assemble structured RcaContext
```

**Phase 2: AI Interpretation** (LLM with guardrails)
```
Send structured evidence to AI
    ↓
AI produces structured response:
  - Likely root cause
  - Confidence score (0.87)
  - Supporting evidence
  - Alternative hypotheses
  - Recommended actions
  - Limitations
```

### Why this matters
"Anyone can call ChatGPT. What makes OpsPilot valuable is the structured evidence pipeline. If you feed an LLM raw logs and ask 'what went wrong?', it'll confidently hallucinate a plausible but potentially fabricated root cause. By gathering structured evidence first, the AI's output becomes verifiable — if it mentions a deployment, I can check whether that deployment was actually in the evidence context."

### AI Guardrails
- NEVER invent logs, deployments, or metrics
- ONLY use evidence from the supplied context
- If evidence is insufficient, say so
- Distinguish facts from hypotheses
- Provide confidence scores
- Provide alternative hypotheses

### Why NOT let AI do everything?
"The AI is the analyst, not the detective. Our backend is the detective — it gathers facts. The AI reads the evidence report and offers an interpretation. This keeps AI outputs grounded in reality."

### Correlation ≠ Causation
"A deployment happened 8 minutes before the incident — that's a strong correlation signal, but not proof. Maybe the deployment was fine and the database ran out of disk space simultaneously. We present deployment correlation as evidence, not as an absolute conclusion."

---

## Part 9: Human-in-the-Loop

### Why AI must NOT auto-execute remediation
1. **AI can hallucinate** — wrong remediation could make things worse
2. **Blast radius** — automated rollback without review could cascade
3. **Audit trail** — regulated industries need records of who authorized changes
4. **Engineering judgment** — shows mature architecture thinking

### Workflow
```
AI: "Recommend rollback v1.8.3 → v1.8.2"
    ↓
Dashboard: [Approve] [Reject]
    ↓
Operator reviews evidence, clicks Approve
    ↓
System records: who, when, what action
    ↓
Remediation service executes
```

---

## Part 10: Recovery Verification

### Why not just mark as resolved after remediation?
"A rollback doesn't guarantee recovery. What if the database is also damaged? We verify by checking post-remediation metrics:
- Error rate: 18.5% → 0.7% ✓
- P95 latency: 1900ms → 240ms ✓

Only when metrics return to baseline do we consider the incident resolved. This is closed-loop incident management."

---

## Part 11: Observability

### Three Pillars
| Pillar | What it tells you | Example |
|---|---|---|
| **Metrics** | *What* is happening (numbers) | Error rate: 18.5% |
| **Logs** | *What specifically* happened | DatabaseConnectionTimeoutException at PaymentService.java:142 |
| **Traces** | *Where* in the chain it broke | Request stuck at DB query for 30s |

### How we implement it
- **Spring Boot Actuator** — health checks, metrics endpoints
- **Micrometer** — metrics collection (counters, gauges, timers)
- **Structured logging** — JSON logs with correlationId in every entry
- **Application Insights** — Azure's observability platform (traces, metrics, logs)

---

## Part 12: Docker & Deployment

### Key Docker Concepts
| Concept | Meaning |
|---|---|
| **Image** | Blueprint/template. Immutable. Like a class in Java. |
| **Container** | Running instance of an image. Like an object in Java. |
| **Dockerfile** | Instructions to build an image |
| **Layer** | Each instruction creates a layer. Layers are cached. |
| **docker-compose** | Run multiple containers together with networking |

### Sample Dockerfile
```dockerfile
FROM eclipse-temurin:17-jre-alpine    # Base image with Java
COPY target/*.jar app.jar              # Copy our built JAR
EXPOSE 8081                            # Document the port
ENTRYPOINT ["java", "-jar", "app.jar"] # Start command
```

---

## Part 13: CI/CD

### Continuous Integration vs Delivery vs Deployment
| Term | Meaning |
|---|---|
| **Continuous Integration** | Every push: build, test, verify. Catches bugs early. |
| **Continuous Delivery** | CI + artifact is always deployable. Deploy is manual. |
| **Continuous Deployment** | CI + automatically deployed to production. No manual step. |

### Our GitHub Actions Pipeline
```
Push/PR → Checkout → Setup Java → Maven Build → Unit Tests 
→ Integration Tests → Build Docker Image → Push to ACR → Deploy to Container Apps
```

---

## Part 14: Terraform

### What is Terraform?
"Infrastructure as Code. Instead of clicking through the Azure portal, I define infrastructure in code files. This makes infrastructure reproducible, version-controlled, and reviewable."

### Key Commands
| Command | Does |
|---|---|
| `terraform init` | Download providers, initialize |
| `terraform plan` | Preview changes without applying |
| `terraform apply` | Actually create/modify resources |
| `terraform destroy` | Delete all managed resources |

### Key Concepts
| Concept | Meaning |
|---|---|
| **Provider** | Plugin for a cloud (azurerm, aws, gcp) |
| **Resource** | A thing to create (container app, database, etc.) |
| **State** | Terraform's record of what exists. Critical — never delete it. |
| **Variable** | Parameterize configs (environment, region, etc.) |

---

## Part 15: Security

### Principles
- **Never hardcode secrets** — use environment variables locally, Key Vault in cloud
- **JWT authentication** — stateless auth tokens
- **Role-based access:** VIEWER (read), OPERATOR (approve remediation), ADMIN (configure)
- **Remediation approval requires OPERATOR role** — critical safety constraint

---

## Part 16: Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Repository** | Data access layer | Abstracts database operations |
| **DTO** | API boundary | Decouples API from database schema |
| **Strategy** | Detection rules | Swap detection algorithms without modifying the engine |
| **Adapter** | Cloud integrations | InMemory vs Azure implementations |
| **Dependency Injection** | Everywhere | Testability, loose coupling |
| **State Machine** | Incident lifecycle | Enforces valid status transitions |

---

## Part 17: Important Things to Say in an Interview

### If asked "What was the hardest part?"
"Designing the evidence pipeline before the AI integration. It was tempting to just send everything to the LLM, but that would produce unreliable results. Building the deterministic correlation engine — matching deployment timelines, calculating metric deltas, fingerprinting errors — was the foundation that made the AI analysis trustworthy."

### If asked "What would you improve?"
"Three things: (1) Add anomaly detection using statistical baselines instead of just static thresholds. (2) Implement vector search for historical incident similarity using embeddings. (3) Add integration tests using Testcontainers for full database testing."

### If asked "How does AI add value vs just looking at dashboards?"
"An experienced SRE might take 15 minutes to correlate metrics, check recent deployments, and review error logs. OpsPilot assembles that evidence in seconds and presents a structured hypothesis. The AI doesn't replace the engineer — it accelerates their investigation and makes the reasoning transparent."

### If asked "Why not just use PagerDuty/Datadog?"
"OpsPilot isn't trying to replace those tools — it demonstrates how such tools work internally. Building it from scratch shows understanding of detection engines, correlation algorithms, event-driven systems, and AI integration that using an off-the-shelf tool wouldn't demonstrate."

---

## Quick Reference: Key Annotations

| Annotation | What it does |
|---|---|
| `@SpringBootApplication` | Combines @Configuration + @EnableAutoConfiguration + @ComponentScan |
| `@RestController` | Handles HTTP requests, auto-converts returns to JSON |
| `@Service` | Marks a class as business logic (Spring manages it) |
| `@Repository` | Marks a data access class |
| `@Entity` | Maps a class to a database table |
| `@Id` / `@GeneratedValue` | Primary key, auto-generated |
| `@Column` | Customizes DB column (nullable, unique, length) |
| `@Enumerated(EnumType.STRING)` | Stores enum as text, not ordinal |
| `@Transactional` | Makes the method atomic — all or nothing |
| `@Valid` | Triggers Bean Validation on request body |
| `@NotBlank` / `@NotNull` | Validation constraints |
| `@RestControllerAdvice` | Global exception handler for all controllers |
| `@GetMapping` / `@PostMapping` / `@PatchMapping` | Maps HTTP methods to methods |
| `@PathVariable` | Extracts value from URL path |
| `@RequestBody` | Deserializes JSON body to Java object |
| `@RequestParam` | Extracts query parameter |
