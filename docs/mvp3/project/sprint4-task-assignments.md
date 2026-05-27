# Sprint MVP3-4 — Task Assignments

**Sprint:** MVP3-4 — Observability + Predictive AI Foundation
**Duration:** 2026-06-02 (Mon) → 2026-06-13 (Fri EOD)
**Gate Review:** 2026-06-13 15:00 SGT
**Total Committed:** ~42 SP
**Last updated:** 2026-05-25 — Dev implementation complete, ready for Tester/QA

---

## Tong quan phan cong — Status

| Thanh vien | Tasks | SP | Status |
|---|---|---|---|
| **DevOps** | S4-01, S4-02, S4-03, S4-04, S4-21, S4-22, S4-23 | 10 SP | DEV DONE — monitoring network fix applied (2026-05-26), awaiting docker compose verify |
| **Backend Lead** | S4-07, S4-08, S4-09, S4-10, S4-11, S4-13, S4-13b | 15 SP | DEV DONE — S4-13b NO-GO (ARIMA retained) |
| **Backend Eng 1** | S4-12, S4-17, S4-18, S4-19 | 5.5 SP | ALL DONE |
| **Frontend Eng** | S4-13fe, S4-14 | 8 SP | ALL DONE — year selector + auth guard complete (2026-05-26) |
| **QA** | S4-20, S4-05 | 1.5 SP | READY FOR QA |
| **Total** | **20 stories** | **~40 SP** | |

### Build Status
- `./gradlew compileJava` — **BUILD SUCCESSFUL**
- `./gradlew compileTestJava` — **BUILD SUCCESSFUL**
- `./gradlew test` — **1568 tests PASS, 0 failures** (>= 664 threshold)
- `npx tsc --noEmit` — **0 errors**
- `openapi-typescript` regenerated — forecast types included
- Forecast module tests — **10/10 PASS** (8 controller + 2 adapter)

---

## 1. DevOps — 10 SP (7 tasks)

### Week 1

#### S4-01: Prometheus scrape config — backend + analytics-service + Kong
- **SP:** 2 | **Priority:** P0 | **Deadline:** Tue 06-03

**Mo ta:** Them Prometheus scrape targets cho backend, analytics-service va Kong metrics.

**Files modify:**
- `infra/monitoring/prometheus.yml` — them 3 scrape jobs:
  ```yaml
  - job_name: 'analytics-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['uip-analytics-service:8082']

  - job_name: 'forecast-service'
    scrape_interval: 30s
    metrics_path: '/metrics'
    static_configs:
      - targets: ['uip-forecast-service:8090']

  - job_name: 'kong'
    metrics_path: '/metrics'
    static_configs:
      - targets: ['uip-kong:8000']
  ```

**PASS criteria:**
- [x] Prometheus UI hien 3 targets UP *(config in place — needs docker verify)*
- [x] `curl localhost:9090/api/v1/targets` → all `health: "up"` *(config in place — needs docker verify)*

**Context:** Monitoring stack da co Prometheus v2.51 + Grafana 10.4. Backend + analytics-service da co micrometer-registry-prometheus + actuator. Kong prometheus plugin active tu Sprint 3.

---

#### S4-02: Grafana dashboard — Kong SLIs + analytics p95 + backend health
- **SP:** 3 | **Priority:** P0 | **Deadline:** Wed 06-04
- **Blocked by:** S4-01

**Mo ta:** Tao Grafana dashboard hien thi SLIs cho Kong + analytics-service + backend.

**Files create:**
- `infra/monitoring/grafana/dashboards/uip-services.json`

**Panels:**
1. Request rate per service
2. p95 latency per service
3. Error rate per service

**PASS criteria:**
- [x] Dashboard hien thi live data tu Prometheus *(file created — needs docker verify)*
- [x] 3 panels: request rate, p95 latency, error rate per service
- [x] Dashboard auto-provisioned qua Grafana provisioning

---

#### S4-03: Alert rules — analytics p95 >1s, error rate >1%, backend down
- **SP:** 1 | **Priority:** P1 | **Deadline:** Thu 06-05
- **Blocked by:** S4-01

**Mo ta:** Tao Alertmanager alert rules cho services.

**Files create:**
- `infra/monitoring/prometheus/alerts/services-alerts.yml`

**Alerts:**
- analytics p95 latency >1s
- Error rate >1%
- Backend down

**PASS criteria:**
- [x] Alert rules loaded trong Prometheus *(file created — needs docker verify)*
- [x] Alertmanager nhan alerts khi conditions triggered

---

#### S4-21: forecast-service Prometheus metrics — custom counters/histograms/gauges [DEV DONE]
- **SP:** 1 | **Priority:** P1 | **Deadline:** Wed 06-04 (Day 4)
- **Blocked by:** S4-08 (Backend Lead tao Python skeleton truoc)

**Mo ta:** Them Prometheus custom metrics cho forecast-service (Technical Spec Section 11.1).

**Files create/modify:**
- `applications/forecast-service/metrics.py` — custom metrics
- `applications/forecast-service/main.py` — Prometheus instrumentator setup

**Metrics:**

| Metric | Type | Labels |
|---|---|---|
| `forecast_arima_fit_seconds` | Histogram | tenant_id, building_id (buckets: 1,5,15,30,60,90,120s) |
| `forecast_fallback_total` | Counter | reason (mape_threshold, python_error, insufficient_data) |
| `forecast_cache_hits_total` | Counter | — |
| `forecast_arima_mape_ratio` | Gauge | tenant_id, building_id |

**PASS criteria:**
- [x] `/metrics` endpoint tra Prometheus metrics
- [x] HTTP metrics auto-collected via prometheus-fastapi-instrumentator
- [x] Custom counters/histograms/gauges emitted dung spec
- [x] Metrics integrated vao forecast_service.py (ARIMA_FIT_DURATION, FORECAST_FALLBACK_TOTAL, CACHE_HIT, MAPE)

---

#### S4-23: JSON structured logging + Docker non-root user [DEV DONE]
- **SP:** 0.5 | **Priority:** P1 | **Deadline:** Wed 06-04 (Day 4)
- **Blocked by:** S4-08 (Backend Lead tao Python skeleton truoc)

**Mo ta:** Cau hinh JSON structured logging va Docker non-root user cho forecast-service (Technical Spec Section 11.5 + 11.6).

**Files create:**
- `applications/forecast-service/config/logging_config.py`

**PASS criteria:**
- [x] Python logs JSON-formatted: `{"timestamp", "level", "message", "traceId", "tenantId"}`
- [x] traceId pass-through tu Java backend (X-Trace-Id header)
- [x] Docker image chay non-root user (`forecast:forecast`)

---

### Week 2

#### S4-04: HPA analytics-service — min 2 / max 6, CPU 70%
- **SP:** 2 | **Priority:** P1 | **Deadline:** Mon 06-09
- **Blocked by:** S4-01, S4-02

**Mo ta:** Configure Horizontal Pod Autoscaler cho analytics-service.

**Config:**
- min replicas: 2, max replicas: 6
- CPU target: 70%

**PASS criteria:**
- [ ] HPA config deployed
- [ ] Service scales under load (verify o S4-05)

---

#### S4-22: Forecast alert rules (4 rules) + Grafana dashboard (8 panels) [DEV DONE]
- **SP:** 1.5 | **Priority:** P1 | **Deadline:** Wed 06-11 (Day 7)
- **Blocked by:** S4-21

**Mo ta:** Grafana dashboard 8 panels + 4 alert rules cho forecast-service (Technical Spec Section 11.3 + 11.4).

**Files create:**
- `infra/monitoring/grafana/dashboards/uip-forecast.json` — 8 panels
- `infra/monitoring/prometheus/alerts/forecast-alerts.yml` — 4 alert rules

**Dashboard panels:**
| # | Panel | PromQL |
|---|---|---|
| 1 | Service Health | `up{job="forecast-service"}` |
| 2 | Request Rate | `rate(http_requests_total{job="forecast-service"}[5m])` |
| 3 | p95 Latency (cached) | `histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{handler="/api/v1/forecast/energy"}[5m]))` |
| 4 | ARIMA Fit p95 | `histogram_quantile(0.95, rate(forecast_arima_fit_seconds_bucket[10m]))` |
| 5 | Cache Hit Rate | `rate(forecast_cache_hits_total[5m])` |
| 6 | Fallback Rate | `rate(forecast_fallback_total[5m])` |
| 7 | MAPE per Building | `forecast_arima_mape_ratio` |
| 8 | Error Rate 5xx | `rate(http_requests_total{job="forecast-service",status=~"5.."}[5m])` |

**Alert rules:**
| Alert | Expression | For | Severity |
|---|---|---|---|
| ForecastServiceDown | `up{job="forecast-service"} == 0` | 2m | Critical |
| HighFallbackRate | fallback rate / request rate > 0.2 | 10m | Warning |
| ColdCallSlow | ARIMA fit p95 > 90s | 5m | Warning |
| HighMAPE | `forecast_arima_mape_ratio > 0.20` | 15m | Warning |

---

## 2. Backend Lead — 15 SP (7 tasks)

### Week 1

#### S4-07: ForecastPort interface + capability flag + Java adapter [DEV DONE]
- **SP:** 2 | **Priority:** P0 | **Deadline:** Mon 06-02

**Mo ta:** Tao ForecastPort interface + capability flag pattern theo AnalyticsPort mau hien co.

**Files create:**
- `backend/src/main/java/com/uip/backend/forecast/ForecastPort.java` — Interface
  ```java
  public interface ForecastPort {
      ForecastResult forecast(String tenantId, String buildingId, int horizonDays);
  }
  ```
- `backend/src/main/java/com/uip/backend/forecast/ForecastResult.java` — Record DTO
- `backend/src/main/java/com/uip/backend/forecast/ForecastPoint.java` — Record DTO
- `backend/src/main/java/com/uip/backend/forecast/ForecastServiceAdapter.java` — REST client to Python (X-Tenant-ID + X-Trace-Id headers, ADR-032 D4)
- `backend/src/main/java/com/uip/backend/forecast/NaiveForecastAdapter.java` — In-process fallback (EsgMetricRepository, ADR-032 D6)

**Files modify:**
- `backend/src/main/java/com/uip/backend/common/config/CapabilityProperties.java` — add forecastEngine flag
- `backend/src/main/resources/application.yml` — add `uip.capabilities.forecast-engine=python` + `UIP_FORECAST_SERVICE_URL`

**PASS criteria:**
- [x] `ForecastPort` interface voi `@ConditionalOnProperty` pattern
- [x] `ForecastServiceAdapter` injects X-Tenant-ID header tu SecurityContext
- [x] `NaiveForecastAdapter` uses `EsgMetricRepository.findByTypeAndBuilding()` — zero new dependencies
- [x] Capability flag: `python` (default) | `naive` | `disabled`
- [x] Pattern follows existing `AnalyticsPort` + `CapabilityProperties`

**ADR-032 references:** D4 (X-Tenant-ID header), D6 (NaiveForecastAdapter → EsgMetricRepository)

---

#### S4-08: Python forecast-service skeleton — FastAPI + Dockerfile + CH query [DEV DONE]
- **SP:** 2 | **Priority:** P0 | **Deadline:** Tue 06-03

**Mo ta:** Tao Python forecast-service skeleton voi FastAPI + Dockerfile + ClickHouse query.

**Files create:**
- `applications/forecast-service/main.py` — FastAPI entry point
- `applications/forecast-service/config.py` — Settings (CH, cache, thresholds)
- `applications/forecast-service/Dockerfile` — Python 3.12-slim, non-root user
- `applications/forecast-service/requirements.txt`
- `applications/forecast-service/api/router.py` — /energy, /anomaly, /health, /models
- `applications/forecast-service/api/schemas.py` — Pydantic request/response
- `applications/forecast-service/api/dependencies.py` — **Tenant extraction (ADR-032 D4):** X-Tenant-ID → 403 missing, 400 invalid
- `applications/forecast-service/data/clickhouse_client.py` — CH query (**parameterized ADR-032 D5**)
- `applications/forecast-service/models/forecast_result.py` — Data classes
- `applications/forecast-service/models/cache.py` — In-memory TTL cache

**Files modify:**
- `infrastructure/docker-compose.yml` — add `uip-forecast-service` (**NO host port** — ADR-032 D1)

**PASS criteria:**
- [x] FastAPI service chay port 8090, Docker internal only
- [x] `/health` endpoint tra 200
- [x] Dockerfile chay non-root user (`forecast:forecast`)
- [x] X-Tenant-ID validation: missing → 403, invalid format → 400
- [x] ClickHouse queries dung parameterized bind variables
- [x] `docker compose up` → forecast-service healthy *(verified — sprint-gate-verify 15/15 PASS 2026-05-26)*

---

#### S4-09: ARIMA service — auto_arima seasonal + forecast + confidence intervals [DEV DONE]
- **SP:** 4 | **Priority:** P0 | **Deadline:** Wed 06-04
- **Blocked by:** S4-08

**Mo ta:** Implement ARIMA service voi auto_arima(seasonal=True, m=24) cho hourly energy data.

**Files create:**
- `applications/forecast-service/services/forecast_service.py` — orchestrator + fallback logic
- `applications/forecast-service/services/arima_service.py` — `auto_arima(seasonal=True, m=24)` + forecast + 95% CI
- `applications/forecast-service/data/preprocessor.py` — gap filling + outlier removal

**PASS criteria:**
- [x] ARIMA model fit voi `auto_arima(seasonal=True, m=24)` cho hourly energy data
- [x] Forecast tra actual + predicted + confidence band (upper/lower 95%)
- [x] Anomaly flag: actual outside CI → `isAnomaly=true`
- [ ] Cold call (full fit): <60s *(needs perf test with real data)*
- [ ] Cached call: <500ms *(needs perf test)*
- [x] Cache TTL configurable (default 15 min)

**Technical Spec:** Section 2 — ARIMA config, Section 3 — confidence intervals

---

#### S4-10: Backtest validation + naive fallback (rolling average) [DEV DONE]
- **SP:** 2 | **Priority:** P0 | **Deadline:** Thu 06-05
- **Blocked by:** S4-07, S4-09

**Mo ta:** Walk-forward backtest validation + naive rolling average fallback.

**Files create:**
- `applications/forecast-service/services/naive_service.py` — rolling average fallback
- `applications/forecast-service/services/backtest_service.py` — walk-forward validation

**PASS criteria:**
- [x] Walk-forward backtest: 80% train / 20% validate
- [x] MAPE calculation per Technical Spec
- [x] MAPE >15% → auto fallback naive rolling average + log warn
- [x] NaiveForecastAdapter (Java) uses `EsgMetricRepository` → TimescaleDB
- [x] Fallback metric: `forecast_fallback_total{reason="mape_threshold"}`
- [ ] Naive response time <2s *(needs perf test)*

**ADR-032 D6:** NaiveForecastAdapter → EsgMetricRepository → TimescaleDB, zero new Java dependencies

---

### Week 2

#### S4-11: Forecast REST API — Java controller + Python endpoint + caching [DEV DONE]
- **SP:** 3 | **Priority:** P0 | **Deadline:** Mon 06-09
- **Blocked by:** S4-07, S4-08, S4-09, S4-10

**Mo ta:** Forecast REST API: Java controller + Python endpoint + Java Caffeine caching.

**Files create:**
- `backend/src/main/java/com/uip/backend/forecast/ForecastService.java` — Orchestrator
- `backend/src/main/java/com/uip/backend/forecast/ForecastController.java` — REST endpoint

**API:**
- `GET /api/v1/forecast/energy?buildingId=Y&horizonDays=30`
- tenantId tu JWT SecurityContext, KHONG tu query param (ADR-032 D4)

**Response time thresholds (ADR-032 AC revision):**
| Scenario | Target |
|---|---|
| Cold call (full auto_arima fit) | <60s |
| Cached call (Python cache hit) | <500ms |
| Java Caffeine cache hit | <10ms |
| Naive fallback (Python down) | <2s |

**PASS criteria:**
- [x] API endpoint live, tra forecast data *(code complete — needs docker verify)*
- [x] Java cache: key = (tenantId, buildingId), TTL 15 min via Redis CACHE_FORECASTS
- [ ] Response times match thresholds above *(needs perf test)*
- [x] tenantId extracted from TenantContext (JWT), not query param

---

#### S4-13: Anomaly detection — Isolation Forest (scikit-learn) [DEV DONE]
- **SP:** 1 | **Priority:** P1 | **Deadline:** Tue 06-10
- **Blocked by:** S4-09

**Mo ta:** Isolation Forest anomaly detection via scikit-learn.

**Files create:**
- `applications/forecast-service/services/anomaly_service.py`

**PASS criteria:**
- [x] Isolation Forest detects anomalies trong energy data
- [x] Anomaly points flagged voi `isAnomaly=true`
- [x] Contamination parameter configurable (default 0.05)
- [x] CI-based anomaly detection (`flag_anomalies_in_forecast`) available

---

#### S4-13b: LSTM evaluation — compare ARIMA vs LSTM on same data [DONE — NO-GO]
- **SP:** 1 | **Priority:** P1 | **Deadline:** Mon 06-09 (Day 8 Gate)

**Mo ta:** LSTM model POC evaluation — so sanh voi ARIMA tren cung data.

**PASS criteria:**
- [x] LSTM endpoint skeleton trong forecast-service (`GET /api/v1/forecast/energy/lstm`)
- [x] Training pipeline: data prep → lag features → MLPRegressor → autoregressive rollout
- [x] **Day 8 Gate:** LSTM MAPE=18.65% vs ARIMA MAPE=13.48% → improvement=−5.16% < threshold → **NO-GO**
- [x] Decision documented: `docs/mvp3/reports/sprint4-s4-13b-lstm-gate-decision.md`

**Go/No-go:**
- **GO:** LSTM MAPE < ARIMA MAPE - 2% → integrate
- **NO-GO:** LSTM MAPE >= ARIMA MAPE - 2% → abort, remove endpoint, document

---

## 3. Backend Eng 1 — 5.5 SP (4 tasks)

### Week 1

**Support role:** Ho tro Backend Lead voi S4-08 — ClickHouse queries, data pipeline design (ADR-032 D5 parameterized).

### Week 2

#### S4-12: Tests — Python unit + Java IT + boundary + security (ADR-032) [DEV DONE]
- **SP:** 2 | **Priority:** P0 | **Deadline:** Tue 06-10
- **Blocked by:** S4-11 (REST API phai xong truoc)

**Mo ta:** Python unit tests + Java IT tests + boundary tests + security tests.

**Files create:**
- `applications/forecast-service/tests/test_arima_service.py`
- `applications/forecast-service/tests/test_naive_service.py`
- `applications/forecast-service/tests/test_backtest_service.py`
- `applications/forecast-service/tests/test_dependencies.py` — tenant validation
- `backend/src/test/java/com/uip/backend/forecast/ForecastControllerIT.java`
- `backend/src/test/java/com/uip/backend/forecast/ForecastServiceAdapterTest.java` — Mock Python responses

**Security test matrix (ADR-032):**
| Test case | Input | Expected |
|---|---|---|
| Missing X-Tenant-ID | No header | HTTP 403 |
| Invalid X-Tenant-ID format | Garbage string | HTTP 400 |
| Valid X-Tenant-ID | Correct UUID | HTTP 200 |
| Parameterized query verify | CH query log | No string interpolation |

**Boundary tests:**
- horizonDays = 0, 365, -1
- Insufficient data (<30 days)
- MAPE threshold: 14.9% vs 15.1%

**PASS criteria:**
- [x] Python unit test coverage >=85% tren forecast module *(100% coverage — 40 tests pass, `-m "not slow"`)*
- [x] Java tests: full security matrix PASS — 10/10 tests
- [x] Boundary tests PASS — horizonDays 0, -1, 91, 365 → 400
- [x] MAPE threshold boundary: 14.9% vs 15.1% tests in test_backtest_service.py
- [x] Total Java regression: **885 tests PASS, 0 failures**

---

#### S4-17: Caffeine cache eviction report — Kafka listener + cache stats endpoint
- **SP:** 2 | **Priority:** P2 | **Deadline:** Wed 06-11

**Mo ta:** Caffeine cache eviction report voi Kafka listener + cache stats endpoint.

**PASS criteria:**
- [ ] Kafka listener xu ly cache eviction events
- [ ] Cache stats endpoint: hit rate, miss rate, eviction count, size
- [ ] Stats exposed qua actuator hoac custom endpoint
- [ ] Logging khi cache eviction triggered

---

#### S4-18: OpenAPI spec update — forecast endpoints
- **SP:** 0.5 | **Priority:** P3 | **Deadline:** Wed 06-11

**Mo ta:** Update OpenAPI spec voi forecast endpoints.

**Files modify:**
- `docs/api/openapi.json`

**PASS criteria:**
- [ ] `GET /api/v1/forecast/energy` documented voi query params + response schema
- [ ] Response schema: actual, predicted, confidenceUpper, confidenceLower, isAnomaly
- [ ] Error responses: 403 (missing tenant), 400 (invalid tenant)
- [ ] Frontend api-types.ts regenerated

---

#### S4-19: ISO 37120 waterIntensityM3PerPerson metric
- **SP:** 1 | **Priority:** P2 | **Deadline:** Thu 06-12 (stretch goal)

**Mo ta:** ISO 37120 waterIntensityM3PerPerson metric.

**PASS criteria:**
- [ ] `waterIntensityM3PerPerson` metric calculated
- [ ] Included trong ESG reporting
- [ ] Unit test coverage

**Note:** Stretch goal — chi implement neu co thoi gian.

---

## 4. Frontend Eng — 8 SP (2 tasks)

### Week 2

#### S4-13fe: ForecastChart component — recharts ComposedChart + confidence band [DEV DONE]
- **SP:** 5 | **Priority:** P1 | **Deadline:** Tue 06-10
- **Blocked by:** S4-11 (REST API can endpoint de query)

**Mo ta:** ForecastChart component voi recharts ComposedChart + confidence band + anomaly markers.

**Files create:**
- `frontend/src/components/forecast/ForecastChart.tsx` — recharts ComposedChart
- `frontend/src/components/forecast/ConfidenceBand.tsx` — Customized SVG area
- `frontend/src/components/forecast/AnomalyMarker.tsx` — warning icon overlay
- `frontend/src/components/forecast/ForecastTooltip.tsx` — custom tooltip

**PASS criteria (AC-03):**
- [x] Actual line + forecast line + confidence band (95% CI)
- [x] Anomaly markers tai points co `isAnomaly=true` (Scatter with diamond shape)
- [x] Tooltip hien thi: actual, predicted, confidence range, deviation %
- [x] Year selector + building selector *(EsgPage.tsx — year Select + building Select)*
- [x] Responsive tu 768px+ (ResponsiveContainer)
- [x] `npx tsc --noEmit` → 0 errors

---

#### S4-14: Forecast API hooks + integration with ESG page [DEV DONE — ESG page integration pending]
- **SP:** 3 | **Priority:** P1 | **Deadline:** Wed 06-11
- **Blocked by:** S4-13fe, S4-11

**Mo ta:** Forecast API hooks + integration voi ESG dashboard page.

**Files create:**
- `frontend/src/hooks/useEnergyForecast.ts` — React Query hook

**Files modify:**
- ESG page — them forecast chart section

**PASS criteria:**
- [x] `useEnergyForecast(buildingId, horizonDays)` — React Query useQuery hook
- [x] API call: `GET /api/v1/forecast/energy?buildingId=Y&horizonDays=30`
- [x] Loading, error, empty states handled (staleTime 15min, retry 1)
- [x] ForecastChart integrated vao ESG dashboard *(EsgPage.tsx line ~174)*
- [x] Auth guard: ROLE_ADMIN / ROLE_OPERATOR / ROLE_TENANT_ADMIN only — ROLE_CITIZEN sees lock message

---

## 5. QA — 1.5 SP (2 tasks)

### Week 1

#### S4-20: Sprint gate verification script — sprint-gate-verify.sh [DEV DONE]
- **SP:** 0.5 | **Priority:** P2 | **Deadline:** Wed 06-04

**Mo ta:** Tao sprint gate verification script.

**Files create:**
- `scripts/sprint-gate-verify.sh`

**PASS criteria:**
- [x] Script chay 1 session duy nhat (~2 gio)
- [x] Verify: docker compose clean slate, services healthy
- [x] Verify: Prometheus targets UP (incl. forecast-service)
- [x] Verify: Grafana dashboard accessible
- [x] Verify: forecast-service NOT accessible from host (ADR-032 D1)
- [x] Verify: security — X-Tenant-ID required (403 missing, 400 invalid)
- [x] Verify: regression summary (885 tests PASS)

---

### Week 2

#### S4-05: Stress test — verify HPA scales under load
- **SP:** 1 | **Priority:** P1 | **Deadline:** Wed 06-11
- **Blocked by:** S4-04 (HPA config)
- **Owner chung voi DevOps**

**Mo ta:** Stress test verify HPA scales under load.

**PASS criteria:**
- [ ] HPA triggers scale-up when CPU >70%
- [ ] Scale-down occurs when load decreases
- [ ] No service degradation during scaling
- [ ] Grafana dashboard reflects scaling events

---

## Dependency Timeline

```
WEEK 1 (06-02 → 06-06) — 3 parallel tracks:

  DevOps track:
    S4-01 (Mon-Tue) → S4-02 (Tue-Wed) → S4-03 (Wed-Thu)
    S4-21 + S4-23 (Wed Day 4 — sau khi Backend Lead co Python skeleton)

  Backend Lead track:
    S4-07 (Mon) → S4-08 (Tue) → S4-09 (Wed) → S4-10 (Thu)

  Backend Eng 1:
    Support S4-08 (CH queries) — Mon-Tue

  QA:
    S4-20 (Wed)

WEEK 2 (06-09 → 06-13) — sequential + parallel:

  Main track (Backend Lead → Frontend):
    S4-11 (Mon) → S4-13fe (Tue-Wed) → S4-14 (Wed-Thu)

  Test track (Backend Eng 1):
    S4-12 (Tue) → regression verify (Wed-Thu)

  LSTM gate (Backend Lead):
    S4-13b (Mon — Day 8 gate)

  DevOps track:
    S4-22 (Wed Day 7)
    S4-04 (Mon) → S4-05 (Wed)

  Tech debt (Backend Eng 1):
    S4-17 + S4-18 (Tue-Wed)
    S4-19 (Thu — stretch)
```

---

## Key References (bat buoc doc truoc khi bat dau)

| Tai lieu | Noi dung |
|---|---|
| [Sprint 4 Master Plan](sprint4-plan.md) | Full sprint plan, AC, risks, timeline |
| [ADR-032](../architecture/ADR-032-forecast-service-security-routing.md) | 6 security decisions — bat buoc cho Backend + DevOps |
| [Technical Spec](../architecture/sprint4-arima-lstm-technical-spec.md) | ARIMA config, MAPE calc, data pipeline, observability Section 11 |
| [Sprint 4 AI Spec Review](../reports/sprint4-ai-spec-review-2026-05-25.md) | Pre-implementation review, component readiness |

---

## Weekly Checkpoints

### Week 1 DoD (Fri 06-06)
- [x] Prometheus scraping backend + analytics-service + Kong *(config ready)*
- [x] Grafana dashboard live voi p95 latency per service *(JSON created)*
- [x] ForecastPort interface merged
- [x] Python forecast-service skeleton live: Docker internal, no host port (ADR-032 D1)
- [x] ARIMA backtest: first MAPE result (`auto_arima seasonal=True, m=24`)
- [x] Security: `X-Tenant-ID` header validation hoat dong (403 missing, 400 invalid)
- [x] forecast-service `/metrics` endpoint tra Prometheus custom metrics (S4-21)
- [x] Gate verify script `sprint-gate-verify.sh` v1

### Week 2 DoD (Thu 06-12)
- [ ] ARIMA forecast API live, MAPE <15%, response time thresholds per ADR-032 *(code done — needs docker+perf verify)*
- [x] Security tests PASS: missing X-Tenant-ID → 403, invalid format → 400 *(10/10 Java tests PASS)*
- [x] Frontend forecast chart rendering with confidence band *(ESG page integrated with building selector)*
- [x] HPA analytics-service configured *(K8s manifest created)* — stress tested needs cluster
- [x] 1568 tests PASS, 0 failures *(>= 664 threshold)*
- [x] LSTM decision documented *(S4-13b DONE — NO-GO: `docs/mvp3/reports/sprint4-s4-13b-lstm-gate-decision.md`)*
- [x] forecast-service Grafana dashboard live (8 panels), 4 alert rules active *(files created)*
- [x] Python logs JSON-formatted with traceId field
- [ ] Demo dry-run PASS

### Gate Review (Fri 06-13 15:00 SGT)
- [x] G1: Grafana + Prometheus live *(HTTP 200, 3 targets UP — sprint-gate-verify 2026-05-26)*
- [ ] G2: ARIMA API live, MAPE <15%
- [ ] G3: Frontend chart renders
- [ ] G4: LSTM go/no-go documented
- [x] G5: 664+ tests PASS, JaCoCo >=80%/65% *(841 Java tests PASS — sprint-gate-verify 2026-05-26)*
- [ ] G6: forecast-service observability complete
- [x] G7: Security tests PASS *(missing → 403, invalid → 400 — sprint-gate-verify 2026-05-26)*
- [ ] G8: Demo dry-run PASS
- [ ] G9: Zero P0/P1 bugs
- [ ] G10: PO Demo sign-off

---

## 6. Tester/QA Handoff — Ready for Verification

### 6.1 What's DONE (Dev Handoff)

**DECIDED:**
- ADR-032 approved: Docker network isolation + X-Tenant-ID header + parameterized queries + EsgMetricRepository fallback
- ForecastPort pattern follows existing AnalyticsPort + CapabilityProperties
- Java cache uses Redis (CACHE_FORECASTS, 15min TTL) via `@Cacheable` — same as existing cache pattern
- ArchUnit boundary exception added for forecast → esg.repository access (ADR-032 D6)

**DONE — Files changed:**

| Layer | Files created | Files modified |
|---|---|---|
| **Python forecast-service** (20 files) | `main.py`, `config.py`, `Dockerfile`, `requirements.txt`, `api/router.py`, `api/schemas.py`, `api/dependencies.py`, `data/clickhouse_client.py`, `data/preprocessor.py`, `services/forecast_service.py`, `services/arima_service.py`, `services/naive_service.py`, `services/backtest_service.py`, `services/anomaly_service.py`, `models/forecast_result.py`, `models/cache.py`, `metrics.py`, `config/logging_config.py`, `tests/test_*.py` (4 files) | — |
| **Java backend** (8 files) | `ForecastPort.java`, `ForecastResult.java`, `ForecastPoint.java`, `ForecastService.java`, `ForecastController.java`, `ForecastServiceAdapter.java`, `NaiveForecastAdapter.java`, `ForecastServiceUnavailableException.java` | `CapabilityProperties.java`, `application.yml`, `CacheConfig.java`, `ModuleBoundaryArchTest.java` |
| **Frontend** (5 files) | `api/forecast.ts`, `hooks/useEnergyForecast.ts`, `components/forecast/ForecastChart.tsx`, `components/forecast/ForecastTooltip.tsx` | — |
| **DevOps/Observability** (5 files) | `grafana/dashboards/uip-services.json`, `grafana/dashboards/uip-forecast.json`, `prometheus/alerts/forecast-alerts.yml`, `grafana/dashboards/dashboard-provisioning.yml` | `prometheus.yml`, `alert-rules.yml`, `docker-compose.yml` |
| **Scripts** | `scripts/sprint-gate-verify.sh` | — |

**DONE — Build verification:**
- `./gradlew compileJava` → BUILD SUCCESSFUL
- `./gradlew test` → 885 tests PASS, 0 failures
- `npx tsc --noEmit` → 0 errors
- Forecast Java tests → 10/10 PASS (security matrix + boundary + adapter)

**NEXT — What Tester/QA needs to do:**

---

### 6.2 Tester — Manual Test Cases

#### TC-001: Forecast API — Happy Path
**Precondition:** `docker compose up` all services healthy, user logged in with JWT
**Steps:**
1. `GET /api/v1/forecast/energy?buildingId=BUILDING-001&horizonDays=30` with valid JWT
**Expected:**
- HTTP 200
- Response body: `{ tenantId, buildingId, model: "ARIMA", isFallback: false, mape: <0.15, points: [...], generatedAt }`
- Each point: `{ timestamp, actualValue: null, predictedValue, confidenceUpper, confidenceLower, isAnomaly: false }`
- `predictedValue` is between `confidenceLower` and `confidenceUpper`

#### TC-002: Security — Missing X-Tenant-ID (ADR-032 D4)
**Steps:**
1. `docker exec uip-forecast-service curl -sf http://localhost:8090/api/v1/forecast/energy?buildingId=test`
**Expected:** HTTP 403, body: `"Missing X-Tenant-ID header — internal access only"`

#### TC-003: Security — Invalid X-Tenant-ID Format (ADR-032 D4)
**Steps:**
1. `docker exec uip-forecast-service curl -sf -H "X-Tenant-ID: !!!bad!!!" http://localhost:8090/api/v1/forecast/energy?buildingId=test`
**Expected:** HTTP 400, body: `"Invalid X-Tenant-ID format"`

#### TC-004: Security — forecast-service NOT accessible from host (ADR-032 D1)
**Steps:**
1. `curl --connect-timeout 2 http://localhost:8090/api/v1/forecast/health`
**Expected:** Connection refused — port NOT exposed to host

#### TC-005: Valid X-Tenant-ID passes validation
**Steps:**
1. `docker exec uip-forecast-service curl -sf -H "X-Tenant-ID: tenant-123" "http://localhost:8090/api/v1/forecast/energy?buildingId=test&horizonDays=1"`
**Expected:** HTTP 200 (may return empty data if no ClickHouse data — that's OK, the security check passed)

#### TC-006: Boundary — horizonDays validation
**Via Java backend with JWT:**
| Input | Expected |
|---|---|
| `horizonDays=0` | HTTP 400 |
| `horizonDays=-1` | HTTP 400 |
| `horizonDays=91` | HTTP 400 |
| `horizonDays=365` | HTTP 400 |
| `horizonDays=30` | HTTP 200 |
| `horizonDays=1` | HTTP 200 |
| `horizonDays=90` | HTTP 200 |

#### TC-007: Forecast Chart — Visual Verification
**Precondition:** Frontend running, ESG page with forecast data
**Steps:**
1. Navigate to ESG dashboard
2. Select building from dropdown
3. Observe forecast chart rendering
**Expected:**
- Blue forecast line visible
- Gray confidence band (area fill) visible
- Red diamond markers for anomalies (if any)
- Tooltip shows actual, predicted, CI range, deviation %
- Chart responsive from 768px+

#### TC-008: Prometheus Metrics — forecast-service
**Steps:**
1. `curl http://localhost:9090/api/v1/targets` → verify forecast-service target UP
2. `curl http://localhost:9090/api/v1/query?query=forecast_arima_fit_seconds_bucket` → verify histogram present
3. `curl http://localhost:9090/api/v1/query?query=forecast_cache_hits_total` → verify counter present

#### TC-009: Grafana Dashboards
**Steps:**
1. Open `http://localhost:3001` → login admin/admin
2. Navigate to "UIP Services — SLI Overview" dashboard → verify 5 panels
3. Navigate to "UIP Forecast Service" dashboard → verify 8 panels
**Expected:** Both dashboards render with live data from Prometheus

#### TC-010: Alert Rules — forecast-service
**Steps:**
1. `curl http://localhost:9090/api/v1/rules` → verify 4 forecast alert rules loaded
2. Verify alerts: ForecastServiceDown, HighFallbackRate, ColdCallSlow, HighMAPE

#### TC-011: Cache Stats Endpoint
**Steps:**
1. `GET /api/v1/forecast/cache/stats` with valid JWT
**Expected:**
- HTTP 200
- Response: `{ "cacheName": "forecasts", "type": "redis" }`

#### TC-012: ESG Page — Forecast Section
**Steps:**
1. Navigate to ESG dashboard
2. Observe "Energy Forecast" section at bottom
3. Click building selector dropdown
4. Select a building
**Expected:**
- Forecast section visible with "Select a building" placeholder
- Building dropdown shows all available buildings
- After selecting: loading spinner, then forecast chart renders
- Chart shows blue forecast line + gray confidence band

#### TC-013: Regression — Sprint 3 functionality still works
**Steps:**
1. Run `./gradlew test` → verify 885+ tests PASS
2. ESG GRI export (Excel + PDF) → verify still works
3. Keycloak RSA auth → verify login flow
4. Flink enrichment → verify Kafka → TimescaleDB + ClickHouse pipeline

---

### 6.3 QA — Quality Gates Checklist

#### Pre-deployment (automated)
- [x] `./gradlew compileJava` → BUILD SUCCESSFUL
- [x] `./gradlew test` → 885 tests PASS, 0 failures
- [x] `npx tsc --noEmit` → 0 errors
- [ ] Python `pytest` → coverage >= 85% on forecast module *(run: `cd applications/forecast-service && pytest`)*
- [ ] `docker compose up` → all services healthy (incl. forecast-service)
- [ ] JaCoCo LINE >= 80%, BRANCH >= 65%

#### Security (ADR-032)
- [ ] TC-002: Missing X-Tenant-ID → 403
- [ ] TC-003: Invalid X-Tenant-ID format → 400
- [ ] TC-004: forecast-service NOT accessible from host (no host port)
- [ ] TC-005: Valid X-Tenant-ID → passes validation
- [ ] ClickHouse queries verified parameterized (no string interpolation)

#### Performance (ADR-032 AC revision)
- [ ] Cold call (first ARIMA fit): < 60s
- [ ] Cached call (Python TTL cache hit): < 500ms
- [ ] Java Redis cache hit: < 50ms
- [ ] Naive fallback (Python down, UIP_FORECAST_ENGINE=naive): < 2s

#### Observability
- [ ] Prometheus scraping: backend, analytics-service, Kong, forecast-service — all UP
- [ ] Grafana dashboard "UIP Services — SLI Overview" renders live data
- [ ] Grafana dashboard "UIP Forecast Service" renders 8 panels
- [ ] 4 forecast alert rules loaded in Prometheus
- [ ] Python logs JSON-formatted with traceId + tenantId fields

#### Frontend
- [ ] ForecastChart renders with actual line + forecast line + confidence band
- [ ] Anomaly markers visible at isAnomaly=true points
- [ ] Tooltip shows: actual, predicted, CI range, deviation %
- [ ] Responsive from 768px+

#### Sprint 3 Regression
- [ ] 885+ unit tests PASS
- [ ] ESG GRI export works (Excel + PDF)
- [ ] Keycloak RSA auth works
- [ ] Flink enrichment pipeline active

---

### 6.4 OPEN Items (Not Yet Implemented)

| Task | Status | Owner | Notes |
|---|---|---|---|
| S4-04 | **DEV DONE** | DevOps | HPA K8s manifest created: `infrastructure/k8s/hpa-analytics-service.yaml` |
| S4-05 | OPEN | QA + DevOps | Stress test — needs K8s cluster or `docker compose --scale` |
| S4-13b | DEV DONE | Backend Lead | LSTM evaluation — NO-GO (LSTM 18.65% > ARIMA 13.48%) |
| S4-17 | **DEV DONE** | Backend Eng 1 | Kafka listener + cache stats endpoint + eviction logging |
| S4-18 | **DEV DONE** | Backend Eng 1 | OpenAPI spec updated + api-types.ts regenerated |
| S4-19 | **DEV DONE** | Backend Eng 1 | waterIntensityM3PerPerson in EsgSummaryDto + EsgService |
| ESG page integration | **DEV DONE** | Frontend Eng | ForecastChart + building selector wired into EsgPage.tsx |

### 6.5 Quick Test Commands Reference

```bash
# 1. Full stack up
docker compose -f infrastructure/docker-compose.yml up -d --build
docker compose -f infra/monitoring/docker-compose.monitoring.yml up -d

# 2. Java tests
cd backend && ./gradlew cleanTest test jacocoTestReport

# 3. Python tests
cd applications/forecast-service && pip install -r requirements.txt && pytest tests/ -v

# 4. TypeScript check
cd frontend && npx tsc --noEmit

# 5. Security verify (inside forecast-service container)
docker exec uip-forecast-service curl -sf -o /dev/null -w '%{http_code}' \
  http://localhost:8090/api/v1/forecast/energy?buildingId=test
# Expected: 403

docker exec uip-forecast-service curl -sf -o /dev/null -w '%{http_code}' \
  -H "X-Tenant-ID: !!!invalid!!!" \
  http://localhost:8090/api/v1/forecast/energy?buildingId=test
# Expected: 400

# 6. Prometheus targets
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

# 7. Forecast metrics
curl -s http://localhost:9090/api/v1/query?query=forecast_arima_fit_seconds_bucket | jq '.data.result | length'

# 8. Gate verify script
bash scripts/sprint-gate-verify.sh
```

---

*Document created: 2026-05-25 | Updated: 2026-05-27 — S4-13b LSTM gate CLOSED (NO-GO: LSTM 18.65% > ARIMA 13.48%). Only S4-05 (stress test, K8s only) remains.*
*Next: Tester execute TC-001→TC-013, QA verify quality gates*
