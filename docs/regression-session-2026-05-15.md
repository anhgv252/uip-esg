# UIP Smart City — Regression Session Report
**Date:** 2026-05-15  
**Scope:** PO demo walkthrough + full regression run (unit+IT + API)

---

## Executive Summary

Full regression run completed. All **103 API tests pass**. Two pre-existing unit test failures were identified and fixed. The demo environment is healthy and all analytics charts render with live ClickHouse data.

---

## Demo Walkthrough Results

| Page | Status | Notes |
|------|--------|-------|
| Dashboard | ✅ PASS | Active Sensors=8, Open Alerts=203,840, Carbon=87,676t |
| Buildings Analytics | ✅ PASS | All 4 charts rendering (Energy, CO2, AQI, Breakdown) with real ClickHouse data |
| ESG Metrics | ✅ PASS | 194,986 kWh, 9,725 m³, 87,676 tCO₂e; Q2 report generator works |
| Traffic | ✅ PASS | Vehicle count chart (INT-001), 4 open incidents (2 ACCIDENT, 2 CONGESTION) |
| Environment | ✅ PASS | 8 AQI stations listed (OFFLINE expected in demo mode) |

---

## Regression Test Results

### Phase 1 — Unit + Integration Tests (`./gradlew test`)

| Result | Count |
|--------|-------|
| Total tests | ~530+ |
| Passed | All except 2 |
| **Fixed in this session** | **2** |

### Phase 2 — API Tests (`api_regression_test.py`)

| Suite | Tests | Result |
|-------|-------|--------|
| health | 5 | ✅ PASS |
| auth | 7 | ✅ PASS |
| environment | 5 | ✅ PASS |
| esg | 8 | ✅ PASS |
| alerts | 5 | ✅ PASS |
| traffic | 3 | ✅ PASS |
| tenant | 3 | ✅ PASS |
| citizen | 1 | ✅ PASS |
| admin | 3 | ✅ PASS |
| workflow | 3 | ✅ PASS |
| tenant_admin | 6 | ✅ PASS |
| invite | 3 | ✅ PASS |
| rate_limit | 4 | ✅ PASS |
| esg_export | 8 | ✅ PASS |
| pwa_citizen | 7 | ✅ PASS |
| tenant_admin_dashboard | 12 | ✅ PASS |
| analytics | 20 | ✅ PASS |
| **TOTAL** | **103** | **✅ ALL PASS** |

---

## Bugs Fixed This Session

### BUG-1: `CrossBuildingAggregationService` — empty time range not rejected

**File:** `backend/src/main/java/com/uip/backend/building/service/CrossBuildingAggregationService.java`  
**Test:** `AGG-IT-11: Empty time range (from == to) returns empty`  
**Root Cause:** The SQL `BETWEEN ? AND ?` is inclusive on both ends. When `from == to`, the query returns any row whose timestamp matches that exact instant (in this case the seeded "out-of-range" row at `BASE.minusDays(30)`).  
**Fix:** Added early-return guard at the top of `aggregate()`:
```java
if (!request.to().isAfter(request.from())) {
    return List.of();
}
```
**Impact:** Zero-duration or inverted time ranges now return an empty list immediately, avoiding unnecessary DB queries and returning semantically correct results.

---

### BUG-2: `CrossBuildingAggregationServiceIT` — incorrect expected dataPoints for zero-value test

**File:** `backend/src/test/java/com/uip/backend/building/service/CrossBuildingAggregationServiceIT.java`  
**Test:** `AGG-IT-14: Zero-value metrics — SUM and AVG handle 0.0 correctly`  
**Root Cause:** The test queried window `[BASE-11min, BASE+1min]` and expected `dataPoints=2` (1 original + 1 zero-value). However, the `@BeforeAll` seed loop inserts 500 rows at `BASE-1min` through `BASE-500min`. This window captures rows at i=1..11 (11 rows at 100.0 each) plus the new 0.0 row = **12 total**, not 2.  
**Fix:** Updated assertions to match actual behavior:
```java
// Before (wrong):
assertThat(results.get(0).dataPoints()).isEqualTo(2L);
assertThat(results.get(0).totalValue()).isCloseTo(100.0, offset(0.001));

// After (correct):
assertThat(results.get(0).dataPoints()).isEqualTo(12L);
assertThat(results.get(0).totalValue()).isCloseTo(1100.0, offset(0.001));
```
**Note:** The test still validates that zero-value rows are included in `COUNT(*)` (if 0.0 were skipped, `dataPoints` would be 11, not 12).

---

### BUG-3 (Prior session): `regression_test.sh` — wrong health check endpoint

**File:** `scripts/regression_test.sh` line 161  
**Root Cause:** Health check was hitting `/actuator/health` (returns 404), not the actual health endpoint.  
**Fix:** Changed to `$BASE_URL/api/v1/health`.

---

## Infrastructure Notes

| Component | Status | Notes |
|-----------|--------|-------|
| uip-backend | ✅ UP | Port 8080 |
| uip-analytics-service | ✅ UP | Port 8082 (rebuilt fresh image) |
| uip-clickhouse | ✅ UP | Port 8123; seeded with building codes |
| uip-timescaledb | ✅ UP | Port 5432 |
| uip-frontend | ✅ UP | Port 3000 (nginx) |
| uip-emqx | ⚠️ UNHEALTHY | Not critical for analytics demo |
| uip-keycloak | ⚠️ UNHEALTHY | Not critical for admin credential flow |

**Service worker warning:** Vite PWA service worker caches "offline" page and intercepts navigation in browser automation. Workaround: unregister SW + clear caches via `page.evaluate()` before each `page.goto()`.

---

## ClickHouse Data (analytics.esg_readings)

| Building Code | ENERGY rows | CARBON rows | AIR_QUALITY rows |
|---------------|-------------|-------------|-----------------|
| BLD-DEFAULT-001 | 11 | 5 | 7 |
| PERF-BLD-004 | 5 | 3 | 3 |
| PERF-BLD-005 | 5 | 3 | 3 |

Energy aggregate result (all 3 buildings, wide range): `totalKwh = 9265.0 kWh` ✅
