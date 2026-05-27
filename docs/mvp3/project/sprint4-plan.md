# Sprint MVP3-4 — Master Plan (Revised)

**Status:** APPROVED — PO Brainstorm 2026-05-25 | Updated post ADR-032 approval
**Document Date:** 2026-05-25 (Updated 2026-05-25 — ADR-032 + Technical Spec approved)
**Sprint Start:** 2026-06-02 (Mon)
**Sprint End:** 2026-06-13 (Fri EOD)
**Gate Review:** 2026-06-13 15:00 SGT
**Sprint trước:** MVP3-3 (GATE READY — awaiting PO sign-off 2026-05-30)
**PO:** anhgv

---

## Context

Sprint 3 (2026-05-19 → 05-30) đã hoàn thành ESG GRI Export + Keycloak RSA + Flink Enrichment. Code xong nhanh (5 ngày) nhưng verify kéo dài 5-6 ngày vì thiếu automated verification pipeline.

**PO quyết định (brainstorm 2026-05-25):**
- ClickHouse HA **DESCOPED hoàn toàn** — single-node ổn định, không cần cho City Authority deadline
- Predictive AI: **ARIMA + LSTM contingency** — bắt đầu trong Sprint 4
- Observability: Prometheus/Grafana dashboard cho Kong + analytics-service
- BMS SDK: **Defer Sprint 5**
- Giữ timeline: Gate 2026-06-13, City Authority deadline 2026-06-15

**Key discovery từ exploration:**
- Monitoring stack đã có: Prometheus v2.51 + Grafana 10.4 + Alertmanager (`infra/monitoring/`) nhưng KHÔNG có analytics-service scrape target
- Backend + analytics-service đã có `micrometer-registry-prometheus` + actuator → chỉ cần thêm scrape config
- `AnalyticsPort` interface + `CapabilityProperties` sẵn — pattern tốt cho `ForecastPort` mới
- Chưa có `ForecastPort` interface — cần tạo mới theo pattern `AnalyticsPort`
- Kong prometheus plugin đã active (Sprint 3 verified)

**Pre-sprint spikes COMPLETED (2026-05-25):**
- **ADR-032 APPROVED** — Forecast-service security, routing & tenant isolation: 6 decisions chốt (Docker network isolation, no Kong, no nginx change, X-Tenant-ID header, parameterized queries, EsgMetricRepository fallback)
- **Technical Spec APPROVED** — Python FastAPI architecture final, observability spec (Section 11), AC #11 revised với realistic latency thresholds

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-4: Observability + Predictive AI Foundation |
| **Duration** | 2026-06-02 (Mon) → 2026-06-13 (Fri) — 10 calendar days |
| **Team** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) |
| **Net Capacity** | ~47 SP (59 SP - 20% buffer) |
| **Committed** | ~42 SP |
| **Buffer** | ~5 SP |

> **Note:** SA Spike ADR-032 (2 SP) đã hoàn thành PRE-SPRINT → giải phóng 2 SP buffer. Observability forecast-service (3 SP) sử dụng buffer này. Net committed vẫn ~42 SP.

---

## 2. Sprint Goal (SMART)

Team sẽ đạt **HARD PASS** by 2026-06-13 15:00 SGT bằng cách:
1. Deploy Prometheus/Grafana dashboard cho Kong + analytics-service SLIs (p95 latency, error rate, throughput)
2. Implement ARIMA energy forecast backend với `ForecastPort` capability flag, MAPE <15% trên validation data
3. Frontend forecast chart với confidence intervals + anomaly markers
4. LSTM spike POC — nếu MAPE >15% ở Day 8 gate → abort, ARIMA only (no gate impact)
5. Sprint 3 regression maintain: 664+ testUnit PASS, 0 failures

---

## 3. Acceptance Criteria

### AC-01 (P0): Observability Dashboard Live
> Grafana hiển thị SLIs cho Kong + analytics-service + backend, team có thể monitor p95 latency + error rate real-time.

**PASS criteria:**
- [ ] Prometheus scraping: backend (8080), analytics-service (8082), Kong metrics
- [ ] Grafana dashboard: request rate, p95 latency, error rate per service
- [ ] Alert rule: p95 >1s trên analytics → Alertmanager notification
- [ ] HPA analytics-service: min 2 / max 6, CPU 70%

### AC-02 (P0): ARIMA Energy Forecast API
> Backend API trả về energy forecast 30 ngày với confidence interval 95%, MAPE <15%. Security per ADR-032.

**PASS criteria:**
- [ ] `ForecastPort` interface + ARIMA adapter với `@ConditionalOnProperty`
- [ ] `GET /api/v1/forecast/energy?buildingId=Y&horizonDays=30` trả forecast data (tenantId từ JWT, không từ query param — ADR-032 Decision 4)
- [ ] MAPE <15% trên backtest (80% train / 20% validate)
- [ ] MAPE >15% → automatic fallback naive rolling average + log warn
- [ ] **Response time thresholds (revised per ADR-032 AC revision):**
  - First cold call (full `auto_arima` fit, cold cache): <60s
  - Subsequent call within TTL (Python cache hit): <500ms
  - Java Caffeine cache hit (same tenant + building): <10ms
  - NaiveForecastAdapter (Python service down): <2s
- [ ] Unit tests >=85% coverage trên forecast module
- [ ] **Security (ADR-032):**
  - forecast-service KHÔNG expose host port — Docker internal only (Decision 1)
  - Missing `X-Tenant-ID` header → HTTP 403 (Decision 4)
  - Invalid `X-Tenant-ID` format → HTTP 400 (Decision 4)
  - ClickHouse queries dùng parameterized bind variables — no string interpolation (Decision 5)
  - NaiveForecastAdapter uses `EsgMetricRepository.findByTypeAndBuilding()` — TimescaleDB, zero new dependencies (Decision 6)

### AC-03 (P1): Forecast Frontend Chart
> Dashboard hiển thị energy forecast với confidence band + anomaly markers.

**PASS criteria:**
- [ ] `ForecastChart` component: actual line + forecast line + confidence band (recharts)
- [ ] Anomaly markers tại points có `isAnomaly=true`
- [ ] Tooltip: actual, predicted, confidence range, deviation %
- [ ] Year selector + building selector
- [ ] Responsive 768px +

### AC-04 (P1): LSTM Spike Evaluation
> LSTM model POC evaluated, quyết định go/no-go ở Day 8. Same Python service — chỉ thêm PyTorch endpoint.

**PASS criteria:**
- [ ] LSTM endpoint skeleton trong forecast-service (không còn spike riêng)
- [ ] Training pipeline: data preparation → model training → validation
- [ ] **Day 8 Gate:** LSTM MAPE < ARIMA MAPE (improvement >2%) → integrate. Otherwise → abort, document findings
- [ ] Nếu abort: ARIMA-only path, no gate impact, chỉ remove endpoint

### AC-07 (P1): Forecast-Service Observability
> forecast-service có đầy đủ Prometheus metrics, alert rules, Grafana dashboard, structured logging per Technical Spec Section 11.

**PASS criteria:**
- [ ] `/metrics` endpoint trả Prometheus metrics (HTTP metrics + custom counters/histograms/gauges)
- [ ] 4 alert rules deployed: ForecastServiceDown, HighFallbackRate, ColdCallSlow, HighMAPE
- [ ] Grafana dashboard `uip-forecast.json` — 8 panels (Service Health, Request Rate, p95 Latency, ARIMA Fit p95, Cache Hit Rate, Fallback Rate, MAPE per Building, Error Rate 5xx)
- [ ] Python logs JSON-formatted với `traceId` + `tenantId` fields
- [ ] Docker image chạy non-root user (`forecast:forecast`)

### AC-05 (P0): No Regression
> Tất cả Sprint 3 functionality vẫn hoạt động.

**PASS criteria:**
- [ ] 664+ testUnit PASS, 0 failures
- [ ] ESG GRI export (Excel + PDF) vẫn hoạt động
- [ ] Keycloak RSA auth hoạt động
- [ ] Flink enrichment inline hoạt động
- [ ] JaCoCo LINE ≥80%, BRANCH ≥65%

### AC-06 (P2): Carry-over Tech Debt
> Minor items từ Sprint 3.

**PASS criteria:**
- [ ] Caffeine cache eviction: Kafka listener + cache stats endpoint
- [ ] OpenAPI spec updated with forecast endpoints
- [ ] ISO 37120 waterIntensityM3PerPerson metric (nếu có thời gian)

---

## 4. Sprint Backlog

### 4.1 Epic 1: Observability (DevOps + Backend Lead) — 9 SP

| Story ID | Title | SP | Owner | Priority | Week |
|---|---|---|---|---|---|
| S4-01 | Prometheus scrape config: backend + analytics-service + Kong | 2 | DevOps | P0 | W1 |
| S4-02 | Grafana dashboard: Kong SLIs + analytics p95 + backend health | 3 | DevOps | P0 | W1 |
| S4-03 | Alert rules: analytics p95 >1s, error rate >1%, backend down | 1 | DevOps | P1 | W1 |
| S4-04 | HPA analytics-service (min 2 / max 6, CPU 70%) | 2 | DevOps | P1 | W2 |
| S4-05 | Stress test: verify HPA scales under load | 1 | QA + DevOps | P1 | W2 |

### 4.2 Epic 2: ARIMA Forecast — Python Service + Java Integration — 18 SP

| Story ID | Title | SP | Owner | Priority | Week | Notes |
|---|---|---|---|---|---|---|
| ~~S4-06~~ | ~~SA Spike: ADR-032~~ | ~~2~~ | ~~SA~~ | — | — | **DONE pre-sprint 2026-05-25.** 6 decisions chốt, ADR-032 APPROVED |
| S4-07 | ForecastPort interface + capability flag + Java adapter | 2 | Backend Lead | P0 | W1 | |
| S4-08 | Python forecast-service skeleton (FastAPI + Dockerfile + CH query) | 2 | Backend Lead | P0 | W1 | **ADR-032 D1:** no host port expose, Docker internal only |
| S4-09 | ARIMA service: `auto_arima(seasonal=True, m=24)` + forecast + confidence intervals | 4 | Backend Lead | P0 | W1 | Per Technical Spec Section 2 |
| S4-10 | Backtest validation + naive fallback (rolling average) | 2 | Backend Lead | P0 | W1 | **ADR-032 D6:** NaiveForecastAdapter uses EsgMetricRepository |
| S4-11 | Forecast REST API (Java controller + Python endpoint) + caching | 3 | Backend Lead | P0 | W2 | **ADR-032 D4:** X-Tenant-ID header pass-through from SecurityContext |
| S4-12 | Tests: Python unit + Java IT + boundary + **security** | 2 | Backend Eng 1 | P0 | W2 | **ADR-032:** missing X-Tenant-ID → 403, invalid format → 400, parameterized query verify |
| S4-13 | Anomaly detection: Isolation Forest (scikit-learn) | 1 | Backend Lead | P1 | W2 | |
| S4-13b | LSTM evaluation: compare ARIMA vs LSTM on same data | 1 | Backend Lead | P1 | W2 | Day 8 gate, go/no-go |

### 4.2b Epic 2b: Forecast Observability (DevOps) — 3 SP

| Story ID | Title | SP | Owner | Priority | Week | Notes |
|---|---|---|---|---|---|---|
| S4-21 | Prometheus metrics + custom counters/histograms/gauges | 1 | DevOps | P1 | W1 Day 4 | Technical Spec Section 11.1: ARIMA_FIT_DURATION, FORECAST_FALLBACK_TOTAL, FORECAST_CACHE_HIT, FORECAST_MAPE |
| S4-22 | Alert rules (4 rules) + Grafana dashboard (8 panels) | 1.5 | DevOps | P1 | W2 Day 7 | Technical Spec Section 11.3 + 11.4 |
| S4-23 | JSON structured logging + Docker non-root user | 0.5 | DevOps | P1 | W1 Day 4 | Technical Spec Section 11.5 + 11.6 |

### 4.3 Epic 3: Forecast Frontend (Frontend Eng) — 8 SP

| Story ID | Title | SP | Owner | Priority | Week |
|---|---|---|---|---|---|
| S4-13 | ForecastChart component (recharts ComposedChart + confidence band) | 5 | Frontend Eng | P1 | W2 |
| S4-14 | Forecast API hooks + integration with ESG page | 3 | Frontend Eng | P1 | W2 |

### 4.4 ~~Epic 4: LSTM Spike~~ — MERGED vào Epic 2

> LSTM evaluation (S4-12b) giờ là 1 SP task trong Epic 2 — cùng Python service, chỉ thêm PyTorch endpoint. Không còn spike riêng 8 SP.

### 4.5 Epic 4: Carry-over + Tech Debt — 4 SP

| Story ID | Title | SP | Owner | Priority | Week |
|---|---|---|---|---|---|
| S4-17 | Caffeine cache eviction report (Kafka listener) | 2 | Backend Eng 1 | P2 | W2 |
| S4-18 | OpenAPI spec update (forecast endpoints) | 0.5 | Backend Eng 1 | P3 | W2 |
| S4-19 | ISO 37120 waterIntensity metric | 1 | Backend Eng 1 | P2 | W2 (stretch) |
| S4-20 | Sprint gate verification script (`scripts/sprint-gate-verify.sh`) | 0.5 | QA | P2 | W1 |

**Total: ~42 SP committed (47 SP available, ~5 SP buffer)**

| Epic | SP | Status |
|---|---|---|
| Epic 1: Observability (Kong + analytics) | 9 SP | Unchanged |
| Epic 2: ARIMA Forecast (Python + Java) | 18 SP | S4-06 spike DONE pre-sprint, tiết kiệm 2 SP |
| Epic 2b: Forecast Observability | 3 SP | **NEW** — sử dụng 2 SP từ SA spike + 1 SP buffer |
| Epic 3: Forecast Frontend | 8 SP | Unchanged |
| Epic 4: Carry-over + Tech Debt | 4 SP | Unchanged |
| **Total** | **~42 SP** | |

> SA Spike ADR-032 (2 SP) hoàn thành PRE-SPRINT → 2 SP giải phóng cho forecast observability stories (S4-21/22/23). Python stack tiết kiệm ~8 SP so với Java-only approach (per Technical Spec Appendix A).

---

## 5. Dependency Graph

```
PRE-SPRINT (DONE):
  S4-06 (SA Spike ADR-032) → APPROVED 2026-05-25, 6 decisions chốt

Week 1 (parallel tracks):
  DevOps:          S4-01 (Prometheus scrape) → S4-02 (Grafana) → S4-03 (Alerts)
                   S4-21 (forecast metrics + JSON logging) — Day 4
  Backend Lead:    S4-07 (ForecastPort + Java adapter) → S4-08 (Python skeleton — ADR-032 D1 no host port)
                        → S4-09 (ARIMA seasonal=True m=24) → S4-10 (Backtest + fallback — ADR-032 D6)
  Backend Eng 1:   S4-08 support (CH queries, data pipeline — ADR-032 D5 parameterized)
  QA:              S4-20 (gate verify script)

Week 2 (sequential):
  S4-11 (REST API Java+Python — ADR-032 D4 X-Tenant-ID) → S4-13 (Frontend chart) → S4-14 (Integration)
  S4-12 (Tests + security: 403/400 per ADR-032) + S4-13b (LSTM eval) → regression verify
  DevOps: S4-22 (forecast alert rules + Grafana dashboard) — Day 7
          S4-04 (HPA) → S4-05 (Stress test)
  Backend: S4-17 (Cache) + S4-18 (OpenAPI)
```

---

## 6. Week-by-Week Plan

### Week 1 (2026-06-02 → 2026-06-06): Foundation + ARIMA Core + Observability

| Day | Milestone | Owner |
|---|---|---|
| Mon 06-02 | Sprint kickoff. DevOps: Prometheus scrape config. Backend Lead: ForecastPort interface + capability flag. Backend Eng 1: data pipeline design | All |
| Tue 06-03 | DevOps: Grafana dashboard POC. Backend Lead: Python forecast-service skeleton (FastAPI + `api/dependencies.py` per ADR-032 D4). Backend Eng 1: ClickHouse query (parameterized per ADR-032 D5) | DevOps + Backend |
| Wed 06-04 | Backend Lead: ARIMA backtest running (`auto_arima seasonal=True, m=24`). DevOps: dashboard refined + Kong metrics + **forecast-service Prometheus metrics (S4-21)** + JSON logging (S4-23). QA: gate verify script v1 | Backend + DevOps + QA |
| Thu 06-05 | Backend Lead: MAPE validation + fallback logic (NaiveForecastAdapter via EsgMetricRepository per ADR-032 D6). Backend Eng 1: data pipeline complete. DevOps: verify forecast-service Docker non-root, no host port (ADR-032 D1) | Backend + DevOps |
| Fri 06-06 | **Week 1 checkpoint:** ARIMA adapter backtest PASS, Grafana live, Prometheus scraping (incl. forecast-service), security contract verified (403/400 per ADR-032) | All |

**Week 1 DoD:**
- [ ] Prometheus scraping backend + analytics-service + Kong
- [ ] Grafana dashboard live với p95 latency per service
- [ ] ForecastPort interface merged
- [ ] Python forecast-service skeleton live: Docker internal, no host port (ADR-032 D1)
- [ ] ARIMA backtest: first MAPE result (`auto_arima seasonal=True, m=24`)
- [ ] Security: `X-Tenant-ID` header validation hoạt động (403 missing, 400 invalid)
- [ ] forecast-service `/metrics` endpoint trả Prometheus custom metrics (S4-21)
- [ ] Gate verify script `sprint-gate-verify.sh` v1

### Week 2 (2026-06-09 → 2026-06-13): API + Frontend + Gate

| Day | Milestone | Owner |
|---|---|---|
| Mon 06-09 | **Day 8 LSTM Gate:** evaluate LSTM MAPE vs ARIMA → go/no-go. Backend Lead: Forecast REST API (X-Tenant-ID header pass-through per ADR-032 D4). DevOps: HPA config | Backend Lead + DevOps |
| Tue 06-10 | Backend: API + caching complete. Frontend: ForecastChart start. Backend Eng 1: anomaly detection + security tests (403/400 boundary) | All |
| Wed 06-11 | Frontend: chart integration + responsive. Backend: forecast IT tests (full security test matrix per ADR-032). DevOps: stress test HPA + **forecast alert rules + Grafana dashboard (S4-22)** | All |
| Thu 06-12 | Regression run. Demo dry-run. SA code review. Tech debt items. Verify ForecastServiceDown alert fires on service stop | All |
| Fri 06-13 | **Gate Review 15:00 SGT** — PO Demo Live | All |

**Week 2 DoD:**
- [ ] ARIMA forecast API live, MAPE <15%, response time thresholds per ADR-032 AC revision
- [ ] Security tests PASS: missing X-Tenant-ID → 403, invalid format → 400, parameterized queries verified
- [ ] Frontend forecast chart rendering with confidence band
- [ ] HPA analytics-service configured + stress tested
- [ ] 664+ testUnit PASS, 0 failures
- [ ] LSTM decision documented
- [ ] forecast-service Grafana dashboard live (8 panels), 4 alert rules active (S4-22)
- [ ] Python logs JSON-formatted with traceId field (S4-23)
- [ ] Demo dry-run PASS

---

## 7. Technical Architecture

### 7.1 Python forecast-service + Java Backend (Strangler Fig pattern)

```
Frontend → nginx (/api/ catch-all) → Java Backend (JWT auth, tenant, cache)
                                        ├→ ForecastServiceAdapter → Python forecast-service (ML) → ClickHouse
                                        └→ NaiveForecastAdapter (fallback, in-process → TimescaleDB)
```

- **Python FastAPI** (`applications/forecast-service/`): ARIMA/SARIMA via `pmdarima.auto_arima(seasonal=True, m=24)`, Isolation Forest via `scikit-learn`, LSTM via `PyTorch`. Port 8090, Docker internal only.
- **Java Backend**: `ForecastPort` interface + `ForecastServiceAdapter` (REST client to Python, X-Tenant-ID header) + `NaiveForecastAdapter` (in-process fallback khi Python down, uses EsgMetricRepository)
- **Capability flag**: `uip.capabilities.forecast-engine=python` (default) | `naive` | `disabled`

> **Full spec:** [ARIMA & LSTM Technical Spec](../architecture/sprint4-arima-lstm-technical-spec.md)

### 7.1b Security Architecture (ADR-032 — 6 Decisions Summary)

| Decision | Resolution | Impact |
|---|---|---|
| D1: Auth model | Docker network isolation + X-Tenant-ID header — no JWT in Python | No host port, `uip-forecast-service:8090` internal only |
| D2: Kong scope | forecast-service KHÔNG qua Kong | Kong config unchanged (ADR-028) |
| D3: Nginx routing | Không thay đổi — `/api/` catch-all đã cover | Zero nginx changes |
| D4: Cross-tenant | Python validate X-Tenant-ID header từ Java backend | `api/dependencies.py` — missing → 403, invalid → 400 |
| D5: SQL injection | Parameterized queries (`clickhouse-connect`) | No string interpolation of user-controlled values |
| D6: Fallback source | `EsgMetricRepository.findByTypeAndBuilding()` → TimescaleDB | Zero new Java dependencies cho fallback |

### 7.2 ForecastPort (Java Interface)

```java
// backend/src/main/java/com/uip/backend/forecast/ForecastPort.java
public interface ForecastPort {
    ForecastResult forecast(String tenantId, String buildingId, int horizonDays);
}
```

### 7.3 Python Stack

```txt
# applications/forecast-service/requirements.txt
fastapi + uvicorn + clickhouse-connect + pandas + numpy
statsmodels + pmdarima + scikit-learn + torch (CPU-only)
prometheus-fastapi-instrumentator  # observability
```

### 7.4 Frontend Components

```
src/components/forecast/
  ├── ForecastChart.tsx          ← recharts ComposedChart
  ├── ConfidenceBand.tsx         ← Customized SVG area
  ├── AnomalyMarker.tsx          ← warning icon overlay
  └── ForecastTooltip.tsx        ← custom tooltip
src/hooks/
  └── useEnergyForecast.ts       ← React Query hook
```

### 7.5 Docker Compose — forecast-service (ADR-032 D1)

```yaml
# infrastructure/docker-compose.yml — ADD:
uip-forecast-service:
  build:
    context: ../applications/forecast-service
    dockerfile: Dockerfile
  container_name: uip-forecast-service
  # ADR-032 Decision 1: NO host port exposed — internal only
  environment:
    - CLICKHOUSE_HOST=uip-clickhouse
    - CLICKHOUSE_PORT=8123
    - CLICKHOUSE_DB=analytics
    - FORECAST_CACHE_TTL_MINUTES=15
    - FORECAST_MAPE_THRESHOLD=0.15
    - FORECAST_MIN_DATA_DAYS=30
  depends_on:
    uip-clickhouse:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8090/api/v1/forecast/health"]
    interval: 30s
    timeout: 10s
    retries: 3
  restart: unless-stopped
  networks:
    - uip-network
```

### 7.6 Prometheus Scrape Config (existing file update)

```yaml
# infra/monitoring/prometheus.yml — ADD:
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

### 7.7 forecast-service Prometheus Metrics (Technical Spec Section 11.1)

| Metric | Type | Labels | Purpose |
|---|---|---|---|
| `forecast_arima_fit_seconds` | Histogram | tenant_id, building_id | ARIMA model fit latency (buckets: 1, 5, 15, 30, 60, 90, 120s) |
| `forecast_fallback_total` | Counter | reason (mape_threshold, python_error, insufficient_data) | Fallback trigger count |
| `forecast_cache_hits_total` | Counter | — | Python TTL cache hit rate |
| `forecast_arima_mape_ratio` | Gauge | tenant_id, building_id | Latest MAPE per building |

### 7.8 Grafana Dashboard — forecast-service (Technical Spec Section 11.4)

**File:** `infra/monitoring/grafana/dashboards/uip-forecast.json`
**Dashboard UID:** `uip-forecast` | **Folder:** `UIP Smart City`

| Panel | PromQL | Visualization |
|---|---|---|
| Service Health | `up{job="forecast-service"}` | Stat |
| Request Rate | `rate(http_requests_total{job="forecast-service"}[5m])` | Time series |
| p95 Latency (cached) | `histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{handler="/api/v1/forecast/energy"}[5m]))` | Time series |
| ARIMA Fit p95 | `histogram_quantile(0.95, rate(forecast_arima_fit_seconds_bucket[10m]))` | Time series |
| Cache Hit Rate | `rate(forecast_cache_hits_total[5m])` | Stat |
| Fallback Rate | `rate(forecast_fallback_total[5m])` | Time series |
| MAPE per Building | `forecast_arima_mape_ratio` | Table |
| Error Rate 5xx | `rate(http_requests_total{job="forecast-service",status=~"5.."}[5m])` | Stat |

### 7.9 Alert Rules — forecast-service (Technical Spec Section 11.3)

| Alert | Expression | For | Severity | Action |
|---|---|---|---|---|
| ForecastServiceDown | `up{job="forecast-service"} == 0` | 2m | Critical | NaiveForecastAdapter fallback active |
| HighFallbackRate | fallback rate / request rate > 0.2 | 10m | Warning | Check MAPE or data quality |
| ColdCallSlow | ARIMA fit p95 > 90s | 5m | Warning | Dataset grown beyond expected |
| HighMAPE | `forecast_arima_mape_ratio > 0.20` | 15m | Warning | Review data quality per building |

---

## 8. Risk Register

| ID | Risk | Probability | Impact | Mitigation |
|---|---|---|---|---|
| R1 | ARIMA MAPE >15% trên real data | 30% | MED | Naive fallback auto-kicks in. Still provides baseline forecast |
| R2 | LSTM evaluation overruns → impacts ARIMA timeline | 20% | HIGH | LSTM is 1 SP evaluation task; if ARIMA slips, abort LSTM Day 8 |
| ~~R3~~ | ~~smile-core dependency conflict~~ | — | — | **REMOVED** — không còn dùng smile-core, đã chuyển sang Python stack |
| R4 | Grafana dashboard takes longer than 3 SP | 20% | LOW | Use pre-built Grafana JSON templates, minimal custom panels |
| R5 | City Authority deadline 06-15 — sprint ends 06-13 buffer only 2 days | 15% | CRITICAL | Zero scope creep in Week 2. Forecast is P1, not P0 for deadline |
| R6 | Historical data insufficient (<90 days) for good ARIMA | 25% | MED | Fallback to shorter training window. Document data requirements |
| R7 | Python cold start latency >60s with large dataset (8,760 points) | 20% | MED | Cache + pre-warm option (`POST /warmup`). ADR-032 AC revised: cold <60s acceptable |
| R8 | Cross-language debugging complexity (Java ↔ Python) | 25% | MED | Structured JSON logging + traceId pass-through (Technical Spec Section 11.5). Java logs "calling Python" + "fallback triggered" |
| R9 | Docker network compromise → Python accessible without auth | 10% | LOW | Sprint 5: add `X-Forecast-Key` shared secret. Sprint 4: network isolation sufficient per ADR-032 |

---

## 9. Verification Plan (Learning from Sprint 3)

### Gate Verify Script (`scripts/sprint-gate-verify.sh`)

```bash
#!/bin/bash
# Sprint 4 Gate Verification — chạy 1 session duy nhất (~2 giờ)
set -e
echo "=== S4 GATE VERIFY ==="

# 1. Clean slate
echo "[1/10] docker compose down -v && up -d"
docker compose -f infrastructure/docker-compose.yml down -v
docker compose -f infrastructure/docker-compose.yml up -d

# 2. Wait for services healthy (incl. forecast-service)
echo "[2/10] Waiting for services healthy..."
sleep 30

# 3. Verify forecast-service internal (no host port expected)
echo "[3/10] Verify forecast-service is NOT accessible from host..."
curl -s --connect-timeout 2 http://localhost:8090/api/v1/forecast/health && echo "FAIL: port exposed" || echo "OK: port not exposed (ADR-032 D1)"

# 4. Unit tests
echo "[4/10] ./gradlew testUnit jacocoTestUnitReport"
./gradlew cleanTest testUnit jacocoTestUnitReport

# 5. Integration tests
echo "[5/10] ./gradlew integrationTest"
./gradlew integrationTest

# 6. Prometheus scrape verify (incl. forecast-service)
echo "[6/10] Prometheus targets UP..."
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | .health'

# 7. Grafana dashboard verify
echo "[7/10] Grafana dashboard accessible..."
curl -s http://localhost:3001/api/dashboards/home | jq '.status'

# 8. Forecast API smoke test (via Java backend, not directly)
echo "[8/10] Forecast API smoke test (via backend)..."
# curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/forecast/energy?buildingId=...

# 9. Security verify (ADR-032)
echo "[9/10] Security: verify X-Tenant-ID required..."
# Missing header → 403 (test via internal docker exec or mock)

# 10. Regression summary
echo "[10/10] Regression PASS summary"
echo "Done. All gates verified."
```

### Pre-Demo Checklist (from Sprint 3 lessons)

1. `cleanTest` trước mỗi test run — tránh stale XML
2. `docker compose down -v && up -d` — clean slate
3. Playwright E2E full run (không chỉ smoke)
4. Boundary input test cho forecast API (horizonDays=0, 365, -1)
5. SPA navigation test sau any frontend change
6. JaCoCo coverage chỉ đọc khi BUILD SUCCESSFUL
7. Confirm all Change Orders formalized

---

## 10. Descoped / Deferred Items

| Item | Status | Reason |
|---|---|---|
| ClickHouse 2-node HA | **DESCOPED** | PO confirmed 2026-05-25 — single-node stable, not needed for deadline |
| BMS SDK (Modbus/BACnet) | Deferred Sprint 5 | Detail-plan shift — faster to do after AI foundation |
| iot-ingestion-service shadow | Deferred Sprint 5 | Depends on BMS SDK |
| Kong production TLS | Deferred Sprint 5 | Not needed for internal demo |

---

## 11. Files to Create/Modify

### New Files — Python forecast-service
- `applications/forecast-service/main.py` — FastAPI entry point + Prometheus instrumentator
- `applications/forecast-service/config.py` — Settings (ClickHouse, cache, thresholds)
- `applications/forecast-service/Dockerfile` — Python 3.12-slim, non-root user (Technical Spec Section 11.6)
- `applications/forecast-service/requirements.txt` — FastAPI + statsmodels + pmdarima + scikit-learn + torch + prometheus-fastapi-instrumentator
- `applications/forecast-service/api/router.py` — /energy, /anomaly, /health, /models endpoints
- `applications/forecast-service/api/schemas.py` — Pydantic request/response models
- `applications/forecast-service/api/dependencies.py` — **Tenant extraction per ADR-032 Decision 4** (X-Tenant-ID header → 403 missing, 400 invalid)
- `applications/forecast-service/services/forecast_service.py` — orchestrator + fallback
- `applications/forecast-service/services/arima_service.py` — `auto_arima(seasonal=True, m=24)` + forecast + CI
- `applications/forecast-service/services/naive_service.py` — rolling average fallback
- `applications/forecast-service/services/backtest_service.py` — walk-forward validation
- `applications/forecast-service/services/anomaly_service.py` — Isolation Forest
- `applications/forecast-service/data/clickhouse_client.py` — CH query (**parameterized per ADR-032 Decision 5**)
- `applications/forecast-service/data/preprocessor.py` — gap filling + outlier removal
- `applications/forecast-service/models/forecast_result.py` — Data classes
- `applications/forecast-service/models/cache.py` — In-memory TTL cache
- `applications/forecast-service/metrics.py` — **Prometheus custom metrics** (Technical Spec Section 11.1): ARIMA_FIT_DURATION, FORECAST_FALLBACK_TOTAL, FORECAST_CACHE_HIT, FORECAST_MAPE
- `applications/forecast-service/config/logging_config.py` — **JSON formatter** with traceId + tenantId (Technical Spec Section 11.5)
- `applications/forecast-service/tests/` — unit + integration tests

### New Files — Java Backend
- `backend/src/main/java/com/uip/backend/forecast/ForecastPort.java` — Interface
- `backend/src/main/java/com/uip/backend/forecast/ForecastResult.java` — Record DTO
- `backend/src/main/java/com/uip/backend/forecast/ForecastPoint.java` — Record DTO
- `backend/src/main/java/com/uip/backend/forecast/ForecastService.java` — Orchestrator
- `backend/src/main/java/com/uip/backend/forecast/ForecastController.java` — REST endpoint
- `backend/src/main/java/com/uip/backend/forecast/ForecastServiceAdapter.java` — REST client to Python (X-Tenant-ID + X-Trace-Id headers per ADR-032 D4)
- `backend/src/main/java/com/uip/backend/forecast/NaiveForecastAdapter.java` — In-process fallback (EsgMetricRepository per ADR-032 D6)
- `backend/src/test/java/com/uip/backend/forecast/ForecastControllerIT.java`
- `backend/src/test/java/com/uip/backend/forecast/ForecastServiceAdapterTest.java` — Mock Python responses

### New Files — Frontend
- `frontend/src/components/forecast/ForecastChart.tsx`
- `frontend/src/hooks/useEnergyForecast.ts`
- `scripts/sprint-gate-verify.sh`

### New Files — Observability (DevOps)
- `infra/monitoring/prometheus/alerts/forecast-alerts.yml` — 4 alert rules: ForecastServiceDown, HighFallbackRate, ColdCallSlow, HighMAPE (Technical Spec Section 11.3)
- `infra/monitoring/grafana/dashboards/uip-forecast.json` — Grafana dashboard 8 panels (Technical Spec Section 11.4)

### Modified Files
- `infrastructure/docker-compose.yml` — add forecast-service (no host port, non-root, healthcheck) + monitoring stack + HPA
- `infra/monitoring/prometheus.yml` — add analytics-service + forecast-service + Kong scrape targets
- `backend/src/main/resources/application.yml` — add forecast capability flag + `UIP_FORECAST_SERVICE_URL`
- `backend/src/main/java/com/uip/backend/common/config/CapabilityProperties.java` — add forecastEngine flag
- `docs/api/openapi.json` — update with forecast endpoints

---

## 12. City Authority Deadline Checklist

> **Deadline: 2026-06-15 — NON-NEGOTIABLE**

- [x] GRI 302/305 export API live (Sprint 3)
- [x] Keycloak RSA auth (Sprint 3)
- [ ] Observability dashboard live — team có thể monitor health
- [ ] 664+ regression PASS
- [ ] Staging deployment verified (DevOps smoke test)
- [ ] City Authority demo rehearsal (≥1 dry-run trước deadline)

---

## 13. Sprint 4 Gate Checklist

| Gate | Criteria | AC | Target |
|---|---|---|---|
| G1 | Grafana dashboard live + Prometheus scraping (incl. forecast-service) | AC-01 | Thu 06-12 |
| G2 | ARIMA forecast API live, MAPE <15%, response thresholds per ADR-032 | AC-02 | Wed 06-11 |
| G3 | Frontend forecast chart renders | AC-03 | Thu 06-12 |
| G4 | LSTM go/no-go decision documented | AC-04 | Mon 06-09 |
| G5 | 664+ testUnit PASS, JaCoCo >=80%/65% | AC-05 | Thu 06-12 |
| G6 | Forecast-service observability: metrics + alerts + dashboard + JSON logs | AC-07 | Thu 06-12 |
| G7 | Security tests PASS: X-Tenant-ID 403/400, parameterized queries | AC-02 | Wed 06-11 |
| G8 | Demo dry-run PASS | — | Thu 06-12 |
| G9 | Zero P0/P1 bugs | — | Fri 06-13 |
| G10 | PO Demo sign-off | — | Fri 06-13 15:00 |

---

*Document created: 2026-05-25 | Owner: PO + PM*
*Status: APPROVED — Updated post ADR-032 + Technical Spec approval (2026-05-25)*
*Next: Sprint 3 Gate Review 2026-05-30 → Sprint 4 kickoff 2026-06-02*

---

## References

- **[ARIMA & LSTM Technical Spec](../architecture/sprint4-arima-lstm-technical-spec.md)** — Deep-dive: ARIMA(p,d,q), MAPE calculation, backtest validation, data pipeline, naive fallback, LSTM evaluation, Day-by-Day walkthrough, observability (Section 11)
- **[ADR-032: forecast-service Security, Routing & Tenant Isolation](../architecture/ADR-032-forecast-service-security-routing.md)** — APPROVED 2026-05-25. 6 decisions: Docker network isolation, no Kong, no nginx change, X-Tenant-ID header, parameterized queries, EsgMetricRepository fallback
- [Sprint 4 AI Spec Review](../reports/sprint4-ai-spec-review-2026-05-25.md) — Pre-implementation review: 3 blockers resolved by ADR-032, component readiness 16/18 post-fix
- [Sprint 3 Master Plan](sprint3-master-plan.md) — Previous sprint context
- [Detail Plan](detail-plan.md) — MVP3 full roadmap
