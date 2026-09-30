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

## Phase 1 — Repository + First Spring Boot Service

### Spring Boot Basics
- **Spring Boot** = Spring Framework + auto-configuration + embedded server + starter dependencies
- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- Your app runs its own **embedded Tomcat** — no need for a separate server
- `main()` calls `SpringApplication.run()` → Spring starts, scans for components, auto-configures, starts Tomcat

### Layered Architecture
```
Controller (HTTP)  →  Service (Logic)  →  Repository (Database)
```
- **Controller**: Receives HTTP requests, returns responses. Never has business logic or DB queries.
- **Service**: Business logic. Doesn't know about HTTP status codes or request objects.
- **Repository**: Database operations only. No business rules.
- Why: **testability** — each layer can be tested independently.

### Key Annotations
| Annotation | Goes On | Does |
|---|---|---|
| `@SpringBootApplication` | Main class | Combines Configuration + AutoConfig + ComponentScan |
| `@RestController` | Class | Marks as REST controller, auto-converts returns to JSON |
| `@GetMapping("/path")` | Method | Maps GET requests to this method |

### Maven & pom.xml
- **Maven** = build tool + dependency manager (like npm for Java)
- **pom.xml** = declares dependencies, Java version, build plugins, project metadata
- **`spring-boot-starter-*`** = bundled dependency groups (starter-web = Tomcat + MVC + Jackson)
- **Parent POM** (`spring-boot-starter-parent`) manages versions so you don't specify them

### Request Flow (for GET /api/v1/health)
```
Client sends GET request
  → Tomcat receives it
    → DispatcherServlet routes to the right controller method (via @GetMapping)
      → Method executes, returns a Java object
        → Jackson converts it to JSON
          → Tomcat sends HTTP 200 with JSON body
```

### Project Structure
```
services/incident-service/
├── pom.xml                          ← Build config
└── src/main/
    ├── java/com/opspilot/incident/
    │   ├── IncidentServiceApplication.java   ← Entry point
    │   └── controller/
    │       └── HealthController.java         ← REST endpoint
    └── resources/
        └── application.yml                   ← Config (port, DB, logging)
```

### Configuration (application.yml)
- **Server port**: Each microservice gets its own port (8081, 8082, 8083...)
- **H2 database**: In-memory DB for early development (data lost on restart)
- **`hibernate.ddl-auto`**: Controls schema management — `create-drop` for dev, `validate` for prod
- **`show-sql: true`**: Shows SQL queries in console (learning aid, disable in prod)

### Java Syntax Reminders
- Every file needs `package` + `import` + `class` structure
- `public`/`private` = only for class-level things (methods, fields). **Never on local variables.**
- Every statement ends with `;`
- Class names are capitalized (`LocalDateTime`), variables are lowercase (`health`)
- Method declaration: `public ReturnType methodName() { }` — the `{` opens the body
- Variable creation inside method: `Type name = new Type();` — no access modifier

### Git Conventions
- **Conventional Commits**: `feat(scope): description`
  - `feat` = new feature, `fix` = bug fix, `docs` = documentation, `refactor` = restructure
- `.gitignore` prevents committing: compiled classes, IDE files, OS files, secrets, node_modules
- Set `git config` for user.name and user.email (should match GitHub account)

---

## Phase 2 — Domain Model (Entities, DTOs, Validation)

### JPA Entity Mapping
- **@Entity** tells JPA "this class = a database table"
- **@Table(name = "incidents")** overrides the default table name
- **@Id + @GeneratedValue(strategy = GenerationType.UUID)** — auto-generates UUIDs
- **@Column(nullable = false)** — NOT NULL constraint in the database
- **@Enumerated(EnumType.STRING)** — **ALWAYS USE STRING**, never ORDINAL
  - ORDINAL stores position (0, 1, 2) → breaks if you reorder enums
  - STRING stores "SEV2" as text → safe to modify enums later
- **@CreationTimestamp / @UpdateTimestamp** — Hibernate auto-sets these

### Why UUID Over Auto-Increment?
1. No conflicts across microservices (each service generates independently)
2. No information leakage (can't guess INC-1043 after seeing INC-1042)
3. Works across databases and services without coordination

### DTO Pattern
- **Entity** = database representation (JPA annotations, relationships)
- **DTO** = API representation (validation annotations, no JPA coupling)
- **Why separate?** Changing the DB schema shouldn't break the API
- CreateIncidentRequest → what the client sends
- IncidentResponse → what the client receives
- Entity → never exposed to the outside world

### Bean Validation
- `@NotBlank` = not null AND not empty AND not just whitespace
- `@NotNull` = not null (use for non-String types like Severity)
- `@Valid` on controller parameter triggers validation
- Failures → `MethodArgumentNotValidException` → caught by `GlobalExceptionHandler` → 400

### State Machine (Most Important Business Logic)
```
OPEN → INVESTIGATING → MITIGATING → RESOLVED
  ↓         ↑    ↓          ↓
  RESOLVED  └────┘      RESOLVED
```

Valid transitions stored in a `Map<IncidentStatus, Set<IncidentStatus>>`:
- OPEN → [INVESTIGATING, RESOLVED]
- INVESTIGATING → [MITIGATING, RESOLVED, OPEN]
- MITIGATING → [INVESTIGATING, RESOLVED]
- RESOLVED → [] (terminal state — no transitions out)

Invalid transitions throw `InvalidStatusTransitionException` → 409 Conflict.

### Service Layer Design
- Contains ALL business logic
- Enforces state machine rules
- Converts entities ↔ DTOs
- Uses `@Transactional` for atomicity
- **Never depends on HTTP concepts** (no HttpServletRequest, no ResponseEntity)

### Repository Layer
- Extends `JpaRepository<Incident, UUID>` → gets CRUD for free
- Custom queries from method names:
  - `findByStatus(status)` → `WHERE status = ?`
  - `findByServiceNameAndStatusNot(name, status)` → `WHERE service_name = ? AND status != ?`
- No business logic — just data access

### Exception Handling
- `@RestControllerAdvice` = global exception handler for ALL controllers
- `IncidentNotFoundException` → 404
- `InvalidStatusTransitionException` → 409
- `MethodArgumentNotValidException` → 400 with field-level errors
- `Exception` → 500 (safety net)

---

## Phase 3 — Event-Driven Architecture

### Why Events?
- **Decoupling** — incident service doesn't know RCA service exists
- **Resilience** — if RCA service is down, event waits in queue
- **Scalability** — consumers scale independently
- **Auditability** — events are a natural audit trail

### Domain Events
Every significant action publishes an event:
- `INCIDENT_CREATED` — triggers RCA to start
- `INCIDENT_STATUS_CHANGED` — notifies dashboard, triggers workflows
- (Future) `RCA_COMPLETED`, `REMEDIATION_APPROVED`, etc.

### Event Structure
```java
DomainEvent {
    eventId          // unique per event
    eventType        // what happened
    timestamp        // when
    correlationId    // traces across services
    sourceService    // who published
    payload          // event-specific data
}
```

### Dependency Inversion Principle (DIP)
```
IncidentService → EventPublisher (interface)
                      ↑
           ┌──────────┴──────────┐
  InMemoryEventPublisher    AzureServiceBusEventPublisher
```

- Business logic depends on `EventPublisher` interface
- Spring injects the right implementation based on profile/config
- **Zero code changes** needed to switch local ↔ cloud

### Key Concepts
| Concept | Definition |
|---|---|
| **Idempotency** | Processing the same event twice = same result. Check eventId before processing. |
| **Dead-letter queue** | Failed messages go here after retries, not lost. Operators investigate later. |
| **Eventual consistency** | Services temporarily out of sync, converge after event propagation. |
| **At-least-once delivery** | Messages may be delivered more than once → idempotency is essential. |

### InMemoryEventPublisher
- Logs events to console (visible during development)
- Stores events in a thread-safe list (inspectable for testing)
- Proves the event flow works end-to-end without Azure

---

## Phase 4 — Testing, Docker, CI/CD

### Testing Strategy
Two levels of testing:

| Level | Tool | Tests | Speed |
|---|---|---|---|
| **Unit** | JUnit 5 + Mockito + AssertJ | Business logic with mocked deps | Milliseconds |
| **Integration** | Spring Boot Test + MockMvc | Full HTTP → DB stack with H2 | Seconds |

### Unit Testing (IncidentServiceTest)
- **@ExtendWith(MockitoExtension.class)** — enables Mockito annotations
- **@Mock** — creates a fake repository (no database)
- **@Nested** — groups related tests for readability
- **@DisplayName** — human-readable test names
- **Arrange → Act → Assert** pattern for every test
- Key tests:
  - Create incident → status is OPEN, number starts with INC-
  - Valid transitions (OPEN→INVESTIGATING, INVESTIGATING→MITIGATING)
  - Invalid transitions rejected (OPEN→MITIGATING → throws exception)
  - RESOLVED is terminal (no transitions out)
  - Events are published on create and status change

### Integration Testing (IncidentControllerIntegrationTest)
- **@SpringBootTest** — loads full Spring context
- **@AutoConfigureMockMvc** — provides MockMvc (HTTP testing without real server)
- Tests the entire stack: HTTP request → Controller → Service → Repository → H2
- Verifies HTTP status codes (201, 400, 404, 409)
- Verifies JSON response structure

### Docker
```
Dockerfile (multi-stage):
  Stage 1: maven:3.9 → build the JAR
  Stage 2: eclipse-temurin:17-jre-alpine → run the JAR
```
- **Multi-stage** = build tools not in production image (smaller, more secure)
- **Non-root user** = security best practice
- **HEALTHCHECK** = Docker/orchestrator can verify container health
- **Layer caching** = copy pom.xml first, then source → dependencies cached

### Docker Compose
- Runs PostgreSQL + incident-service together
- `depends_on` with health check → service waits for database
- Volumes persist data across restarts
- Environment variables override `application.yml`

### CI/CD with GitHub Actions
```
Push to main → Checkout → Setup JDK 17 → mvn clean verify → Upload results
```
- Runs on every push and PR to `main`
- `mvn clean verify` = build + unit tests + integration tests
- Caches Maven dependencies for faster builds
- Uploads test results as artifacts

---

<!-- NEW PHASES WILL BE ADDED BELOW THIS LINE -->
