# SA Fix Backlog — API Contract Remediation

**Source**: `docs/mvp3/reports/sprint9-contract-audit.md` (2026-06-04)  
**Owner**: Solution Architect / Backend Team  
**Policy**: See `docs/adr/ADR-039-openapi-first-api-contract.md`  
**Tracking Sprint**: MVP3-9 and beyond  

---

## Summary

| Priority | Count | Status |
|---|---|---|
| P0 — Critical (fixed in Sprint 9 early start) | 3 | ✅ DONE |
| P1 — Whole modules missing from spec | 42 endpoints, 7 modules | ✅ DONE (SA-001–037 all resolved in Sprint 9/10 buffer) |
| P2 — Debug endpoints in production code | 4 | ✅ DONE (SA-028/038/039 `@Profile("!production")`; SA-040 done) |
| P3 — Parameter/response mismatches | 5 | 🟡 PARTIAL (SA-041 done; SA-043 done; SA-042 Sprint 11+) |

---

## P0 — Critical Gaps (all resolved)

| ID | Gap | Sprint | Status | PR/Commit |
|---|---|---|---|---|
| P0-1 | `WorkflowDefinitionController` 7 CRUD+deploy+execute endpoints at `/api/v1/workflows` not in spec | S9 | ✅ DONE — added to `docs/api/openapi.json` | Sprint 9 early start |
| P0-2 | `PUT /api/v1/alerts/{id}/resolve` not in spec | S9 | ✅ DONE — added to `docs/api/openapi.json` | Sprint 9 early start |
| P0-3 | `POST /api/v1/admin/sensors` not in spec | S9 | ✅ DONE — added to `docs/api/openapi.json` | Sprint 9 early start |

---

## P1 — Whole Modules Missing from Spec

### Module 1: Tenant Administration — 10 endpoints
**Controller**: `TenantAdminController.java`  
**Risk**: Highest-privilege surface; API gateway cannot enforce tenant isolation without spec

| ID | Endpoint | Method | Priority | Sprint | Status |
|---|---|---|---|---|---|
| SA-001 | `/api/v1/admin/tenants` | GET | P1-High | S10 | ✅ DONE (buffer) — added to spec; `TenantSummaryDto` schema added |
| SA-002 | `/api/v1/admin/tenants` | POST | P1-High | S10 | ✅ DONE (buffer) — added to spec; `CreateTenantRequest` schema added |
| SA-003 | `/api/v1/admin/tenants/{tenantId}/users` | GET | P1-High | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-004 | `/api/v1/admin/tenants/{tenantId}/users/invite` | POST | P1-High | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-005 | `/api/v1/admin/tenants/{tenantId}/users/{userId}/role` | PUT | P1-High | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-006 | `/api/v1/admin/tenants/{tenantId}/usage` | GET | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-007 | `/api/v1/admin/tenants/{tenantId}/settings` | GET | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-008 | `/api/v1/admin/tenants/{tenantId}/settings` | PUT | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-009 | `/api/v1/admin/tenants/{tenantId}/features` | GET | P1-Med | S10 | ✅ DONE (buffer) — added to spec; `UpdateFeatureRequest` schema added |
| SA-010 | `/api/v1/admin/tenants/{tenantId}/features` | PUT | P1-Med | S10 | ✅ DONE (buffer) — added to spec |

### Module 2: Building Management System — 7 endpoints
**Controllers**: `BmsDeviceController.java`, `BmsDeviceCommandController.java`  
**Risk**: Device command injection (`POST /{id}/commands`) has no documented auth/schema

| ID | Endpoint | Method | Priority | Sprint | Status |
|---|---|---|---|---|---|
| SA-011 | `/api/v1/bms/devices` | GET | P1-Med | S10 | ✅ DONE (buffer) — added to spec; `BmsDeviceResponse` schema added |
| SA-012 | `/api/v1/bms/devices` | POST | P1-High | S10 | ✅ DONE (buffer) — added to spec; `BmsDeviceRequest` schema added |
| SA-013 | `/api/v1/bms/devices/{id}` | GET | P1-Med | S10 | ✅ DONE (buffer) — added to spec |
| SA-014 | `/api/v1/bms/devices/{id}` | PUT | P1-Med | S10 | ✅ DONE (buffer) — added to spec |
| SA-015 | `/api/v1/bms/devices/{id}` | DELETE | P1-High | S10 | ✅ DONE (buffer) — added to spec |
| SA-016 | `/api/v1/bms/devices/discover` | POST | P1-High | S10 | ✅ DONE (buffer) — added to spec |
| SA-017 | `/api/v1/bms/devices/{id}/commands` | POST | **P0-SEC** | S9/S10 | ✅ DONE (buffer) — schema + OPERATOR auth documented in spec |

> ⚠️ **SA-017** escalated to P0-SEC: actuator command endpoint with no documented request schema or auth requirements.

### Module 3: Buildings — 6 endpoints
**Controllers**: `BuildingClusterController.java`, `BuildingSafetyController.java`

| ID | Endpoint | Method | Priority | Sprint | Status |
|---|---|---|---|---|---|
| SA-018 | `/api/v1/buildings` | GET | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-019 | `/api/v1/buildings` | POST | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-020 | `/api/v1/buildings/{buildingCode}` | GET | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-021 | `/api/v1/buildings/clusters/{clusterId}` | GET | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-022 | `/api/v1/buildings/{id}/safety` | GET | P1-Med | S10 | ✅ DONE (buffer) — added to spec; `SafetyScoreResponse` schema added |
| SA-023 | `/api/v1/buildings/{id}/vibration/readings` | GET | P1-Low | S10 | ✅ DONE (buffer) — added to spec; `VibrationReadingResponse` schema added |

### Module 4: Push Notifications — 5 endpoints
**Controller**: `PushSubscriptionController.java`

| ID | Endpoint | Method | Priority | Sprint | Status |
|---|---|---|---|---|---|
| SA-024 | `/api/v1/push/vapid-key` | GET | P1-Low | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-025 | `/api/v1/push/subscribe` | POST | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-026 | `/api/v1/push/subscriptions` | GET | P1-Low | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-027 | `/api/v1/push/subscriptions/{id}` | DELETE | P1-Low | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-028 | `/api/v1/push/test` | POST | P2 | S9 | ✅ DONE (buffer) — extracted to `PushTestController` with `@Profile("!production")` at class level; method-level `@Profile` was a no-op |

### Module 5: Forecast — 2 endpoints
**Controllers**: `ForecastController.java`, `ForecastCacheStatsController.java`

| ID | Endpoint | Method | Priority | Sprint | Status |
|---|---|---|---|---|---|
| SA-029 | `/api/v1/forecast/energy` | GET | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |
| SA-030 | `/api/v1/forecast/cache/stats` | GET | P1-Low | S10 | ✅ DONE — already in spec (confirmed buffer audit) |

### Module 6: Dashboard — 2 endpoints
**Controller**: `DashboardController.java`

| ID | Endpoint | Method | Priority | Sprint | Status |
|---|---|---|---|---|---|
| SA-031 | `/api/v1/dashboard` | GET | P1-High | S9 | ✅ DONE (buffer) — DashboardStats schema + path added to spec |
| SA-032 | `/api/v1/dashboard/stats` | GET | P1-High | S9 | ✅ DONE (buffer) — getDashboardStats operation added to spec |

### Module 7: Auth / Mobile / Analytics — 3 endpoints

| ID | Endpoint | Method | Controller | Priority | Sprint | Status |
|---|---|---|---|---|---|---|
| SA-033 | `/api/v1/auth/invite/accept` | POST | `InviteController` | P1-High | S9 | ✅ DONE (already in spec — confirmed in buffer) |
| SA-034 | `/api/v1/mobile/auth/config` | GET | `MobileAuthConfigController` | P1-Med | S10 | ✅ DONE (buffer) — added to spec |
| SA-035 | `/api/v1/analytics/cross-building/aggregate` | POST | `CrossBuildingAnalyticsController` | P1-Med | S10 | ✅ DONE — already in spec (confirmed buffer audit) |

---

## P1 — Undocumented Endpoints on Documented Modules

| ID | Endpoint | Controller | Gap | Priority | Sprint | Status |
|---|---|---|---|---|---|---|
| SA-036 | `POST /api/v1/esg/reports/pdf` | `EsgReportController` | PDF sync export; spec only has async generate+download | P1-High | S9 | ✅ DONE (buffer) — path added to spec, ADMIN+esg:write security |
| SA-037 | `GET /api/v1/alerts/stream` (SSE) | `AlertStreamController` | Duplicate of `/api/v1/notifications/stream`; ambiguous contract | P1-Med | S10 | ✅ DONE (buffer) — added to spec with SSE media type and deprecation note |

---

## P2 — Debug/Internal Endpoints in Production Code

| ID | Endpoint | Controller | Risk | Fix | Sprint | Status |
|---|---|---|---|---|---|---|
| SA-038 | `POST /api/v1/test/inject-reading` | `FloodTestController` | Synthetic sensor injection — bypasses IoT pipeline | Add `@Profile("!production")` | S9 | ✅ DONE (buffer) — `@Profile("!production")` applied |
| SA-039 | `POST /api/v1/test/inject-flood-alert` | `FloodTestController` | Synthetic flood alert — could trigger real notifications | Add `@Profile("!production")` | S9 | ✅ DONE (buffer) — `@Profile("!production")` applied |
| SA-040 | `GET /api/v1/internal/fake-traffic` | `FakeTrafficDataController` | Synthetic traffic data pollution | `@Profile("!production")` + `@Hidden` | S9 | ✅ DONE — `@Profile("!production")` added |

---

## P3 — Parameter / Response Mismatches

| ID | Endpoint | Gap | Fix | Sprint | Status |
|---|---|---|---|---|---|
| SA-041 | `GET /api/v1/alerts` | `module` query param in controller but not in spec | Add `module` param to spec | S9 | ✅ DONE (buffer) — `module` param added to spec |
| SA-042 | All 61 spec endpoints | Only `200` responses documented; no `201`, `204`, `400`, `401`, `403`, `404` | Add error responses to P0 endpoints first | S11+ | 🔴 OPEN |
| SA-043 | `POST /api/v1/citizen/meters` | Response code 200→201 mismatch; missing `security` on GET+POST | Fix response code + add `security: bearerAuth` to both GET and POST | S9 buffer | ✅ DONE — response 201, bearerAuth security added to GET+POST |

---

## Remediation Priority Order

1. **Sprint 9 buffer (DONE)**:
   - ✅ SA-017 (`POST /api/v1/bms/devices/{id}/commands`) — P0-SEC, BMS actuator auth + schema documented
   - ✅ SA-031/SA-032 (Dashboard endpoints + `DashboardStats` schema) — frontend contract complete
   - ✅ SA-033 (`/auth/invite/accept`) — already in spec (confirmed during buffer audit)
   - ✅ SA-036 (`POST /api/v1/esg/reports/pdf`) — path added, ADMIN+esg:write security
   - ✅ SA-038/SA-039 (`FloodTestController`) — `@Profile("!production")` applied
   - ✅ SA-028 (`POST /api/v1/push/test`) — extracted to `PushTestController` with class-level `@Profile("!production"); method-level @Profile was a no-op`
   - ✅ SA-041 (`module` param on alerts) — added to spec

2. **Sprint 10 buffer (DONE)**:
   - ✅ SA-001/002 (`GET/POST /admin/tenants`) — `TenantSummaryDto` + `CreateTenantRequest` schemas added
   - ✅ SA-003–008 (`/admin/tenants/{id}/users`, `/invite`, `/role`, `/usage`, `/settings`) — confirmed already in spec
   - ✅ SA-009/010 (`GET/PUT /admin/tenants/{id}/features`) — `UpdateFeatureRequest` schema added
   - ✅ SA-011–016 (BMS `/devices` CRUD + `/discover`) — `BmsDeviceRequest/Response` schemas added
   - ✅ SA-018–021 (`GET/POST /buildings`, `/{code}`, `/clusters/{id}`) — confirmed already in spec
   - ✅ SA-022/023 (`/{id}/safety`, `/{id}/vibration/readings`) — `SafetyScoreResponse` + `VibrationReadingResponse` schemas added
   - ✅ SA-024–027 (Push vapid/subscribe/subscriptions) — confirmed already in spec
   - ✅ SA-029/030 (Forecast energy/cache) — confirmed already in spec
   - ✅ SA-034 (`GET /mobile/auth/config`) — added to spec
   - ✅ SA-035 (`POST /analytics/cross-building/aggregate`) — confirmed already in spec
   - ✅ SA-037 (`GET /alerts/stream` SSE) — added with deprecation note
   - ✅ `packages/api-types/src/generated.ts` regenerated (0 TypeScript errors)

3. **Sprint 11+**:
   - SA-042 (Global error response coverage for all endpoints)
   - ✅ SA-043 (`POST /citizen/meters` — response 201 + bearerAuth security)

---

## Notes

- All changes to `docs/api/openapi.json` must be followed by running `npm run gen-api-types` and committing `packages/api-types/src/generated.ts`  
- CI workflow `.github/workflows/api-contract-check.yml` will reject PRs where types are out of sync  
- See ADR-039 for governance policy  
