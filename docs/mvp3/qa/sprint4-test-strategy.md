# Sprint MVP3-4 — Test Strategy

**Sprint:** MVP3-4 (2026-06-02 → 2026-06-13)  
**Gate Review:** 2026-06-13 15:00 SGT  
**QA Owner:** QA Lead  
**Status:** ACTIVE

---

## Sprint Goal

**Observability + Predictive AI Foundation:** Ship Prometheus/Grafana observability stack, ARIMA energy forecast API with Naive fallback, and Forecast UI chart component. Validate MAPE < 15% on historical backtest. All Sprint 3 functionality must remain regression-free.

---

## Test Scope

### In Scope

| Area | Stories | Test Level | AC |
|---|---|---|---|
| Prometheus metrics scraping (backend + analytics + Kong) | S4-01 | Manual + Infrastructure | AC-01 |
| Grafana dashboard: 8 forecast panels | S4-02 | Manual | AC-01/AC-07 |
| HPA autoscaling: analytics-service (2→6 replicas) | S4-03 | Manual + Load | AC-01 |
| ARIMA forecast API `GET /api/v1/forecast/energy` | S4-04 | Unit + IT + Manual | AC-02 |
| NaiveForecastAdapter — rolling average fallback | S4-05 | Unit | AC-02 |
| DisabledForecastAdapter — no-op guard | S4-05 | Unit | AC-02 |
| ForecastCacheStatsService — Caffeine/Redis cache stats | S4-06 | Unit | AC-02 |
| ForecastCacheKafkaListener — cache eviction on ESG events | S4-07 | Unit + IT | AC-02 |
| ForecastServiceAdapter — Python REST client | S4-08 | Unit + MockRestServer | AC-02 |
| Forecast UI chart — confidence band, anomaly markers | S4-09 | Manual + E2E | AC-03 |
| LSTM spike evaluation (experimental) | S4-11 | Manual review | AC-04 |
| Forecast coverage ≥ 85% LINE + BRANCH | S4-12 | JaCoCo report | AC-02 |
| TraceIdFilter — X-Trace-Id propagation | S4-13 | Unit | AC-05 |
| No Sprint 3 regression | S4-14 | Automated + Manual | AC-05 |

### Out of Scope

- BMS direct integration (Sprint 5)
- Mobile app testing (Sprint 5)
- LSTM production deployment (Sprint 5)
- Building structural safety monitoring (Sprint 6)

---

## Quality Gates (10/10 Required for HARD PASS)

| Gate | ID | Criteria | Verification Method | Owner |
|---|---|---|---|---|
| G1 | AC-01 | Prometheus scrapes 3 targets (`health: "up"`) | `curl /api/v1/targets` | DevOps |
| G2 | AC-01 | Grafana 8 forecast panels load with data (no "No data") | Manual TC-S4-06 | QA |
| G3 | AC-01 | HPA scales analytics-service from 2→6 under load | `kubectl describe hpa` | QA + DevOps |
| G4 | AC-02 | Forecast API returns 200 + valid ARIMA response | Manual TC-S4-01 | QA |
| G5 | AC-02 | Unit test coverage: forecast module LINE ≥ 85% BRANCH ≥ 85% | JaCoCo XML report | QA |
| G6 | AC-02 | MAPE < 15% on 30-day horizon backtest | `jq '.mape'` TC-S4-05 | QA + Data |
| G7 | AC-03 | Forecast chart renders confidence band + anomaly markers | Manual TC-S4-09 / E2E | QA |
| G8 | AC-04 | LSTM spike evaluation document approved by tech lead | Docs review | Arch + Tech Lead |
| G9 | AC-05 | Zero regression: 739+ testUnit PASS, 0 failures | `./gradlew testUnit` | QA |
| G10 | AC-05 | Zero P0/P1 bugs open at gate | bug-tracker.md | All |

### Conditional Pass Thresholds

| Gate | Borderline | Action |
|---|---|---|
| G5 (LINE coverage) | 83–84% | Extend 1 day; < 83% escalate Tech Lead |
| G6 (MAPE) | 15–18% | ARIMA hyperparameter tuning; > 18% → use NAIVE_ROLLING only |
| G9 (regression) | 1 failure if `@Disabled` | Must document reason; test class cannot be new code |

---

## Test Levels

### 1. Unit Tests (`./gradlew testUnit`)

#### Forecast Module — 44 tests (added 2026-05-25)

| Test Class | Tests | Coverage Target | What Is Tested |
|---|---|---|---|
| `ForecastControllerWebMvcTest` | 8 | Controller layer 100% | Missing tenant→403, blank→403, horizon 0/91/365→400, serviceUnavailable→503, valid→200 |
| `ForecastServiceTest` | 3 | Service delegation | Port delegation, exception propagation, multi-tenant |
| `NaiveForecastAdapterTest` | 10 | All branches | Insufficient data (<720), boundary (719/720), rolling avg, 1/30/90 day horizons, upper/lower bounds |
| `DisabledForecastAdapterTest` | 3 | Single path | Always throws with correct message |
| `ForecastCacheStatsServiceTest` | 8 | All branches | Cache null, non-Redis, evict by key, evict null key, evict all, noop when null |
| `ForecastCacheKafkaListenerTest` | 8 | All branches | ENERGY evicts, WATER evicts, null/empty/irrelevant/lowercase does not |
| `ForecastServiceAdapterTest` | 4 | Success + failure | HTTP 200 maps to ForecastResult, HTTP 500 throws Unavailable, fallback response |

#### Common / TraceIdFilter — 6 tests (added 2026-05-25)

| Test Class | Tests | Coverage Target | What Is Tested |
|---|---|---|---|
| `TraceIdFilterTest` | 6 | 100% LINE + BRANCH | Provided header, null header generates UUID, blank header generates UUID, MDC cleared after chain, MDC cleared on exception, unique IDs |

#### Citizen Service — expanded (2026-05-25)

| Test Class | New Tests | Coverage Target | What Is Tested |
|---|---|---|---|
| `CitizenServiceTest` | +8 | BRANCH ≥ 75% | Household already linked, citizen not found, getProfile with household, getProfile without household, building lookup returns null, getBuildingsByDistrict empty/non-empty, username collision retry |

### 2. Integration Tests (`./gradlew integrationTest`)

| Test Class | Tests | Container Dependencies | What Is Tested |
|---|---|---|---|
| `EsgReportApiIT` | 19 | TimescaleDB + Redis | ESG API end-to-end, export, multi-tenant isolation |
| `Sprint3ApiRegressionIntegrationTest` | — | TimescaleDB | Sprint 3 regression: GRI export, Keycloak RSA, cross-tenant |

> **Note:** `ForecastCacheKafkaListener` IT with embedded Kafka is **deferred to Sprint 5** — requires Testcontainers Kafka setup not yet configured.

### 3. Manual Test Cases

See Section 7 in [sprint4-test-session-report-2026-05-25.md](../testing/sprint4-test-session-report-2026-05-25.md) for full TC-S4-01 to TC-S4-12 steps.

| TC | Title | AC | Priority | Environment |
|---|---|---|---|---|
| TC-S4-01 | Forecast API happy path | AC-02 | P0 | Live (Docker Compose) |
| TC-S4-02 | Forecast API: missing tenant → 403 | AC-02 | P0 | Live |
| TC-S4-03 | Forecast API: boundary horizonDays 0/91 → 400 | AC-02 | P0 | Live |
| TC-S4-04 | Naive fallback when Python service down | AC-02 | P0 | Live |
| TC-S4-05 | MAPE validation: ARIMA backtest < 15% | AC-02 | P0 | Live |
| TC-S4-06 | Grafana: 8 forecast panels load with data | AC-01/AC-07 | P1 | Live (Grafana) |
| TC-S4-07 | Prometheus: 3 targets health:up | AC-01 | P0 | Live |
| TC-S4-08 | HPA autoscaling under load | AC-01 | P1 | k8s cluster |
| TC-S4-09 | Forecast chart: confidence band + anomaly markers | AC-03 | P1 | Live (Frontend) |
| TC-S4-10 | Regression: ESG GRI Excel export | AC-05 | P0 | Live |
| TC-S4-11 | Regression: Keycloak RSA RS256 auth | AC-05 | P0 | Live |
| TC-S4-12 | Regression: Flink enrichment district field | AC-05 | P0 | Live |

### 4. E2E Tests (Playwright) — Requires Live App

Target specs for Sprint 4 gate:

```bash
# P0 regression (must pass)
npx playwright test auth.spec.ts dashboard.spec.ts alert-pipeline.spec.ts esg-reports.spec.ts

# Sprint 4 new (forecast)
npx playwright test esg-metrics.spec.ts  # includes forecast tab when added
```

---

## Coverage Targets (AC-02)

| Package | LINE Target | BRANCH Target | Current (2026-05-25) | Status |
|---|---|---|---|---|
| `forecast` | ≥ 85% | ≥ 85% | 96.5% / 92.3% | ✅ PASS |
| `citizen/service` | ≥ 85% | ≥ 65% | 94.5% / 75.0% | ✅ PASS |
| `common/filter` | ≥ 80% | ≥ 80% | 100% / 100% | ✅ PASS |
| **Overall project** | ≥ 80% | ≥ 65% | 87.7% / 71.4% | ✅ PASS |

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Python forecast-service MAPE > 15% | MEDIUM | HIGH (AC-02 fails) | NaiveForecastAdapter always available as fallback; ARIMA MAPE is advisory for sprint 4 |
| Grafana panels show "No data" | MEDIUM | MEDIUM (G2 gate) | Verify Prometheus scrape interval ≤ 15s; pre-seed 24h of metrics before gate |
| HPA not triggering in k8s local | HIGH | LOW (G3 gate) | Acceptable to demo on staging; local Docker Compose skips HPA |
| E2E Playwright flakiness on forecast chart | MEDIUM | LOW | Use `--retries 2`; chart assertions with `waitForSelector` |
| LSTM evaluation delayed | LOW | LOW (AC-04 is P1, not P0) | Worst case: defer to Sprint 5; gate still passes without AC-04 |

---

## Test Environment Setup

### Backend
```bash
cd backend
./gradlew testUnit                     # 739+ unit tests
./gradlew integrationTest              # 19 IT tests (Docker required)
./gradlew testUnit jacocoTestUnitReport  # with coverage
```

### Live Stack (Manual TCs)
```bash
cd infrastructure
docker-compose up -d  # starts: backend, frontend, timescaledb, redis, kafka, prometheus, grafana
# Wait ~60s for all services healthy
docker-compose ps     # verify all HEALTHY
```

### Coverage Report
```
backend/build/reports/jacoco/jacocoTestUnitReport/index.html   # unit coverage
backend/build/reports/jacoco/test/index.html                   # integration coverage
```

---

## Bug Tracking

All P0/P1 bugs must be logged in [bug-tracker.md](bug-tracker.md) before gate close.

**Current status (2026-05-25):** 0 P0, 0 P1.

---

## Sprint 4 Gate Checklist

**Gate Review:** 2026-06-13 15:00 SGT

- [ ] G1 — Prometheus 3 targets health:up verified
- [ ] G2 — Grafana 8 forecast panels show data
- [ ] G3 — HPA scales analytics-service (or staging evidence)
- [ ] G4 — Forecast API manual TC-S4-01 PASS
- [ ] G5 — JaCoCo forecast LINE ≥ 85% BRANCH ≥ 85% ✅ (already 96.5%/92.3%)
- [ ] G6 — MAPE < 15% verified
- [ ] G7 — Forecast chart TC-S4-09 PASS
- [ ] G8 — LSTM evaluation document reviewed
- [ ] G9 — `./gradlew testUnit` → 0 failures
- [ ] G10 — bug-tracker.md: P0 = 0, P1 = 0 (or PM risk-accepted)

**Sign-off required:** QA Lead + Tech Lead + PO
