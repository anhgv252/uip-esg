# Sprint 9 — API Contract Audit

**Date**: 2026-06-04  
**Auditor**: Solution Architect (automated)  
**Scope**: `backend/openapi.json` (spec v0.1.0) vs `@RestController` implementations in `backend/src/`  
**Method**: Python JSON parsing of openapi.json + grep extraction of all mapping annotations across 34 `@RestController` classes

---

## Executive Summary

| Category | Count |
|---|---|
| Spec endpoints (openapi.json) | **61** |
| Controller endpoints (implemented) | **110** |
| Spec endpoints with no implementation | **0** |
| Implemented endpoints missing from spec | **49** |
| Path-name mismatches (same function, different URL) | **1 (P0)** |
| Parameter mismatches (extra/missing params) | **3 (P1)** |
| Debug/test endpoints exposed in production code | **3 (P2)** |
| Whole modules absent from spec | **9 modules** |

**Risk level**: HIGH — 49 undocumented endpoints include security-sensitive operations (tenant admin, BMS device CRUD, auth invite flow) with no documented auth contract.

---

## P0 — Critical Gaps (immediate action required)

### P0-1: `WorkflowDefinitionController` registered at wrong path

| Item | Value |
|---|---|
| **Controller** | `WorkflowDefinitionController.java` |
| **Registered base path** | `/api/v1/workflows` |
| **Spec paths** | `/api/v1/workflow/definitions` (GET read-only), `/api/v1/admin/workflow-configs` (config CRUD) |
| **Impact** | 7 undocumented CRUD + action endpoints with no frontend contract |

The controller registers 7 endpoints at `/api/v1/workflows` (plural, no `/definitions` suffix):

```
POST   /api/v1/workflows              ← create workflow definition
GET    /api/v1/workflows              ← list workflow definitions
GET    /api/v1/workflows/{id}         ← get by id
PUT    /api/v1/workflows/{id}         ← update
DELETE /api/v1/workflows/{id}         ← delete
POST   /api/v1/workflows/{id}/deploy  ← deploy to engine
POST   /api/v1/workflows/{id}/execute ← trigger execution
```

The spec documents **read-only** access to definitions at `/api/v1/workflow/definitions` (GET list + GET XML) and configuration CRUD at `/api/v1/admin/workflow-configs`. This standalone CRUD + deploy/execute surface is completely absent from the spec.

**Consequence**: Frontend BPMN designer will call URLs that don't match any spec contract. Any API gateway URL allowlist will block these routes.

**Fix**: Either update the controller base path to match spec (`/api/v1/workflow/definitions`) and add missing endpoints, or add all 7 routes to the OpenAPI spec with proper schemas.

---

### P0-2: `PUT /api/v1/alerts/{id}/resolve` — undocumented alert action

| Item | Value |
|---|---|
| **Controller** | `AlertController.java` |
| **Method** | `PUT /api/v1/alerts/{id}/resolve` |
| **Spec** | Has `/acknowledge` (✓) and `/escalate` (✓) but NO `/resolve` |
| **Impact** | Alert lifecycle incomplete in spec; frontend UI cannot discover resolve action |

The spec models only two alert state transitions (`acknowledge`, `escalate`) but the controller implements a third (`resolve`). The alert state machine has a gap in its documented contract.

**Fix**: Add `PUT /api/v1/alerts/{id}/resolve` to openapi.json with `AlertEventDto` response schema.

---

### P0-3: `POST /api/v1/admin/sensors` — undocumented sensor creation

| Item | Value |
|---|---|
| **Controller** | `AdminController.java` |
| **Method** | `POST /api/v1/admin/sensors` |
| **Spec** | `GET /api/v1/admin/sensors` (✓), `PUT /api/v1/admin/sensors/{id}/status` (✓) — no POST |
| **Security risk** | No documented auth requirements; sensor creation is security-sensitive (affects data ingestion pipeline) |

**Fix**: Add to spec with `@PreAuthorize` role documented, or delete if sensors are managed via external IoT registry only.

---

## P1 — Whole Modules Missing from Spec

These modules are fully implemented in controllers but have **zero coverage** in `openapi.json`:

### Module: Tenant Administration (`/api/v1/admin/tenants`)

**Controller**: `TenantAdminController.java` — **10 endpoints**

```
GET    /api/v1/admin/tenants
POST   /api/v1/admin/tenants
GET    /api/v1/admin/tenants/{tenantId}/users
POST   /api/v1/admin/tenants/{tenantId}/users/invite
PUT    /api/v1/admin/tenants/{tenantId}/users/{userId}/role
GET    /api/v1/admin/tenants/{tenantId}/usage
GET    /api/v1/admin/tenants/{tenantId}/settings
PUT    /api/v1/admin/tenants/{tenantId}/settings
GET    /api/v1/admin/tenants/{tenantId}/features
PUT    /api/v1/admin/tenants/{tenantId}/features
```

**Risk**: Multi-tenant admin is the highest-privilege surface in the system. No documented auth contract means API gateway policy cannot enforce tenant isolation at the routing layer.

---

### Module: Building Management System (`/api/v1/bms/devices`)

**Controllers**: `BmsDeviceController.java`, `BmsDeviceCommandController.java` — **7 endpoints**

```
GET    /api/v1/bms/devices
POST   /api/v1/bms/devices
GET    /api/v1/bms/devices/{id}
PUT    /api/v1/bms/devices/{id}
DELETE /api/v1/bms/devices/{id}
POST   /api/v1/bms/devices/discover
POST   /api/v1/bms/devices/{id}/commands
```

**Risk**: Device command injection (`POST /{id}/commands`) is not in spec — no documented request schema or auth requirements for actuator control.

---

### Module: Buildings (`/api/v1/buildings`)

**Controllers**: `BuildingClusterController.java`, `BuildingSafetyController.java` — **6 endpoints**

```
GET    /api/v1/buildings
POST   /api/v1/buildings
GET    /api/v1/buildings/{buildingCode}
GET    /api/v1/buildings/clusters/{clusterId}
GET    /api/v1/buildings/{id}/safety
GET    /api/v1/buildings/{id}/vibration/readings
```

---

### Module: Push Notifications (`/api/v1/push`)

**Controller**: `PushSubscriptionController.java` — **5 endpoints**

```
GET    /api/v1/push/vapid-key
POST   /api/v1/push/subscribe
GET    /api/v1/push/subscriptions
DELETE /api/v1/push/subscriptions/{id}
POST   /api/v1/push/test
```

**Note**: `POST /api/v1/push/test` is a test-trigger endpoint that should be disabled in production.

---

### Module: Forecast (`/api/v1/forecast`)

**Controllers**: `ForecastController.java`, `ForecastCacheStatsController.java` — **2 endpoints**

```
GET    /api/v1/forecast/energy
GET    /api/v1/forecast/cache/stats
```

---

### Module: Dashboard (`/api/v1/dashboard`)

**Controller**: `DashboardController.java` — **2 endpoints**

```
GET    /api/v1/dashboard
GET    /api/v1/dashboard/stats
```

---

### Module: Auth — Invite Flow (`/api/v1/auth/invite`)

**Controller**: `InviteController.java` — **1 endpoint**

```
POST   /api/v1/auth/invite/accept
```

---

### Module: Mobile Auth Config (`/api/v1/mobile/auth`)

**Controller**: `MobileAuthConfigController.java` — **1 endpoint**

```
GET    /api/v1/mobile/auth/config
```

---

### Module: Cross-Building Analytics (`/api/v1/analytics/cross-building`)

**Controller**: `CrossBuildingAnalyticsController.java` — **1 endpoint**

```
POST   /api/v1/analytics/cross-building/aggregate
```

---

### Undocumented Endpoint on Documented Module

| Endpoint | Controller | Gap |
|---|---|---|
| `POST /api/v1/esg/reports/pdf` | `EsgReportController.java` | PDF export endpoint — spec has `generate` (async) + `status` + `download` but no dedicated `pdf` (sync) endpoint |
| `GET /api/v1/alerts/stream` (SSE) | `AlertStreamController.java` | Duplicate SSE stream — spec only documents `/api/v1/notifications/stream`; two live SSE endpoints serve the same concept |

---

## P2 — Debug/Internal Endpoints in Production Code

These endpoints are implemented in active `@RestController` classes and will be routed by Spring in all environments:

| Endpoint | Controller | Risk |
|---|---|---|
| `POST /api/v1/test/inject-reading` | `FloodTestController.java` | Allows synthetic sensor injection — bypasses IoT ingestion pipeline |
| `POST /api/v1/test/inject-flood-alert` | `FloodTestController.java` | Allows synthetic flood alert injection — could trigger real notifications |
| `GET /api/v1/internal/fake-traffic` | `FakeTrafficDataController.java` | Returns synthetic traffic data — could pollute dashboards if hit by accident |

**Fix**: Annotate with `@Profile("dev")` or `@Profile("!production")`, or move to a separate `test-support` module excluded from the production build.

---

## P3 — Parameter Mismatches

### GET /api/v1/alerts — extra `module` filter not in spec

| Item | Spec | Controller |
|---|---|---|
| Query params | `status`, `severity`, `from`, `to`, `page`, `size` | `status`, `severity`, **`module`**, `from`, `to`, `page`, `size` |

`AlertController.java` accepts a `module` filter that is absent from the OpenAPI spec. Clients cannot discover this filter.

---

### All responses documented as `200` only

The spec has `responses: {200: ...}` for every single endpoint — no `201` (created), `204` (no content), `400` (validation error), `401` (unauthenticated), `403` (forbidden), or `404` (not found) are documented. This makes error handling in clients non-deterministic.

**Priority** endpoints to add error responses to:
- `POST /api/v1/auth/login` — should document `401 Unauthorized`
- `PUT /api/v1/alerts/{id}/acknowledge` — should document `403 Forbidden`, `404 Not Found`
- `POST /api/v1/esg/reports/generate` — should document `400 Bad Request` (invalid period/year)
- `GET /api/v1/environment/sensors/{sensorId}/readings` — should document `404 Not Found`

---

### POST /api/v1/citizen/meters — params in spec vs controller

| Item | Spec | Note |
|---|---|---|
| Query params | `meterCode`, `meterType` | Should verify controller uses `@RequestParam` not `@RequestBody`; if body-based, spec schema is wrong |

---

## Compliance Matrix

| Controller | Spec Coverage | Status |
|---|---|---|
| `AdminController` | 5/6 endpoints documented | ⚠️ POST /admin/sensors missing |
| `ErrorRecordController` | 3/3 | ✅ |
| `WorkflowDefinitionController` | 0/7 | ❌ P0 — completely undocumented |
| `AlertController` | 4/5 | ⚠️ resolve missing |
| `AlertRuleController` | 3/3 | ✅ |
| `FloodTestController` | 0/2 | ❌ P2 — debug endpoints |
| `AuthController` | 3/3 | ✅ |
| `MobileAuthConfigController` | 0/1 | ❌ P1 |
| `BmsDeviceCommandController` | 0/1 | ❌ P1 |
| `BmsDeviceController` | 0/6 | ❌ P1 |
| `BuildingClusterController` | 0/4 | ❌ P1 |
| `CrossBuildingAnalyticsController` | 0/1 | ❌ P1 |
| `CitizenController` | 5/5 | ✅ |
| `InvoiceController` | 6/6 | ✅ |
| `HealthController` | 1/1 | ✅ |
| `DashboardController` | 0/2 | ❌ P1 |
| `EnvironmentController` | 4/4 | ✅ |
| `EsgController` | 6/6 | ✅ |
| `EsgReportController` | 0/1 | ⚠️ PDF endpoint undocumented |
| `ForecastCacheStatsController` | 0/1 | ❌ P1 |
| `ForecastController` | 0/1 | ❌ P1 |
| `AlertStreamController` | 0/1 | ⚠️ duplicate SSE |
| `NotificationController` | 1/1 | ✅ |
| `PushSubscriptionController` | 0/5 | ❌ P1 |
| `BuildingSafetyController` | 0/2 | ❌ P1 |
| `InviteController` | 0/1 | ❌ P1 |
| `TenantAdminController` | 0/10 | ❌ P1 — high-privilege module |
| `TenantConfigController` | 1/1 | ✅ |
| `FakeTrafficDataController` | 0/1 | ❌ P2 — debug endpoint |
| `TrafficController` | 5/5 | ✅ |
| `SimulateController` | 1/1 | ✅ |
| `WorkflowConfigController` | 7/7 | ✅ |
| `WorkflowController` | 5/5 | ✅ |
| `GenericRestTriggerController` | 1/1 | ✅ |

**Fully documented controllers**: 15 / 34  
**Partially documented**: 3 / 34  
**Completely undocumented**: 16 / 34

---

## Recommendations

### Sprint 9 (immediate)

1. **Fix WorkflowDefinitionController path** (P0-1) — Align `/api/v1/workflows` with spec or add all 7 endpoints to spec. Blocking for frontend BPMN designer integration.

2. **Add `PUT /api/v1/alerts/{id}/resolve`** (P0-2) — Alert state machine is broken without this. Add to spec; frontend Alert Management UI needs this action.

3. **Gate debug endpoints with `@Profile("!production")`** (P2) — `FloodTestController` and `FakeTrafficDataController` endpoints must not be reachable in UAT or production.

### Sprint 10 (documentation sprint)

4. **Document TenantAdminController** — 10 endpoints, highest-privilege surface. Required before any third-party operator onboarding.

5. **Document BMS module** — Device command endpoint needs security schema before BMS integration is handed to city building owners.

6. **Add error response codes** — At minimum: `401`, `403`, `404`, `400` on auth, alert, sensor, and ESG report endpoints.

### Backlog

7. **Document 9 remaining undocumented modules** — Buildings, Push, Forecast, Dashboard, Analytics, Invite, Mobile Auth, ESG PDF, Alert SSE stream.

8. **Resolve dual SSE stream endpoints** — Decide canonical URL (`/alerts/stream` vs `/notifications/stream`) and deprecate the other.

9. **Add `module` filter param to GET /api/v1/alerts** spec.

---

## Full Undocumented Endpoint Inventory

| # | Method | Path | Controller | Priority |
|---|---|---|---|---|
| 1 | POST | `/api/v1/admin/sensors` | AdminController | P0 |
| 2 | PUT | `/api/v1/alerts/{id}/resolve` | AlertController | P0 |
| 3 | POST | `/api/v1/workflows` | WorkflowDefinitionController | P0 |
| 4 | GET | `/api/v1/workflows` | WorkflowDefinitionController | P0 |
| 5 | GET | `/api/v1/workflows/{id}` | WorkflowDefinitionController | P0 |
| 6 | PUT | `/api/v1/workflows/{id}` | WorkflowDefinitionController | P0 |
| 7 | DELETE | `/api/v1/workflows/{id}` | WorkflowDefinitionController | P0 |
| 8 | POST | `/api/v1/workflows/{id}/deploy` | WorkflowDefinitionController | P0 |
| 9 | POST | `/api/v1/workflows/{id}/execute` | WorkflowDefinitionController | P0 |
| 10 | GET | `/api/v1/admin/tenants` | TenantAdminController | P1 |
| 11 | POST | `/api/v1/admin/tenants` | TenantAdminController | P1 |
| 12 | GET | `/api/v1/admin/tenants/{tenantId}/users` | TenantAdminController | P1 |
| 13 | POST | `/api/v1/admin/tenants/{tenantId}/users/invite` | TenantAdminController | P1 |
| 14 | PUT | `/api/v1/admin/tenants/{tenantId}/users/{userId}/role` | TenantAdminController | P1 |
| 15 | GET | `/api/v1/admin/tenants/{tenantId}/usage` | TenantAdminController | P1 |
| 16 | GET | `/api/v1/admin/tenants/{tenantId}/settings` | TenantAdminController | P1 |
| 17 | PUT | `/api/v1/admin/tenants/{tenantId}/settings` | TenantAdminController | P1 |
| 18 | GET | `/api/v1/admin/tenants/{tenantId}/features` | TenantAdminController | P1 |
| 19 | PUT | `/api/v1/admin/tenants/{tenantId}/features` | TenantAdminController | P1 |
| 20 | GET | `/api/v1/bms/devices` | BmsDeviceController | P1 |
| 21 | POST | `/api/v1/bms/devices` | BmsDeviceController | P1 |
| 22 | GET | `/api/v1/bms/devices/{id}` | BmsDeviceController | P1 |
| 23 | PUT | `/api/v1/bms/devices/{id}` | BmsDeviceController | P1 |
| 24 | DELETE | `/api/v1/bms/devices/{id}` | BmsDeviceController | P1 |
| 25 | POST | `/api/v1/bms/devices/discover` | BmsDeviceController | P1 |
| 26 | POST | `/api/v1/bms/devices/{id}/commands` | BmsDeviceCommandController | P1 |
| 27 | GET | `/api/v1/buildings` | BuildingClusterController | P1 |
| 28 | POST | `/api/v1/buildings` | BuildingClusterController | P1 |
| 29 | GET | `/api/v1/buildings/{buildingCode}` | BuildingClusterController | P1 |
| 30 | GET | `/api/v1/buildings/clusters/{clusterId}` | BuildingClusterController | P1 |
| 31 | GET | `/api/v1/buildings/{id}/safety` | BuildingSafetyController | P1 |
| 32 | GET | `/api/v1/buildings/{id}/vibration/readings` | BuildingSafetyController | P1 |
| 33 | GET | `/api/v1/push/vapid-key` | PushSubscriptionController | P1 |
| 34 | POST | `/api/v1/push/subscribe` | PushSubscriptionController | P1 |
| 35 | GET | `/api/v1/push/subscriptions` | PushSubscriptionController | P1 |
| 36 | DELETE | `/api/v1/push/subscriptions/{id}` | PushSubscriptionController | P1 |
| 37 | POST | `/api/v1/push/test` | PushSubscriptionController | P1/P2 |
| 38 | GET | `/api/v1/forecast/energy` | ForecastController | P1 |
| 39 | GET | `/api/v1/forecast/cache/stats` | ForecastCacheStatsController | P1 |
| 40 | GET | `/api/v1/dashboard` | DashboardController | P1 |
| 41 | GET | `/api/v1/dashboard/stats` | DashboardController | P1 |
| 42 | POST | `/api/v1/esg/reports/pdf` | EsgReportController | P1 |
| 43 | GET | `/api/v1/alerts/stream` | AlertStreamController | P1 |
| 44 | POST | `/api/v1/analytics/cross-building/aggregate` | CrossBuildingAnalyticsController | P1 |
| 45 | GET | `/api/v1/mobile/auth/config` | MobileAuthConfigController | P1 |
| 46 | POST | `/api/v1/auth/invite/accept` | InviteController | P1 |
| 47 | POST | `/api/v1/test/inject-reading` | FloodTestController | P2 |
| 48 | POST | `/api/v1/test/inject-flood-alert` | FloodTestController | P2 |
| 49 | GET | `/api/v1/internal/fake-traffic` | FakeTrafficDataController | P2 |
