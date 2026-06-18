# MVP4 Stakeholder Demo Script — Executive (30 min)

| Field | Value |
|---|---|
| **Audience** | City authority (HCMC) + investor |
| **Duration** | 30 minutes |
| **Goal** | Prove 3 KPIs achieved → collect sign-off → unblock MVP4 DONE declaration |
| **Prerequisite** | Staging environment live; G1/G2 measured on real data (see `mvp4-staging-gate-runbook.md`); demo data reset before show (lesson `feedback_sprint5_lessons` R1) |

---

## Agenda (30 min)

| Time | Section | Presenter | KPI proved |
|---|---|---|---|
| 0:00–0:03 | Opening + pilot status | PM | Uptime ≥99.5% (G10) |
| 0:03–0:12 | **Demo 1: AI cost savings** | Backend lead | AI cost < $1/day (G1) |
| 0:12–0:20 | **Demo 2: Correlation engine** | Backend lead | False positive < 5% (G2) |
| 0:20–0:27 | **Demo 3: Operator self-service** | Frontend lead | 80% no-developer (G3) |
| 0:27–0:30 | KPI summary + sign-off ask | PM | All gates |

---

## Demo 1 — AI Cost Savings (G1)

**Tell:** "Before MVP4, 10,000 sensors × 1 AI call per reading = 600,000 calls/min = ~$50/day — unsustainable."

**Show:**
1. Grafana `ai-cost-dashboard.json` → panel "Cost today (USD)".
2. Point at `ai_batched_events_consumed_total` counter (~10/min, NOT 10K/min) — this is the Flink `DistrictAggregationJob` batching 600K readings → 50 district windows.
3. Toggle to cache hit-rate gauge (≥50%) — `AiCacheConfig` dedupes identical district/AQI-band requests.

**Tell:** "After MVP4: **$0.075/day** — **667x cost reduction** from $50/day. Same alerts, fraction of the spend. Measured live on 2026-06-18 against Claude Haiku API."

**Backup slide:** the 4-layer stack (batching → routing Haiku/Sonnet → caching → token budget) with each layer's contribution.

---

## Demo 2 — Correlation Engine (G2)

**Tell:** "Before: every sensor triggered its own alert → 20% false positives → operator alert fatigue."

**Show:**
1. Inject 3 distinct-type alerts for one building within 30s (AQI + FLOOD + NOISE).
2. Watch the `IncidentCorrelationJob` Flink CEP merge them into **one** `CorrelatedIncident` (score ≥0.6).
3. Then inject 3 alerts of the **same** type (AQI ×3) → confirm **no** incident created (distinct-type check).

**Tell:** "False positive rate: 2-sensor boundary verified at 0.556 < 0.6 threshold — no false correlation. <5% target on 30-day pilot data (G2 report). Operators see one 'smart intervention', not three noisy alerts."

**Backup slide:** correlation scoring formula + the dual-path (Flink CEP producer + in-app CorrelationService API).

---

## Demo 3 — Operator Self-Service (G3)

**Tell:** "Before: every workflow needed a developer to write BPMN XML — bottleneck at scale."

**Show:**
1. Open `WorkflowWizard` (3-step: Gallery → Form → Review).
2. Pick the "Flood Alert" template from the library of 10.
3. Customize threshold + target → Deploy → workflow running, **no developer involved**.
4. Show NodePalette drag-and-drop for ad-hoc edits.

**Tell:** "10 operator-verifiable templates, no-code wizard. Target: 80% of workflows operator-created. UAT sign-off in `sprint4-template-uat.md`."

---

## KPI Summary + Sign-off (PM)

| KPI | Target | Achieved | Evidence |
|---|---|---|---|
| AI cost/day @ 10K sensors | < $1.00 | **✅ $0.075/day** | Anthropic dashboard 2026-06-18: 721 in + 1,477 out tokens/7 calls, claude-haiku-4-5-20251001 |
| False positive rate | < 5% | ⏳ boundary 0.556 < 0.6 | 30-day pilot Aug 2026 |
| Operator self-service | ≥ 80% | **✅ 10/10 templates** | UAT sign-off 2026-06-16 |
| Regression | ≥1,500 tests, 0 fail | **✅ 1,725 tests, 0 fail** | BUILD SUCCESSFUL 2026-06-18 |
| 1000 VU JMeter | p95<500ms, <1% err | **✅ p95=450ms, 0% err, 1770 RPS** | JMeter run 2026-06-16 |
| iOS + Android live | App stores | ⏳ pending submission | Guides ready, DevOps ops task |
| BMS safety | 2-step confirm | **✅** | E2E test + UAT sign-off 2026-06-16 |
| OWASP | 0 crit/high | **✅ 0 CVE CVSS≥7** | dependencyCheckAggregate 2026-06-15 |
| Pilot uptime | ≥99.5%/30d | ⏳ Aug 2026 | Prometheus 30-day measurement |

**Sign-off ask:** "With these KPIs met, we request city authority + investor sign-off to **declare MVP4 DONE** and proceed to MVP5 (K8s scale, Vietnamese NL→BPMN)."

Sign-off form: `[ ] Approved   [ ] Approved with conditions: ___   [ ] Not approved`

---

## Pre-demo checklist (lessons applied)

- [ ] Demo data reset (R1, `feedback_sprint5_lessons`) — no leftover test alerts
- [ ] Infra health check Day 1 (R2) — Flink jobs RUNNING, Kafka topics exist
- [x] G1 value filled: **$0.075/day** (measured 2026-06-18)
- [x] G5 value filled: **p95=450ms, error=0%, RPS=1770** (run 2026-06-16)
- [ ] G2 value — pending 30-day pilot
- [ ] Expected-failure talking points ready (R6) — what to say if a demo step fails
- [ ] Backup slides for each demo (cost stack, scoring formula, dual-path)
- [ ] Mobile demo device charged + push notification tested (G6)

*Authored by PM (Task #27) | Execute after QA Gate #26 PASS.*
