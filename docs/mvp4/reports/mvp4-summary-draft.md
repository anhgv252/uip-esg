# MVP4 Summary — AI Scale + Correlation Engine + Operator Self-Service (DRAFT)

| Field | Value |
|---|---|
| **Status** | DRAFT — pending QA Gate #26 PASS + stakeholder demo |
| **Drafted** | 2026-06-15 |
| **Sprints** | 6 (Aug → Oct 2026 planned; code-complete 2026-06-12) |
| **Total SP committed** | ~255 |
| **SP delivered (code-side)** | ~250 (all 27 tasks DEV DONE except QA gate execution + PM close-out) |

> This summary is auto-drafted from verified task records + code inspection. Final numbers for KPIs (G1 AI cost, G2 false-positive) require the Sprint 6 QA gate run on staging.

---

## 1. Deliverables Completed

### Trụ 1: AI Scale & Cost Optimization (~26 SP) ✅
| ID | Feature | Status | Evidence |
|---|---|---|---|
| M4-AI-01 | District-level Flink batching | ✅ | `DistrictAggregationConfig` |
| M4-AI-02 | Model routing (Haiku/Sonnet) | ✅ | `ModelRouter` (9 tests) |
| M4-AI-03 | Smart pre-filter | ✅ | `SmartPreFilter` (13 tests) |
| M4-AI-04 | AI response caching (Redis) | ✅ | `AiCacheConfig` (21 tests), Grafana ai-cache-dashboard |
| M4-AI-05 | Token budgeting | ✅ | `TokenBudgetService` (10 tests) |
| M4-AI-06 | Cost dashboard (Grafana) | ✅ | ai-cost-dashboard.json (6 panels), `AiCostMetrics` (10 tests) |
| M4-AI-07 | Welford Universal anomaly | ✅ | `WelfordAnomalyDetector` — 10 sensor types (12 tests) |

**Target:** AI cost < $1/ngày @ 10K sensors (83x reduction) — **verify at G1 gate**.

### Trụ 2: Multi-Device Correlation Engine (~26 SP) ✅
| ID | Feature | Status | Evidence |
|---|---|---|---|
| M4-COR-01 | IncidentCorrelationFlinkJob | ✅ | `CorrelationService` + DLQ + metrics (CorrelationE2ETest 8 PASS) |
| M4-COR-02 | Correlated payload builder | ✅ | `CorrelatedPayloadBuilder` (9 tests) |
| M4-COR-03 | BMS auto-command POC | ✅ | 2-step operator confirm, BR-010 enforced, 30s timeout |
| M4-COR-04 | BMS bidirectional feedback loop | ✅ | `BmsFeedbackService` + `BmsFeedbackRetryService` (retry + DLQ) |
| M4-COR-05 | Baseline drift detection | ✅ | `BaselineDriftDetector` 7-day window (11 tests) |
| M4-COR-06 | Operator feedback capture | ✅ | `AlertFeedbackController` (6 tests) + AlertFeedbackButton.tsx |
| M4-COR-07 | Incident Feedback Loop | ✅ | `TriggerSuggestionGenerator` ≥3 suggestions guaranteed |

**Target:** False positive < 5% (from ~20%) — boundary verified at 0.556 < 0.6; **confirm on 30-day pilot data at G2**.

### Trụ 3: Operator Self-Service (~16 SP) ✅
| ID | Feature | Status | Evidence |
|---|---|---|---|
| M4-SS-01 | Workflow Template Library | ✅ | 10 templates (flood, AQI, equipment, ESG, complaint, energy, noise, water, safety, traffic) |
| M4-SS-02 | No-code Trigger Config wizard | ✅ | `WorkflowWizard` 3-step (Gallery → Form → Review), React Hook Form |
| M4-SS-03 | NodePalette DnD wire | ✅ | HTML5 DnD with dataTransfer |

**Target:** 80% workflows operator-created without developer — **verify at G3 UAT**.

### v3.1 Carry-Over (~72 SP) ✅
- Security: JWT/Rate/SQL injection ITs, PII masking, DLQ audit
- Backend fixes: BacnetIpAdapter real execution, MQTT race fix, CO2 configurable, Water intensity ISO 37120
- Frontend: BPMN UX, code-split (648KB→15.91KB), mobile offline, aria-label
- QA: REST Assured contracts, Pact, JMeter 1000 VU, Awaitility migration
- DevOps: FCM/APNs, strong passwords, resource limits, Kong JWKS, Flink/Avro automation

### ADRs Authored (6) ✅
ADR-041 (AI Cost), ADR-042 (Correlation), ADR-043 (BMS Safety), ADR-044 (Self-Service), ADR-045 (Welford), ADR-046 (Feedback Loop) — all at `docs/adr/`.

---

## 2. KPIs vs Target (pending gate run)

| KPI | Target | Status |
|---|---|---|
| AI cost/day @ 10K sensors | < $1.00 | ⏳ G1 — run Grafana dashboard on 10K simulated load |
| False positive rate | < 5% | ⏳ G2 — boundary verified; confirm on 30-day data |
| Operator self-service adoption | ≥ 80% | ⏳ G3 — 10 templates ready, UAT pending |
| Correlated incident detection | < 60s | ✅ CEP 30s window |
| BMS command latency (auto) | < 5s | ✅ implemented |
| Pilot uptime | ≥ 99.5% | ⏳ G10 — 30-day measurement |

---

## 3. Deferred / Carry-over to MVP5

| Item | Reason | Doc |
|---|---|---|
| GAP-010 gRPC IT vs real analytics-service | REST-first path; Pact + adapter tests suffice | gap-010-grpc-it-deferral.md |
| K8s migration | Pilot 5 buildings không cần; trigger >20 buildings | README §6 |
| NL→BPMN (Vietnamese) | MVP5 feature | README §12 |
| GAP-039/040/046 (CH Keeper dashboard, proto-breaking CI, SSL/TLS termination) | P2 low priority | README §3 |

---

## 4. Lessons Learned (MVP4)

*(To be filled from sprint retrospectives at close-out)*
- **Config bugs surfaced by full test suite** (4 Spring config bugs: bean override, missing @Primary, @RetryableTopic KafkaTemplate, non-lazy cache) — see `feedback_mvp4_config_bugs.md`. Always run full `@SpringBootTest` when adding CacheManager/KafkaTemplate beans.
- **P0-2 password hygiene**: `.env` (dev defaults) was committed despite `.gitignore` rule. Resolved by untracking + externalizing CHANGE_ME placeholders. Audit `.gitignore` vs `git ls-files` for secret files.
- **ADR location convention**: ADRs live at `docs/adr/` (repo standard), not per-MVP folders. README §13 updated.

---

## 5. MVP5 Roadmap (draft)

- **K8s migration** — Helm charts, HPA, when >20 buildings or Tier-3 customer
- **NL→BPMN** — Vietnamese natural language → workflow generation
- **HashiCorp Vault** — secret management with K8s migration
- **Scale** — 50+ buildings, Series A trigger at $100K MRR
- **Timeline** — Q1 2027

---

## 6. Stakeholder Demo Plan (S6, Task #27)

30-min executive demo, focus:
1. **AI cost savings** — before/after Grafana dashboard ($50 → <$1/day)
2. **Correlation engine** — multi-sensor → single incident live
3. **Operator self-service** — create workflow via wizard, no developer

Sign-off required from: city authority + investor.

---

*Drafted by: SA (2026-06-15) | Finalize after QA Gate #26 + stakeholder demo*
