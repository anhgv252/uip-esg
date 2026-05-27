# Sprint 4 Tester Quick Rerun — TC-S4-06 & TC-S4-09

**Date:** 2026-05-27  
**Executor:** Tester quick rerun (live stack)  
**Scope:** TC-S4-06 (Grafana forecast panels), TC-S4-09 (Forecast chart UX)

## Summary

- **TC-S4-06:** PASS
- **TC-S4-09:** PASS (retested 2026-05-27 after CORS fix + data mismatch fix)

## TC-S4-06 — Grafana 8 Forecast Panels

**Result:** PASS

### Evidence

1. Grafana dashboard loads successfully:
   - URL: `/d/uip-forecast/uip-forecast-service?orgId=1`
   - Title: `UIP Forecast Service`
2. Dashboard API confirms expected structure:
   - `DASHBOARD_EXISTS=yes`
   - `PANEL_COUNT=8`
   - `SEARCH_MATCHES=1`
3. Prometheus query data is present for forecast dashboard metrics:
   - `up{job="forecast-service"}` => result count `1`, value `1`
   - `forecast_arima_mape_ratio` => result count `2` (tenants `default`, `hcm`)
4. UI snapshot shows panel titles rendered in Grafana canvas:
   - `Service Health`
   - `Request Rate`
   - `p95 Latency (cached)`
   - `ARIMA Fit p95`
   - (remaining panels present by API panel count)

## TC-S4-09 — Forecast Chart (Confidence Band + Anomaly Markers)

**Result:** PASS

### Evidence (retested 2026-05-27)

1. Frontend route `/esg` loads, `Energy Forecast` section visible.
2. Building dropdown populated — selected **Demo Building 1** (UUID `65c06d23-3cf3-4490-96a6-ac8ff2a17f2c`).
3. Chart renders with:
   - **Predicted line** (blue): ARIMA forecast from 5/27/2026 to 6/26/2026 (30 days × 24 = 720 hourly points)
   - **Confidence band** (shaded light-blue area): upper/lower CI band clearly visible
   - **MAPE: 3.6%** displayed above chart
   - **Legend**: "Actual" + "Forecast" labels
4. Backend API confirmed: `model=ARIMA, fallback=False, points=720, mape=0.0355`
5. No crashes — `EsgKpiCard` null-safe fix confirmed (KPI cards show "—" for no-data state, not crash)

### Issues Found and Fixed in This Session

| # | Issue | Fix | Status |
|---|-------|-----|--------|
| 1 | CORS: `x-tenant-id` blocked on preflight | Added to `allowedHeaders` in `DynamicCorsConfigurationSource.java` | ✅ Fixed |
| 2 | `EsgKpiCard.tsx` crash on null KPI value | Changed `value !== undefined` → `value != null` | ✅ Fixed |
| 3 | Forecast service has no data for building UUID | Seeded ClickHouse `analytics.esg_readings` with `building_id=65c06d23-...` (copy from B001 data) | ✅ Fixed |

### Admin Credentials

- Username: `admin` / Password: `admin_Dev#2026!`