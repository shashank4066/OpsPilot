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

<!-- NEW PHASES WILL BE ADDED BELOW THIS LINE -->
