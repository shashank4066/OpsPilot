# OpsPilot — Cloud Incident Management & AI Root Cause Analysis Platform

An event-driven incident management platform that detects production failures, correlates evidence with deployments and telemetry, performs AI-assisted root cause analysis, and manages remediation with human-in-the-loop safety.

## Why I Built This

To demonstrate production engineering concepts beyond CRUD: microservice architecture, event-driven communication, AI integration with guardrails, structured evidence gathering, and closed-loop incident management.

## The Incident Story

```
Deploy → Failure → Detection → Incident → Evidence Gathering → AI RCA
    → Human Approval → Remediation → Recovery Verification → Postmortem
```

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    OpsPilot Platform                      │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   Incident    │  │  Detection   │  │     RCA      │  │
│  │   Service     │  │   Service    │  │   Service    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                  │          │
│  ┌──────┴─────────────────┴──────────────────┴───────┐  │
│  │              Event Bus (async messaging)           │  │
│  └───────────────────────────────────────────────────┘  │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Remediation  │  │   Failure    │  │    React     │  │
│  │  Service     │  │  Simulator   │  │  Dashboard   │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└─────────────────────────────────────────────────────────┘
```

## Key Design Decisions

### AI RCA: Evidence First, AI Second
The backend gathers structured evidence (metrics, deployments, errors, dependencies) before calling the LLM. AI receives curated facts and produces a structured interpretation — never invents data.

### Human-in-the-Loop
AI recommends remediation but cannot execute destructive actions. A human operator must review evidence and approve before any rollback or change is applied.

### Recovery Verification
Incidents aren't resolved after remediation — the system verifies that metrics actually recovered before closing the incident.

## Tech Stack

| Category | Technologies |
|---|---|
| **Backend** | Java 17, Spring Boot, Spring Data JPA, Spring Security |
| **Database** | PostgreSQL, Redis (where justified) |
| **Frontend** | React, TypeScript, Vite |
| **Cloud** | Azure Container Apps, Service Bus, Key Vault, Blob Storage |
| **AI** | Azure OpenAI (with MockRcaAiClient for local dev) |
| **DevOps** | Docker, GitHub Actions, Terraform |
| **Observability** | Spring Actuator, Micrometer, Application Insights |

## Running Locally

```bash
# Start the incident service (uses H2 in-memory database)
cd services/incident-service
mvn spring-boot:run

# Test the health endpoint
curl http://localhost:8081/api/v1/health

# Create an incident
curl -X POST http://localhost:8081/api/v1/incidents \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Checkout Service Error Rate Increased",
    "serviceName": "checkout-service",
    "environment": "production",
    "severity": "SEV2"
  }'
```

## API Endpoints

```
GET    /api/v1/health                              Health check
POST   /api/v1/incidents                           Create incident
GET    /api/v1/incidents                            List incidents
GET    /api/v1/incidents/{id}                       Get incident
PATCH  /api/v1/incidents/{id}/status                Change status
POST   /api/v1/deployments                          Record deployment
GET    /api/v1/deployments                          List deployments
```

## Project Structure

```
opspilot/
├── services/
│   └── incident-service/          # Core incident management
├── docs/
│   ├── complete-interview-guide.md
│   ├── 50-interview-questions.md
│   ├── interview-questions.md
│   └── notes.md
├── .gitignore
└── README.md
```

## License

MIT
