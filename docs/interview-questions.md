# OpsPilot — Interview Questions & Answers

This file is updated after every phase. Use it to prepare for interviews.

---

## Phase 0 — Understanding OpsPilot

### Q1: Why does OpsPilot gather structured evidence *before* calling the AI, instead of just sending raw logs to the LLM?

**Answer:**
Sending raw logs directly feeds the AI incomplete, unstructured data. The LLM might hallucinate — it could invent log entries, reference deployments that never happened, or confidently produce an incorrect root cause.

By gathering structured evidence first (metrics, deployments, error fingerprints, dependency health), we achieve two things:
1. The AI reasons from **verified facts**, not noise.
2. The AI's output becomes **verifiable** — if the AI references a deployment, we can check whether we actually provided that data. Without structured input, we can't distinguish real reasoning from hallucination.

**Interview tip:** This is OpsPilot's core design differentiator. Lead with this when explaining the architecture.

---

### Q2: A deployment happened 3 minutes before an error spike. Does the deployment *definitely* cause the error spike?

**Answer:**
No. **Correlation does not equal causation.** The deployment is a strong correlation signal due to temporal proximity, but it's not proof. The database could have run out of disk space at the same time by coincidence, or a traffic spike could have hit independently.

Our system treats deployments as **evidence with a confidence score**, not as absolute proof. The RCA engine presents it as: "Recent deployment is a strong correlation candidate" — the AI and the human operator make the final judgment.

**Interview tip:** Interviewers love hearing candidates say "correlation doesn't equal causation" — it shows mature engineering thinking.

---

### Q3: Name the three pillars of observability and explain what each one tells you.

**Answer:**

| Pillar | What it tells you |
|---|---|
| **Metrics** | *What* is happening at a high level — numerical measurements over time (error rate, latency, CPU usage). Cheap to store, fast to query. Powers dashboards and alerts. |
| **Logs** | *What specifically* happened — timestamped records of discrete events with detail (exception stack traces, error messages, request IDs). |
| **Traces** | *Where* in the chain a request failed — follows a single request's journey across multiple services in a distributed system. |

**How they work together:**
- Metrics tell you something is wrong ("error rate is 18%")
- Logs tell you what the error is ("DatabaseConnectionTimeoutException")
- Traces tell you where it broke ("request stuck at DB query for 30s")

**Bonus distinction:** *Monitoring* is watching predefined metrics and alerting on known failures. *Observability* is the ability to understand any internal state by examining outputs — even failure modes you didn't predict.

---

### Q4: Why is requiring human approval for remediation a *better* design than letting AI automatically execute rollbacks?

**Answer:**
Several reasons:

1. **Safety** — AI can hallucinate. A wrong remediation (e.g., rolling back the wrong service) could make things worse. A human reviews the evidence before authorizing a destructive action.
2. **Blast radius** — Automatic rollbacks without verification could cascade into more failures.
3. **Compliance & audit** — In regulated industries, you need a record of *who* authorized a production change and *when*. Human-in-the-loop provides this audit trail.
4. **Architectural maturity** — Interviewers want to see that you think about safety and don't blindly automate everything. Saying "I deliberately chose human approval" shows engineering judgment.

**Interview tip:** Frame this as a deliberate design decision, not a limitation. "I designed the system to require human approval for destructive actions because autonomous remediation in production is a safety risk."

---

### Q5: We use `InMemoryEventPublisher` locally instead of Azure Service Bus. What software design principle makes this swap possible?

**Answer:**
The **Dependency Inversion Principle (DIP)** — one of the SOLID principles.

Business logic depends on an **abstraction** (the `EventPublisher` interface), not a concrete implementation. Spring injects the right implementation based on the active profile (`dev` → `InMemoryEventPublisher`, `prod` → `AzureServiceBusEventPublisher`).

```java
public interface EventPublisher {
    void publish(Event event);
}
```

This means:
- We can build and test the entire project locally without Azure.
- Swapping to real Azure services later requires zero changes to business logic.
- Each implementation can be tested independently.

**Interview tip:** If asked "How did you handle the local-to-cloud transition?", explain dependency inversion and Spring profiles. This pattern applies to the AI client, storage, and event bus.

---

## Phase 1 — Repository + First Spring Boot Service

### Q1: What does `@SpringBootApplication` do?

**Answer:**
It's a shortcut that combines three annotations:
1. **`@Configuration`** — marks the class as a source of Spring bean definitions
2. **`@EnableAutoConfiguration`** — tells Spring Boot to auto-configure based on dependencies (e.g., sees PostgreSQL driver → configures a DataSource)
3. **`@ComponentScan`** — scans the current package and sub-packages for classes annotated with `@Controller`, `@Service`, `@Repository`, etc. and registers them as Spring-managed beans

**Interview tip:** Don't just say "it starts Spring Boot." Name the three annotations it combines — that shows deeper understanding.

---

### Q2: Why shouldn't a Controller contain database queries directly?

**Answer:**
**Separation of concerns.** Each layer has one responsibility:
- **Controller** → handles HTTP (request parsing, response formatting)
- **Service** → contains business logic
- **Repository** → talks to the database

If the Controller had database queries:
- You couldn't **test** business logic without spinning up a web server
- You couldn't **change your database** without editing your API layer
- You couldn't **reuse** business logic in other contexts (e.g., event handlers)

**Interview tip:** The key word is **testability**. Separation makes each layer independently testable.

---

### Q3: Our health endpoint returns a Java Map, but the client receives JSON. What converts it?

**Answer:**
**Jackson** — a JSON serialization library that Spring Boot includes automatically via `spring-boot-starter-web`. When `@RestController` is used, Spring adds `@ResponseBody` behavior, which tells Spring to pass the return value through Jackson to convert it to JSON before sending the HTTP response.

You never write JSON conversion code yourself — Spring handles it automatically based on the annotations.

---

### Q4: Why did we put incident-service on port 8081?

**Answer:**
Because we're building **multiple microservices** that will all run on the same machine during local development. Each needs its own port:
- incident-service: **8081**
- detection-service: **8082**
- rca-service: **8083**

If they all used the default 8080, only one could run at a time.

---

### Q5: What is `pom.xml` and what does it define?

**Answer:**
`pom.xml` (Project Object Model) is Maven's build configuration file — like `package.json` for Node.js. It defines:
1. **Project coordinates** — groupId, artifactId, version (uniquely identifies the project)
2. **Dependencies** — libraries the project needs (Spring Web, JPA, PostgreSQL driver, etc.)
3. **Java version** — which Java version to compile for
4. **Build plugins** — how to compile, test, and package the application
5. **Parent POM** — `spring-boot-starter-parent` provides sensible defaults for all Spring Boot projects

---

## Phase 2 — Domain Model (Entities, DTOs, Validation)

### Q1: Why did you use UUID as the primary key instead of auto-increment?

**Answer:**
In a microservice architecture, different services might generate IDs independently. UUIDs avoid conflicts across services — two services can't accidentally generate the same ID. Auto-increment also leaks information: if an attacker sees INC-1042, they know INC-1043 probably exists. UUIDs are unpredictable and universally unique.

---

### Q2: What is `@Enumerated(EnumType.STRING)` and why is it critical?

**Answer:**
Without `EnumType.STRING`, JPA stores the enum's ordinal position (0, 1, 2). If you reorder the enum or add a value in the middle, **all existing data breaks** — what was stored as `1` now maps to a different value. `STRING` stores `"SEV2"` as text in the database, so it's safe to modify the enum later.

**Interview tip:** Always say STRING, never use ORDINAL. This is a common production bug that junior developers cause.

---

### Q3: Why use DTOs instead of exposing entities directly?

**Answer:**
Four reasons:
1. **Decoupling** — changing the entity doesn't break the API
2. **Security** — prevent leaking internal fields (like JPA version columns)
3. **Validation** — different operations need different validation (create vs update)
4. **Control** — expose exactly what clients need, nothing more

**Interview tip:** This shows understanding of API design maturity. Many tutorials skip DTOs — mentioning them shows production thinking.

---

### Q4: Why is `@Transactional` important on the `updateStatus` method?

**Answer:**
`@Transactional` makes the entire method atomic — all database operations succeed or all roll back. If we update the status but then fail to save the timestamp, `@Transactional` ensures the database reverts to its previous state. Without it, we could end up with an inconsistent state: status changed but timestamp not set.

`@Transactional(readOnly = true)` on read methods is a performance hint — Hibernate skips dirty-checking because it knows nothing will change.

---

### Q5: Why validate status transitions in the service layer and not the controller?

**Answer:**
Because the service layer is the **single source of truth** for business logic. Status changes can come from REST APIs, event handlers, or scheduled jobs. If validation was in the controller, event-driven status changes would **bypass the rules entirely**. By putting it in the service, the same rules apply regardless of the entry point.

---

## Phase 3 — Event-Driven Architecture

### Q1: What is the Dependency Inversion Principle and how did you apply it?

**Answer:**
Business logic depends on **abstractions** (interfaces), not concrete implementations. We created an `EventPublisher` interface that the `IncidentService` depends on. In development, Spring injects `InMemoryEventPublisher`. In production, it would inject `AzureServiceBusEventPublisher`. **Zero business logic changes** needed when switching — just configuration.

```java
public interface EventPublisher {
    void publish(DomainEvent event);
}
```

This same pattern applies to the AI client (`MockRcaAiClient` vs `AzureOpenAiClient`) and storage.

---

### Q2: Why does every event carry a `correlationId`?

**Answer:**
In distributed systems, a single business operation (like handling an incident) spans multiple services. The correlationId follows the incident from detection → RCA → remediation. When debugging, I can search all service logs with one correlationId and reconstruct the **entire lifecycle** across services. Without it, debugging distributed systems is nearly impossible.

---

### Q3: What is idempotency and why does it matter for events?

**Answer:**
Idempotency means processing the same event twice produces the **same result**. It matters because message brokers can deliver messages more than once (at-least-once delivery). Without idempotency, reprocessing an `INCIDENT_CREATED` event could create duplicate incidents. We handle it by storing processed event IDs and checking before processing.

---

### Q4: What is a dead-letter queue?

**Answer:**
When a message fails processing after all retry attempts, it goes to a dead-letter queue instead of being **discarded or blocking the queue**. This prevents message loss and lets operators investigate failures later. Example: if the RCA service can't parse an event, after 3 retries it goes to the dead-letter queue. An alert notifies the team.

---

### Q5: What is eventual consistency and when is it acceptable?

**Answer:**
In microservices with async messaging, services can be **temporarily inconsistent**. When an incident is created, the RCA service doesn't know about it until it processes the event (maybe 100ms later). We accept this brief inconsistency because:
- The decoupling benefit outweighs the consistency cost
- The inconsistency window is very small
- The data eventually converges to a consistent state

It would NOT be acceptable for things like financial transactions where strong consistency is required.

---

## Phase 4 — Testing, Docker, CI/CD

### Q1: Explain your testing strategy.

**Answer:**
Two levels:
1. **Unit tests** — test business logic in isolation with mocked dependencies (Mockito). The `IncidentServiceTest` mocks the repository and event publisher. Tests run in milliseconds, no database needed.
2. **Integration tests** — test the full stack with MockMvc. The `IncidentControllerIntegrationTest` boots the real Spring context with H2 and tests HTTP request → controller → service → repository → database.

**Interview tip:** The state machine tests are the most valuable — they prove invalid transitions are correctly rejected.

---

### Q2: Why mock the repository in unit tests?

**Answer:**
Unit tests should test **one thing** — business logic. If we use a real database:
- Tests become slow (database setup/teardown)
- Failures might be caused by database issues, not logic bugs
- We can't run tests without infrastructure

By mocking the repository, we test **only** the service logic. Repository behavior is separately verified in integration tests.

---

### Q3: Explain your Dockerfile. Why multi-stage?

**Answer:**
Two stages:
1. **Build stage** — has Maven + full JDK (~500MB). Compiles source code and packages the JAR.
2. **Run stage** — has only JRE Alpine (~100MB). Copies the built JAR from stage 1.

The final image is lean and secure — no build tools, no source code, no compiler. We also run as a non-root user for security and include a HEALTHCHECK for container orchestration.

---

### Q4: What does your CI/CD pipeline do?

**Answer:**
On every push to `main`, GitHub Actions:
1. Checks out the code
2. Sets up JDK 17
3. Runs `mvn clean verify` — which compiles, runs unit tests, and runs integration tests
4. Uploads test results as artifacts

If any test fails, the pipeline **blocks the push**. This is Continuous Integration — catching bugs before they reach production.

---

### Q5: What's the difference between `mvn test` and `mvn verify`?

**Answer:**
- `mvn test` — runs unit tests only (Surefire plugin)
- `mvn verify` — runs unit tests AND integration tests (Failsafe plugin), plus any verification checks

In our CI pipeline, we use `verify` to ensure both unit and integration tests pass.

---

<!-- NEW PHASES WILL BE ADDED BELOW THIS LINE -->
