# MVP5 Sprint 1 — Pact Provider Verification Fix (Task T10)

**Date:** 2026-06-24
**Task:** T10 — `AnalyticsServiceProviderPactTest` 3/3 FAIL (401) → PASS
**Status:** DONE

## Symptom

`AnalyticsServiceProviderPactTest` failed 3/3 with `401 Unauthorized`
("Bearer token required"). Pre-existing since S11-PACT-01, masked by
`excludeTags 'integration'` on the default `test` task; surfaced when
`integrationTest` was wired into `check`.

## Root cause

Three independent defects, all masked by the 401:

1. **Security (the diagnosed cause).** `SecurityConfig` requires JWT bearer
   tokens for all non-actuator endpoints. Pact provider verification sends
   requests without `Authorization` headers → 401.

2. **Doc-vs-code gap: stale contract paths.** Two consumer pact files
   referenced endpoints that do **not** exist in `AnalyticsController`:
   - `backend-analytics-service.json` (consumer "backend"):
     `GET /api/v1/analytics/summary`, `GET /api/v1/analytics/energy/aggregated`
   - `uip-backend-analytics-service.json` (consumer "uip-backend"):
     `POST /energy-aggregate` (missing `/api/v1/analytics` prefix)
   The controller maps `POST /api/v1/analytics/energy-aggregate`
   (+ `/emissions-aggregate`, `/aqi-trend`). Once auth was stubbed, all three
   interactions returned 404 instead of 401 — exposing the gap.

3. **Empty provider state → zero-value responses.** `@State("analytics data
   exists for tenant alpha")` was a no-op; the real service queries
   ClickHouse, which is unreachable in the test profile, so `safeQuery`
   returned empty results. Contract expected seeded values (15000 kWh etc.).

## Fix

| Defect | Fix |
|---|---|
| Security 401 | `TestSecurityConfig` (`src/test/java`) — `permitAll` + stamped principal (covers `@PreAuthorize` roles). Loaded **only** by the provider test via `@Import` + class-level `properties = "uip.security.enabled=false"`. Production `SecurityConfig` gained `@ConditionalOnProperty(name="uip.security.enabled", matchIfMissing=true)` — **ON by default**, so production and `AnalyticsControllerTest` (which asserts 401/403) are unaffected. |
| Stale paths | Deleted orphaned `backend/.../contract/AnalyticsServiceConsumerPactTest.java` (dead contract for non-existent APIs). Updated `esg/config/analytics/AnalyticsServiceConsumerPactTest` path to `/api/v1/analytics/energy-aggregate`. Refreshed committed pact snapshot. |
| Zero values | `PactProviderStubConfiguration` — `@Primary` stubs for the three analytics services returning contract-shaped fixtures. Provider verification asserts **contract shape**; data correctness is covered by `ClickHouseEnergyRepositoryIT` (Testcontainers). |
| Script bug | `pact-verify.sh` Step 3 used `./gradlew test` (excludes `integration` tag → "No tests found"). Switched to `integrationTest`. Also removed the `rm` cleanup that was deleting the committed pact snapshot (broke standalone provider runs). |

## Verification

```
analytics-service :check          → BUILD SUCCESSFUL (clean)
  test            → 48 tests, 0 failures
  integrationTest →  7 tests, 0 failures
scripts/pact-verify.sh            → exit 0 (consumer → copy → provider PASS)
standalone provider test          → BUILD SUCCESSFUL (committed pact)
```

## Note: production REST adapter path

`ClickHouseRestAnalyticsAdapter` (backend consumer) calls
`analyticsServiceUrl + "/energy-aggregate"`. For the contract and the
production call to align, the configured `analyticsServiceUrl` must include
the `/api/v1/analytics` base path, OR the adapter must append it. This is a
**deployment-config concern** (Kong `strip_path: false` + service base URL),
not changed here — flagged for the DevOps owner to confirm the wired base URL.
