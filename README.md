# OpsPilot — Cloud Incident Management & AI Root Cause Analysis Platform

[![CI - Build & Test](https://github.com/shashank4066/OpsPilot/actions/workflows/ci.yml/badge.svg)](https://github.com/shashank4066/OpsPilot/actions/workflows/ci.yml)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.4](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An event-driven incident management platform that detects production failures, correlates evidence with deployments and telemetry, performs AI-assisted root cause analysis, and manages remediation with human-in-the-loop safety.

## Why I Built This

To demonstrate **production engineering concepts beyond CRUD**: microservice architecture, event-driven communication, AI integration with guardrails, structured evidence gathering, and closed-loop incident management — the kind of problems SRE and platform teams solve daily.

## The Incident Story

```
Deploy → Failure → Detection → Incident → Evidence Gathering → AI RCA
    → Human Approval → Remediation → Recovery Verification → Postmortem
```

## Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        OpsPilot Platform                         │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Incident    │  │  Detection   │  │     RCA      │          │
│  │   Service     │  │   Service    │  │   Service    │          │
│  │  (Port 8081)  │  │  (Port 8082) │  │  (Port 8083) │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                  │                   │
│  ┌──────┴─────────────────┴──────────────────┴───────────────┐  │
│  │           Event Bus (In-Memory / Azure Service Bus)        │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ Remediation  │  │   Failure    │  │    React     │          │
│  │  Service     │  │  Simulator   │  │  Dashboard   │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└──────────────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### AI RCA: Evidence First, AI Second
The backend gathers structured evidence (metrics, deployments, errors, dependencies) before calling the LLM. AI receives curated facts and produces a structured interpretation — **never invents data**. This makes AI output verifiable.

### Human-in-the-Loop
AI recommends remediation but **cannot execute destructive actions**. A human operator must review evidence and approve before any rollback or change is applied. This is a deliberate safety design, not a limitation.

### Recovery Verification
Incidents aren't resolved after remediation — the system **verifies that metrics actually recovered** before closing the incident. Error rate from 18.5% → 0.7%? ✓ Only then is the incident resolved.

### Event-Driven Architecture
Services communicate through domain events (`INCIDENT_CREATED`, `STATUS_CHANGED`, etc.) using the `EventPublisher` interface. Local dev uses `InMemoryEventPublisher`; production uses Azure Service Bus. **Zero code changes** to switch — Dependency Inversion Principle.

### State Machine
Incident status transitions are enforced in the service layer:
```
OPEN → INVESTIGATING → MITIGATING → RESOLVED
  ↓         ↑    ↓          ↓
  RESOLVED  └────┘      RESOLVED
```
Invalid transitions (e.g., OPEN → MITIGATING) return HTTP 409 Conflict.

## Tech Stack

| Category | Technologies |
|---|---|
| **Backend** | Java 17, Spring Boot 3.4, Spring Data JPA, Spring Validation |
| **Database** | H2 (dev), PostgreSQL 16 (docker/prod) |
| **Events** | InMemoryEventPublisher (dev), Azure Service Bus (prod) |
| **Testing** | JUnit 5, Mockito, AssertJ, MockMvc |
| **Cloud** | Azure Container Apps, Service Bus, Key Vault |
| **AI** | Azure OpenAI (with MockRcaAiClient for local dev) |
| **DevOps** | Docker, Docker Compose, GitHub Actions CI |
| **Observability** | Spring Actuator, Micrometer, Application Insights |

## Running Locally

### Quick Start (No Docker needed)
```bash
# Navigate to incident service
cd services/incident-service

# Build and run (uses H2 in-memory database)
mvn spring-boot:run

# The API is now running at http://localhost:8081
```

### With Docker Compose (PostgreSQL)
```bash
# From the project root
docker-compose up -d

# Check logs
docker-compose logs -f incident-service
```

### Test the API

```bash
# Health check
curl http://localhost:8081/api/v1/health

# Create an incident
curl -X POST http://localhost:8081/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Checkout Service Error Rate Spike",
    "serviceName": "checkout-service",
    "environment": "production",
    "severity": "SEV2",
    "source": "HIGH_ERROR_RATE_RULE"
  }'

# List all incidents
curl http://localhost:8081/api/v1/incidents

# Filter by status
curl http://localhost:8081/api/v1/incidents?status=OPEN

# Update status (replace {id} with actual UUID)
curl -X PATCH http://localhost:8081/api/v1/incidents/{id}/status \
  -H "Content-Type: application/json" \
  -d '{"status": "INVESTIGATING"}'

# Record a deployment
curl -X POST http://localhost:8081/api/v1/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "checkout-service",
    "environment": "production",
    "version": "v1.8.3",
    "commitSha": "a1b2c3d",
    "deployedAt": "2026-10-01T14:02:00",
    "deployedBy": "ci-pipeline",
    "deploymentStatus": "SUCCEEDED"
  }'

# List deployments
curl http://localhost:8081/api/v1/deployments

# Recent deployments for a service
curl http://localhost:8081/api/v1/deployments/service/checkout-service/recent
```

### Run Tests
```bash
cd services/incident-service

# Unit tests + integration tests
mvn test

# Build with tests
mvn clean verify
```

## API Endpoints

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `GET` | `/api/v1/health` | Health check | 200 |
| `POST` | `/api/v1/incidents` | Create incident | 201, 400 |
| `GET` | `/api/v1/incidents` | List incidents (optional `?status=OPEN`) | 200 |
| `GET` | `/api/v1/incidents/{id}` | Get incident by ID | 200, 404 |
| `PATCH` | `/api/v1/incidents/{id}/status` | Change incident status | 200, 404, 409 |
| `POST` | `/api/v1/deployments` | Record deployment | 201, 400 |
| `GET` | `/api/v1/deployments` | List all deployments | 200 |
| `GET` | `/api/v1/deployments/service/{name}/recent` | Recent deployments for service | 200 |

## Project Structure

```
OpsPilot/
├── .github/workflows/
│   └── ci.yml                           # GitHub Actions CI pipeline
├── services/
│   └── incident-service/
│       ├── Dockerfile                   # Multi-stage Docker build
│       ├── pom.xml                      # Maven build config
│       └── src/
│           ├── main/java/com/opspilot/incident/
│           │   ├── IncidentServiceApplication.java  # Entry point
│           │   ├── config/
│           │   │   ├── DataSeeder.java              # Demo data loader
│           │   │   └── WebConfig.java               # CORS configuration
│           │   ├── controller/
│           │   │   ├── HealthController.java        # Health endpoint
│           │   │   ├── IncidentController.java      # Incident CRUD API
│           │   │   └── DeploymentController.java    # Deployment tracking API
│           │   ├── dto/                             # Request/Response DTOs
│           │   ├── entity/                          # JPA entities
│           │   ├── enums/                           # IncidentStatus, Severity
│           │   ├── event/
│           │   │   ├── DomainEvent.java             # Event structure
│           │   │   ├── EventPublisher.java          # Interface (DIP)
│           │   │   └── InMemoryEventPublisher.java  # Local dev implementation
│           │   ├── exception/                       # Custom exceptions + handler
│           │   ├── repository/                      # Spring Data JPA repos
│           │   └── service/                         # Business logic
│           ├── main/resources/
│           │   └── application.yml                  # Configuration
│           └── test/java/                           # Unit + Integration tests
├── docs/
│   ├── interview-questions.md           # Interview Q&A by phase
│   ├── complete-interview-guide.md      # Comprehensive interview prep
│   ├── 50-interview-questions.md        # 50 Q&A covering all topics
│   └── notes.md                         # Study notes by phase
├── docker-compose.yml                   # Local dev environment
├── .gitignore
├── LICENSE
└── README.md
```

## Design Patterns

| Pattern | Where | Why |
|---|---|---|
| **Repository** | Data access layer | Abstracts database operations via Spring Data JPA |
| **DTO** | API boundary | Decouples API from database schema |
| **State Machine** | IncidentService | Enforces valid status transitions |
| **Dependency Inversion** | EventPublisher | Swap InMemory ↔ Azure without code changes |
| **Adapter** | Cloud integrations | InMemory/Mock implementations for local dev |
| **Global Exception Handler** | RestControllerAdvice | Centralized, structured error responses |

## What I Learned

- **Microservice boundaries** — Each service owns its domain and data
- **Event-driven architecture** — Async communication for resilience and decoupling
- **AI integration with guardrails** — Structured evidence pipeline prevents hallucination
- **State machine design** — Enforcing valid transitions in the service layer
- **Docker multi-stage builds** — Minimal production images with security best practices
- **CI/CD with GitHub Actions** — Automated build and test on every push

## License

MIT
