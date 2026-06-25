# MVP5 Sprint 1 — T06 Cache Key Tenant-Namespacing Audit

**Task:** MVP5-S1-T06 — Cache key namespacing audit + fix
**Date:** 2026-06-24
**Rule enforced:** `feedback_sprint3_readiness` — every cache key MUST carry a `tenant_id` prefix to prevent cross-tenant cache leaks.
**Status:** DONE — 385/385 tests PASS (0 failures)

---

## Summary

Audited every Redis/cache key in the backend that is reachable by per-tenant
data. Found 5 cache points missing the tenant prefix (P1 cross-tenant leak
risk) and fixed all of them. The OK list (6 points already namespaced) is
unchanged. Two unit-test regressions in `AiCacheConfigTest` (stale seed keys)
were fixed to match the new tenant-prefixed key shape.

---

## Fixed cache points (P1 leak risk)

| # | File | Key before | Key after | Tenant source | Fallback on null tenant |
|---|------|-----------|-----------|---------------|--------------------------|
| 1 | `ai/AiInferenceService.java` — `analyzeAqiWithMetrics` pre-check + `@Cacheable analyzeAqiConditions` | `ai-responses::{districtCode}:{aqiRange}` | `ai-responses:{tenantId}:{districtCode}:{aqiRange}` | `TenantContext.getCurrentTenant()` (ThreadLocal) | `"global"` namespace (documented — AI advisory cache is non-sensitive, idempotent, 5-min TTL) |
| 2 | `ai/AiInferenceService.java` — `analyzeGenericWithMetrics` pre-check + `@Cacheable analyzeGenericConditions` | `ai-responses::{districtCode}:{sensorType}:{valueRange}` | `ai-responses:{tenantId}:{districtCode}:{sensorType}:{valueRange}` | `TenantContext.getCurrentTenant()` | `"global"` namespace (same rationale as #1) |
| 3 | `alert/service/AlertEngine.java` — `evaluate` dedup | `alert:dedup:{sensorId}:{measureType}:{ruleId}` | `alert:dedup:tenant:{tenantId}:{sensorId}:{measureType}:{ruleId}` | `TenantContext.getCurrentTenant()` | **Fail-open**: dedup skipped entirely (no `setIfAbsent`) — a null tenant never blocks a P0/P1 alert |
| 4 | `alert/kafka/AlertEventKafkaConsumer.java` — `consume` dedup | `alert:dedup:kafka:{sensorId}:{measureType}:{severity}` | `alert:dedup:kafka:tenant:{tenantId}:{sensorId}:{measureType}:{severity}` | `AlertEvent.tenantId` (extracted from payload `tenantId`/`tenant_id`; entity defaults to `"default"`) | `"default"` namespace |
| 5 | `alert/flood/FloodAlertConsumer.java` — `consume` dedup | `alert:dedup:flood:{sensorId}:{measureType}:{severity}` | `alert:dedup:flood:tenant:{tenantId}:{sensorId}:{measureType}:{severity}` | `AlertEvent.getTenantId()` (validated against `uip.tenant.allowed-ids` gate before reaching dedup — unknown tenants are DLQ'd) | N/A — tenant validation gate precedes dedup |
| 6 | `safety/consumer/StructuralAlertConsumer.java` — `consume` dedup | `alert:dedup:structural:{sensorId}:{measureType}:{severity}` | `alert:dedup:structural:tenant:{tenantId}:{sensorId}:{measureType}:{severity}` | `AlertEvent.getTenantId()` (same validation gate as flood) | N/A — tenant validation gate precedes dedup |

### Notes on design decisions

**AI cache null-tenant → `"global"` (fail-close to a single shared namespace).**
AI responses are advisory text — non-sensitive, idempotent, 5-min TTL. The
worst case of a stale shared entry is a duplicated Claude call, never a data
leak. A fail-open-per-thread approach would fan out an unbounded number of
per-thread keys, so fail-close to `"global"` is the documented choice. This
matches the `BuildingSafetyService` / `EnvironmentService` pattern of reading
`TenantContext.getCurrentTenant()` directly.

**Alert dedup null-tenant → fail-open (skip dedup).**
A shared `"default"` dedup key could block a legitimate P0/P1 alert for a
tenant whose context was not bound in time. Alerting is fail-safe by design
(`feedback_sprint5_lessons`), so a null tenant skips dedup entirely and the
alert is always created.

**AiInferenceService: pre-check key and `@Cacheable` SpEL key MUST match.**
Both the manual `cacheKey` string built in `analyzeAqiWithMetrics` /
`analyzeGenericWithMetrics` and the `@Cacheable(key = ...)` SpEL expression in
`analyzeAqiConditions` / `analyzeGenericConditions` now build the tenant prefix
through the same code path (`tenantKeySegment()` / `currentTenantOrGlobal()`).
The SpEL references the public static `currentTenantOrGlobal()` directly:
`T(com.uip.backend.ai.AiInferenceService).currentTenantOrGlobal()`. A code
comment in each pre-check marks the invariant.

**AlertEventKafkaConsumer tenant extraction.**
`mapToAlertEvent` now reads both `tenantId` (camelCase) and `tenant_id`
(snake_case) from the Flink payload and sets it on the `AlertEvent`. The
entity's `tenant_id` column is `nullable = false` with a `"default"` value, so
a missing payload field degrades to `"default"` (single-tenant fallback) rather
than NPE.

---

## OK cache points (already namespaced — unchanged)

| # | File | Key pattern | Tenant source |
|---|------|------------|---------------|
| 1 | `esg/common/CacheKeyBuilder.java` | `esg-dashboard:{tenantId}:...` | `tenantId` constructor param |
| 2 | `safety/service/BuildingSafetyService.java` | `{tenantId}:{buildingId}` | `TenantContext.getCurrentTenant()` |
| 3 | `environment/service/EnvironmentService.java` | `{tenantId}:all` | `TenantContext.getCurrentTenant()` |
| 4 | `forecast/ForecastService.java` | `{tenantId}\|{buildingId}\|{horizonDays}` | `#tenantId` SpEL param |
| 5 | `aiworkflow/gateway/DecisionRouter.java` | `{CACHE_PREFIX}{tenantId}:{scenarioKey}:{hexHash}` | `tenantId` param |
| 6 | `common/ratelimit/TenantRateLimiter.java` | per-tenant key | `TenantContext.getCurrentTenant()` |

---

## Tests added / updated

| Test class | Change | Cross-tenant coverage |
|-----------|--------|------------------------|
| `ai/AiInferenceServiceBatchTest` | Added `CrossTenantIsolation` nested class (5 tests) | Tenant A HCM-D1 AQI=50 → entry; tenant B HCM-D1 AQI=50 → MISS. Verifies 2 distinct cache entries. Generic path + null→`global` fallback covered. |
| `alert/service/AlertEngineTest` | Rewritten: bind `TenantContext` per test; added 2 cross-tenant tests | Tenant A sensor "shared-sensor" dedup does NOT suppress tenant B same-sensor alert. Null-tenant fail-open (no `setIfAbsent`). |
| `alert/kafka/AlertEventKafkaConsumerTest` | Updated dedup-key assertion to tenant-prefixed shape; added cross-tenant test | Tenant A (hcm) + tenant B (hanoi) same sensor → 2 distinct dedup keys, both processed. |
| `alert/flood/FloodAlertConsumerTest` | Added `FL-T-13` cross-tenant test | hcm + hanoi same sensor → 2 distinct dedup keys. |
| `safety/consumer/StructuralAlertConsumerTest` | Added cross-tenant test | hcm + hanoi same sensor/building → 2 distinct dedup keys. |
| `ai/cache/AiCacheConfigTest` | Updated 2 stale seed keys (`HCM-D1:51-100` → `hcm:HCM-D1:51-100`) to match new key shape; added `@AfterEach` tenant cleanup | Regression fix only (no new cross-tenant case here — covered by `AiInferenceServiceBatchTest`). |

---

## Build verification

```
cd backend && ./gradlew test --tests "com.uip.backend.ai.*" \
    --tests "com.uip.backend.alert.*" --tests "com.uip.backend.safety.*"
```

**Result:** `BUILD SUCCESSFUL` — 385 tests, 0 failures, 0 errors, 0 skipped.

---

## Blockers

None. Tenant context is available at every call site:
- AI path: `TenantContext` ThreadLocal (bound by `TenantContextFilter` on HTTP
  and `TenantAwareKafkaListener` on Kafka). `DistrictAggregationEvent` also
  carries a `tenantId` field (used as the source of truth when the consumer
  binds the context before delegating).
- Alert inline path (`AlertEngine`): no production caller today (only tests),
  but `TenantContext` is the correct source — the same context the
  `TenantContextAspect` uses to run `SET LOCAL app.tenant_id` for the JPA write.
- Kafka alert consumers: `AlertEvent.tenantId` extracted from payload, with a
  validation gate (`uip.tenant.allowed-ids`) in flood/structural consumers.
