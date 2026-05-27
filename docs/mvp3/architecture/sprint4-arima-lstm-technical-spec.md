# Sprint 4 — Forecast Service: Python FastAPI Technical Spec

**Document:** Technical Specification for Predictive AI Foundation
**Sprint:** MVP3-4 (2026-06-02 → 2026-06-13)
**Author:** SA + Backend Lead
**Date:** 2026-05-25 (Revised — Python FastAPI Architecture)
**Status:** APPROVED — Blockers B1–B3 resolved by ADR-032 (2026-05-25)

---

## Mục lục

1. [Kiến trúc Tổng thể — forecast-service](#1-architecture)
2. [ARIMA — Cách hoạt động](#2-arima)
3. [MAPE — Metric đánh giá](#3-mape)
4. [Backtest Validation](#4-backtest)
5. [Data Pipeline — ClickHouse → Python → Forecast](#5-data-pipeline)
6. [forecast-service Implementation (Python)](#6-implementation-python)
7. [Java Backend Integration](#7-java-backend)
8. [LSTM trong Python — Không còn spike riêng](#8-lstm)
9. [Rủi ro & Mitigation](#9-risks)
10. [Day-by-Day Walkthrough](#10-walkthrough)
11. [DevOps & Observability](#11-devops)

---

## 1. Kiến trúc Tổng thể — forecast-service

### Tại sao Python thay Java?

| Khía cạnh | Java (smile-core) | Python (statsmodels + PyTorch) |
|---|---|---|
| **ARIMA maturity** | smile-core ARIMA — basic, no auto-arima | statsmodels — full SARIMAX, auto-arima (pmdarima) |
| **LSTM ecosystem** | Deeplearning4j — heavy, complex | PyTorch/Keras — de facto standard, rich tooling |
| **ML tooling** | Limited — JMX, visualvm | Jupyter notebooks, matplotlib, tensorboard |
| **Grid search** | Manual loop 48 combos | pmdarima.auto_arima() — built-in, efficient |
| **Confidence interval** | smile-core có nhưng limited | Full prediction intervals + bootstrap |
| **Anomaly detection** | Isolation Forest phải tự implement | scikit-learn IsolationForest — production-ready |
| **Experiment tracking** | None built-in | MLflow / Weights & Biases available |
| **Dependencies** | +5 MB (smile-core) | +300 MB Docker image (but isolated service) |

**Kết luận:** Python là đúng tool cho đúng job. Java giữ role orchestration, Python làm ML heavy lifting.

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      Frontend (React)                        │
│  GET /api/v1/forecast/energy?tenantId=X&buildingId=Y        │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Java Backend (uip-backend:8080)                  │
│                                                               │
│  ForecastController ──→ ForecastService                       │
│        (auth, tenant check, caching)                          │
│                              │                                │
│                    ForecastPort interface                     │
│                              │                                │
│              ┌───────────────┴───────────────┐                │
│              ▼                               ▼                │
│  ForecastServiceAdapter          NaiveForecastAdapter         │
│  (REST call to Python)           (in-process fallback)        │
│              │                                                │
└──────────────┼───────────────────────────────────────────────┘
               │ HTTP REST
               ▼
┌─────────────────────────────────────────────────────────────┐
│         Python forecast-service (FastAPI :8090)               │
│                                                               │
│  /api/v1/forecast/energy                                      │
│      ├─ Data fetch: ClickHouse JDBC query                     │
│      ├─ Preprocess: fill gaps, outliers, stationarity         │
│      ├─ Model: auto_arima → fit → forecast + CI               │
│      ├─ Backtest: walk-forward MAPE validation                │
│      ├─ Fallback: naive rolling average if MAPE >15%          │
│      └─ Anomaly: Isolation Forest on residuals                │
│                                                               │
│  /api/v1/forecast/health                                      │
│  /api/v1/forecast/models  ← model catalog                    │
│  /api/v1/forecast/backtest ← MAPE report                     │
│                                                               │
│  Stack: FastAPI + statsmodels + pmdarima + scikit-learn       │
│  LSTM: PyTorch (same service, different endpoint)             │
└─────────────────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────┐
│         ClickHouse (analytics.esg_readings) :8123             │
│         302K+ rows, hourly energy data per building           │
└─────────────────────────────────────────────────────────────┘
```

### Service Placement trong Docker Compose

```yaml
# infrastructure/docker-compose.yml — ADD:
  uip-forecast-service:
    build:
      context: ../applications/forecast-service
      dockerfile: Dockerfile
    container_name: uip-forecast-service
    # ADR-032 Decision 1: NO host port exposed — reachable only within uip-network
    # Internal URL: http://uip-forecast-service:8090
    # ports: - "8090:8090"  ← intentionally absent
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

### Capability Flag (Java side)

```yaml
# application.yml
uip:
  capabilities:
    forecast-engine: ${UIP_FORECAST_ENGINE:python}  # python | naive | disabled

# python = call forecast-service REST
# naive = in-process rolling average (no external service)
# disabled = return 501 Not Implemented
```

---

## 2. ARIMA — Cách hoạt động

### ARIMA(p,d,q)

- **p (AutoRegressive):** Dùng bao nhiêu giá trị quá khứ. `p=3` = dùng 3 giá trị gần nhất
- **d (Integrated):** Số lần differencing để chuỗi stationary. `d=1` = lấy hiệu
- **q (Moving Average):** Dùng bao nhiêu residuals quá khứ. `q=2` = dùng 2 residuals

### Python auto_arima thay grid search thủ công

```python
# Thay vì Java grid search 48 combos → dùng pmdarima (auto-arima)
import pmdarima as pm

model = pm.auto_arima(
    time_series,
    start_p=0, start_q=0,
    max_p=3, max_q=3,
    d=None,           # auto-detect stationarity
    seasonal=True,     # capture weekly pattern
    m=24,              # 24 hours = daily cycle
    stepwise=True,     # efficient search
    information_criterion='aic',
    trace=True,
    error_action='ignore',
    suppress_warnings=True,
)
```

**Lợi ích auto_arima:**
- Tự động detect `d` (ADF test)
- Seasonal ARIMA (SARIMA) — capture daily/weekly cycles
- Stepwise search — nhanh hơn brute-force grid
- Trả về best model + AIC + diagnostics

### Confidence Intervals

```python
# statsmodels SARIMAX — full prediction intervals
from statsmodels.tsa.statespace.sarimax import SARIMAX

model = SARIMAX(
    train_data,
    order=(p, d, q),
    seasonal_order=(P, D, Q, 24),  # daily seasonality
)
result = model.fit(disp=False)

forecast = result.get_forecast(steps=horizon * 24)
predicted = forecast.predicted_mean
ci = forecast.conf_int(alpha=0.05)  # 95% CI
```

---

## 3. MAPE — Metric đánh giá

### MAPE = Mean Absolute Percentage Error

```
              Σ |actual_i - predicted_i| / |actual_i|
  MAPE = ────────────────────────────────────────────── × 100%
                            n
```

### Threshold cho Energy Forecasting

| MAPE | Đánh giá | Context |
|---|---|---|
| **<5%** | Excellent | Short-term (<7 days), stable building |
| **5-10%** | Very Good | Typical ARIMA cho building energy |
| **10-15%** | Good | **UIP target** — 30-day horizon |
| **15-25%** | Fair | Cần investigate seasonal pattern |
| **>25%** | Poor | Fallback naive, ARIMA không phù hợp |

### WAPE thay MAPE khi có zero/near-zero values

```python
def wape(actual: np.ndarray, predicted: np.ndarray) -> float:
    """Weighted Absolute Percentage Error — robust khi có zeros."""
    return np.sum(np.abs(actual - predicted)) / np.sum(np.abs(actual))

def mape_safe(actual: np.ndarray, predicted: np.ndarray, epsilon=1.0) -> float:
    """MAPE với epsilon-smoothing để tránh division by zero."""
    actual_safe = np.where(np.abs(actual) < epsilon, epsilon, actual)
    return np.mean(np.abs((actual - predicted) / actual_safe))
```

### Fallback Logic (Python)

```python
MAPE_THRESHOLD = 0.15  # 15%

async def forecast_with_fallback(tenant_id: str, building_id: str, horizon_days: int):
    # 1. Try ARIMA
    result = await arima_forecast(tenant_id, building_id, horizon_days)

    if result.mape > MAPE_THRESHOLD:
        logger.warning(f"ARIMA MAPE={result.mape:.1%} > {MAPE_THRESHOLD:.0%} → naive fallback")
        result = naive_forecast(tenant_id, building_id, horizon_days)
        result.is_fallback = True
        result.model = "NAIVE"

    return result
```

---

## 4. Backtest Validation

### Walk-Forward Backtest

```
Total data: 365 days hourly (8,760 points)
Train/Test split: 80/20

for i in range(train_size, len(data) - horizon):
    train = data[i - train_size : i]
    model = fit_arima(train)
    forecast = model.predict(horizon)
    actuals.append(data[i])
    predictions.append(forecast[0])

mape = calculate_mape(actuals, predictions)
```

### Python Implementation

```python
def walk_forward_backtest(
    series: np.ndarray,
    horizon: int = 24,
    train_ratio: float = 0.8,
) -> BacktestResult:
    train_size = int(len(series) * train_ratio)
    actuals, predictions = [], []

    for i in range(train_size, len(series) - horizon):
        train_window = series[max(0, i - train_size):i]

        try:
            model = pm.auto_arima(
                train_window,
                seasonal=True, m=24,
                stepwise=True,
                suppress_warnings=True,
                error_action='ignore',
            )
            fc = model.predict(n_periods=horizon)
            actuals.append(series[i])
            predictions.append(fc[0])
        except Exception:
            continue

    mape = mape_safe(np.array(actuals), np.array(predictions))
    return BacktestResult(
        mape=float(mape),
        n_points=len(actuals),
        actuals=actuals,
        predictions=predictions,
    )
```

### Validation Criteria

| Check | Pass Threshold | Action nếu Fail |
|---|---|---|
| MAPE overall | <15% | Fallback naive |
| MAPE per-building | <20% | Exclude building, log warn |
| Residual Ljung-Box | p > 0.05 | Increase model complexity |
| Forecast stability | 3 runs same MAPE within 1% | Fix random seed |
| Data sufficiency | ≥720 hours (30 days) | Return insufficient |

---

## 5. Data Pipeline — ClickHouse → Python → Forecast

### ClickHouse Data Source

> ⚠️ **Security note (ADR-032 Decision 5):** `tenant_id` is received from the internal `X-Tenant-ID` header (set by Java backend after JWT verification), NOT from query params. All values use parameterized queries — no string interpolation.

```python
# forecast-service/data/clickhouse_client.py
import clickhouse_connect
from config import settings

async def fetch_hourly_energy(
    tenant_id: str,
    building_id: str,
    days: int = 365,
) -> pd.DataFrame:
    client = clickhouse_connect.get_client(
        host=settings.clickhouse_host,
        port=settings.clickhouse_port,
        database=settings.clickhouse_db,
    )

    # SAFE: parameterized — tenant_id and building_id are NEVER string-interpolated
    query = """
        SELECT
            toUnixTimestamp(toStartOfHour(recorded_at)) AS ts_hour,
            sum(value) AS total_kwh
        FROM esg_readings
        WHERE tenant_id = %(tenant_id)s
          AND building_id = %(building_id)s
          AND metric_type = 'ENERGY'
          AND recorded_at >= now() - INTERVAL %(days)s DAY
        GROUP BY ts_hour
        ORDER BY ts_hour ASC
    """
    return client.query_df(query, parameters={
        "tenant_id": tenant_id,
        "building_id": building_id,
        "days": str(days),
    })
```

### Data Preprocessing (Python — rich tooling)

```python
# forecast-service/data/preprocessor.py
import pandas as pd
import numpy as np
from scipy import interpolate

def preprocess(df: pd.DataFrame, freq: str = 'H') -> np.ndarray:
    series = df.set_index('ts_hour')['total_kwh']

    # 1. Fill missing hours — linear interpolation
    full_index = pd.date_range(series.index.min(), series.index.max(), freq=freq)
    series = series.reindex(full_index)
    series = series.interpolate(method='linear')

    # 2. Remove outliers — IQR method
    Q1, Q3 = series.quantile(0.25), series.quantile(0.75)
    IQR = Q3 - Q1
    mask = (series >= Q1 - 3 * IQR) & (series <= Q3 + 3 * IQR)
    series[~mask] = np.nan
    series = series.interpolate(method='linear')

    # 3. Stationarity test (ADF)
    from statsmodels.tsa.stattools import adfuller
    adf_result = adfuller(series.dropna())
    is_stationary = adf_result[1] < 0.05

    return series.values, is_stationary
```

### Python Dependencies

```txt
# applications/forecast-service/requirements.txt
fastapi==0.111.0
uvicorn[standard]==0.30.0
clickhouse-connect==0.7.0
pandas==2.2.2
numpy==1.26.4
statsmodels==0.14.2
pmdarima==2.0.4
scikit-learn==1.5.0
scipy==1.14.0
pydantic==2.7.0
httpx==0.27.0
# LSTM (same service)
torch==2.3.1  # CPU only
# Optional: experiment tracking
# mlflow==2.14.0
```

### Dockerfile

```dockerfile
# applications/forecast-service/Dockerfile
FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

COPY . .

EXPOSE 8090

HEALTHCHECK CMD curl -f http://localhost:8090/api/v1/forecast/health || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]
```

---

## 6. forecast-service Implementation (Python)

### File Structure

```
applications/forecast-service/
├── Dockerfile
├── requirements.txt
├── main.py                          ← FastAPI app entry
├── config.py                        ← Settings (pydantic BaseSettings)
├── api/
│   ├── router.py                    ← API routes
│   ├── schemas.py                   ← Pydantic models (request/response)
│   └── dependencies.py             ← Auth, tenant validation
├── services/
│   ├── forecast_service.py          ← Main orchestrator
│   ├── arima_service.py             ← ARIMA + auto-arima
│   ├── naive_service.py             ← Rolling average fallback
│   ├── backtest_service.py          ← Walk-forward validation
│   └── anomaly_service.py           ← Isolation Forest
├── data/
│   ├── clickhouse_client.py         ← CH connection + queries
│   └── preprocessor.py              ← Clean, interpolate, stationarity
├── models/
│   ├── forecast_result.py           ← Data classes
│   └── cache.py                     ← In-memory TTL cache
└── tests/
    ├── test_arima_service.py        ← Unit: synthetic data
    ├── test_backtest_service.py     ← Unit: known series
    ├── test_preprocessor.py         ← Unit: gap filling
    ├── test_naive_service.py        ← Unit: edge cases
    ├── test_api.py                  ← Integration: FastAPI test client
    └── conftest.py                  ← Fixtures
```

### Dependencies — Tenant Extraction (ADR-032 Decision 4)

```python
# api/dependencies.py
# ADR-032 Decision 4: tenant_id comes from X-Tenant-ID header ONLY.
# Java backend sets this after JWT verification. Python never accepts
# tenant_id from query params — prevents cross-tenant parameter injection.
import re
from fastapi import Request, HTTPException

async def get_tenant_id(request: Request) -> str:
    tenant_id = request.headers.get("X-Tenant-ID")
    if not tenant_id or not tenant_id.strip():
        raise HTTPException(
            status_code=403,
            detail="Missing X-Tenant-ID header — internal access only"
        )
    # Prevent header injection: allow only alphanumeric + hyphen/underscore
    if not re.match(r'^[a-zA-Z0-9_-]{1,64}$', tenant_id):
        raise HTTPException(status_code=400, detail="Invalid X-Tenant-ID format")
    return tenant_id
```

### API Endpoints

```python
# api/router.py
from fastapi import APIRouter, Depends, Query
from api.dependencies import get_tenant_id

router = APIRouter(prefix="/api/v1/forecast", tags=["forecast"])

@router.get("/energy", response_model=ForecastResponse)
async def forecast_energy(
    building_id: str = Query(..., description="Building ID"),
    horizon_days: int = Query(30, ge=1, le=90, description="Forecast horizon"),
    include_backtest: bool = Query(False, description="Include MAPE backtest"),
    tenant_id: str = Depends(get_tenant_id),  # from X-Tenant-ID header — ADR-032 Decision 4
):
    """ARIMA energy forecast với auto-fallback."""
    return await forecast_service.forecast_with_fallback(
        tenant_id, building_id, horizon_days, include_backtest
    )

@router.get("/anomaly", response_model=AnomalyResponse)
async def detect_anomalies(
    building_id: str = Query(...),
    days: int = Query(30, ge=7, le=90),
    tenant_id: str = Depends(get_tenant_id),  # from X-Tenant-ID header — ADR-032 Decision 4
):
    """Isolation Forest anomaly detection."""
    return await anomaly_service.detect(tenant_id, building_id, days)

@router.get("/health")
async def health():
    return {"status": "UP", "service": "forecast-service"}

@router.get("/models")
async def list_models():
    return {"models": ["ARIMA", "SARIMA", "NAIVE", "LSTM"]}
```

### Response Schema

```python
# api/schemas.py
from pydantic import BaseModel

class ForecastPoint(BaseModel):
    timestamp: int        # epoch seconds
    predicted: float
    lower_bound: float    # 95% CI
    upper_bound: float
    is_anomaly: bool = False

class ForecastResponse(BaseModel):
    tenant_id: str
    building_id: str
    horizon_days: int
    model: str            # "ARIMA" | "SARIMA" | "NAIVE" | "LSTM"
    mape: float           # backtest MAPE
    is_fallback: bool     # true if naive used
    data_quality: str     # "EXCELLENT" | "GOOD" | "MINIMAL" | "INSUFFICIENT"
    points: list[ForecastPoint]
    metadata: dict = {}   # model params, AIC, training window size
```

### Cache (Python in-memory)

```python
# models/cache.py
from datetime import datetime, timedelta

class TTLCache:
    def __init__(self, ttl_minutes: int = 15):
        self._store: dict[str, tuple[datetime, any]] = {}
        self._ttl = timedelta(minutes=ttl_minutes)

    def get(self, key: str) -> any | None:
        if key in self._store:
            created, value = self._store[key]
            if datetime.now() - created < self._ttl:
                return value
            del self._store[key]
        return None

    def set(self, key: str, value: any):
        self._store[key] = (datetime.now(), value)
```

---

## 7. Java Backend Integration

### ForecastPort Interface (Java — unchanged)

```java
// backend/src/main/java/com/uip/backend/forecast/ForecastPort.java
public interface ForecastPort {
    ForecastResult forecast(String tenantId, String buildingId, int horizonDays);
}
```

### Python Adapter (Java — REST client)

```java
// backend/src/main/java/com/uip/backend/forecast/ForecastServiceAdapter.java
@Component
@ConditionalOnProperty(
    name = "uip.capabilities.forecast-engine",
    havingValue = "python",
    matchIfMissing = true  // ← Default: call Python service
)
@Slf4j
public class ForecastServiceAdapter implements ForecastPort {

    @Value("${uip.forecast-service.url:http://localhost:8090}")
    private String forecastServiceUrl;

    private final RestClient restClient;
    private final Cache<ForecastKey, ForecastResult> cache;

    @Override
    public ForecastResult forecast(String tenantId, String buildingId, int horizonDays) {
        // 1. Check local cache (Java Caffeine)
        ForecastKey key = new ForecastKey(tenantId, buildingId, horizonDays);
        ForecastResult cached = cache.getIfPresent(key);
        if (cached != null) return cached;

        // 2. Call Python forecast-service
        // ADR-032 Decision 4: tenant_id via X-Tenant-ID header (not query param)
        // ADR-032 Decision 1: internal Docker URL — no Kong, no nginx
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        try {
            ForecastResponse response = restClient.get()
                .uri("/api/v1/forecast/energy?buildingId={bid}&horizonDays={hd}",
                    buildingId, horizonDays)
                .header("X-Tenant-ID", tenantId)   // set from SecurityContext after JWT verify
                .header("X-Trace-Id", traceId)     // correlate logs across services
                .retrieve()
                .body(ForecastResponse.class);

            ForecastResult result = mapToResult(response);
            cache.put(key, result);
            return result;

        } catch (Exception e) {
            log.warn("[Forecast] Python service unavailable: {} → naive fallback", e.getMessage());
            // 3. Fallback to in-process naive (no Python dependency)
            return new NaiveForecastAdapter().forecast(tenantId, buildingId, horizonDays);
        }
    }
}
```

### Naive Adapter (Java — in-process fallback)

```java
// backend/src/main/java/com/uip/backend/forecast/NaiveForecastAdapter.java
// ADR-032 Decision 6: uses EsgMetricRepository (TimescaleDB) — zero new dependencies
// Used when: forecast-engine=naive OR Python service unavailable (exception fallback)

@Component
@ConditionalOnProperty(
    name = "uip.capabilities.forecast-engine",
    havingValue = "naive"
)
@RequiredArgsConstructor
@Slf4j
public class NaiveForecastAdapter implements ForecastPort {

    private final EsgMetricRepository esgMetricRepository;

    @Override
    public ForecastResult forecast(String tenantId, String buildingId, int horizonDays) {
        Instant from = Instant.now().minus(90, ChronoUnit.DAYS);
        Instant to   = Instant.now();

        List<EsgMetric> metrics = esgMetricRepository.findByTypeAndBuilding(
            tenantId, "ENERGY", buildingId, from, to
        );

        if (metrics.size() < 720) {  // < 30 days of hourly data
            log.warn("[NaiveForecast] Insufficient data: {}/{} has {} points",
                tenantId, buildingId, metrics.size());
            return ForecastResult.insufficientData(tenantId, buildingId);
        }

        // 7-day rolling average projected forward
        double avg = metrics.stream()
            .mapToDouble(EsgMetric::getValue)
            .average()
            .orElse(0.0);

        log.info("[NaiveForecast] {}/{} avgKwh={} horizonDays={}",
            tenantId, buildingId, avg, horizonDays);
        return ForecastResult.naiveForecast(tenantId, buildingId, horizonDays, avg);
    }
}
```

### Fallback Chain

```
Java ForecastController
  → ForecastService (cache check)
    → ForecastServiceAdapter (REST to Python)
      → SUCCESS: return Python forecast
      → FAIL (timeout/connection): fall back to NaiveForecastAdapter
        → SUCCESS: return naive forecast (isFallback=true)
        → FAIL (no data): return insufficient data response
```

**Key point:** Naive fallback trong Java đảm bảo **forecast luôn available** kể cả khi Python service down. Forecast degraded (rolling average) nhưng không fail.

---

## 8. LSTM trong Python — Không còn spike riêng

### LSTM giờ là phần của forecast-service

Vì đã dùng Python, LSTM **không cần spike riêng** — chỉ cần thêm endpoint:

```python
# services/lstm_service.py
import torch
import torch.nn as nn

class EnergyLSTM(nn.Module):
    def __init__(self, input_size=1, hidden_size=64, num_layers=2, output_size=1):
        super().__init__()
        self.lstm = nn.LSTM(input_size, hidden_size, num_layers, batch_first=True)
        self.fc = nn.Linear(hidden_size, output_size)

    def forward(self, x):
        out, _ = self.lstm(x)
        return self.fc(out[:, -1, :])

@router.get("/lstm", response_model=ForecastResponse)
async def forecast_lstm(
    tenant_id: str, building_id: str,
    horizon_days: int = 30,
    retrain: bool = False,
):
    """LSTM forecast — train on demand if no saved model."""
    ...
```

### LSTM Strategy cho Sprint 4

```
Week 1: ARIMA là primary — LSTM không bắt đầu
Week 2 Day 8: So sánh ARIMA vs LSTM trên same data

Nếu LSTM MAPE < ARIMA MAPE (improvement >2%):
  → Thêm /api/v1/forecast/energy?model=lstm endpoint
  → Frontend toggle: ARIMA vs LSTM

Nếu LSTM không better:
  → Document findings, không integrate
  → Không tech debt vì LSTM code trong Python service đã isolated
```

### So sánh với cũ (LSTM spike riêng)

| Trước (Java stack) | Sau (Python stack) |
|---|---|
| LSTM spike = 8 SP riêng | LSTM = thêm endpoint trong same service |
| Cần Python microservice POC | Đã có Python service, chỉ thêm PyTorch |
| Day 8 Go/No-Go gate | Vẫn có evaluation nhưng risk thấp hơn |
| Nếu abort → tech debt trong Java | Nếu abort → chỉ remove endpoint, clean |

**SP savings:** LSTM spike giảm từ 8 SP → ~3 SP (chỉ thêm endpoint + evaluation)

---

## 9. Rủi ro & Mitigation

### R1: ARIMA MAPE >15% trên real data (30%)

```
Mitigation chain:
  1. auto_arima → thử SARIMA (seasonal)
  2. Nếu vẫn >15% → naive fallback (rolling average)
  3. Log MAPE per building → identify problematic buildings
  4. UI shows "model confidence" badge
```

### R2: Historical data insufficient — ~50 days by Sprint 4 (25%)

```
Mitigation:
  1. auto_arima auto-adjusts training window
  2. <30 days → return insufficient (HTTP 422)
  3. 30-90 days → shorter horizon (recommend 7-day instead of 30)
  4. UI shows data quality warning
```

### R3: Python forecast-service unavailable (15%)

```
Mitigation:
  1. Java NaiveForecastAdapter — in-process fallback (no external call)
  2. Forecast degraded nhưng không fail
  3. Health check trong docker-compose → auto-restart
  4. Alert nếu forecast-service down >5 min (Prometheus)
```

### R4: Python Docker image size (~500MB) (10%)

```
Mitigation:
  1. Use python:3.12-slim base (~150MB)
  2. PyTorch CPU-only (~200MB)
  3. Total image ~500MB — acceptable cho ML service
  4. Nếu cần nhỏ hơn → dùng ONNX Runtime thay PyTorch (~100MB)
```

### R5: Cross-language debugging complexity (20%)

```
Mitigation:
  1. Python service có /health + /models endpoints
  2. Java logs rõ ràng: "calling Python" + "fallback triggered"
  3. Distributed tracing: X-Correlation-ID pass-through
  4. Python structured logging (JSON) → same format as Java
```

---

## 10. Day-by-Day Walkthrough

### Day 1 (Mon 06-02): Foundation

```
Python (forecast-service):
  ✅ FastAPI skeleton: main.py + config.py + api/router.py
  ✅ Dockerfile + requirements.txt
  ✅ /health endpoint working
  ✅ clickhouse-connect POC — verify CH query

Java (backend):
  ✅ ForecastPort.java interface
  ✅ ForecastResult.java + ForecastPoint.java records
  ✅ CapabilityProperties.forecastEngine flag
  ✅ application.yml updated

DevOps:
  ✅ docker-compose.yml — forecast-service block added (no host port per ADR-032 Decision 1)
  ✅ Reserve Prometheus scrape slot: uip-forecast-service:8090/metrics

⏱️ 6-7 hours
```

### Day 2 (Tue 06-03): ARIMA Core

```
Python:
  ✅ arima_service.py — auto_arima + forecast + CI
  ✅ preprocessor.py — gap filling + outlier removal
  ✅ cache.py — TTL cache
  ✅ Unit test: synthetic sinusoidal data → MAPE <5%

⏱️ 7-8 hours
```

### Day 3 (Wed 06-04): Backtest + Fallback

```
Python:
  ✅ backtest_service.py — walk-forward validation
  ✅ naive_service.py — rolling average fallback
  ✅ forecast_service.py — orchestrator with auto-fallback
  ✅ /api/v1/forecast/energy endpoint live

⏱️ 7-8 hours
```

### Day 4 (Thu 06-05): Java Integration

```
Java:
  ✅ ForecastServiceAdapter.java — REST client to Python
  ✅ NaiveForecastAdapter.java — in-process fallback
  ✅ ForecastController.java — GET /api/v1/forecast/energy
  ✅ ForecastService.java — orchestrate

DevOps:
  ✅ docker-compose.yml deployed — forecast-service on uip-network, no host port
  ✅ Prometheus scrape config activated: uip-forecast-service:8090/metrics
  ✅ Verify end-to-end: Java → Python → ClickHouse via internal Docker hostnames
  ✅ Confirm /health returns 200 OK in deployed environment

⏱️ 6-7 hours
```

### Day 5 (Fri 06-06): Week 1 Checkpoint

```
All:
  ✅ End-to-end: Frontend → Java → Python → ClickHouse → forecast
  ✅ MAPE measurement on real data
  ✅ Anomaly detection: anomaly_service.py (Isolation Forest)
  ✅ Week 1 checkpoint report

⚠️ Decision: Nếu MAPE >20% → investigate data quality
```

### Day 6-7 (Mon-Tue 06-09→06-10): Tests + LSTM eval

```
Python tests:
  ✅ test_arima_service.py — synthetic + edge cases
  ✅ test_backtest_service.py — known series
  ✅ test_preprocessor.py — gap filling
  ✅ test_api.py — FastAPI test client

Java tests:
  ✅ ForecastControllerIT.java — full API test
  ✅ ForecastServiceAdapterTest.java — mock Python responses
  ✅ Boundary tests: horizonDays=0, -1, 366

LSTM evaluation (Day 7):
  ✅ Compare ARIMA vs LSTM MAPE on same data
  ✅ Document decision

DevOps (Day 7):
  ✅ forecast-alerts.yml — 4 alert rules deployed (see Section 11)
  ✅ Grafana dashboard uip-forecast.json — 8 panels published
  ✅ Verify ForecastServiceDown alert fires on service stop
  ✅ Verify JSON logs contain traceId field
```

### Day 8-9 (Wed-Thu 06-11→06-12): Regression + Demo

```
  ✅ Full regression: testUnit + integrationTest
  ✅ SA code review
  ✅ OpenAPI spec update
  ✅ Demo dry-run
```

### Day 10 (Fri 06-13): Gate Review

```
  ✅ PO Demo Live 15:00 SGT
```

---

## 11. DevOps & Observability

### 11.1 Prometheus Metrics — forecast-service

Thêm `prometheus-fastapi-instrumentator` vào `requirements.txt` và expose `/metrics`:

```txt
# requirements.txt — ADD
prometheus-fastapi-instrumentator==7.0.0
```

```python
# main.py — expose /metrics
from prometheus_fastapi_instrumentator import Instrumentator

app = FastAPI(title="forecast-service")
Instrumentator().instrument(app).expose(app, endpoint="/metrics")
```

Custom metrics (ngoài default HTTP metrics):

```python
# metrics.py
from prometheus_client import Histogram, Counter, Gauge

ARIMA_FIT_DURATION = Histogram(
    "forecast_arima_fit_seconds",
    "Time to fit ARIMA model",
    ["tenant_id", "building_id"],
    buckets=[1, 5, 15, 30, 60, 90, 120],
)

FORECAST_FALLBACK_TOTAL = Counter(
    "forecast_fallback_total",
    "Number of times naive fallback was triggered",
    ["reason"],  # mape_threshold | python_error | insufficient_data
)

FORECAST_CACHE_HIT = Counter(
    "forecast_cache_hits_total",
    "Python TTL cache hits",
)

FORECAST_MAPE = Gauge(
    "forecast_arima_mape_ratio",
    "Latest ARIMA MAPE score per building",
    ["tenant_id", "building_id"],
)
```

### 11.2 Prometheus Scrape Config

```yaml
# infra/monitoring/prometheus/prometheus.yml — ADD target:
scrape_configs:
  # ... existing targets (backend, clickhouse, kafka, etc.)
  - job_name: 'forecast-service'
    scrape_interval: 30s
    static_configs:
      - targets: ['uip-forecast-service:8090']
    metrics_path: '/metrics'
```

> Prometheus container phải trong cùng `uip-network`. Verify với existing pattern của `uip-backend` scrape target.

### 11.3 Alert Rules

```yaml
# infra/monitoring/prometheus/alerts/forecast-alerts.yml
groups:
  - name: forecast-service
    rules:
      - alert: ForecastServiceDown
        expr: up{job="forecast-service"} == 0
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "forecast-service is down"
          description: "Java NaiveForecastAdapter fallback active. Forecast quality degraded."

      - alert: ForecastHighFallbackRate
        expr: |
          rate(forecast_fallback_total[5m]) /
          rate(http_requests_total{handler="/api/v1/forecast/energy"}[5m]) > 0.2
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Forecast fallback rate >20% — check MAPE or data quality"

      - alert: ForecastColdCallSlow
        expr: |
          histogram_quantile(0.95,
            rate(forecast_arima_fit_seconds_bucket[10m])
          ) > 90
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "ARIMA fit p95 >90s — dataset may have grown beyond expected size"

      - alert: ForecastHighMAPE
        expr: forecast_arima_mape_ratio > 0.20
        for: 15m
        labels:
          severity: warning
        annotations:
          summary: "ARIMA MAPE >20% for building {{ $labels.building_id }}"
          description: "Model accuracy degraded. Review data quality or increase training window."
```

### 11.4 Grafana Dashboard

**File:** `infra/monitoring/grafana/dashboards/uip-forecast.json`  
**Dashboard UID:** `uip-forecast`  
**Folder:** `UIP Smart City`

| Panel | PromQL | Viz |
|---|---|---|
| Service Health | `up{job="forecast-service"}` | Stat |
| Request Rate | `rate(http_requests_total{job="forecast-service"}[5m])` | Time series |
| p95 Latency (cached) | `histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{handler="/api/v1/forecast/energy"}[5m]))` | Time series |
| ARIMA Fit p95 | `histogram_quantile(0.95, rate(forecast_arima_fit_seconds_bucket[10m]))` | Time series |
| Cache Hit Rate | `rate(forecast_cache_hits_total[5m])` | Stat |
| Fallback Rate | `rate(forecast_fallback_total[5m])` | Time series |
| MAPE per Building | `forecast_arima_mape_ratio` | Table |
| Error Rate 5xx | `rate(http_requests_total{job="forecast-service",status=~"5.."}[5m])` | Stat |

### 11.5 Structured Logging (Python → JSON)

```python
# config/logging_config.py
import json
import logging
from datetime import datetime, timezone

class JSONFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        return json.dumps({
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "service": "forecast-service",
            "traceId": getattr(record, "trace_id", None),
            "tenantId": getattr(record, "tenant_id", None),
        }, ensure_ascii=False)

# main.py — configure on startup
handler = logging.StreamHandler()
handler.setFormatter(JSONFormatter())
logging.basicConfig(level=logging.INFO, handlers=[handler])
```

Log format aligns với Java logback JSON pattern — logs correlated qua `traceId` (từ `X-Trace-Id` header, MDC key `traceId` theo `TraceIdFilter.java`).

### 11.6 Docker Image Security

```dockerfile
# applications/forecast-service/Dockerfile
FROM python:3.12-slim AS base

# Non-root user — security hardening
RUN addgroup --system forecast && adduser --system --ingroup forecast forecast

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && pip install --no-cache-dir torch --index-url https://download.pytorch.org/whl/cpu

COPY --chown=forecast:forecast . .

USER forecast

EXPOSE 8090

HEALTHCHECK CMD curl -f http://localhost:8090/api/v1/forecast/health || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8090"]
```

---

## Appendix A: SP Recalculation (Python Architecture)

| Story | Old SP (Java) | New SP (Python) | Reason |
|---|---|---|---|
| S4-06: ForecastPort + flag | 3 | 2 | Java interface simpler — no ML code |
| S4-07: Data pipeline | 3 | 2 | Python clickhouse-connect easier than JDBC |
| S4-08: ARIMA adapter | 5 | 4 | auto_arima replaces manual grid search |
| S4-09: Naive fallback | 2 | 1 | Same in Java, simpler in Python |
| S4-10: REST API + caching | 3 | 3 | Same effort |
| S4-11: Tests | 3 | 2 | Python tests faster to write |
| S4-12: Anomaly detection | 2 | 1 | scikit-learn IsolationForest built-in |
| S4-15: LSTM spike | 5 | 2 | Same Python service, just add endpoint |
| S4-16: LSTM evaluation | 3 | 1 | Compare ARIMA vs LSTM on same data |
| S4-17: DevOps + Observability | — | 3 | New: Prometheus metrics + alert rules + Grafana dashboard + Docker security |
| **Epic 2+4 Total** | **29** | **21** | **Python saves net ~8 SP (−11 Python + 3 DevOps)** |

**Buffer release:** ~8 SP → stretch items: model persistence strategy, cache stampede guard.

---

## Appendix B: Acceptance Criteria Checklist

| # | Criteria | Test Type | Pass |
|---|---|---|---|
| 1 | forecast-service container healthy | Docker | 200 OK /health |
| 2 | Python ARIMA returns forecast for valid building | IT | 720 points |
| 3 | auto_arima selects model with AIC logged | Unit | AIC < inf |
| 4 | MAPE <15% on backtest | Backtest | <0.15 |
| 5 | MAPE >15% → naive fallback | Unit | isFallback=true |
| 6 | <30 days data → HTTP 422 | IT | 422 |
| 7 | Java → Python REST call success | IT | 200 OK |
| 8 | Python down → Java naive fallback | IT | 200, isFallback=true |
| 9 | Auth: missing X-Tenant-ID header → 403 | IT | 403 |
| 10 | Cross-tenant: invalid X-Tenant-ID format → 400 | IT | 400 |
| 11a | First forecast call (cold cache, auto_arima fit) | Perf | <60s |
| 11b | Subsequent call (Python TTL cache hit) | Perf | <500ms |
| 11c | Java Caffeine cache hit | Perf | <10ms |
| 11d | NaiveForecastAdapter (Python service down) | IT | <2s |
| 12 | Java Caffeine cache hit response | Perf | <10ms |
| 13 | Isolation Forest returns anomalies | IT | anomaly points |
| 14 | LSTM evaluation documented | Review | decision doc |
| 15 | /metrics endpoint returns Prometheus metrics | IT | HTTP 200, metrics non-empty |
| 16 | ForecastServiceDown alert fires within 2 min of service stop | Alert | alert active in Prometheus |
| 17 | Python logs are JSON-formatted with traceId field | Manual | JSON parseable, traceId present |

---

## Appendix C: Gap Analysis — SA Spike Resolved (ADR-032)

### Rà soát completeness

Spec hiện tại **cover tốt 10/18 areas**. 8 gaps đã identify. Gaps 1–3 (CRITICAL) đã được giải quyết trong **ADR-032** (`ADR-032-forecast-service-security-routing.md`). Implement theo ADR-032, không theo mô tả "options" bên dưới.

> **ADR-032 decisions (final):**
> - **Auth model**: Docker network isolation + `X-Tenant-ID` header trust (Option A)
> - **Nginx routing**: Không thay đổi — `/api/` catch-all đã cover
> - **Cross-tenant isolation**: Python validate `X-Tenant-ID` header, Java set từ SecurityContext
> - **SQL injection**: Parameterized queries (đã cập nhật Section 5)
> - **Fallback data**: `EsgMetricRepository.findByTypeAndBuilding()` → TimescaleDB

### Gap 1 — Security: forecast-service auth model (CRITICAL)

**Vấn đề:** Python service trên port 8090 KHÔNG có authentication. Ai cũng gọi được `/api/v1/forecast/energy`.

**ClickHouse query hiện tại dùng string interpolation** — SQL injection risk:
```python
# KHÔNG SAFE — string interpolation
query = "... WHERE tenant_id = '{tenant_id}' ...".format(...)
```

**Options cần SA quyết định:**

| Option | Mô tả | Pros | Cons |
|---|---|---|---|
| **A: Docker network isolation** | forecast-service không expose port ra host, chỉ reachable từ backend container qua docker network | Zero auth complexity, fastest to implement | Phải trust backend |
| **B: JWT passthrough** | Java forward JWT token → Python verify với PyJWKs/Keycloak public key | Full end-to-end auth, tenant isolation tự nhiên | Python cần JWK library, thêm latency verify |
| **C: Shared secret header** | Java gửi `X-Forecast-Secret: <env-var>` → Python verify | Simple | Secret management, không có tenant context |

**Recommendation:** Option A cho POC (internal-only) + Option B cho production Sprint 5.

**SQL injection fix (independent of SA decision):**
```python
# SAFE — parameterized query
query = """
    SELECT toUnixTimestamp(toStartOfHour(recorded_at)) AS ts_hour,
           sum(value) AS total_kwh
    FROM esg_readings
    WHERE tenant_id = %(tenant_id)s
      AND building_id = %(building_id)s
      AND metric_type = 'ENERGY'
      AND recorded_at >= now() - INTERVAL %(days)s DAY
    GROUP BY ts_hour
    ORDER BY ts_hour ASC
"""
client.query_df(query, parameters={
    "tenant_id": tenant_id,
    "building_id": building_id,
    "days": str(days),
})
```

### Gap 2 — Nginx routing cho forecast API (HIGH)

**Vấn đề:** Frontend gọi `/api/v1/forecast/energy` → đi qua nginx → đến đâu?

**Routing options:**

| Option | Path | Auth | Rate limit |
|---|---|---|---|
| **A: nginx → backend → Python** | frontend → nginx:80 → backend:8080 → Python:8090 | Backend verify JWT (existing) | Backend/Kong |
| **B: nginx → Kong → backend → Python** | frontend → nginx:80 → Kong:8000 → backend:8080 → Python:8090 | Kong JWT + backend JWT | Kong rate-limit |
| **C: nginx → Kong → Python** | frontend → nginx:80 → Kong:8000 → Python:8090 | Kong JWT only | Kong rate-limit |

**Recommendation:** Option A — consistent với existing flow (backend handles auth, Python is internal-only). Thêm vào nginx.conf:
```nginx
# frontend/nginx.conf — ADD (no special route needed, /api/v1/forecast/ matches /api/ catch-all)
# Forecast goes through backend like all other /api/ routes — no special nginx config needed.
```

### Gap 3 — Cross-tenant data isolation (CRITICAL — follows Gap 1)

**Vấn đề:** Python service nhận `tenant_id` từ query param — ai cũng gửi `tenant_id=sgn` được.

**Giải pháp theo Option A (network isolation):**
- Python **trust** tenant_id từ Java backend (Java đã verify JWT)
- Java thêm `X-Tenant-Id` header khi gọi Python → Python validate header present
- Docker network isolation: Python không reachable từ bên ngoài

```java
// Java ForecastServiceAdapter — thêm tenant header
ForecastResponse response = restClient.get()
    .uri("/api/v1/forecast/energy?...")
    .header("X-Tenant-Id", tenantId)  // Java đã verify JWT → trusted
    .header("X-Correlation-Id", MDC.get("correlationId"))
    .retrieve()
    .body(ForecastResponse.class);
```

```python
# Python dependencies.py — validate internal headers
async def verify_internal_request(request: Request):
    """Trust tenant_id từ backend (network-isolated)."""
    tenant_id = request.headers.get("X-Tenant-Id")
    if not tenant_id:
        raise HTTPException(403, "Missing X-Tenant-Id — internal access only")
    return tenant_id
```

### Gap 4 — NaiveForecastAdapter data source (HIGH)

**Vấn đề:** Java fallback cần query data — từ đâu?

**Options:**
- **A: Query TimescaleDB** qua existing `EsgMetricRepository` — đã có trong monolith
- **B: Query ClickHouse** qua analytics-service REST — thêm latency
- **C: Query TimescaleDB** qua existing repository — simplest

**Recommendation:** Option A — dùng existing `EsgMetricRepository` trong monolith (no new dependency).

### Gap 5 — Error contract Java ↔ Python (MEDIUM)

**Cần spec:**

| Python response | Java handling |
|---|---|
| 200 OK + forecast data | Map to ForecastResult, cache |
| 422 Unprocessable (insufficient data) | Return ForecastResult.insufficientData() |
| 403 Forbidden (tenant mismatch) | Throw ResponseStatusException(403) |
| 500 Internal Server Error | Log warn → NaiveForecastAdapter fallback |
| Timeout (RestClient >5s) | Log warn → NaiveForecastAdapter fallback |
| Connection refused | Log warn → NaiveForecastAdapter fallback |

```java
// ForecastServiceAdapter error handling
try {
    ForecastResponse response = restClient.get()
        .uri(...)
        .retrieve()
        .onStatus(status -> status.value() == 422, (req, res) -> {
            throw new InsufficientDataException(tenantId, buildingId);
        })
        .body(ForecastResponse.class);
    ...
} catch (InsufficientDataException e) {
    return ForecastResult.insufficientData(tenantId, buildingId);
} catch (RestClientException e) {
    log.warn("[Forecast] Python unavailable → naive fallback");
    return naiveAdapter.forecast(tenantId, buildingId, horizonDays);
}
```

### Gap 6 — Kong rate-limiting cho forecast (LOW)

Forecast API compute-heavy (ARIMA fit ~500ms). Rate limiting nên khác analytics API.

**Decision:** Kong rate-limiting không áp cho forecast vì forecast call đi qua backend (backend handles rate). Nếu cần riêng → thêm Kong route cho `/api/v1/forecast/` với rate 30 req/min.

### Gap 7 — Observability cho forecast-service → **Resolved, xem Section 11**

Full implementation spec (Prometheus metrics + custom counters/histograms, alert rules, Grafana dashboard 8 panels, structured JSON logging, Docker non-root image) được document đầy đủ trong **[Section 11: DevOps & Observability](#11-devops)**.

DevOps story: **S4-17** (3 SP), thực hiện Day 4 + Day 7 theo walkthrough Section 10.

### Gap 8 — Docker image security (LOW)

```dockerfile
# Dockerfile improvements
FROM python:3.12-slim AS base
RUN addgroup --system forecast && adduser --system --ingroup forecast forecast
USER forecast
COPY --chown=forecast:forecast . .
```

---

### SA Spike Summary — RESOLVED

**ADR-032 đã được viết:** `docs/mvp3/architecture/ADR-032-forecast-service-security-routing.md`

| Decision | Final Decision | ADR-032 Section |
|---|---|---|
| Auth model | Docker network isolation + `X-Tenant-ID` header | Decision 1 |
| Kong scope | Không bao gồm forecast-service | Decision 2 |
| Nginx routing | Không thay đổi — `/api/` catch-all cover | Decision 3 |
| Cross-tenant | Python validate `X-Tenant-ID` header từ Java | Decision 4 |
| SQL injection | Parameterized queries (cập nhật Section 5) | Decision 5 |
| Fallback data | `EsgMetricRepository.findByTypeAndBuilding()` | Decision 6 |

---

*Document created: 2026-05-25 | Revised: Python FastAPI architecture*
*Status: Approved — blockers resolved by ADR-032 (2026-05-25)*
*Related: docs/mvp3/project/sprint4-plan.md, docs/mvp3/architecture/ADR-032-forecast-service-security-routing.md*
