# Sprint MVP3-4 — Gate Review Checklist

**Sprint:** MVP3-4 (Observability + Predictive AI Foundation)  
**Gate Review:** 2026-06-13 15:00 SGT  
**QA Owner:** QA Lead  
**Last updated:** 2026-05-25  
**Status:** 🟡 IN PROGRESS — automated gates PASS, live env gates PENDING

---

## Tổng Quan

| Category | Gates Total | PASS | PENDING | FAIL |
|---|---|---|---|---|
| Automated Tests | 3 | ✅ 3 | — | — |
| Coverage | 4 | ✅ 4 | — | — |
| QA Artifacts | 5 | ✅ 4 | ⏸ 1 | — |
| Live Environment | 6 | — | ⏸ 6 | — |
| SA Code Review | 1 | — | ⏸ 1 | — |
| **TOTAL** | **19** | **✅ 11** | **⏸ 8** | **❌ 0** |

> **Verdict:** Tất cả automated gates PASS. 8 gates còn lại cần live environment (Docker stack) — không thể verify offline. **0 FAIL.** ✅

---

## 1. Automated Tests ✅ (3/3 PASS)

- [x] **G9** — `./gradlew testUnit` → **739 tests PASS, 0 failures** (AC-05)
  - Evidence: `sprint4-test-session-report-2026-05-25.md` §1 Executive Summary
  - Timestamp: 2026-05-25

- [x] **G9b** — `./gradlew integrationTest` → **19 tests PASS, 0 failures**
  - Evidence: `sprint4-test-session-report-2026-05-25.md` §3

- [x] **G9c** — Frontend Vitest → **180 tests PASS, 51 skipped (known), 0 failures**
  - Evidence: `sprint4-test-session-report-2026-05-25.md` §5

---

## 2. JaCoCo Coverage ✅ (4/4 PASS)

- [x] **AC-02 / G5** — Forecast module LINE ≥ 85%: **96.5%** ✅ (was 21.9% BLOCKER)
  - Fixed: 2026-05-25 — 44 unit tests added across 7 test classes
  - Evidence: JaCoCo XML `jacocoTestUnitReport.xml` → package `com/uip/backend/forecast`

- [x] **AC-02 / G5b** — Forecast module BRANCH ≥ 85%: **92.3%** ✅ (was 15.4% BLOCKER)

- [x] **Overall LINE ≥ 80%**: **87.7%** ✅
  - Evidence: JaCoCo XML overall counter

- [x] **Overall BRANCH ≥ 65%**: **71.4%** ✅

---

## 3. QA Artifacts ✅ (4/5) ⏸ (1/5)

- [x] **Sprint 4 Test Strategy** — `docs/mvp3/qa/sprint4-test-strategy.md`
  - Created: 2026-05-25 — 10 quality gates, 12 manual TCs, risk register, gate checklist

- [x] **Test Session Report updated** — `docs/mvp3/testing/sprint4-test-session-report-2026-05-25.md`
  - Updated: 2026-05-25 — AC-02 status corrected, coverage numbers updated, 739 tests

- [x] **Bug Tracker updated** — `docs/mvp3/qa/bug-tracker.md`
  - Sprint label → MVP3-4, gate date → 2026-06-13

- [x] **Sprint Gate Verify Script updated** — `scripts/sprint-gate-verify.sh`
  - Threshold updated: 664 → 739 (AC-05)

- [ ] **⏸ AC-04 LSTM Evaluation Document**
  - Cần Tech Lead viết spike evaluation doc trước day-8 gate (2026-06-11)
  - Location: `docs/mvp3/reports/sprint4-lstm-spike-evaluation.md` (chưa tồn tại)
  - Owner: Tech Lead / ML Engineer

---

## 4. SA Code Review ⏸ (0/1)

> Per `CLAUDE.md`: *"Sau khi Backend/Frontend agent hoàn thành implementation, PHẢI chạy SA Code Review trước khi giao cho DevOps deploy."*

- [ ] **⏸ SA Code Review — Sprint 4 changes**
  - Scope: 7 new forecast test classes + `TraceIdFilter`, `CitizenService` changes
  - Scope: `ForecastController`, `ForecastServiceAdapter`, `NaiveForecastAdapter` source files
  - Output: `docs/mvp3/reports/sprint4-code-review.md`
  - Owner: Solution Architect
  - Checklist: Unused imports, Spring bean registration, Null safety, Exception handling, JWT claims, Resource leak, Thread safety, Config env vars, Dependency license, API contract match frontend

---

## 5. Live Environment Gates ⏸ (0/6) — Requires Docker Stack

> Cần: `cd infrastructure && docker-compose up -d --build` + monitoring stack  
> Script: `./scripts/sprint-gate-verify.sh` (~2 giờ)

### AC-01 — Observability
- [ ] **G1** — Prometheus 3 targets health: `uip-backend`, `analytics-service`, `kong` → `"health":"up"`
  - Command: `curl http://localhost:9090/api/v1/targets | jq '.data.activeTargets[].labels.job'`
  - Manual TC: TC-S4-07

- [ ] **G2** — Grafana accessible + 8 forecast panels load with data (no "No data")
  - URL: `http://localhost:3001` → Dashboard: `UIP Forecast Service`
  - Manual TC: TC-S4-06

- [ ] **G3** — HPA analytics-service scales 2→6 replicas under load *(k8s cluster — can demo on staging)*
  - Command: `kubectl describe hpa analytics-service-hpa`
  - Manual TC: TC-S4-08

### AC-02 — Forecast API
- [ ] **G4** — Forecast API happy path TC-S4-01: HTTP 200, `adapter: "ARIMA"`, 30 forecast entries
  - Precondition: Building B1 has ≥ 24 months historical energy data

- [ ] **G6** — MAPE < 15%: `curl .../forecast/energy... | jq '.mape'` < 0.15
  - Manual TC: TC-S4-05
  - Fallback: If ARIMA MAPE > 15%, switch to `NAIVE_ROLLING` and document

### AC-03 — Forecast Frontend
- [ ] **G7** — Forecast chart renders: confidence band visible, anomaly markers shown
  - URL: ESG Dashboard → Energy → Forecast tab → Building B1
  - Manual TC: TC-S4-09

---

## 6. Regression Manual TCs ⏸ — Requires Live App

| TC | Title | AC | Status |
|---|---|---|---|
| TC-S4-01 | Forecast API happy path | AC-02 | [ ] |
| TC-S4-02 | Missing tenant → 403 | AC-02 security | [ ] |
| TC-S4-03 | horizonDays 0/91 → 400 | AC-02 boundary | [ ] |
| TC-S4-04 | Naive fallback when Python down | AC-02 resilience | [ ] |
| TC-S4-05 | MAPE backtest < 15% | AC-02 ML quality | [ ] |
| TC-S4-06 | Grafana 8 panels with data | AC-07 | [ ] |
| TC-S4-07 | Prometheus 3 targets up | AC-01 | [ ] |
| TC-S4-08 | HPA scales under load | AC-01 | [ ] |
| TC-S4-09 | Forecast chart confidence band | AC-03 | [ ] |
| TC-S4-10 | Regression: GRI Excel export | AC-05 | [ ] |
| TC-S4-11 | Regression: Keycloak RS256 | AC-05 | [ ] |
| TC-S4-12 | Regression: Flink enrichment district | AC-05 | [ ] |

---

## 7. E2E Playwright ⏸ — Requires Live App

```bash
cd frontend
npx playwright test auth.spec.ts dashboard.spec.ts alert-pipeline.spec.ts esg-reports.spec.ts
# Expected: 4 P0 specs PASS
```

| Spec | Priority | Status |
|---|---|---|
| `auth.spec.ts` | P0 | [ ] |
| `dashboard.spec.ts` | P0 | [ ] |
| `alert-pipeline.spec.ts` | P0 | [ ] |
| `esg-reports.spec.ts` | P1 (regression) | [ ] |

---

## 8. Sign-off Requirements

**Gate HARD PASS requires ALL of:**
- [ ] G1–G10 từ sprint4-test-strategy.md: tất cả PASS
- [ ] 0 P0 open bugs (bug-tracker.md)
- [ ] 0 P1 open bugs hoặc tất cả có PM risk-accepted sign-off
- [ ] SA Code Review: Tech Lead approved
- [ ] PO sign-off: demo + UAT verified

| Role | Sign-off | Date |
|---|---|---|
| QA Lead | [ ] Approved | __________ |
| Tech Lead | [ ] Approved | __________ |
| PO | [ ] Approved | __________ |

---

## Execution Order (Day of Gate — 2026-06-13)

```
08:00  Docker Compose up --build (full stack + monitoring)
       ./scripts/sprint-gate-verify.sh  [~2h automated]

10:30  Manual TCs: TC-S4-01 → TC-S4-05 (Forecast API + MAPE)
       Manual TCs: TC-S4-06, TC-S4-07 (Prometheus/Grafana)
       
11:30  E2E Playwright P0 specs (auth, dashboard, alert-pipeline, esg-reports)
       TC-S4-10, TC-S4-11, TC-S4-12 (Sprint 3 regression)

13:00  Results collation → bug-tracker.md final update
       Sign-off collection (QA → Tech Lead → PO)

15:00  Gate Review presentation to PO
```
