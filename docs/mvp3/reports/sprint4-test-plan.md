# Sprint 4 — Test Plan & Execution Guide

**Sprint:** MVP3-4 — Observability + Predictive AI Foundation
**Tester:** [Name] | **Date:** 2026-05-25
**Environment:** Local Docker Compose
**Prerequisites:** `docker compose up --build` all services healthy + monitoring stack running

---

## 0. Test Session Timeline (est. 4 giờ)

| Phase | Duration | Scope |
|---|---|---|
| **Phase 1: Pre-flight** | 15 min | Docker healthy check, services UP, monitoring stack |
| **Phase 2: Security (ADR-032)** | 30 min | TC-001 → TC-006 — security matrix + network isolation |
| **Phase 3: Forecast API** | 45 min | TC-007 → TC-014 — happy path, boundary, fallback, perf |
| **Phase 4: Observability** | 30 min | TC-015 → TC-019 — Prometheus, Grafana, alerts, logging |
| **Phase 5: Frontend UI** | 30 min | TC-020 → TC-024 — ESG page, chart, responsive |
| **Phase 6: Regression** | 30 min | TC-025 → TC-028 — Sprint 3 features still work |
| **Phase 7: Gate verify script** | 15 min | Run sprint-gate-verify.sh, collect results |

---

## 1. Pre-flight Health Check

```bash
#!/bin/bash
echo "=== SPRINT 4 PRE-FLIGHT CHECK ==="

# 1. Infrastructure services
echo "[1] Infrastructure..."
for svc in uip-timescaledb uip-clickhouse uip-redis uip-kafka uip-emqx; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "not found")
  echo "  $svc: $STATUS"
done

# 2. Application services
echo "[2] Application services..."
for svc in uip-backend uip-analytics-service uip-forecast-service uip-frontend uip-kong uip-keycloak; do
  STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "not found")
  echo "  $svc: $STATUS"
done

# 3. Monitoring stack
echo "[3] Monitoring..."
cd infra/monitoring && docker compose -f docker-compose.monitoring.yml ps
cd ../..

# 4. Unit tests
echo "[4] Java tests..."
cd backend && ./gradlew cleanTest test jacocoTestReport --quiet 2>&1 | tail -3
cd ..

echo "[5] Python tests..."
cd applications/forecast-service && pip install -q -r requirements.txt 2>/dev/null && pytest tests/ -q 2>&1 | tail -5
cd ../..

echo "[6] TypeScript..."
cd frontend && npx tsc --noEmit 2>&1 | tail -3
cd ..

echo "=== PRE-FLIGHT DONE ==="
```

**PASS criteria:**
- [ ] All services: `healthy`
- [ ] Java tests: 1568+ PASS, 0 failures
- [ ] Python tests: all PASS
- [ ] TypeScript: 0 errors

---

## 2. Security Test Matrix (ADR-032) — 30 min

### TC-001: forecast-service NOT accessible from host (ADR-032 Decision 1)
**Priority:** P0 | **Type:** Security
**Precondition:** Docker stack running, forecast-service container healthy

**Steps:**
```bash
curl -s --connect-timeout 2 http://localhost:8090/api/v1/forecast/health
echo "Exit code: $?"
```

**Expected:**
- Connection refused or timeout — port 8090 NOT exposed to host
- Exit code != 0

**Actual:** ____________ **Status:** ____

---

### TC-002: Missing X-Tenant-ID → HTTP 403 (ADR-032 Decision 4)
**Priority:** P0 | **Type:** Security

**Steps:**
```bash
docker exec uip-forecast-service curl -sf -w '\nHTTP_CODE:%{http_code}' \
  http://localhost:8090/api/v1/forecast/energy?buildingId=test
```

**Expected:**
- HTTP 403
- Body: `"Missing X-Tenant-ID header — internal access only"`

**Actual:** ____________ **Status:** ____

---

### TC-003: Empty X-Tenant-ID → HTTP 403
**Priority:** P0 | **Type:** Security

**Steps:**
```bash
docker exec uip-forecast-service curl -sf -w '\nHTTP_CODE:%{http_code}' \
  -H "X-Tenant-ID: " \
  http://localhost:8090/api/v1/forecast/energy?buildingId=test
```

**Expected:** HTTP 403

**Actual:** ____________ **Status:** ____

---

### TC-004: Invalid X-Tenant-ID format → HTTP 400 (ADR-032 Decision 4)
**Priority:** P0 | **Type:** Security

**Steps:**
```bash
# Test multiple invalid formats
for tid in '!!!bad!!!' '<script>alert(1)</script>' 'tenant id spaces' 'a'*65; do
  CODE=$(docker exec uip-forecast-service curl -sf -o /dev/null -w '%{http_code}' \
    -H "X-Tenant-ID: $tid" \
    http://localhost:8090/api/v1/forecast/energy?buildingId=test)
  echo "  '$tid' → HTTP $CODE"
done
```

**Expected:**
- `!!!bad!!!` → 400
- `<script>alert(1)</script>` → 400
- `tenant id spaces` → 400
- 65-char string → 400 (max 64 chars)

**Actual:** ____________ **Status:** ____

---

### TC-005: Valid X-Tenant-ID → passes validation
**Priority:** P0 | **Type:** Security

**Steps:**
```bash
for tid in 'tenant-123' '550e8400-e29b-41d4-a716-446655440000' 'my-tenant_2024' 'hcm'; do
  CODE=$(docker exec uip-forecast-service curl -sf -o /dev/null -w '%{http_code}' \
    -H "X-Tenant-ID: $tid" \
    "http://localhost:8090/api/v1/forecast/energy?buildingId=test&horizonDays=1")
  echo "  '$tid' → HTTP $CODE"
done
```

**Expected:** All return HTTP 200 (may have empty data, but security check passed — NOT 403/400)

**Actual:** ____________ **Status:** ____

---

### TC-006: ClickHouse parameterized query verification (ADR-032 Decision 5)
**Priority:** P0 | **Type:** Security

**Steps:**
```bash
# Verify Python code uses parameterized queries, not string interpolation
docker exec uip-forecast-service grep -A5 'parameters=' /app/data/clickhouse_client.py
```

**Expected:**
- Query uses `%(tenant_id)s`, `%(building_id)s`, `%(days)s` placeholders
- Parameters dict: `{"tenant_id": ..., "building_id": ..., "days": ...}`
- No f-string or string concatenation with user input

**Actual:** ____________ **Status:** ____

---

## 3. Forecast API Tests — 45 min

### TC-007: Forecast API — Happy Path (via Java backend)
**Priority:** P0 | **Type:** Functional
**Precondition:** JWT token obtained, building with ENERGY data exists

**Steps:**
```bash
# Get JWT token first
TOKEN=$(curl -sf http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"operator-hcm","password":"Operator#2026!"}' | jq -r '.accessToken')

# Call forecast API
curl -sf -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=BUILDING-001&horizonDays=30" | jq '{
    model: .model,
    isFallback: .isFallback,
    mape: .mape,
    pointsCount: (.points | length),
    firstPoint: .points[0],
    lastPoint: .points[-1]
  }'
```

**Expected:**
- HTTP 200
- `model`: "ARIMA" or "NAIVE"
- `isFallback`: false if ARIMA MAPE < 15%
- `mape`: null or number < 0.15
- `points` array with 720 entries (30 days × 24 hours)
- Each point: `predictedValue` between `confidenceLower` and `confidenceUpper`
- `actualValue`: null for future points

**Actual:** ____________ **Status:** ____

---

### TC-008: Boundary — horizonDays validation
**Priority:** P0 | **Type:** Boundary
**Precondition:** JWT token

**Steps:**
```bash
for hd in 0 -1 91 365 1 30 90; do
  CODE=$(curl -sf -o /dev/null -w '%{http_code}' \
    -H "Authorization: Bearer $TOKEN" \
    "http://localhost:8080/api/v1/forecast/energy?buildingId=B1&horizonDays=$hd")
  echo "  horizonDays=$hd → HTTP $CODE"
done
```

**Expected:**
| Input | Expected |
|---|---|
| 0 | 400 |
| -1 | 400 |
| 91 | 400 |
| 365 | 400 |
| 1 | 200 |
| 30 | 200 |
| 90 | 200 |

**Actual:** ____________ **Status:** ____

---

### TC-009: No tenant context → 403
**Priority:** P0 | **Type:** Security

**Steps:**
```bash
curl -sf -o /dev/null -w '%{http_code}' \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=B1&horizonDays=30"
```

**Expected:** HTTP 403 (no auth token → no tenant context)

**Actual:** ____________ **Status:** ____

---

### TC-010: Forecast service unavailable → 503
**Priority:** P1 | **Type:** Error handling

**Steps:**
```bash
# Stop forecast-service temporarily
docker stop uip-forecast-service

# Call API — should fall back or return 503
curl -sf -w '\nHTTP_CODE:%{http_code}' \
  -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=B1&horizonDays=1"

# Restart
docker start uip-forecast-service
```

**Expected:**
- HTTP 503 (Service Unavailable) — when forecast-engine=python and Python is down
- OR HTTP 200 with model="NAIVE" if forecast-engine=naive

**Actual:** ____________ **Status:** ____

---

### TC-011: Naive fallback mode
**Priority:** P1 | **Type:** Functional

**Steps:**
```bash
# Set forecast engine to naive mode
# Edit application.yml: uip.capabilities.forecast-engine=naive
# Or set env var: UIP_FORECAST_ENGINE=naive

curl -sf -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=B1&horizonDays=7" | jq '{
    model: .model,
    isFallback: .isFallback,
    pointsCount: (.points | length)
  }'
```

**Expected:**
- `model`: "NAIVE"
- `isFallback`: true
- `points`: 168 entries (7 days × 24 hours)

**Actual:** ____________ **Status:** ____

---

### TC-012: Performance — Cold call timing
**Priority:** P1 | **Type:** Performance

**Steps:**
```bash
# Clear cache, measure first call
docker exec uip-redis redis-cli -a "$REDIS_PASSWORD" DEL "forecasts::*" 2>/dev/null

time curl -sf -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=B1&horizonDays=30" \
  -o /dev/null
```

**Expected:**
- First call (cold): < 60s (ADR-032 AC revision)
- Second call (cached): < 500ms

**Actual:** ____________ **Status:** ____

---

### TC-013: /anomaly endpoint
**Priority:** P2 | **Type:** Functional

**Steps:**
```bash
docker exec uip-forecast-service curl -sf \
  -H "X-Tenant-ID: hcm" \
  "http://localhost:8090/api/v1/forecast/anomaly?buildingId=B1&contamination=0.05"
```

**Expected:**
- HTTP 200
- Response: `{ "anomalies": [...], "total_points": N, "anomaly_count": M }`

**Actual:** ____________ **Status:** ____

---

### TC-014: /models endpoint
**Priority:** P2 | **Type:** Functional

**Steps:**
```bash
docker exec uip-forecast-service curl -sf \
  http://localhost:8090/api/v1/forecast/models
```

**Expected:**
- HTTP 200
- Response lists ARIMA (active), NAIVE (active), LSTM (experimental)

**Actual:** ____________ **Status:** ____

---

## 4. Observability Tests — 30 min

### TC-015: Prometheus scrape targets UP
**Priority:** P0 | **Type:** Infrastructure

**Steps:**
```bash
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {
  job: .labels.job,
  health: .health,
  lastScrape: .lastScrape
}'
```

**Expected:**
| Job | health |
|---|---|
| uip-backend | up |
| analytics-service | up |
| kong | up |
| forecast-service | up |
| kafka | up |
| postgres | up |
| redis | up |

**Actual:** ____________ **Status:** ____

---

### TC-016: Grafana — UIP Services dashboard
**Priority:** P0 | **Type:** Visual

**Steps:**
1. Open `http://localhost:3001` → login admin/admin
2. Navigate to Dashboards → "UIP Services — SLI Overview"

**Expected:**
- 5 panels: Request Rate, P95 Latency, Error Rate, Service Up, Kong Request Rate
- Data visible (not "No data") for at least backend
- Time range selector works

**Actual:** ____________ **Status:** ____

---

### TC-017: Grafana — Forecast Service dashboard
**Priority:** P1 | **Type:** Visual

**Steps:**
1. Navigate to Dashboards → "UIP Forecast Service"

**Expected:**
- 8 panels: Service Health, Request Rate, p95 Latency, ARIMA Fit p95, Cache Hit Rate, Fallback Rate, MAPE per Building, Error Rate 5xx
- Service Health shows UP (green)

**Actual:** ____________ **Status:** ____

---

### TC-018: Forecast alert rules loaded
**Priority:** P1 | **Type:** Infrastructure

**Steps:**
```bash
curl -s http://localhost:9090/api/v1/rules | jq '.data.groups[] |
  select(.name | test("forecast")) | {
    name: .name,
    rules: [.rules[] | {alert: .alert, state: .state}]
  }'
```

**Expected:** 4 alert rules loaded:
- ForecastServiceDown (Critical)
- HighFallbackRate (Warning)
- ColdCallSlow (Warning)
- HighMAPE (Warning)

**Actual:** ____________ **Status:** ____

---

### TC-019: Python JSON structured logging
**Priority:** P1 | **Type:** Observability

**Steps:**
```bash
# Trigger a forecast request, then check logs
docker logs uip-forecast-service --tail 20 2>&1 | grep -E '^{'
```

**Expected:**
- Logs are valid JSON
- Each log entry has: `timestamp`, `level`, `message` fields
- Log entries may include `traceId`, `tenantId` when available

**Actual:** ____________ **Status:** ____

---

## 5. Frontend UI Tests — 30 min

### TC-020: ESG Page — Forecast section visible
**Priority:** P1 | **Type:** UI

**Steps:**
1. Login at `http://localhost:3000`
2. Navigate to ESG Metrics page
3. Scroll to bottom → "Energy Forecast" section

**Expected:**
- "Energy Forecast" header visible
- Building selector dropdown visible
- Placeholder text: "Select a building to view energy forecast"

**Actual:** ____________ **Status:** ____

---

### TC-021: Forecast Chart renders after building selection
**Priority:** P1 | **Type:** UI + Integration

**Steps:**
1. Click building selector dropdown
2. Select a building with data
3. Wait for chart to load

**Expected:**
- Dropdown shows list of buildings
- After selection: loading spinner appears briefly
- Chart renders with:
  - Blue forecast line
  - Gray confidence band (shaded area)
  - X axis: dates, Y axis: kWh
  - Legend: Actual, Forecast, Anomaly
- MAPE badge shown (if available)
- "Fallback mode" badge if naive (orange color)

**Actual:** ____________ **Status:** ____

---

### TC-022: Forecast Tooltip interaction
**Priority:** P2 | **Type:** UI

**Steps:**
1. Hover over a data point on the forecast chart

**Expected:**
- Tooltip shows:
  - Date/time
  - Predicted value (kWh)
  - Confidence range: [lower, upper]
  - Deviation % (if actual exists)
- "Anomaly detected" warning if point is anomaly

**Actual:** ____________ **Status:** ____

---

### TC-023: Forecast Chart — Responsive
**Priority:** P2 | **Type:** UI

**Steps:**
1. Resize browser to 768px width (tablet)
2. Observe forecast chart

**Expected:**
- Chart resizes to fit container
- No horizontal scroll
- All elements readable

**Actual:** ____________ **Status:** ____

---

### TC-024: Forecast error state
**Priority:** P2 | **Type:** UI

**Steps:**
1. Stop forecast-service: `docker stop uip-forecast-service`
2. Select a building in forecast section
3. Observe error handling

**Expected:**
- Error alert: "Failed to load forecast data. The service may be unavailable."
- Chart area shows appropriate empty/error state
- No JavaScript console errors

**Actual:** ____________ **Status:** ____

---

## 6. Regression Tests — 30 min

### TC-025: Java unit tests still PASS
**Priority:** P0 | **Type:** Regression

```bash
cd backend && ./gradlew cleanTest test jacocoTestReport --quiet
```

**Expected:**
- 1568+ tests PASS, 0 failures
- BUILD SUCCESSFUL

**Actual:** ____________ **Status:** ____

---

### TC-026: ESG GRI Export still works
**Priority:** P0 | **Type:** Regression

**Steps:**
1. Navigate to ESG Metrics → Report Generation
2. Generate a quarterly report
3. Download as Excel and PDF

**Expected:**
- Report generation triggers successfully
- Excel download: file opens with data
- PDF download: file opens with charts
- `waterIntensityM3PerPerson` field present (may be null)

**Actual:** ____________ **Status:** ____

---

### TC-027: Keycloak RSA auth still works
**Priority:** P0 | **Type:** Regression

**Steps:**
1. Logout
2. Login with Keycloak credentials (operator-hcm)
3. Verify JWT token obtained
4. Access protected endpoints

**Expected:**
- Login succeeds
- Token valid with correct claims
- Protected API calls return 200

**Actual:** ____________ **Status:** ____

---

### TC-028: Flink enrichment pipeline active
**Priority:** P0 | **Type:** Regression

**Steps:**
```bash
# Check Flink job is running
curl -s http://localhost:8081/jobs/overview | jq '.jobs[] | select(.name=="EsgDualSinkJob") | .state'
```

**Expected:** `RUNNING`

**Actual:** ____________ **Status:** ____

---

## 7. Gate Verification Script — 15 min

### TC-029: Run sprint-gate-verify.sh
**Priority:** P0 | **Type:** Gate

```bash
bash scripts/sprint-gate-verify.sh
```

**Expected:**
- All 10 gates PASS
- Summary: `ALL GATES PASSED`

**Actual:** ____________ **Status:** ____

---

## 8. Test Session Report Template

```markdown
## Test Session Report — Sprint 4
**Tester:** ___________  **Date:** 2026-05-__
**Environment:** Local Docker Compose

### Tests Executed
| TC ID | Title | Priority | Result | Notes |
|-------|-------|----------|--------|-------|
| TC-001 | Network isolation (ADR-032 D1) | P0 | | |
| TC-002 | Missing X-Tenant-ID → 403 | P0 | | |
| TC-003 | Empty X-Tenant-ID → 403 | P0 | | |
| TC-004 | Invalid X-Tenant-ID → 400 | P0 | | |
| TC-005 | Valid X-Tenant-ID → passes | P0 | | |
| TC-006 | Parameterized queries verified | P0 | | |
| TC-007 | Forecast API happy path | P0 | | |
| TC-008 | horizonDays boundary | P0 | | |
| TC-009 | No auth → 403 | P0 | | |
| TC-010 | Service unavailable → 503 | P1 | | |
| TC-011 | Naive fallback mode | P1 | | |
| TC-012 | Performance cold/cached | P1 | | |
| TC-013 | /anomaly endpoint | P2 | | |
| TC-014 | /models endpoint | P2 | | |
| TC-015 | Prometheus targets UP | P0 | | |
| TC-016 | Grafana services dashboard | P0 | | |
| TC-017 | Grafana forecast dashboard | P1 | | |
| TC-018 | Forecast alert rules | P1 | | |
| TC-019 | JSON structured logging | P1 | | |
| TC-020 | ESG page forecast section | P1 | | |
| TC-021 | Forecast chart renders | P1 | | |
| TC-022 | Tooltip interaction | P2 | | |
| TC-023 | Responsive 768px | P2 | | |
| TC-024 | Error state handling | P2 | | |
| TC-025 | Java regression 1568+ | P0 | | |
| TC-026 | ESG GRI export | P0 | | |
| TC-027 | Keycloak auth | P0 | | |
| TC-028 | Flink pipeline | P0 | | |
| TC-029 | Gate verify script | P0 | | |

### Summary
- Total: 29 | Passed: ___ | Failed: ___ | Blocked: ___

### Bugs Found
| Bug ID | Severity | Title | TC |
|--------|----------|-------|----|
| | | | |

### Acceptance Criteria Sign-off
- [ ] AC-01: Observability Dashboard Live
- [ ] AC-02: ARIMA Forecast API (security + MAPE + perf)
- [ ] AC-03: Forecast Frontend Chart
- [ ] AC-05: No Regression (1568+ tests)
- [ ] AC-07: Forecast-Service Observability

### Tester Sign-off
Name: ___________  Date: ___________
```

---

## Quick Command Reference

```bash
# JWT token
TOKEN=$(curl -sf http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"operator-hcm","password":"Operator#2026!"}' | jq -r '.accessToken')

# Forecast API
curl -sf -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=B1&horizonDays=30" | jq

# Security tests inside forecast-service container
docker exec uip-forecast-service curl -sf -w '\nHTTP:%{http_code}' \
  http://localhost:8090/api/v1/forecast/energy?buildingId=test

# Prometheus targets
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets[] | {job: .labels.job, health: .health}'

# Forecast alert rules
curl -s http://localhost:9090/api/v1/rules | jq '.data.groups[].rules[] | select(.alert) | .alert'

# Python logs
docker logs uip-forecast-service --tail 10 2>&1

# Cache stats
curl -sf -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/forecast/cache/stats | jq

# Gate verify
bash scripts/sprint-gate-verify.sh
```

---

*Document created: 2026-05-25 | Sprint 4 Test Plan — 29 test cases*
*Prerequisite: All dev tasks complete (S4-01→S4-23 + ESG integration)*
*Next: Execute test session, fill actual results, report bugs*
