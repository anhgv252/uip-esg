# ADR-032: forecast-service Security, Routing & Tenant Isolation

**Date:** 2026-05-25  
**Status:** Accepted  
**Deciders:** SA, Backend Lead, DevOps  
**Sprint:** MVP3-4 — Pre-implementation spike (Day 1, 2026-06-02)  
**Resolves:** Blockers B1–B3 identified in `sprint4-ai-spec-review-2026-05-25.md`

---

## Context

Sprint 4 introduces `forecast-service` — a Python FastAPI internal computation engine that performs ARIMA/LSTM forecasting by querying ClickHouse directly. The Java monolith backend calls this service synchronously via REST to serve `GET /api/v1/forecast/energy`.

Five security and routing questions were left open in the technical spec and must be resolved before implementation starts:

1. **Auth model** — Does forecast-service need authentication? If yes, which mechanism?
2. **Kong scope** — Should forecast-service traffic pass through Kong?
3. **Nginx routing** — Does nginx need a new routing rule for `/api/v1/forecast/`?
4. **Cross-tenant isolation** — How does Python service enforce tenant boundaries?
5. **Fallback data source** — What does `NaiveForecastAdapter` (Java) query when Python is unavailable?

### Constraints

- forecast-service is an **internal computation engine** — it is never called directly by browsers, mobile clients, or partner APIs. Only the Java monolith backend calls it.
- Per ADR-028, Kong scope is locked to extracted services exposed to external traffic (`analytics-service`, `iot-ingestion-service`). forecast-service is not external-facing.
- Per ADR-027, JWT issuance is via Keycloak. Java backend already validates JWT and extracts `tenant_id` into `SecurityContext` before calling forecast-service.
- Docker Compose `uip-network` provides network-level isolation for all internal services.
- Sprint 4 timeline is 10 days. Security complexity must be proportional to Phase 1 risk.

---

## Decisions

### Decision 1 — Auth Model: Docker Network Isolation + X-Tenant-ID Header

**Accepted.** forecast-service is NOT exposed on any host port. It is reachable only within `uip-network` Docker network by container name (`uip-forecast-service:8090`). No external process can reach port 8090 without first entering the Docker network.

```yaml
# docker-compose.yml — forecast-service does NOT expose host port
uip-forecast-service:
  # NO "ports:" section → not accessible from host or external traffic
  networks:
    - uip-network
```

Java backend calls Python using the Docker internal hostname:
```
http://uip-forecast-service:8090/api/v1/forecast/energy
```

Cross-tenant isolation is enforced via the `X-Tenant-ID` header (see Decision 3). This is the same header convention already used by `BuildingClusterController` and Kong's `request-transformer` plugin (ADR-028).

**Why not JWT passthrough?** Python verifying Keycloak JWT requires `python-keycloak` + JWK endpoint call on every request, adding ~50–100ms latency and a new Keycloak dependency in Python. Java has already authenticated the caller. Adding a second JWT verification in an internal-only service is unnecessary complexity for Sprint 4.

**Sprint 5 upgrade path:** Add shared secret header `X-Forecast-Key` (env-var secret, not hardcoded) as an additional layer when forecast-service is considered for staging exposure. Full Keycloak JWT passthrough deferred to Phase 2 microservices migration.

---

### Decision 2 — Kong Scope: NOT included

**Accepted.** forecast-service traffic never reaches Kong. The call flow is:

```
Browser
  → nginx (frontend static + /api/ proxy to monolith)
  → Java monolith (Spring Security validates JWT, extracts tenant)
  → forecast-service (Docker internal network, no Kong)
  → ClickHouse (Docker internal network)
```

Adding forecast-service to Kong would require:
1. A new Kong service/route config entry
2. Kong rate-limit bypass for internal monolith calls (Kong would see the backend as an API consumer)
3. Plugin order re-verification per ADR-028 security invariant

None of these provide value since forecast-service has no direct external callers. **Kong scope unchanged from ADR-028.**

---

### Decision 3 — Nginx Routing: No Change Required

**Accepted.** The existing nginx catch-all rule for `/api/` already proxies all `/api/v1/forecast/` requests to the Java monolith backend. No nginx configuration change is needed.

```nginx
# frontend/nginx.conf — existing rule covers forecast
location /api/ {
    proxy_pass http://uip-backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    # ... existing headers
}
```

Forecast endpoints are served by `ForecastController.java` in the monolith, not by Python directly.

---

### Decision 4 — Cross-Tenant Isolation: Trust X-Tenant-ID from Backend

**Accepted.** Python forecast-service enforces the following contract:

1. **Every request from Java backend MUST include `X-Tenant-ID` header.** Java sets this from `SecurityContextHolder` after JWT validation — the same `tenant_id` used throughout the monolith.
2. **Python rejects requests missing `X-Tenant-ID` with HTTP 403.** This guards against misconfigured internal callers.
3. **ClickHouse query uses `X-Tenant-ID` value as a parameterized bind variable.** Python does NOT accept `tenant_id` from query params — it reads only from the trusted internal header.

```python
# api/dependencies.py — FINAL IMPLEMENTATION (not a stub)
from fastapi import Request, HTTPException

async def get_tenant_id(request: Request) -> str:
    """
    Extract tenant_id from internal X-Tenant-ID header.
    This header is set by Java backend after JWT verification.
    forecast-service is network-isolated; direct external calls are not possible.
    """
    tenant_id = request.headers.get("X-Tenant-ID")
    if not tenant_id or not tenant_id.strip():
        raise HTTPException(
            status_code=403,
            detail="Missing X-Tenant-ID header — internal access only"
        )
    # Prevent header injection: allow only alphanumeric + hyphen/underscore
    import re
    if not re.match(r'^[a-zA-Z0-9_-]{1,64}$', tenant_id):
        raise HTTPException(status_code=400, detail="Invalid X-Tenant-ID format")
    return tenant_id
```

```java
// ForecastServiceAdapter.java — Java sets the header from SecurityContext
import org.springframework.security.core.context.SecurityContextHolder;

String tenantId = extractTenantId(SecurityContextHolder.getContext().getAuthentication());
String traceId  = MDC.get(TraceIdFilter.MDC_TRACE_ID);

ForecastResponse response = restClient.get()
    .uri("/api/v1/forecast/energy?buildingId={bid}&horizonDays={hd}", buildingId, horizonDays)
    .header("X-Tenant-ID", tenantId)        // tenant from JWT — already verified
    .header("X-Trace-Id", traceId)          // correlate logs across services
    .retrieve()
    .body(ForecastResponse.class);
```

**API endpoint parameter change:** Python endpoint does NOT accept `tenant_id` as a query param. Remove it from the public signature. Only `building_id` and `horizon_days` are query params.

```python
# api/router.py — UPDATED (no tenant_id query param)
@router.get("/energy", response_model=ForecastResponse)
async def forecast_energy(
    building_id: str = Query(..., description="Building ID"),
    horizon_days: int = Query(30, ge=1, le=90),
    include_backtest: bool = Query(False),
    tenant_id: str = Depends(get_tenant_id),  # from header, not query param
):
    ...
```

---

### Decision 5 — SQL Injection: Parameterized Queries (Non-Negotiable)

**Accepted.** The clickhouse_client.py implementation in Section 5 of the technical spec uses unsafe string interpolation and **must be replaced** with `clickhouse-connect` parameterized queries before any implementation begins. This fix supersedes Section 5 of the spec.

```python
# data/clickhouse_client.py — FINAL SAFE IMPLEMENTATION
import clickhouse_connect
from config import settings

async def fetch_hourly_energy(
    tenant_id: str,
    building_id: str,
    days: int = 365,
):
    client = clickhouse_connect.get_client(
        host=settings.clickhouse_host,
        port=settings.clickhouse_port,
        database=settings.clickhouse_db,
    )

    # SAFE: parameterized — no string interpolation of user-controlled values
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

---

### Decision 6 — Fallback Data Source: EsgMetricRepository (TimescaleDB)

**Accepted.** `NaiveForecastAdapter` (Java in-process fallback) queries TimescaleDB via the existing `EsgMetricRepository.findByTypeAndBuilding()` method. No new repository or database connection is needed.

```java
// NaiveForecastAdapter.java — data access via existing repo
@Component
@ConditionalOnProperty(name = "uip.capabilities.forecast-engine", havingValue = "naive")
@RequiredArgsConstructor
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
            return ForecastResult.insufficientData(tenantId, buildingId);
        }

        // 7-day rolling average → project forward
        double avg = metrics.stream()
            .mapToDouble(EsgMetric::getValue)
            .average()
            .orElse(0.0);

        return ForecastResult.naiveForecast(tenantId, buildingId, horizonDays, avg);
    }
}
```

**Why TimescaleDB, not ClickHouse?** `EsgMetricRepository` already exists and is battle-tested. Calling ClickHouse from Java fallback would require a new HTTP client dependency in the monolith just for the fallback path — disproportionate complexity for a degraded-mode scenario.

---

## Consequences

### Positive
- **Zero auth complexity added to Python** for Sprint 4 — no JWK fetching, no token parsing library
- **Network isolation is defense-in-depth** — even if tenant validation logic had a bug, external callers still can't reach the port
- **Consistent header convention** — `X-Tenant-ID` already used by Kong (ADR-028) and controllers; no new header pattern
- **SQL injection eliminated** — parameterized queries are the only approved ClickHouse query pattern
- **NaiveForecastAdapter uses zero new dependencies** — `EsgMetricRepository` already available in ESG module
- **No nginx or Kong changes required** — reduces Sprint 4 scope

### Trade-offs & Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Docker network compromise → Python accessible without auth | Low (infrastructure-level attack) | Sprint 5: add `X-Forecast-Key` shared secret as second layer |
| `X-Tenant-ID` header spoofed if internal service is compromised | Low (requires compromised container) | Header format validation in Python + audit log |
| NaiveForecastAdapter returns older data (TimescaleDB hot store, 30 days) | Acceptable | Naive forecast is explicitly degraded mode; `is_fallback=true` in response |
| Python cold start + first ARIMA fit latency may exceed 60s with large datasets | Known | See latency AC revision below |

---

## Acceptance Criteria Revision — AC #11 (Replaces Appendix B row #11)

The original threshold `<1s first call` is incorrect for cold-cache ARIMA computation. Revised:

| Scenario | Threshold | Test Type |
|---|---|---|
| Health check `/health` | <200ms | IT |
| First forecast call (cold cache, full `auto_arima` fit) | <60s | Perf (acceptable for background computation) |
| Subsequent call within TTL (Python cache hit) | <500ms | Perf |
| Java Caffeine cache hit (same tenant + building) | <10ms | Perf |
| NaiveForecastAdapter (Python down) | <2s | IT |

**Rationale:** `pmdarima.auto_arima()` with `seasonal=True, m=24` on 8,760 data points (365 days × 24h) takes 15–90 seconds depending on AIC convergence. This is acceptable because:
1. Result is cached at Python level (15 min TTL)
2. Result is cached at Java level (Caffeine, 15 min)
3. First call happens during off-peak (pre-warm option available)
4. UI should show loading indicator + non-blocking async behavior during first load

**Pre-warm option (Sprint 4 stretch):** `POST /api/v1/forecast/warmup?tenantId=X&buildingId=Y` triggers async pre-computation so the first UI-triggered call hits cache.

---

## Environment Variables

Two new env vars added to `infrastructure/docker-compose.yml` for forecast-service (update `docs/deployment/environment-variables.xlsx`):

| Variable | Default | Required | Secret? | Purpose |
|---|---|---|---|---|
| `CLICKHOUSE_HOST` | `uip-clickhouse` | Yes | No | ClickHouse hostname (Docker service) |
| `CLICKHOUSE_PORT` | `8123` | Yes | No | ClickHouse HTTP port |
| `CLICKHOUSE_DB` | `analytics` | Yes | No | ClickHouse database name |
| `FORECAST_CACHE_TTL_MINUTES` | `15` | No | No | Python in-memory cache TTL |
| `FORECAST_MAPE_THRESHOLD` | `0.15` | No | No | MAPE threshold above which naive fallback triggers |
| `FORECAST_MIN_DATA_DAYS` | `30` | No | No | Minimum days of history required |
| `UIP_FORECAST_SERVICE_URL` | `http://uip-forecast-service:8090` | Yes (Java) | No | Java → Python service URL |
| `UIP_FORECAST_ENGINE` | `python` | No (Java) | No | `python` \| `naive` \| `disabled` |

---

## Related ADRs & References

| Reference | Relevance |
|---|---|
| ADR-027 (Keycloak Hybrid Auth) | JWT validation done by Java before calling forecast-service |
| ADR-028 (Kong Gateway Scope) | Confirms Kong NOT in forecast path; Kong = external-facing only |
| ADR-033 (Tenant Hierarchy) | `X-Tenant-ID` header convention established here |
| `sprint4-arima-lstm-technical-spec.md` | Implementation spec — Section 5 SQL updated per Decision 5; AC #11 replaced |
| `docs/deployment/environment-variables.xlsx` | Must be updated with 8 new env vars above |

---

*ADR-032 written: 2026-05-25*  
*Effective: Sprint 4 Day 1 (2026-06-02)*  
*Review: SA + Backend Lead sign-off required before `applications/forecast-service/` first commit*
