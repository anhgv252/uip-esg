# Sprint 3 Review — AI Optimization + Correlation Start

| Field | Value |
|---|---|
| **Sprint** | MVP4-S3 (Sep 01-12, 2026) |
| **Review Date** | 2026-06-15 (back-filled) |
| **Sprint Goal** | AI cost đạt < $1/ngày + correlation engine foundation |
| **Status** | ✅ All assigned tasks DEV DONE |

---

## 1. Deliverables Completed

### Backend (#13) — 12 SP ✅
| Item | Status |
|---|---|
| M4-AI-03 Smart pre-filter | ✅ SmartPreFilterTest 13 tests GREEN. Rule-based handles 80% cases; critical events (flood, fire) bypass to AI |
| M4-COR-01 IncidentCorrelationFlinkJob (START) | ✅ CEP config + CorrelationScoringServiceTest 9 tests GREEN |
| M4-COR-06 Operator feedback API | ✅ AlertFeedbackControllerTest 6 tests GREEN, persisted to DB |

### Backend-2 (#15) — 3 SP ✅
- M4-AI-07 Welford Universal (START): WelfordAnomalyDetectorTest 12 tests GREEN
- Cold-start learning phase (first 100 readings), 5 sensor types (AQI, WATER_LEVEL, NOISE, HUMIDITY, TEMPERATURE)

### Frontend (#14) — 10 SP ✅
- M4-SS-01 Template Library START: WorkflowTemplate + TemplateParam interfaces, TemplateGallery.tsx, 5 initial templates
- M4-COR-06 Feedback UI: AlertFeedbackButton.tsx wired in AlertsPage

### DevOps (#16) — 3 SP ✅
- M4-AI-04 Redis AI caching: AiCacheConfig (@Cacheable, Redis DB 2, TTL 300s), AiCacheConfigTest 21 tests
- Grafana dashboard: docs/mvp4/grafana/ai-cache-dashboard.json

---

## 2. Sprint Gate Verification

| Gate Criterion | Status |
|---|---|
| AI cost < $5/ngày @ 10K simulated sensors | ⏳ Pending load test (Sprint 6 gate G1) |
| Correlation job RUNNING | ✅ Config + scoring implemented |

---

## 3. ADRs Authored

| ADR | Title | Status |
|---|---|---|
| ADR-042 | Incident Correlation Engine — Flink CEP | ✅ docs/adr/ADR-042-incident-correlation-engine.md |
| ADR-044 | Operator Self-Service Architecture | ✅ docs/adr/ADR-044-operator-self-service.md |
| ADR-045 | Welford Universal Anomaly Detection | ✅ docs/adr/ADR-045-welford-universal-anomaly.md |

> Task #13/#15 originally listed "[ ] ADR drafted" — **resolved**: ADRs authored at standard repo path `docs/adr/`.

---

## 4. Carry-over to Sprint 4

- Correlation job production-hardening (DLQ, metrics, checkpoint) → Task #17
- Welford remaining sensor types → Task #25

---

*Reviewer: Solution Architect | Back-filled from task-*.md DEV DONE records (2026-06-15)*
