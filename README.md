# TaskMesh AI 🏛️

> **Distributed Multi-Agent Legal Document Analyzer**
>
> Automates legal contract analysis using 3 specialized AI agents that collaborate to extract facts, identify applicable laws, and assess risks — reducing review time from days to minutes.

---

## Overview

TaskMesh AI is a multi-agent orchestration platform built with Spring Boot and Spring AI. It processes legal contracts through a pipeline of specialized AI agents:

- **Facts Agent** — Extracts parties, timeline, financial terms, and key clauses
- **Law Agent** — Identifies applicable acts, sections, and regulatory approvals using legal precedents
- **Risk Agent** — Calculates risk scores (0–100) with specific flags and mitigation strategies

Agents communicate via **Shared Memory** (`ConcurrentHashMap`) for zero-latency data passing between sequential steps.

---

## Architecture

```
REST API → Orchestrator (CaseService) → Facts → Law → Risk (sequential)
                ↓
         Shared Memory (ConcurrentHashMap)
                ↓
         H2 In-Memory Database
                ↓
         OpenAI GPT-4o (via Spring AI)
```

- **REST API** — Case create, analyze, status, and report endpoints
- **Orchestrator** — Coordinates the 3-agent pipeline and status updates
- **Shared Memory** — Thread-safe in-memory store for inter-agent results
- **Database** — H2 (in-memory) for case persistence
- **LLM** — OpenAI GPT-4o via Spring AI structured output

---

## Tech Stack

| Technology | Purpose |
|---|---|
| Java 21 | Runtime & language |
| Spring Boot 3.3 | Application framework |
| Spring AI | LLM integration layer |
| OpenAI GPT-4o | Legal analysis model |
| H2 | In-memory demo database |
| Spring Data JPA | Persistence |
| Lombok | Boilerplate reduction |
| Maven | Build & dependency management |

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the Maven Wrapper if present)
- OpenAI API key

### Steps

1. **Clone the repo**
   ```bash
   git clone https://github.com/Henzlo/TaskmeshAI.git
   cd TaskmeshAI
   ```

2. **Set your OpenAI API key**
   ```bash
   export OPENAI_API_KEY=your_key_here
   ```

3. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   # or: mvn spring-boot:run
   ```

4. **Open the app** — http://localhost:8080  
   H2 console: http://localhost:8080/h2-console

---

## API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/cases` | Create a new case from contract text |
| `POST` | `/api/cases/{id}/analyze` | Run Facts → Law → Risk pipeline |
| `GET` | `/api/cases/{id}` | Get case status and metadata |
| `GET` | `/api/cases/{id}/report` | Fetch full analysis report |

### Curl examples

**1. Create a case** (using `sample-contract.txt`):

```bash
curl -s -X POST http://localhost:8080/api/cases \
  -H "Content-Type: application/json" \
  -d "{
    \"title\": \"TechCorp-InnovateSoft Merger\",
    \"documentType\": \"MERGER_AGREEMENT\",
    \"documentText\": $(jq -Rs . < sample-contract.txt)
  }"
```

**2. Analyze the case** (replace `{id}` with the returned case id):

```bash
curl -s -X POST http://localhost:8080/api/cases/{id}/analyze
```

**3. Get case status:**

```bash
curl -s http://localhost:8080/api/cases/{id}
```

**4. Fetch the report:**

```bash
curl -s http://localhost:8080/api/cases/{id}/report
```

---

## How It Works

1. **Upload** a contract via `POST /api/cases`
2. **Trigger analysis** via `POST /api/cases/{id}/analyze`
3. **Pipeline runs sequentially:**
   - Facts Agent extracts structured facts → Shared Memory
   - Law Agent maps applicable law & approvals → Shared Memory
   - Risk Agent scores risks & mitigations → Shared Memory
4. **Fetch report** via `GET /api/cases/{id}/report`

Case status progresses: `CREATED` → `EXTRACTING` → `ANALYZING` → `REVIEWING` → `COMPLETED`

---

## Sample Output

```json
{
  "caseId": "a1b2c3d4-...",
  "facts": {
    "parties": [
      { "name": "TechCorp Inc.", "role": "acquirer", "type": "corporation" },
      { "name": "InnovateSoft Ltd.", "role": "target", "type": "private limited" }
    ],
    "financialTerms": {
      "totalValue": "500000000",
      "currency": "USD",
      "paymentSchedule": "Payable at closing; earnout of 50000000 if 2027 revenue reaches 100000000"
    },
    "missingStandardClauses": [
      "indemnity",
      "force majeure",
      "dispute resolution",
      "IP assignment"
    ]
  },
  "laws": {
    "applicableActs": [
      { "name": "Companies Act 2013", "sections": ["230", "232"], "relevance": "Scheme of arrangement / merger" }
    ],
    "regulatoryApprovals": [
      { "body": "CCI", "required": true, "timeline": "210 days", "penalty": "Deal may be voidable" }
    ],
    "complianceGaps": ["No dispute resolution clause", "Unilateral amendment by Acquirer"]
  },
  "riskAnalysis": {
    "overallScore": 72,
    "riskLevel": "CRITICAL",
    "topRisks": [
      {
        "type": "Indemnity Gap",
        "severity": "HIGH",
        "description": "No indemnity for representation breaches or IP claims",
        "suggestion": "Add mutual indemnity with survival period"
      }
    ],
    "mitigationStrategies": [
      "Negotiate indemnity and escrow holdback",
      "Add force majeure and dispute resolution clauses"
    ]
  }
}
```

---

## Project Structure

```
TaskmeshAI/
├── pom.xml
├── sample-contract.txt
├── README.md
└── src/main/
    ├── java/com/taskmesh/
    │   ├── TaskMeshAiApplication.java
    │   ├── controller/       # REST endpoints
    │   ├── service/          # Orchestrator + AI agents
    │   ├── model/            # Entities & analysis models
    │   ├── dto/              # Request/response DTOs
    │   ├── repository/       # Spring Data JPA
    │   ├── shared/           # Shared memory store
    │   └── exception/        # Global error handling
    └── resources/
        └── application.yml
```

---

## Future Enhancements

- Redis-backed shared memory for multi-instance deployments
- Kafka for async agent orchestration
- PostgreSQL instead of H2 for durable storage
- Spring State Machine for case lifecycle
- JWT authentication & role-based access
- Docker image + Kubernetes manifests
- Parallel agent execution where dependencies allow

---

## License

MIT License © TaskMesh AI
