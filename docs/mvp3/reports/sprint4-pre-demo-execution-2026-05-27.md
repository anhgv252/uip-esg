# Sprint 4 — Pre-Demo Execution Report

**Date:** 2026-05-27
**Executor:** Tester
**Sprint:** MVP3-4 — Observability + Predictive AI Foundation
**PO:** anhgv

---

## Verdict: GO FOR PO DEMO

**7/7 Demo Scenarios PASS | 19/19 Gates PASS | 0 P0/P1 Bugs**

---

## Pre-Demo Checklist

| Step | Check | Result |
|---|---|---|
| Hot-copies | Re-applied to forecast-service | PASS |
| 8 core services | All healthy | PASS |
| ARIMA MAPE | 3.54% (< 15%) | PASS |
| Grafana | HTTP 200 | PASS |
| Backend | HTTP 200 | PASS |
| Prometheus | 7/7 targets UP | PASS |

---

## Demo Scenario Results

### Scenario 1: System Readiness — PASS
- 8/8 core services healthy
- Prometheus 7/7 targets UP (analytics-service, forecast-service, kafka, kong, postgres, redis, uip-backend)
- Backend health: HTTP 200, status UP

### Scenario 2: Observability Dashboard — PASS
- Grafana v10.4, database OK
- "UIP Forecast Service" dashboard: 8 panels confirmed
- Panels: Service Health, Request Rate, p95 Latency, ARIMA Fit p95, Cache Hit Rate, Fallback Rate, MAPE per Building, Error Rate 5xx
- Prometheus `forecast_arima_mape_ratio`: 3.54% (tenant T001, building B001)
- `forecast_fallback_total`: 0 (no fallbacks)

### Scenario 3: Forecast API Backend — PASS
- `GET /api/v1/forecast/energy?buildingId=...&horizonDays=30` → HTTP 200
- Model: ARIMA, isFallback: false
- MAPE: 3.54% (gate < 15%: PASS)
- Points: 720 hourly predictions (30 days × 24 hours)
- Date range: 2026-05-27T10:00Z → 2026-06-26T09:00Z
- Confidence interval: predictedValue, confidenceLower, confidenceUpper present on every point
- Boundary tests: horizonDays=0 → 400, horizonDays=91 → 400, horizonDays=7 → 200

### Scenario 4: Forecast Frontend Chart — PASS
- Frontend HTTP 200
- TypeScript: 0 errors
- API proxy: HTTP 200
- ForecastChart.tsx + ForecastTooltip.tsx components exist
- EsgPage.tsx imports ForecastChart (line 13, renders at line 212)
- useEnergyForecast hook properly integrated
- NOTE: Visual chart rendering requires browser — API + compile verified programmatically

### Scenario 5: Tenant Isolation — PASS
- admin/default: tenantId=default, model=ARIMA, 168 points
- tadmin/hcm: tenantId=hcm, model=NONE, 0 points (correct — no data seeded for this building in hcm tenant)
- Data properly separated by tenant

### Scenario 6: Security & Model Decision — PASS
- No auth token → HTTP 401 (PASS)
- forecast-service no host port exposed (ADR-032 D1) (PASS)
- Python missing X-Tenant-ID → HTTP 403 (PASS)
- Python XSS X-Tenant-ID → HTTP 400 (PASS)
- Python valid X-Tenant-ID → HTTP 200 (PASS)
- Parameterized queries: `parameters=` at clickhouse_client.py:32 (PASS)
- LSTM NO-GO: ARIMA 3.37% vs LSTM 18.65% — 15.28pp difference, documented

### Scenario 7: Regression & Coverage — PASS
- GRI Excel export: PENDING → DONE, XLSX download HTTP 200 (PK ZIP magic bytes valid)
- Keycloak RS256: 1 RSA signing key, algorithm RS256 (PASS)
- Flink JobManager: reachable (PASS)
- Coverage: 87.7% LINE (target ≥80%) / 71.4% BRANCH (target ≥65%)
- Total tests: 978+ (739 Java unit + 19 IT + 180 FE + 40 Python), 0 failures

---

## Credentials for PO Demo

| Role | Username | Password | Tenant |
|---|---|---|---|
| Admin | `admin` | `admin_Dev#2026!` | default |
| Tenant Admin | `tadmin` | `admin_Dev#2026!` | hcm |
| Grafana | `admin` | `admin` | — |

---

## Known Issues (non-blocking)

| ID | Severity | Description |
|---|---|---|
| BUG-S4-T04 | P2 | Python DOWN → 503, no naive fallback. Deferred Sprint 5. |

---

## QA Sign-off

**Pre-demo verification completed 2026-05-27. All 7 demo scenarios PASS. Sprint 4 is ready for PO demo.**

| Role | Sign-off | Date |
|---|---|---|
| Tester | [x] Verified — 7/7 PASS | 2026-05-27 |
| QA Lead | [x] Approved | 2026-05-27 |
| Tech Lead | [ ] | __________ |
| PO | [ ] | __________ |

---

*Report generated: 2026-05-27 | Tester pre-demo execution*
