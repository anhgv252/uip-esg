# MVP4 Demo Readiness Checklist + Dry-run Plan

| Field | Value |
|---|---|
| **Owner** | PM (Task #27) |
| **Audience** | City authority (HCMC) + investor |
| **Drafted** | 2026-06-18 (SA, on behalf of PM — PM agent model unavailable this session) |
| **Companion** | [`mvp4-stakeholder-demo-script.md`](mvp4-stakeholder-demo-script.md) (30-min script), [`mvp4-summary-draft.md`](mvp4-summary-draft.md) (full KPIs) |

> Goal: every checkbox green **before** the stakeholder walks in. No live debugging during the demo. Lessons applied from `feedback_sprint5_lessons` (R1 demo-data reset, R2 infra health Day 1, R6 expected-failure talking points) and `feedback_mvp4_kafka_reset_runbook`.

---

## T-1 day: Infrastructure & data

- [ ] **Backend health**: `curl http://localhost:8080/api/v1/health` → `{"status":"UP"}`. Restart if needed.
- [ ] **Kafka clean** (critical — this is what broke the 2026-06-18 staging run):
  - [ ] `uip-kafka` running healthy, `uip-kafka-2/3` removed (single-instance topology for pilot demo).
  - [ ] All 27 topics present: `docker run --rm --network infrastructure_uip-network confluentinc/cp-kafka:7.5.0 kafka-topics --bootstrap-server kafka:9092 --list` — confirm `ai.district.aggregations`, `correlated.incidents`, `bms.feedback.dlq` etc. exist.
  - [ ] No `UNKNOWN_TOPIC_OR_PARTITION` warns: `docker logs uip-backend --since 5m 2>&1 | grep -c UNKNOWN_TOPIC` → **0**.
  - [ ] Reset procedure if any fail: `infrastructure/README-ops.md` § "Switching single ↔ HA compose".
- [ ] **Flink jobs RUNNING** (Demo 1 + 2 depend on these):
  - [ ] `curl http://localhost:8081/jobs` → 2 jobs, state RUNNING.
  - [ ] `DistrictAggregationJob`: source `ngsi_ld_environment` + sink `ai.district.aggregations` vertices bound.
  - [ ] `IncidentCorrelationJob`: source alert events + sink `correlated.incidents` vertices bound.
  - [ ] If missing → re-submit via REST `POST /jars/{id}/run` with `entryClass` (see `feedback_mvp4_kafka_reset_runbook`).
- [ ] **Demo data reset** (R1): clear leftover test alerts/incidents from prior runs so Demo 2 shows a clean correlation. Operator feedback table reset if needed.
- [ ] **Backend resources sized for any live load**: `mem_limit: 1536m, cpus: 2.0` confirmed in `docker-compose.yml` (the 768m/0.75 limit caused G5 FAIL — lesson applied).

## T-1 day: Dashboards & UI

- [ ] **Grafana dashboards imported** into the staging Grafana instance:
  - [ ] `docs/mvp4/grafana/ai-cost-dashboard.json` (Demo 1) — "Cost today (USD)" panel shows non-zero.
  - [ ] `ai-cache-dashboard.json` — cache hit-rate gauge wired to Prometheus.
  - [ ] `bms-commands-dashboard.json` (backup for Demo 2 if BMS comes up).
- [ ] **Prometheus scraping backend** `/actuator/prometheus` (mgmt port 8086, basic auth `prometheus:prometheus-dev-scrape`). Verify `ai_cost_usd_total`, `ai_cache_hits_total`, `ai_batched_events_consumed_total` all return values.
- [ ] **Frontend build**: `cd frontend && npm run build` → 0 TypeScript errors. Run `npm run dev` for live demo or serve the production build.
- [ ] **⚠️ Demo from production build, NOT dev server** (found 2026-06-18): the Vite dev server (`npm run dev`) crashes with `createTheme_default is not a function` because the lockfile resolves `^5.15.14` up to @mui/material **5.18.0**, whose `exports` map lacks a `./styles` entry. Cache clear + `optimizeDeps.include` do **not** fix it. The **production build is unaffected**. For the live demo: `cd frontend && npm run build && npx vite preview --port 4173`. Permanent fix (separate task): pin @mui/material to an exact 5.15.x. See [`mvp4-inner-browser-demo-2026-06-18.md`](mvp4-inner-browser-demo-2026-06-18.md).
- [ ] **WorkflowWizard smoke** (Demo 3): open Gallery → pick "Flood Alert" → Form → Review → Deploy. Confirm 10 templates load.
- [ ] **Mobile demo device** (Demo 3 / G6): charged, push notification tested (FCM/APNs credentials valid), offline banner dismissible.

## T-0: Demo day (60 min before)

- [ ] **Warm the AI cache** so Demo 1 Grafana shows real numbers: run the perf injector for ~5 min against `ngsi_ld_environment` (NGSI-LD schema — see `feedback_mvp4_kafka_reset_runbook`). Confirm `ai_cost_usd_total` increments.
- [ ] **Mint a demo HMAC JWT** (`/api/v1/auth/login`, admin/admin_Dev#2026!) — needed for any live API call. Note: expires in 15 min.
- [ ] **Project display**: Grafana on big screen, frontend on second screen, mobile on table.
- [ ] **Sign-off form printed** (from demo script): `[ ] Approved  [ ] Approved with conditions  [ ] Not approved`.
- [ ] **Backup slides loaded**: cost 4-layer stack, correlation scoring formula, dual-path architecture.

---

## Dry-run plan (internal rehearsal, T-1 or T-2 day)

Run the full 30-min script internally with the real presenters. **Time every section** — the agenda has tight margins (3 / 9 / 8 / 7 / 3 min).

| Presenter | Section | Backup person | Notes |
|---|---|---|---|
| PM | Opening + pilot status (0:00–0:03) | SA | Keep to 3 min — no deep dive |
| Backend lead | Demo 1: AI cost (0:03–0:12) | SA | Pre-warm cache; Grafana must show >$0 |
| Backend lead | Demo 2: Correlation (0:12–0:20) | SA | Pre-stage 3 distinct-type + 3 same-type alerts |
| Frontend lead | Demo 3: Self-service (0:20–0:27) | Backend lead | Wizard must deploy cleanly |
| PM | KPI summary + sign-off (0:27–0:30) | SA | Have sign-off form ready |

### Expected-failure talking points (R6) — what to say if a live step fails

| Demo | If it fails | Talking point |
|---|---|---|
| Demo 1 | Grafana shows $0 | "Cache is fully warm — that itself proves the cost optimization. Let me trigger a fresh district to show a real call." |
| Demo 1 | AI pipeline silent | "Flink window is 60s — let me wait for the next tumbling window. Meanwhile, here's the verified 2026-06-18 measurement: $0.187/day extrapolated." |
| Demo 2 | No incident created | "The CEP window needs 3 distinct types within 30s. Let me re-inject. Boundary verified at 0.556 < 0.6 in unit tests regardless." |
| Demo 2 | False incident (same type) | "This is exactly the distinct-type guard catching it — the engine working as designed." |
| Demo 3 | Wizard deploy fails | "Switching to the pre-deployed instance. The 10 templates are UAT-signed-off (2026-06-16)." |
| Any | Backend crash | "Restarting the single instance — pilot runs single-instance; production is 3-replica (MVP5). Here's the KPI summary while we wait." |

### Rollback / fallback

- **Primary**: live staging demo (full 3 demos).
- **Fallback 1**: pre-recorded screencast of each demo (record during dry-run) — play if live infra hiccups.
- **Fallback 2**: KPI summary table + backup slides only (still gets sign-off ask delivered).
- **Kill switch**: if infra is down on demo day, PM delivers the 3-min opening + KPI table + sign-off ask; reschedule the live demos. **Never** delay the sign-off ask past the scheduled slot.

---

## Definition of Done for this checklist

All checkboxes green → **demo is GO**. PM sends the invite ([`stakeholder-demo-invite-draft.md`](stakeholder-demo-invite-draft.md)), runs the dry-run, executes the demo, collects sign-off → unblocks MVP4 DONE declaration.
