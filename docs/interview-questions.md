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

<!-- NEW PHASES WILL BE ADDED BELOW THIS LINE -->
