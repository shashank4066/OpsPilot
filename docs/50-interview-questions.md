# OpsPilot — 50 Interview Questions & Answers

Practice these. For each question, try answering from memory before reading the answer.

---

## Architecture & Design (15 questions)

### 1. Walk me through the architecture of OpsPilot.
"OpsPilot has 4 core microservices: Incident Service manages incident lifecycle, Detection Service evaluates telemetry against rules, RCA Service gathers evidence and calls the AI, and Remediation Service executes approved actions. They communicate through events for decoupling and REST APIs for synchronous reads. The frontend is a React dashboard for operators."

### 2. Why microservices instead of a monolith?
"Each service has a distinct domain boundary and can be deployed independently. The RCA service might need more compute resources or different scaling rules than the incident service. However, I started with a modular approach and only split into services where there was a genuine boundary — not for the sake of having more services."

### 3. How do your services communicate?
"Two ways: synchronous REST APIs for read queries where the caller needs data immediately (dashboard fetching incidents), and asynchronous events for fire-and-forget operations (incident created → trigger RCA). Async communication means if the RCA service is down, the incident still gets created and the message waits in the queue."

### 4. What is a correlationId and why is it important?
"A unique identifier that follows a business operation across all services. When incident INC-1042 is detected, all related events — detection, RCA, remediation — carry the same correlationId. I can search all service logs with that one ID and reconstruct the entire lifecycle. Without it, debugging across services is nearly impossible."

### 5. Why did you choose PostgreSQL over MongoDB?
"Our data is relational — incidents reference deployments, RCA reports reference incidents, evidence links to both. PostgreSQL gives us ACID transactions for status changes (critical for state machine integrity), foreign keys for data consistency, and efficient joins for correlation queries. MongoDB would require denormalization and lose referential integrity."

### 6. Explain your event-driven design.
"Services publish events when significant things happen: IncidentCreated, RcaCompleted, RemediationApproved. Consumers subscribe and react. Locally, I use an InMemoryEventPublisher. In production, Azure Service Bus. This decouples services — the incident service doesn't know the RCA service exists, it just publishes an event."

### 7. What is idempotency and where do you handle it?
"Idempotency means processing the same request or event twice produces the same result. It's critical because network retries and message redelivery can cause duplicates. For events, I check if we've already processed an eventId before acting. For API calls, creating an incident with the same correlationId returns the existing one instead of creating a duplicate."

### 8. What's a dead-letter queue?
"When a message fails processing after all retry attempts, it goes to a dead-letter queue instead of being discarded. This prevents message loss and lets operators investigate failures later. Example: if the RCA service can't parse an event, after 3 retries it goes to the dead-letter queue. An alert notifies the team."

### 9. What is eventual consistency?
"In microservices with async messaging, services are temporarily inconsistent after an event. When an incident is created, the RCA service doesn't know about it yet until it processes the event (maybe 100ms later). We accept this brief inconsistency in exchange for service decoupling and resilience."

### 10. How do you handle the local-to-cloud transition?
"Dependency Inversion Principle. Business logic depends on interfaces like EventPublisher, RcaAiClient, PostmortemStorage. I have local implementations (InMemory, Mock, LocalFile) and Azure implementations. Switching is a configuration change via Spring profiles — zero business logic changes. This proves the abstraction works."

### 11. Why Azure Container Apps instead of Kubernetes?
"Container Apps is a managed platform with built-in scaling, ingress, and secrets management. For this project, managing a Kubernetes cluster would add operational complexity without proportional benefit. Container Apps abstracts away node management, networking, and ingress controllers. I can always migrate to AKS if I need more control."

### 12. Explain your API versioning strategy.
"URL-based versioning: /api/v1/incidents. Simple, explicit, and cache-friendly. When I need breaking changes, I create /api/v2/incidents while keeping v1 running. This lets clients migrate on their schedule."

### 13. Why do you use DTOs instead of exposing entities?
"Decoupling. If I expose the JPA entity directly, changing the database schema changes the API — breaking all clients. DTOs let me evolve the database and API independently. They also prevent leaking internal fields and let me validate input differently for create vs. update operations."

### 14. Explain the layered architecture.
"Controller handles HTTP concerns, Service contains business logic, Repository handles data access. Each layer has a single responsibility. The key benefit is testability — I can unit-test business logic by mocking the repository, without needing an HTTP server or database."

### 15. How do you handle database migrations?
"Using Flyway — versioned SQL migration files (V1__create_incidents.sql, V2__add_deployments.sql). Flyway runs them automatically on startup, in order, and tracks which ones have been applied. This ensures every environment has the same schema and changes are reproducible."

---

## RCA & AI (10 questions)

### 16. How does your root cause analysis work?
"Two phases. First, the backend deterministically gathers evidence: metric deltas (error rate before vs after), recent deployments (within 30 minutes), error fingerprints (top exceptions), and dependency health. Second, this structured evidence is sent to the AI, which produces a structured hypothesis with confidence score, supporting evidence, alternatives, and recommended actions."

### 17. Why do you gather evidence BEFORE calling the AI?
"If I send raw logs to an LLM, it will confidently hallucinate — inventing deployments or metrics that don't exist. By gathering structured evidence first, the AI's output becomes verifiable. If it says 'deployment v1.8.3 caused this', I can check whether v1.8.3 was actually in the evidence. Without structured input, I can't distinguish real reasoning from hallucination."

### 18. What are the AI guardrails?
"The AI is explicitly instructed to: never invent data, only use supplied evidence, state when evidence is insufficient, distinguish facts from hypotheses, provide confidence scores, suggest alternatives, and explain limitations. The prompt engineering is critical — we constrain the output format to prevent free-form hallucination."

### 19. What is the RcaContext?
"A structured object containing all evidence for one incident: the incident details, telemetry before and after, recent deployments, error fingerprints, and dependency health status. This is what gets sent to the AI. Think of it as the case file a detective hands to an analyst."

### 20. How does deployment correlation work?
"When an incident is created for checkout-service, the RCA engine queries recent deployments: 'Were there any deployments to checkout-service in the last 30 minutes?' If v1.8.3 was deployed 8 minutes before the incident, that's a strong temporal correlation. But I treat it as evidence, not proof — correlation doesn't equal causation."

### 21. How do you handle AI provider unavailability?
"Resilience4j circuit breaker. If the AI provider times out or returns errors 3 times in a row, the circuit opens and we fall back to a MockRcaAiClient that returns the raw evidence without AI interpretation. The incident still gets investigated, just without the AI summary. The circuit automatically tries the real provider again after a configured wait time."

### 22. What is prompt engineering in this context?
"Crafting the system prompt and context to get reliable, structured output from the LLM. I define the output JSON schema, instruct the AI to only use supplied evidence, set temperature low (0.2) for consistency, and include few-shot examples. The prompt is as important as the code."

### 23. What's the difference between your correlation engine and the AI?
"The correlation engine is deterministic code that gathers and scores evidence — it calculates metric deltas, finds temporal deployment matches, fingerprints errors. The AI is an interpreter that reads this evidence and produces a human-readable explanation. The correlation engine is the detective; the AI is the analyst writing the report."

### 24. Why not use a vector database for historical incidents?
"We started with PostgreSQL text search for finding similar past incidents — matching on service name, error type, and severity. Vector search with embeddings would be more powerful for semantic similarity, but it adds infrastructure complexity. I'd introduce it only when PostgreSQL search proves insufficient, and I'd document that decision in an ADR."

### 25. How do you ensure AI doesn't make things up?
"Three mechanisms: (1) Structured input — AI only sees evidence we explicitly provide. (2) Structured output — AI must fill a JSON schema, not write free-form text. (3) Verification — the dashboard shows the evidence alongside the AI's interpretation, so operators can validate the reasoning."

---

## Incident Management (8 questions)

### 26. What is a production incident?
"An unplanned disruption to a live system that affects real users. Not every bug is an incident — incidents have urgency, impact, and time pressure. A misaligned button is a bug for the backlog. Checkout returning 500 errors is an incident requiring immediate response."

### 27. Explain the incident lifecycle.
"Detection → Triage → Investigation → Mitigation → Resolution → Postmortem. Detection identifies the problem (automated alert or user report). Investigation determines the cause. Mitigation stops the bleeding (rollback, not perfect fix). Resolution confirms recovery. Postmortem documents lessons learned — blameless, focused on systemic improvements."

### 28. What are SEV levels?
"SEV1: Critical — complete outage, data loss. SEV2: Major — significant degradation, many users affected. SEV3: Moderate — partial impact, workaround exists. SEV4: Minor — cosmetic, non-urgent. Severity determines response urgency — SEV1 pages engineers at 3 AM."

### 29. Explain your incident state machine.
"Four states: OPEN → INVESTIGATING → MITIGATING → RESOLVED. Transitions are validated in the service layer. MITIGATING can go back to INVESTIGATING if the fix didn't work. Any active state can go to RESOLVED for false alarms. RESOLVED is terminal — a new incident is created for regressions, not a reopen, because reopening would mess up the timeline and metrics."

### 30. Why validate status transitions in the service layer?
"Because the same validation must apply regardless of how the status change is triggered — REST API, event handler, scheduled job. If validation was in the controller, event-driven status changes would bypass it."

### 31. What is a postmortem?
"A blameless document written after an incident is resolved. Contains: summary, timeline, root cause, evidence, what went well, what went wrong, and preventive actions. The goal is organizational learning, not assigning blame. 'Engineer X made a mistake' is not a root cause — 'Our CI pipeline lacked configuration validation' is."

### 32. What is Mean Time to Resolution (MTTR)?
"The average time from incident detection to resolution. It's a key SRE metric. OpsPilot calculates it from detectedAt to resolvedAt. Lower MTTR means faster recovery, which means less user impact."

### 33. Why is recovery verification important?
"A rollback doesn't guarantee recovery. What if the database was also damaged? After remediation, we check: error rate dropped from 18.5% to 0.7%? Latency dropped from 1900ms to 240ms? Only when metrics return to baseline do we resolve. This is closed-loop incident management — it proves the fix actually worked."

---

## Spring Boot & Java (8 questions)

### 34. What does @SpringBootApplication do?
"Combines three annotations: @Configuration (defines Spring beans), @EnableAutoConfiguration (configures based on classpath — sees PostgreSQL driver, auto-configures DataSource), @ComponentScan (scans package for @Controller, @Service, @Repository classes)."

### 35. Explain @Transactional.
"Makes a method atomic — all database operations within it either all succeed or all roll back. If we update an incident's status but then fail to save the timestamp, the entire operation rolls back, keeping the database consistent. readOnly=true hints to Hibernate not to check for changes, improving read performance."

### 36. How does Spring Data JPA generate queries?
"From method names. 'findByServiceName(String name)' becomes 'SELECT * FROM incidents WHERE service_name = ?'. 'findByStatusAndSeverity(status, severity)' becomes 'SELECT * FROM incidents WHERE status = ? AND severity = ?'. Spring parses the method name at startup and generates the implementation."

### 37. What is constructor injection and why prefer it?
"Injecting dependencies via the constructor instead of @Autowired on fields. Benefits: (1) Dependencies are explicit — you see them in the constructor. (2) The class can't be instantiated without them — prevents NullPointerException. (3) Easily testable — just pass mocks in a test, no Spring context needed."

### 38. What is @RestControllerAdvice?
"A global exception handler that intercepts exceptions from all controllers. Instead of try-catch blocks in every method, one class handles all errors: IncidentNotFoundException → 404, InvalidStatusTransitionException → 409, validation errors → 400. Keeps controller code clean."

### 39. What is Bean Validation?
"Declarative input validation using annotations: @NotBlank, @NotNull, @Size, @Email. When @Valid is on a request parameter, Spring automatically validates before calling the method. Failures throw MethodArgumentNotValidException, which our GlobalExceptionHandler catches and returns a structured 400 response."

### 40. Explain Spring profiles.
"Configuration sets for different environments. application-dev.yml for local development (H2 database, mock AI), application-prod.yml for production (PostgreSQL, Azure OpenAI). Activate with spring.profiles.active=dev. This is how we swap InMemoryEventPublisher for AzureServiceBusEventPublisher — same interface, different implementation per profile."

### 41. What is Spring Boot Actuator?
"Production-ready features: /actuator/health (service health check), /actuator/metrics (application metrics), /actuator/info (build info). We use it for monitoring OpsPilot itself — our incident management platform is itself observable."

---

## DevOps & Cloud (9 questions)

### 42. Explain Docker image vs container.
"An image is a blueprint — immutable, like a Java class. A container is a running instance — like a Java object. You build an image from a Dockerfile. You run containers from that image. You can run multiple containers from the same image."

### 43. What is Docker Compose?
"A tool for defining and running multi-container applications. One YAML file describes all services (incident-service, database, frontend), their ports, networks, volumes, and environment variables. 'docker-compose up' starts everything. Locally, it replaces cloud infrastructure."

### 44. Explain CI vs CD.
"Continuous Integration: every push triggers build + test, catching bugs early. Continuous Delivery: CI + the artifact is always deployable (deploy is manual button). Continuous Deployment: CI + automatically deployed to production with no manual step. We use Continuous Delivery — human approves production deployment."

### 45. What does Terraform do?
"Infrastructure as Code. I define Azure resources in .tf files: resource groups, container apps, databases, service bus. 'terraform plan' shows what will change. 'terraform apply' creates it. Everything is version-controlled, reviewable, and reproducible. No clicking through Azure portal."

### 46. What is Terraform state?
"Terraform's record of what infrastructure exists. It maps .tf files to real Azure resources. Without state, Terraform doesn't know what's already deployed and would try to create everything from scratch. State is critical — never delete it, store it in a remote backend (Azure Storage) for team collaboration."

### 47. What is Azure Service Bus?
"A managed message broker. Services publish messages to topics, subscribers consume them. It handles delivery guarantees, retries, dead-letter queues, and ordering. We use it for asynchronous communication between microservices — IncidentCreated events, RCA triggers, remediation commands."

### 48. What is Azure Key Vault?
"Secure secrets management. Instead of putting database passwords or API keys in environment variables or code, we store them in Key Vault. Applications authenticate with managed identity and retrieve secrets at runtime. Secrets are encrypted, access-logged, and rotatable without redeployment."

### 49. Explain your CI/CD pipeline.
"On push: GitHub Actions checks out code → sets up Java 17 → runs Maven build → runs unit tests → runs integration tests → builds Docker image → pushes to Azure Container Registry → deploys to Azure Container Apps. If any step fails, the pipeline stops and notifies us."

### 50. How would you troubleshoot OpsPilot itself?
"Using the same observability principles we teach: check Actuator /health for service status, check Application Insights for error traces, search logs by correlationId to trace a specific incident's lifecycle across services, check metrics for latency and error rates. OpsPilot practices what it preaches."

---

## Bonus: Red Flags to Avoid Saying

❌ "I used Redis because it's a popular technology" → ✅ "I used Redis for caching incident data because the dashboard polls frequently and the data changes infrequently"

❌ "AI analyzes the logs and finds the root cause" → ✅ "Our backend gathers structured evidence, then the AI interprets it"

❌ "The AI automatically fixes production issues" → ✅ "AI recommends actions, a human must approve before execution"

❌ "I used microservices because they're better than monoliths" → ✅ "I used microservices because each service has a distinct domain boundary and different scaling needs"

❌ "I used Kubernetes" → ✅ "I chose Container Apps over Kubernetes because managing a K8s cluster would add complexity without proportional benefit for this project's scale"
