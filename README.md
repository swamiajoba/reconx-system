# ReconX — Enterprise Trade Reconciliation Platform (Trainer Copy)

> Deutsche Bank — TDI 2026 Graduate Technical Training Programme
> **Advanced Track (Intermediate-Hybrid)** | 10-Day Case Study | Version 1.0

This repository is the **trainer copy** for the ReconX case study. It contains
the same structure as the student starter, with **fully implemented solution
code** and trainer-facing playbooks under [`TrainersGuide/`](./TrainersGuide/).

**Companion student copy:** `../reconx-studentscopy/` (the stub repo grads work
in — same structure, code removed and replaced with `TODO` blocks).

---

## What grads will build

A near-production-grade trade reconciliation platform used (in concept) by an
Ops team to detect and resolve mismatches between internal trade records and
external counterparty/custodian feeds — built across 10 days of module-based
delivery (AM theory + Percipio labs, PM hands-on case-study work).

```
       ┌──────────┐        ┌──────────────────────────┐        ┌────────────┐
       │  React   │  HTTPS │  Spring Boot REST API    │  JDBC  │ PostgreSQL │
       │ Frontend │ ─────▶ │  recon-service (Java 25) │ ─────▶ │  (Liqui-   │
       │  + Vite  │        │  + Spring Security/JWT   │        │   base     │
       └────┬─────┘        │  + Actuator/Micrometer   │        │   migs)    │
            │              └────────┬─────────────────┘        └─────┬──────┘
            │ SSE                   │  KafkaTemplate / @KafkaListener│
            │                       ▼                                ▼
            │              ┌──────────────────┐               ┌────────────┐
            └──────────────│  Apache Kafka    │               │ recon_*    │
                           │  trade-events    │               │ audit_log  │
                           │  recon-results   │               │ mat. views │
                           │  system-alerts   │               └────────────┘
                           │  + DLQ topics    │
                           └────────┬─────────┘
                                    ▼
                           ┌─────────────────────────┐
                           │ ReconConsumer (auto-rec)│
                           │ AuditConsumer (history) │
                           │ AlertConsumer  (notify) │
                           └─────────────────────────┘

  /actuator/prometheus ─▶ Prometheus (scrape) ─▶ Grafana dashboards + alerts
```

---

## Repository layout

```
reconx-trainerCopy/
├── db/                            ← Day 1: standalone SQL assets
│   ├── seed_data.sql              ← Counterparties, instruments, sample trades
│   ├── queries.sql                ← Analytical queries (window fns, CTEs, JSONB)
│   ├── partitioning.sql           ← Monthly trade partitions
│   └── erd.md                     ← Mermaid ER diagram
│
│   NOTE: Liquibase changelogs live on the JVM classpath at
│         backend/src/main/resources/db/changelog/ — not here.
│
├── backend/                       ← Days 2-6, 9: Java 25 + Spring Boot 3 + Kafka
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/dbtraining/reconx/
│       ├── ReconxApplication.java
│       ├── model/                 ← Day 2-3: sealed TradeType hierarchy, value objects
│       ├── repository/            ← Day 4-5: Spring Data JPA + Specifications
│       ├── service/               ← Day 3-6: reconciliation engine, analytics
│       ├── controller/            ← Day 5: REST API endpoints
│       ├── dto/                   ← Request/response DTOs, TradeEvent, MapStruct mappers
│       ├── exception/             ← Custom hierarchy + @RestControllerAdvice
│       ├── config/                ← Swagger, JPA, Liquibase, Cache, Kafka config
│       ├── security/              ← Day 5: JWT filter, RBAC
│       ├── kafka/                 ← Day 9: producers, consumers, DLQ
│       └── observability/         ← Day 6: custom Micrometer metrics
│
├── static-dashboard/              ← Day 7: vanilla HTML/CSS/JS (pre-React exercise)
│   ├── dashboard.html
│   ├── trades.html
│   ├── recon.html
│   ├── css/style.css
│   └── js/*.js
│
├── frontend/                      ← Day 8-9: React 19 + Vite recon-ui
│   ├── package.json
│   ├── vite.config.js
│   ├── Dockerfile
│   └── src/
│       ├── App.jsx
│       ├── components/            ← DataTable (compound), TradeRow, StatCard, …
│       ├── hooks/                 ← useWebSocket, useTradeStream, useDebouncedSearch
│       ├── context/               ← ThemeProvider, AuthProvider
│       ├── services/              ← apiService.js, sseClient.js
│       └── pages/                 ← Dashboard, Trades, Recon, AddTrade, Audit
│
├── monitoring/                    ← Day 6 + 10: Prometheus / Grafana
│   ├── prometheus/prometheus.yml
│   └── grafana/provisioning/
│
├── .github/workflows/ci.yml       ← Day 10: GitHub Actions pipeline
├── docker-compose.yml             ← Day 10: 7-service stack
├── .env.example                   ← Sample environment variables
├── student-guides/                ← What grads read each day (mirror to student copy)
└── TrainersGuide/                 ← Trainer playbook — this folder is trainer-only
```

The trainer-facing day-by-day walkthrough lives in
[`./TrainersGuide/`](./TrainersGuide/README.md). **Read it before each session.**
The student-facing equivalents live in
[`./student-guides/`](./student-guides/README.md).

---

## Prerequisites

- **Java 25** (Temurin recommended — the Advanced Track uses sealed classes, records, virtual threads where they fit)
- **Maven 3.9+**
- **Node.js 20+** and npm
- **Docker Desktop** (allocate ≥ 6 GB RAM — Kafka + Postgres + Prometheus + Grafana is heavier than Intermediate)
- **PostgreSQL 16** client tools (or use the bundled Docker container)
- **Git**
- IDE: IntelliJ IDEA Ultimate (backend) + VS Code (frontend) recommended

---

## Quick start (after Day 4)

```bash
# 1. Bring up infrastructure (Postgres + Kafka + Prometheus + Grafana + Kafdrop)
docker compose up -d postgres kafka zookeeper prometheus grafana kafdrop

# 2. Run the backend (Liquibase runs migrations automatically on startup)
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Run the frontend
cd ../frontend
npm install
npm run dev

# 4. Open
# - Swagger UI:      http://localhost:8080/swagger-ui.html
# - Frontend:        http://localhost:5173
# - Prometheus:      http://localhost:9090
# - Grafana:         http://localhost:3000   (admin / admin)
# - Kafdrop:         http://localhost:9000
# - Actuator health: http://localhost:8080/actuator/health
```

### Default credentials (dev profile only)

| Role          | Username        | Password     |
|---------------|-----------------|--------------|
| ADMIN         | `admin@db.com`  | `admin123`   |
| TRADER        | `trader@db.com` | `trader123`  |
| VIEWER        | `viewer@db.com` | `viewer123`  |
| RECON_ANALYST | `recon@db.com`  | `recon123`   |

JWT issued from `POST /api/auth/login` is valid for 60 minutes. Refresh tokens
live in HttpOnly cookies for 7 days.

---

## Deploy to the demo laptop (Day 10)

The deploy story is **GitHub Actions builds + pushes Docker images to GHCR;
the demo laptop pulls them and runs the full stack via `docker compose up`.**
No cloud hosting, no PaaS — the demo laptop *is* the deploy target.

```bash
# One-time on the demo laptop (uses a GitHub PAT with read:packages scope):
echo "<your-PAT>" | docker login ghcr.io -u <gh-username> --password-stdin

# Each deploy:
docker compose pull        # fetches the latest CI-tested images from GHCR
docker compose up -d       # brings up all 7 services
```

Full walkthrough: [`TrainersGuide/day10/README.md`](./TrainersGuide/day10/README.md).

---

## How to read the TODOs in this codebase

Every place a student must write code has a comment block that looks like this:

```java
// ============================================================================
// Build EquityTrade with the Builder pattern
//
// WHAT:    A concrete EquityTrade record/class that extends Trade and is
//          constructed via an immutable builder.
// HOW:     Use a static inner Builder with fluent setters returning `this`;
//          build() validates and returns an EquityTrade. Mark final fields.
// WHY:     Builder pattern keeps the call-site readable for trades with 8+
//          fields and gives us a single place to enforce invariants.
// OBSERVE: A trade missing required fields throws IllegalStateException at
//          build(), NOT at field-set time. Verify with the unit test in
//          EquityTradeTest.builder_missingPrice_throws.
// HINT:    See ../model/FXTrade.java for the same pattern applied to a
//          two-currency trade.
// ============================================================================
```

In the **trainer copy** these blocks sit above the **completed** solution. In
the student copy the implementation below is replaced with `// TODO`.

The full exercise text, acceptance criteria, and reference solution are in the
matching day's TrainersGuide README.

---

## Daily flow

Each day pairs an **AM teaching block** (instructor-led theory + Percipio labs)
with a **PM case-study block** (more theory/labs as needed, then hands-on work
on the ReconX platform).

| Day | Module                                                       | Exercises          | Headline new-2026 topic                          |
|----:|--------------------------------------------------------------|--------------------|--------------------------------------------------|
| 0   | Welcome & Onboarding                                         | —                  | —                                                |
| 1   | PostgreSQL Modules 1 & 2 + Liquibase                         | TICKET-ADV001 – TICKET-ADV017   | ★ Liquibase, ★ AI for ADRs                       |
| 2   | Java Modules 1 & 2 — OOP Mastery + SOLID                     | TICKET-ADV018 – TICKET-ADV032   | sealed-class trade hierarchy                     |
| 3   | Java Modules 3 & 4 — Functional + Testing                    | TICKET-ADV033 – TICKET-ADV047   | parallel recon with `CompletableFuture`          |
| 4   | Spring Boot Modules 1 & 2 — Enterprise Setup                 | TICKET-ADV048 – TICKET-ADV062   | multi-module Maven, Hibernate Envers, MapStruct  |
| 5   | Spring Boot Modules 3 & 4 — REST + JWT Security              | TICKET-ADV063 – TICKET-ADV080   | API versioning                                   |
| 6   | Spring Boot Modules 5 & 6 — Performance + Observability      | TICKET-ADV081 – TICKET-ADV097   | ★ Observability deep dive                        |
| 7   | HTML/CSS Module 2 + JS Advanced + React Module 1             | TICKET-ADV098 – TICKET-ADV106    | live SSE trade feed (Flexbox layout primitive)   |
| 8   | React Modules 2 & 3 — Advanced Patterns                      | TICKET-ADV111 – TICKET-ADV125 (+ TICKET-ADV127 stretch) | React performance profiling                      |
| 9   | React Testing + ★ Kafka Deep Dive                            | TICKET-ADV128 – TICKET-ADV145   | ★ Kafka deep dive, event sourcing                |
| 10  | Docker & CI/CD — Enterprise Deployment                       | TICKET-ADV146 – TICKET-ADV165 | ★ Liquibase-in-CI, ★ AI in DevOps                |

Exercises within a day are referenced as **Day N · Ex N.M** (e.g. *Day 2 · TICKET-ADV021*).
Every exercise has acceptance criteria and hints in the matching day's
`TrainersGuide/dayN/README.md` (and reference solutions, for trainers only).

---

## Branching

Use **GitFlow**:

```
main      ← only release tags (v1.0.0 at end of Day 10)
develop   ← integration branch — your team merges here
feature/* ← one branch per exercise (e.g. feature/d2-ex2-equity-builder)
```

Open a Pull Request from each `feature/*` branch into `develop`. Two approvals
required before merge (advanced track convention — Intermediate only required
one).

---

## Final demo (Day 10)

A 20-minute end-to-end walkthrough:

| Minutes | Content |
|--------:|---------|
| 3       | Problem statement + C4 architecture diagram |
| 8       | Live demo: JWT login → post trade → Kafka event → auto-recon → resolve break → Grafana metric ticks |
| 5       | Code walkthrough (one feature each team member is proud of) |
| 4       | Q&A |

The demo deck template + speaker notes are in
[`TrainersGuide/day10/README.md#demo-coaching`](./TrainersGuide/day10/README.md).

---

## Good luck — and tell your trainer when you're stuck 🏦
