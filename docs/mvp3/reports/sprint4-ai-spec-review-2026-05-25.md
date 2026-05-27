# Sprint 4 — AI Spec Completeness Review

**Document:** `sprint4-arima-lstm-technical-spec.md` — Multi-Role Assessment  
**Review Date:** 2026-05-25  
**Reviewers:** SA + QA + BA (coordinated)  
**Verdict:** ⚠️ **CONDITIONAL READY** — 3 blockers must be resolved before implementation starts

---

## Summary Scorecard

| Dimension | Score | Status |
|---|---|---|
| Architecture Design | 8/10 | ✅ Strong |
| Data Pipeline | 7/10 | ⚠️ SQL injection in main body |
| Security Model | 4/10 | ❌ ADR-032 not written |
| Acceptance Criteria | 6/10 | ❌ AC#11 latency is wrong |
| Test Coverage Plan | 6/10 | ⚠️ Missing scenarios, no Python CI config |
| Business Coverage | 5/10 | ⚠️ Anomaly UX undefined, data_quality levels undefined |
| LSTM Specification | 3/10 | ❌ Skeleton only — not implementable |
| **Overall** | **~57%** | **⚠️ Cannot start safely without fixing 3 blockers** |

---

## 🔴 BLOCKER 1 — SQL Injection còn trong main spec body (Section 5)

**Severity:** CRITICAL — OWASP A03:2021  
**Location:** Section 5, `clickhouse_client.py`

Main spec body (Section 5) vẫn dùng unsafe string interpolation:

```python
# ❌ Section 5 — UNSAFE (còn trong spec)
query = """...'WHERE tenant_id = '{tenant_id}'...""".format(
    tenant_id=tenant_id, building_id=building_id, days=days
)
```

Fix parameterized đã có trong **Appendix C Gap 1** nhưng KHÔNG được cập nhật lại Section 5.  
Risk: Backend engineer implement từ Section 5 mà không đọc Appendix → ship vulnerable code.

**Action required:** SA/Backend phải update Section 5 với parameterized query TRƯỚC khi implementation bắt đầu.

---

## 🔴 BLOCKER 2 — ADR-032 (Security Model) vẫn PENDING

**Severity:** CRITICAL — blocks `api/dependencies.py` implementation  
**Location:** Appendix C, cuối document ("Status: Approved with SA spike pending")

Spec đã identify 3 options (A/B/C) cho auth model nhưng chỉ có **recommendation**, không phải **decision**:

> "Recommendation: Option A cho POC + Option B cho production Sprint 5."

**Vấn đề với current state:**
- `api/dependencies.py` file được liệt kê trong file structure nhưng **không có implementation spec**
- Backend engineer không biết `verify_internal_request()` cần implement gì
- Appendix C Gap 3 có code snippet nhưng chưa được đưa vào main spec flow

**Action required:** SA phải viết ADR-032 và cập nhật Section 6 (`api/dependencies.py`) với implementation rõ ràng. Sprint 4 Day 1 spike là đúng nhưng cần output cụ thể hơn.

---

## 🔴 BLOCKER 3 — AC #11: Response <1s first call là **SAI**

**Severity:** CRITICAL — QA sẽ fail test này, gây tranh cãi sprint end  
**Location:** Appendix B, row #11

```
| 11 | Response time <1s first call | Perf | <1000ms |
```

**Vấn đề:** `pmdarima.auto_arima()` trên 365 ngày hourly data (8,760 points) với `stepwise=True` thường mất **15–90 giây** tùy complexity của chuỗi. 1 giây là không thực tế cho cold cache.

**Benchmark thực tế:**
- `auto_arima` stepwise trên 8K points: 15–60s
- `auto_arima` với `seasonal=True, m=24`: 30–90s
- Phải add `max_order=10, maxiter=50` để bound runtime

**Action required:** SA phải revise AC #11 với realistic threshold, ví dụ:

| Scenario | Revised Threshold |
|---|---|
| First call (cold cache, full auto_arima) | <60s |
| First call with data hint (known p,d,q) | <10s |
| Cached response (Python TTL cache) | <500ms |
| Java cached response (Caffeine) | <10ms |

---

## 🟡 HIGH GAP 4 — Model Persistence Strategy thiếu hoàn toàn

**Severity:** HIGH — ảnh hưởng performance và memory  
**Không được đề cập trong spec**

**Vấn đề:**
- `TTLCache` trong Section 6 cache **ForecastResponse** (kết quả), không cache **fitted ARIMA model**
- Mỗi request đến → fetch data từ CH → auto_arima fit → forecast
- Với 100 buildings × 30 req/hour = 3,000 ARIMA fits/hour (expensive)

**Phân tích:**

| Option | Pros | Cons |
|---|---|---|
| Cache response only (current spec) | Simple, no model complexity | Refit on every cache miss |
| Cache fitted model object (pickle) | Fast reuse | Memory pressure, model staleness |
| Cache fitted model params (p,d,q) | Lightweight, fast warm fit | Slightly less accurate (fixed params) |

**Recommendation:** Thêm `ModelCache` riêng — lưu `(order, seasonal_order)` từ `auto_arima` fit, reuse cho calls trong 6h, full refit sau 24h. Spec cần document điều này.

---

## 🟡 HIGH GAP 5 — Concurrent Request Race Condition (Cache Stampede)

**Severity:** HIGH — production issue  

Khi cache miss (startup, sau TTL expire):
- 10 requests hit endpoint đồng thời
- All 10 trigger `auto_arima` fit (vì `cache.get()` returns None cho tất cả)
- 10 parallel ARIMA fits → CPU spike, OOM risk trên container nhỏ

**Spec không có:** mutex/semaphore per (tenant_id, building_id) key, không có "in-flight" tracking.

**Fix pattern:**
```python
# Cần thêm vào forecast_service.py
_in_flight: dict[str, asyncio.Lock] = {}

async def forecast_with_fallback(...):
    cache_key = f"{tenant_id}:{building_id}:{horizon_days}"
    if cache_key not in _in_flight:
        _in_flight[cache_key] = asyncio.Lock()
    async with _in_flight[cache_key]:
        cached = cache.get(cache_key)
        if cached: return cached
        result = await arima_forecast(...)
        cache.set(cache_key, result)
        return result
```

---

## 🟡 HIGH GAP 6 — LSTM Spec là Skeleton, Không Implementable

**Severity:** HIGH — Section 8 không đủ để implement  

Hiện tại Section 8 có:
- ✅ `EnergyLSTM` class definition (basic)
- ✅ Endpoint signature `/api/v1/forecast/lstm`

Thiếu hoàn toàn:
- ❌ Sequence length (lookback window) — bao nhiêu bước?
- ❌ Normalization strategy (MinMaxScaler? StandardScaler? per-building?)
- ❌ Training loop — epochs, batch size, loss function, optimizer
- ❌ Model save/load — pickle? ONNX? per-building model files?
- ❌ Cold start: nếu chưa có model cho building → train on demand? fail? fallback?
- ❌ Training data requirement — cần bao nhiêu data cho LSTM?
- ❌ Go/no-go criteria cho Day 7 — ">2% MAPE improvement" nhưng so sánh trên data nào?

**Impact:** LSTM endpoint có thể skip cho Sprint 4 core delivery, nhưng nếu team muốn include thì cần thêm 1-2 ngày design sprint.

---

## 🟡 MEDIUM GAP 7 — `data_quality` field không được định nghĩa

**Severity:** MEDIUM — Frontend engineer không biết render gì  
**Location:** Section 6, `ForecastResponse.data_quality`

```python
data_quality: str  # "EXCELLENT" | "GOOD" | "MINIMAL" | "INSUFFICIENT"
```

Spec **không define** khi nào mỗi level được assign. Frontend không biết màu sắc / icon / tooltip nào.

**Required definition:**

| Level | Condition | UI treatment |
|---|---|---|
| EXCELLENT | ≥365 ngày data, MAPE <5% | Green badge |
| GOOD | ≥90 ngày data, MAPE 5–10% | Blue badge |
| MINIMAL | 30–90 ngày data, MAPE 10–15% | Yellow badge |
| INSUFFICIENT | <30 ngày data | Red, forecast disabled |

---

## 🟡 MEDIUM GAP 8 — Anomaly Detection Business Flow thiếu

**Severity:** MEDIUM — endpoint `/api/v1/forecast/anomaly` implement xong nhưng không biết dùng vào đâu  

Không có:
- Ai nhìn thấy anomaly? (City operator? Building manager? ESG admin?)
- Anomaly alert → tự động tạo Alert trong AlertEngine không?
- UI component nào hiển thị anomaly points? (Spec không mention frontend)
- Threshold cho anomaly (Isolation Forest contamination param mặc định là 0.1 = 10%)

---

## 🟡 MEDIUM GAP 9 — Python CI/CD Configuration không có

**Severity:** MEDIUM — QA không có quality gate cho Python  

Thiếu:
- `pytest.ini` hoặc `pyproject.toml` với test config
- Coverage threshold (80% cho Python?)
- `Makefile` hoặc CI command: `pytest --cov=. --cov-report=xml --cov-fail-under=80`
- Python lint: `ruff` hoặc `flake8`
- Type check: `mypy` hoặc không dùng?

---

## 🟢 STRENGTHS — Những phần làm tốt

1. **Architecture justification rất rõ ràng** — bảng so sánh Python vs Java (Section 1) thuyết phục
2. **Fallback chain đầy đủ** — Java → Python → Naive → Error, không có single point of failure
3. **ForecastPort abstraction** — clean hexagonal port pattern, testable
4. **ARIMA/MAPE/Backtest explanation** — đủ để Backend engineer hiểu và implement
5. **Day-by-Day walkthrough** — realistic task breakdown, tracing daily
6. **Appendix C Gap Analysis** — tự identify gaps trước khi review, điểm cộng lớn
7. **SP recalculation** — Python saves 11 SP, well-justified
8. **Docker Compose config** — production-ready với healthcheck, restart, env vars
9. **Error contract table** (Gap 5) — rõ ràng về HTTP status → Java handling

---

## Recommended Pre-Implementation Actions

### Phải làm TRƯỚC Day 1 Sprint 4 (Deadline: 06-01-2026)

| # | Action | Owner | Effort |
|---|---|---|---|
| B1 | Update Section 5 `clickhouse_client.py` với parameterized queries | SA | 30 phút |
| B2 | Viết ADR-032 + cập nhật Section 6 `dependencies.py` với final auth decision | SA | 2-3 giờ |
| B3 | Revise AC #11 với realistic latency thresholds | SA + QA | 30 phút |
| H4 | Document model caching strategy (Fitted model cache vs Result cache) | SA | 1 giờ |
| H5 | Add concurrency guard pattern vào `forecast_service.py` spec | Backend | 1 giờ |

### Có thể làm trong Sprint 4 nhưng cần sớm (trước Day 4)

| # | Action | Owner |
|---|---|---|
| M7 | Define `data_quality` level conditions + UI spec | BA + Frontend |
| M8 | Define anomaly detection user flow (who sees it, what action) | BA |
| M9 | Add `pytest.ini`, coverage threshold, lint config vào spec | QA |

### Defer sang Sprint 5 nếu cần

| # | Action |
|---|---|
| LSTM | Full LSTM spec (training loop, model persistence, evaluation criteria) |
| ADR-032 B | JWT passthrough cho production |
| Gap 6 | Kong rate-limiting cho forecast |
| Gap 7 | Prometheus scrape cho forecast-service |
| Gap 8 | Docker non-root user |

---

## Implementation Readiness per Component

| Component | Ready? | Blocking Issue |
|---|---|---|
| `main.py` + `config.py` | ✅ Yes | — |
| `api/router.py` | ✅ Yes | — |
| `api/schemas.py` | ✅ Yes | — |
| `api/dependencies.py` | ❌ No | ADR-032 pending (Blocker 2) |
| `data/clickhouse_client.py` | ❌ No | SQL injection in main spec (Blocker 1) |
| `data/preprocessor.py` | ✅ Yes | — |
| `services/arima_service.py` | ✅ Yes | — |
| `services/forecast_service.py` | ⚠️ Partial | Concurrency guard missing (Gap 5) |
| `services/backtest_service.py` | ✅ Yes | — |
| `services/naive_service.py` | ✅ Yes | — |
| `services/anomaly_service.py` | ✅ Yes (code impl) | Business flow undefined (Gap 8) |
| `services/lstm_service.py` | ❌ No | Skeleton only, cannot implement (Gap 6) |
| `models/cache.py` | ⚠️ Partial | No model cache strategy (Gap 4) |
| Java `ForecastPort.java` | ✅ Yes | — |
| Java `ForecastServiceAdapter.java` | ✅ Yes | — |
| Java `NaiveForecastAdapter.java` | ⚠️ Partial | Data source: `EsgMetricRepository` — confirm exists |
| Java `ForecastController.java` | ✅ Yes | — |
| Docker Compose config | ✅ Yes | — |
| Tests | ⚠️ Partial | AC #11 wrong, no CI config |

**Ready now: 11/18 (61%)**  
**Ready after fixing 3 blockers: 16/18 (89%)**  
**Full ready (incl. LSTM): Sprint 5**

---

*Review completed: 2026-05-25*  
*Spec version: Revised — Python FastAPI Architecture*  
*Next action: SA to resolve Blockers 1–3 by 2026-06-01*
