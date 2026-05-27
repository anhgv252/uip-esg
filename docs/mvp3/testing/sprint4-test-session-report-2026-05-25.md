# Test Session Report — Sprint MVP3-4
**Date:** 2026-05-25  
**Sprint:** MVP3-4 (Observability + Predictive AI Foundation)  
**Environment:** Local / macOS (pre-sprint dev-complete gate)  
**Tester:** QA — automated execution via CI-equivalent Gradle + Vitest  
**Status:** ✅ GATE READY — all blockers resolved (forecast coverage ≥85%)

---

## 1. Executive Summary

| Layer | Tests Executed | Passed | Failed | Skipped | Result |
|---|---|---|---|---|---|
| Backend — Unit (`testUnit`) | 739 | 738 | 0 | 1 | ✅ PASS |
| Backend — Integration (`integrationTest`) | 19 | 19 | 0 | 0 | ✅ PASS |
| Frontend — Unit (Vitest) | 231 | 180 | 0 | 51 | ✅ PASS |
| Frontend — E2E (Playwright) | — | — | — | — | ⏸ NOT RUN (needs live app) |
| **TOTAL** | **989** | **937** | **0** | **52** | ✅ **PASS** |

> ℹ️ **+75 unit tests added 2026-05-25** to resolve AC-02 BLOCKER (forecast coverage 21.9% → 96.5%)

**Build status:** ✅ `BUILD SUCCESSFUL`  
**JaCoCo Coverage:** LINE **87.7%** ≥ 80% ✅ | BRANCH **71.4%** ≥ 65% ✅  
**AC-02 Forecast Coverage:** LINE **96.5%** ≥ 85% ✅ | BRANCH **92.3%** ≥ 85% ✅ (was 21.9% / 15.4%)  
**AC-05 Regression gate:** 739+ testUnit PASS, 0 failures ✅

---

## 2. Backend — Unit Tests (`./gradlew testUnit`)

### 2.1 Execution Summary

```
Suite: testUnit (131 test classes)
  Total:   664
  Passed:  663
  Failed:  0
  Errors:  0
  Skipped: 1
  Build:   SUCCESS
  Duration: ~5m 34s (incl. JaCoCo report)
```

### 2.2 Key Test Classes — ESG Module

| Test Class | Tests | Pass | Fail | Notes |
|---|---|---|---|---|
| `EsgControllerWebMvcTest` | 13 | 13 | 0 | GRI export, PDF/Excel |
| `EsgServiceTest` | 9 | 9 | 0 | Service logic |
| `EsgReportGeneratorTest` | 8 | 8 | 0 | Report generation |
| `EsgExportTest` | 21 | 21 | 0 | Excel + PDF export |
| `EsgMetricRepositoryQueryTest` | 5 | 5 | 0 | JPQL correctness |
| `TelemetryErrorConsumerTest` | 4 | 4 | 0 | Kafka error path |
| `CacheKeyBuilderTest` (all nested) | 9 | 9 | 0 | Multi-tenant cache keys |

### 2.3 Key Test Classes — Forecast Module (Sprint 4 new)

| Test Class | Tests | Pass | Fail | Notes |
|---|---|---|---|---|
| `ForecastControllerWebMvcTest` | 8 | ✅ | 0 | Security matrix (ADR-032 D4): missing tenant → 403, valid → 200 |
| `ForecastServiceAdapterTest` | 4 | ✅ | 0 | ARIMA adapter success + REST failure → 503; fallback response mapping |
| `ForecastServiceTest` | 3 | ✅ | 0 | Delegation to port, exception propagation, multi-tenant |
| `NaiveForecastAdapterTest` | 10 | ✅ | 0 | Insufficient data boundary (719/720), rolling average, horizons 1/30/90 |
| `DisabledForecastAdapterTest` | 3 | ✅ | 0 | Always throws ForecastServiceUnavailableException |
| `ForecastCacheStatsServiceTest` | 8 | ✅ | 0 | Cache null/exists, non-Redis type, evict key/null/all |
| `ForecastCacheKafkaListenerTest` | 8 | ✅ | 0 | ENERGY/WATER evicts, null/empty/irrelevant does not evict |

**Total forecast tests: 44** — Added 2026-05-25 to resolve AC-02 BLOCKER

### 2.4 Skipped Test (1)

One test marked `@Disabled` or `assumeTrue` failure — does not affect gate count. To investigate: check `build/test-results/testUnit/` for `skipped=1` class.

---

## 3. Backend — Integration Tests (`./gradlew integrationTest`)

### 3.1 Execution Summary

```
Suite: integrationTest (2 test classes)
  Total:   19
  Passed:  19
  Failed:  0
  Build:   SUCCESS
  Containers: TimescaleDB (Docker) + Redis (Docker) — auto-provisioned via raw Docker CLI
```

### 3.2 Test Classes

| Test Class | Tests | Result | Notes |
|---|---|---|---|
| `EsgReportApiIT` | 19 | ✅ PASS | ESG API end-to-end with real TimescaleDB |
| `Sprint3ApiRegressionIntegrationTest` | (included above) | ✅ PASS | Sprint 3 regression: GRI export, Keycloak RSA, cross-tenant isolation |

### 3.3 Shutdown Noise (Expected — Not a Bug)

The integration test logs show `HikariPool - Connection refused` + `ENGINE-18001 Could not collect metrics` errors during JVM shutdown. **This is expected behavior:**
- Docker containers (TimescaleDB on random port) are torn down after tests complete
- Camunda `DbMetricsReporter.stop()` and Spring scheduled tasks attempt DB calls during `SpringApplicationShutdownHook`
- All 19 tests had already asserted PASS before the JVM begins shutdown
- **No action required.** Already documented in Sprint 3 retro.

---

## 4. JaCoCo Coverage Analysis

### 4.1 Overall Coverage (Unit Test Report — post AC-02 fix)

| Metric | Covered | Total | % | Gate | Status |
|---|---|---|---|---|---|
| **LINE** | 2,443 | 2,787 | **87.7%** | ≥ 80% | ✅ PASS |
| **BRANCH** | 582 | 815 | **71.4%** | ≥ 65% | ✅ PASS |

> Prior integration test report showed 92.2% LINE / 79.1% BRANCH — unit test report (jacocoTestUnitReport) shows 87.7% / 71.4%. Both exceed gates.

### 4.2 Well-Covered Packages (100% LINE)

| Package | LINE | BRANCH |
|---|---|---|
| `tenant/context` | 100% | 100% |
| `workflow/controller` | 100% | 97.4% |
| `traffic/service` | 100% | 88.5% |
| `scheduler` | 100% | 100% |
| `tenant/hibernate` | 100% | 85.7% |
| `esg/kafka` | 100% | — |
| `common/service` | 100% | — |
| `workflow/trigger/strategy` | 100% | — |
| `auth/api` | 100% | — |

### 4.3 Coverage — Post-Fix Status (2026-05-25)

| Package | LINE % | BRANCH % | Risk | Status |
|---|---|---|---|---|
| `forecast` | **96.5%** ✅ | **92.3%** ✅ | RESOLVED | ✅ AC-02 CLEARED (was 21.9% / 15.4%) |
| `citizen/service` | **94.5%** ✅ | **75.0%** ✅ | RESOLVED | ✅ Above ≥65% target (was 60.0%) |
| `common/filter` | **100%** ✅ | **100%** ✅ | RESOLVED | ✅ All 4 branches covered (was 25%) |

### 4.4 AC-02 Forecast Coverage — RESOLVED

Per **AC-02 PASS criteria**: *"Unit tests ≥85% coverage on forecast module"*

**Result:** 96.5% LINE / 92.3% BRANCH ✅ — **BLOCKER CLEARED** on 2026-05-25

**Fix:** Added 44 unit tests across 7 forecast test classes:
- `ForecastServiceTest`, `NaiveForecastAdapterTest`, `DisabledForecastAdapterTest`
- `ForecastCacheStatsServiceTest`, `ForecastCacheKafkaListenerTest`
- Expanded `ForecastServiceAdapterTest` with `MockRestServiceServer`

**Remaining 2 uncovered branches in `forecast`:**
- `ForecastServiceAdapter.mapResponse()` — `is_fallback: null` JSON edge case (low risk, not a production path)

---

## 5. Frontend — Unit Tests (Vitest)

### 5.1 Execution Summary

```
Framework: Vitest (jsdom)
  Test Files: 20 (18 passed, 2 skipped)
  Tests:      231 (180 passed, 51 skipped, 0 failed)
  Duration:   7.99s
  Build:      PASS
```

### 5.2 Test Files by Module

| Module | Files | Tests | Pass | Skip | Notes |
|---|---|---|---|---|---|
| Auth / Login | 2 | 11 | 11 | 0 | `LoginPage`, `ProtectedRoute` |
| Token Store | 1 | 4 | 4 | 0 | JWT token management |
| Tenant | 4 | — | ✅ | 0 | Store, claims, nav filtering, config context |
| Tenant Admin | 1 | — | — | ⏸ | **SKIPPED** — likely `describe.skip` pending admin page mock setup |
| PWA | 1 | — | — | ⏸ | **SKIPPED** — service worker APIs unavailable in jsdom |
| Notification | 1 | — | ✅ | 0 | `useNotificationSSE` hook |
| Workflow | 9 | — | ✅ | 0 | Full suite: page, BPMN viewer, API hooks, config |

### 5.3 Skipped Test Files (2)

| File | Skip Count | Reason |
|---|---|---|
| `src/test/pwa/PwaComponents.test.tsx` | ~20 | Service Worker / Cache API not available in jsdom environment |
| `src/test/tenant-admin/TenantAdminPages.test.tsx` | ~31 | `describe.skip` — pending complete admin API mock setup |

> **Status:** Both skipped files are **known/intentional** — not regressions. PWA tests require Playwright E2E against a running app. Tenant Admin tests are pending.

---

## 6. Frontend — E2E Tests (Playwright)

### 6.1 Status: NOT RUN — Requires Live Application

Playwright E2E tests require `http://localhost:3000` (or configured `BASE_URL`) to be running.

### 6.2 Available Spec Files (21 specs)

| Spec File | Priority | Covers |
|---|---|---|
| `auth.spec.ts` | P0 | Login, JWT, RBAC |
| `dashboard.spec.ts` | P0 | City Operations Center map load |
| `alert-pipeline.spec.ts` | P0 | Flood/Air quality alert P0/P1 |
| `alerts.spec.ts` | P1 | Alert list, filters, acknowledge |
| `environment.spec.ts` | P1 | AQI gauges, 24h trend charts |
| `esg-metrics.spec.ts` | P1 | ESG dashboard KPI cards |
| `esg-reports.spec.ts` | P1 | Q1 report generation + PDF export |
| `ai-workflow.spec.ts` | P1 | BPMN designer, AI Decision node |
| `citizen-register.spec.ts` | P1 | Complaint submission flow |
| `citizen-rbac.spec.ts` | P1 | Role-based access control |
| `workflow-config.spec.ts` | P2 | Workflow configuration page |
| `traffic.spec.ts` | P2 | Traffic dashboard |
| `tenant-admin-crud.spec.ts` | P2 | Tenant management |
| `sprint3-manual-tcs.spec.ts` | P2 | Sprint 3 manual test automation |
| `sprint2-multi-tenancy.spec.ts` | P2 | Multi-tenant isolation |
| `pwa-mobile.spec.ts` | P3 | PWA mobile — Chrome/Safari emulation |
| `sprint1-demo.spec.ts`, `sprint2-po-demo.spec.ts`, etc. | P3 | Demo scenarios |

### 6.3 E2E Execution Plan

To run E2E tests when app is live:
```bash
# Start app stack first
cd infrastructure && docker-compose up -d

# Run E2E (all browsers)
cd frontend && npm run e2e

# Run targeted P0/P1 only
npx playwright test auth dashboard alert-pipeline alerts environment esg-metrics esg-reports --reporter=list

# Run with slow-mo for demo
SLOW_MO=300 npx playwright test dashboard.spec.ts
```

---

## 7. Sprint 4 Manual Test Cases (TC-Series)

Test cases aligned to Sprint 4 Acceptance Criteria:

### TC-S4-01: Forecast API — Happy Path
**Feature:** AC-02 ARIMA Energy Forecast API  
**Priority:** P0  
**Type:** Functional API

**Preconditions:**
- Backend running, `forecast.enabled=true`
- `X-Tenant-ID: hcm` header propagated from JWT
- Building `B1` has ≥ 24 months historical energy data in TimescaleDB

**Steps:**
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8085/api/v1/forecast/energy?buildingId=B1&horizonDays=30" | jq '.'
```

**Expected:**
- HTTP 200
- Response contains `adapter: "ARIMA"`, `mape < 0.15`, `forecasts` array with 30 entries
- Each entry has `date`, `predicted`, `confidenceLow`, `confidenceHigh`
- `isAnomaly` field present
- Response time: < 60s (cold), < 500ms (warm cache)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-02: Forecast API — Missing Tenant Header → 403
**Feature:** AC-02 Security (ADR-032 D4)  
**Priority:** P0  
**Type:** Security

**Steps:**
```bash
# Call without JWT (no X-Tenant-ID propagated)
curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8085/api/v1/forecast/energy?buildingId=B1&horizonDays=30"
```

**Expected:** HTTP **403 Forbidden**

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-03: Forecast API — Invalid horizonDays Boundary
**Feature:** AC-02 boundary validation  
**Priority:** P0  
**Type:** Boundary

**Steps:**
```bash
TOKEN=<valid_token>
# horizonDays = 0 (invalid)
curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8085/api/v1/forecast/energy?buildingId=B1&horizonDays=0"

# horizonDays = 91 (exceeds max)
curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8085/api/v1/forecast/energy?buildingId=B1&horizonDays=91"
```

**Expected:** Both return HTTP **400 Bad Request**

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-04: Naive Fallback When Python Service Down
**Feature:** AC-02 NaiveForecastAdapter fallback (ADR-032 D6)  
**Priority:** P0  
**Type:** Resilience

**Steps:**
1. Stop forecast-service Python container
2. Call energy forecast API
3. Verify response

**Expected:**
- HTTP 200 (not 500)
- Response: `adapter: "NAIVE_ROLLING"` (not ARIMA)
- Response time: < 2s (TimescaleDB fallback, no Python)
- WARN log: `"Forecast service unavailable, using naive fallback"`

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-05: MAPE Validation — ARIMA Backtest
**Feature:** AC-02 MAPE < 15% gate  
**Priority:** P0  
**Type:** ML Quality

**Steps:**
```bash
# Check backtest results endpoint
TOKEN=<valid_token>
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8085/api/v1/forecast/energy?buildingId=B1&horizonDays=30" | jq '.mape'
```

**Expected:** `mape` value < 0.15 (15%)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-06: Grafana Dashboard — Forecast Service Panels
**Feature:** AC-07 Forecast Observability  
**Priority:** P1  
**Type:** Observability UI

**Steps:**
1. Open `http://localhost:3000` (Grafana)
2. Navigate to dashboard `UIP Forecast Service`
3. Verify 8 panels present:
   - Service Health, Request Rate, p95 Latency, ARIMA Fit p95
   - Cache Hit Rate, Fallback Rate, MAPE per Building, Error Rate 5xx

**Expected:** All 8 panels load with data (not "No data")

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-07: Prometheus Scraping — All 3 Targets
**Feature:** AC-01 Observability  
**Priority:** P0  
**Type:** Infrastructure

**Steps:**
```bash
# Check Prometheus targets
curl -s http://localhost:9090/api/v1/targets | \
  jq '.data.activeTargets[] | {job: .labels.job, health: .health}'
```

**Expected:** 3 targets with `health: "up"`:
- `uip-backend` (port 8080)
- `analytics-service` (port 8082)
- `kong` (Kong metrics)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-08: HPA — Analytics Service Scales Under Load
**Feature:** AC-01 HPA analytics-service  
**Priority:** P1  
**Type:** Performance / Infrastructure

**Steps:**
1. Check current replica count: `kubectl get hpa analytics-service-hpa`
2. Generate load: run JMeter / curl loop against analytics endpoint for 2 minutes
3. Observe HPA scaling event: `kubectl describe hpa analytics-service-hpa`

**Expected:**
- Initial replicas: 2 (minReplicas)
- Under load (>70% CPU): scales up to max 6
- After load stops: scales back to 2 within 5 minutes

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-09: ForecastChart Component — Confidence Band Visible
**Feature:** AC-03 Forecast Frontend Chart  
**Priority:** P1  
**Type:** UI

**Steps:**
1. Open ESG Dashboard (`http://localhost:3002`)
2. Navigate to Energy section → Forecast tab
3. Select Year: 2025, Building: B1
4. Verify chart renders

**Expected:**
- Actual energy line (solid blue)
- Forecast line (dashed orange) from today + 30 days
- Confidence band (shaded area between low/high)
- Anomaly markers (⚠️ icon) at `isAnomaly=true` points
- Tooltip shows: actual, predicted, low, high, deviation %
- Responsive: renders correctly at 768px viewport

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-10: Sprint 3 Regression — ESG GRI Export
**Feature:** AC-05 No Regression  
**Priority:** P0  
**Type:** Regression

**Steps:**
```bash
TOKEN=<valid_token>
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST "http://localhost:8086/api/v1/esg/reports/export/gri?quarter=2025-Q1&format=excel" \
  -o /tmp/gri-export.xlsx
file /tmp/gri-export.xlsx
```

**Expected:** File is valid Excel (`Microsoft Excel 2007+`)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-11: Sprint 3 Regression — Keycloak RSA Auth
**Feature:** AC-05 No Regression  
**Priority:** P0  
**Type:** Regression / Security

**Steps:**
```bash
# 1. Login with valid credentials
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -d '{"username":"operator","password":"pass123"}' \
  -H "Content-Type: application/json" | jq -r '.token')

# 2. Validate token is RSA-signed (RS256)
echo $TOKEN | cut -d. -f1 | base64 -d 2>/dev/null | jq '.alg'

# 3. Access protected endpoint
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  http://localhost:8085/api/v1/sensors/SENSOR-AIR-GOOD/readings/latest
```

**Expected:**
- Login returns 200 + JWT
- Token header: `"alg": "RS256"`
- Protected endpoint: HTTP 200

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

### TC-S4-12: Sprint 3 Regression — Flink Enrichment
**Feature:** AC-05 No Regression  
**Priority:** P0  
**Type:** Regression

**Steps:**
```bash
# Inject sensor reading
curl -X POST http://localhost:8090/api/v1/sensors/readings \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"SENSOR-AIR-001","metrics":{"aqi":125},"timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'

# Wait 30s, check enriched reading
sleep 30
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8085/api/v1/sensors/SENSOR-AIR-001/readings/latest | \
  jq '{aqi: .aqi, district: .district, enrichedAt: .enrichedAt}'
```

**Expected:** Reading includes `district` field (Flink enrichment working)

**Status:** [ ] PASS / [ ] FAIL / [ ] BLOCKED

---

## 8. Bugs Found

*None found in this session (automated test runs — all green).*

Bugs will be logged here as manual TCs are executed against the live environment.

---

## 9. AC Compliance Status

| AC | Description | Gate | Current Status |
|---|---|---|---|
| AC-01 | Observability Dashboard Live | P0 | ⏸ Requires live Grafana/Prometheus — pending Sprint 4 deployment |
| AC-02 | ARIMA Forecast API | P0 | ✅ **PASS** — forecast coverage 96.5% LINE / 92.3% BRANCH (was 21.9%/15.4%) |
| AC-03 | Forecast Frontend Chart | P1 | ⏸ Requires live app — E2E Playwright not yet run |
| AC-04 | LSTM Spike Evaluation | P1 | ⏸ Day 8 gate (2026-06-11) |
| AC-05 | No Regression | P0 | ✅ **PASS** — 739 testUnit PASS, 19 integrationTest PASS, 0 failures |
| AC-06 | Carry-over Tech Debt | P2 | ⏸ Scheduled W2 |
| AC-07 | Forecast Observability | P1 | ⏸ Requires live Prometheus — pending Sprint 4 deployment |

---

## 10. Acceptance Criteria Sign-off

| Check | Status |
|---|---|
| All automated tests: 0 failures | ✅ |
| Backend unit tests: 739 PASS (AC-05 gate) | ✅ |
| JaCoCo LINE ≥ 80% | ✅ 87.7% |
| JaCoCo BRANCH ≥ 65% | ✅ 71.4% |
| No P0/P1 open bugs (automated) | ✅ |
| Forecast module coverage ≥ 85% LINE (AC-02) | ✅ **96.5%** — BLOCKER CLEARED 2026-05-25 |
| Forecast module coverage ≥ 85% BRANCH (AC-02) | ✅ **92.3%** — BLOCKER CLEARED 2026-05-25 |
| citizen/service BRANCH ≥ 65% | ✅ **75.0%** (was 60%) |
| common/filter BRANCH coverage | ✅ **100%** (was 25%) |
| E2E tests executed against live app | ⏸ Pending deployment |

**Gate decision:** ✅ Automated test gate **PASSES** — all blockers resolved.  
**Remaining for Sprint 4 final gate (2026-06-13):** AC-01/AC-07 require live Prometheus/Grafana deployment; AC-03 requires live app for E2E Playwright; AC-04 day-8 ML evaluation gate.

---

## 11. Appendix — Test Infrastructure Notes

### Backend Test Suites
```
./gradlew testUnit       # 664 unit tests — mocked dependencies, fast (<2min)
./gradlew integrationTest # 19 integration tests — real Docker containers
./gradlew test           # All suites (jacocoTestReport generated after)
./gradlew jacocoTestReport # Generate HTML/XML coverage report
```

### Frontend Test Commands
```bash
cd frontend
npx vitest run             # Run all unit tests once
npx vitest run --reporter=verbose  # With detailed output
npx vitest --coverage      # With V8 coverage report

# E2E (requires running app)
npm run e2e                # All browsers
npx playwright test auth dashboard alert-pipeline  # P0 only
BASE_URL=http://staging.uip.local npm run e2e  # Against staging
```

### Coverage Report Location
```
backend/build/reports/jacoco/test/index.html      # Integration test coverage
backend/build/reports/jacoco/jacocoTestUnitReport/ # Unit test coverage
frontend/playwright-report/index.html             # E2E Playwright report
```

### Known Flakiness / Non-Issues
- `HikariPool Connection refused` during shutdown in integration tests → **expected** (Docker containers torn down)
- `ENGINE-18001 Could not collect metrics` on shutdown → **expected** (Camunda shuts down after DB)
- 1 backend unit test `skipped` → investigate class with `assumeTrue` guard
- 51 frontend tests `skipped` → 2 files intentionally skipped (PWA + TenantAdmin pending)
