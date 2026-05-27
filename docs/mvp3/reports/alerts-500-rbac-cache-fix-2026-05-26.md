# Alerts API 500 Fix Report (Operator RBAC)

Date: 2026-05-26
Scope: GET /api/v1/alerts for operator role (alert:read)

## Symptom
- API regression failed on:
  - GET /alerts (admin) -> 200
  - GET /alerts (operator, alert:read) -> 500 (expected 200)

## Root Cause
- Request-thread exception in backend logs:
  - org.springframework.data.redis.serializer.SerializationException
  - caused by: InvalidDefinitionException for org.springframework.data.domain.PageImpl
- `AlertService.queryAlerts(...)` was annotated with `@Cacheable` and returned `Page<AlertEventDto>`.
- Redis JSON serializer could not reliably deserialize cached `PageImpl` payloads for this path, causing 500 on cache read.

## Code Fix
- File: backend/src/main/java/com/uip/backend/alert/service/AlertService.java
- Change:
  - Removed `@Cacheable` from `queryAlerts(...)` (Page-returning method).
  - Removed now-unused import `TenantContext`.
- Kept `@CacheEvict(value = "alerts", allEntries = true)` on mutate methods; harmless and backward-compatible.

## Validation
1. Targeted backend tests:
- ./gradlew test --tests 'com.uip.backend.alert.service.AlertServiceTest' --tests 'com.uip.backend.alert.service.AlertServiceIT'
- Result: BUILD SUCCESSFUL

2. Runtime regression after rebuilding backend container:
- python3 scripts/api_regression_test.py -g alerts --fail-fast
- Result:
  - alerts 5 pass
  - Total: 5 tests | 5 passed
  - ALL TESTS PASSED

## Outcome
- Regression blocker cleared.
- Operator `GET /alerts` now returns 200 as expected.
