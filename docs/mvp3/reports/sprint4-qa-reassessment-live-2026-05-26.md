# Sprint 4 QA Reassessment — Live Environment

**Date:** 2026-05-26 16:15 SGT  
**Assessor:** QA Engineer  
**Scope:** Re-check Sprint 4 forecast/demo readiness against the current local live stack after contradictory evidence was found in previous reports.

## Update Note (2026-05-26 17:05 SGT)

This report captured the environment state at 16:15 and identified tenant-data mismatch as the primary blocker.

Latest re-verification at 17:05 confirms that blocker is resolved (`admin/default` and `tadmin/hcm` now return ARIMA via backend).

Use `docs/mvp3/reports/sprint4-qa-gap-review-2026-05-26.md` as the current source of truth for remaining Sprint 4 QA gaps.

## Verdict

**NO-GO for PO demo in the current live environment.**

Sprint 4 is partially complete and the forecast stack now runs again after two defects were fixed, but the current demo tenant flow still does not produce live ARIMA results through the backend/UI path. The forecast engine works for a tenant with data (`T001`), but the authenticated demo tenants currently available in the app (`default`, `hcm`) return `model=NONE` because they do not map to the seeded forecast dataset.

## What Was Verified Live

### Fixed during reassessment

1. **Backend → forecast-service query contract mismatch fixed**
   - Root cause: backend sent `buildingId` / `horizonDays`
   - Python service expects `building_id` / `horizon_days`
   - Symptom before fix: Python returned `422`, backend translated that to `503`
   - Fix applied in backend adapter

2. **Forecast cache failure mode fixed**
   - Root cause: controller cached `ResponseEntity`, including prior `503` responses
   - Redis later failed to deserialize cached `ResponseEntity`, causing `500`
   - Fix applied by moving cache to service layer so only `ForecastResult` is cached

### Current live checks

| Check | Result | Evidence |
|---|---|---|
| Backend health | PASS | `uip-backend healthy` |
| Forecast service health | PASS | `uip-forecast-service healthy running` |
| Login with `admin / admin_Dev#2026!` | PASS | HTTP 200 |
| Login with `tadmin / admin_Dev#2026!` | PASS | HTTP 200 |
| Backend forecast endpoint after fixes | PASS | HTTP 200 |
| Backend forecast endpoint for `admin/default` | PARTIAL | `model=NONE`, `isFallback=true` |
| Backend forecast endpoint for `tadmin/hcm` | PARTIAL | `model=NONE`, `isFallback=true` |
| TC-010 forecast service unavailable | PASS | HTTP 503 on uncached request |
| Direct Python forecast for `T001/B001` | PASS | `model=ARIMA`, `is_fallback=false`, `mape=0.03372992829070087` |

## Key Evidence

### Backend endpoint now returns 200 again

Example response via backend:

```json
{
  "tenantId": "hcm",
  "buildingId": "B001",
  "model": "NONE",
  "isFallback": true,
  "mape": null,
  "points": []
}
```

This proves the API path is no longer broken, but it also proves the current demo tenant has no usable forecast data.

### Forecast engine itself is working

Direct call inside `uip-forecast-service` with `X-Tenant-ID: T001`:

```json
{
  "tenant_id": "T001",
  "building_id": "B001",
  "model": "ARIMA",
  "is_fallback": false,
  "mape": 0.03372992829070087
}
```

### Data availability in ClickHouse

Live ClickHouse query result:

```text
T001    B001    9600
```

This confirms the forecast source data exists, but for `T001`, not for the authenticated demo tenants currently used in the app flow.

## Assessment of Sprint 4 Completion

### Completed enough to count as implemented

- Forecast backend contract is now corrected
- Forecast cache no longer corrupts endpoint behavior
- Forecast API is reachable and authenticated
- Forecast engine produces valid ARIMA output when tenant/data match
- Availability handling now returns the expected `503`

### Not complete enough for PO demo

- Current backend-authenticated demo tenants do not produce live ARIMA results
- PO demo path would show `model=NONE` instead of the intended forecast chart with real predictions
- Existing earlier GO reports are no longer trustworthy without re-seeding or tenant/data alignment

## Blocking Gap

**Primary blocker:** authenticated demo tenant does not align with the tenant that owns seeded forecast data.

Observed state:
- `admin` token -> tenant `default`
- `tadmin` token -> tenant `hcm`
- usable forecast history exists for tenant `T001`

## Recommendation

**Current status: NO-GO for PO demo until one of these is done:**

1. Seed forecast history for the actual demo tenant (`hcm` or `default`), or
2. Provide a demo user whose JWT tenant resolves to `T001`, or
3. Change demo script to use a tenant that already has seeded forecast history end-to-end.

After tenant/data alignment, rerun these minimum checks:

1. Login via backend with demo user
2. `GET /api/v1/forecast/energy?buildingId=B001&horizonDays=1` returns HTTP 200 with `model=ARIMA`
3. Frontend forecast chart renders non-empty data
4. TC-010 still returns HTTP 503 on uncached request when forecast service is stopped

## Files Changed During Reassessment

- `backend/src/main/java/com/uip/backend/forecast/ForecastServiceAdapter.java`
- `backend/src/test/java/com/uip/backend/forecast/ForecastServiceAdapterTest.java`
- `backend/src/main/java/com/uip/backend/forecast/ForecastController.java`
- `backend/src/main/java/com/uip/backend/forecast/ForecastService.java`

## QA Sign-off

**Decision:** NO-GO  
**Reason:** runtime defects fixed, but demo data path is still not valid for the authenticated tenants available in the current live environment.
