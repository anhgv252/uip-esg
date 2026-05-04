# Sprint 3 — Manual & API Test Cases
**Sprint:** MVP2 Sprint 3 (May 26 – Jun 6, 2026)  
**Author:** QA Team  
**Date:** 2026-05-04  
**ADRs covered:** ADR-015 (Redis Cache), ADR-011 (Capability Flags), ADR-019 (Partner Theme), ADR-022 (Cache Warming), ADR-014 (Telemetry Enrichment)

---

## Backend API Test Cases

### TC-S3-BE-001 — Redis Health Check
| Field | Value |
|---|---|
| **Type** | API |
| **Story** | MVP2-21 (Redis Cache) |
| **Priority** | P0 |
| **Endpoint** | `GET /actuator/health` |
| **Auth** | Bearer token (any role) |
| **Steps** | 1. GET `/actuator/health` with valid JWT |
| **Expected** | `200 OK`, `components.redis.status == "UP"` |
| **Pass Criteria** | Redis health indicator present and UP |

---

### TC-S3-BE-002 — ESG Summary Cache Hit (Performance)
| Field | Value |
|---|---|
| **Type** | API |
| **Story** | MVP2-22 (CacheKeyBuilder) |
| **Priority** | P1 |
| **Endpoint** | `GET /api/v1/esg/summary` |
| **Auth** | Bearer token (esg:read) |
| **Steps** | 1. Call 1: `GET /esg/summary` — record response time<br>2. Call 2: `GET /esg/summary` — record response time |
| **Expected** | Call 2 response time < Call 1 (served from Redis cache) |
| **Pass Criteria** | Cache reduces latency on second call |

---

### TC-S3-BE-003 — ESG Energy Endpoint Returns 200
| Field | Value |
|---|---|
| **Type** | API Regression |
| **Story** | MVP2-22 (BT-22b fix) |
| **Priority** | P0 — BLOCKER |
| **Endpoint** | `GET /api/v1/esg/energy` |
| **Auth** | Bearer token (esg:read) |
| **Steps** | 1. `GET /api/v1/esg/energy` with admin token |
| **Expected** | `200 OK` with JSON array |
| **Pass Criteria** | No 500 Internal Server Error |
| **Note** | Currently FAILING — EsgService missing `tenantId` param (BT-22b) |

---

### TC-S3-BE-004 — ESG Carbon Endpoint Returns 200
| Field | Value |
|---|---|
| **Type** | API Regression |
| **Story** | MVP2-22 (BT-22b fix) |
| **Priority** | P0 — BLOCKER |
| **Endpoint** | `GET /api/v1/esg/carbon` |
| **Auth** | Bearer token (esg:read) |
| **Steps** | 1. `GET /api/v1/esg/carbon` with admin token |
| **Expected** | `200 OK` with JSON array |
| **Pass Criteria** | No 500 Internal Server Error |
| **Note** | Currently FAILING — same root cause as TC-S3-BE-003 |

---

### TC-S3-BE-005 — Environment Sensors Endpoint Returns 200
| Field | Value |
|---|---|
| **Type** | API Regression |
| **Story** | MVP2-11 (regression fix) |
| **Priority** | P1 |
| **Endpoint** | `GET /api/v1/environment/sensors` |
| **Auth** | Bearer token (environment:read) |
| **Steps** | 1. `GET /api/v1/environment/sensors` with admin token |
| **Expected** | `200 OK` with array of sensor objects |
| **Pass Criteria** | No 500 Internal Server Error |
| **Note** | Currently FAILING — 500 error returned |

---

### TC-S3-BE-006 — Tenant Config Capability Flags
| Field | Value |
|---|---|
| **Type** | API |
| **Story** | MVP2-31 (ADR-011 CapabilityProperties) |
| **Priority** | P1 |
| **Endpoint** | `GET /api/v1/tenant/config` |
| **Auth** | Bearer token (any role) |
| **Steps** | 1. `GET /api/v1/tenant/config` with valid JWT<br>2. Verify `features` object has expected module flags<br>3. Verify `branding` object present |
| **Expected** | `200 OK`, JSON with `tenantId`, `features.*enabled`, `branding.primaryColor` |
| **Pass Criteria** | All 6 modules (environment, esg, traffic, citizen, ai-workflow, city-ops) present with `enabled` flag |

---

### TC-S3-BE-007 — Scope-Gated ESG Report Generation (Admin has esg:write)
| Field | Value |
|---|---|
| **Type** | API / Scope-gate |
| **Story** | MVP2-32 (FE-08 scope gate) |
| **Priority** | P1 |
| **Endpoint** | `POST /api/v1/esg/reports/generate` |
| **Auth** | Bearer token (admin — has esg:write) |
| **Steps** | 1. POST with body `{"tenantId":"default","quarter":"2025-Q1"}` |
| **Expected** | `200/202` — report creation accepted |
| **Pass Criteria** | Non-4xx response for user with esg:write scope |

---

### TC-S3-BE-008 — Scope-Gated ESG Report Generation (Operator lacks esg:write)
| Field | Value |
|---|---|
| **Type** | API / Scope-gate |
| **Story** | MVP2-32 (ADR-011 scope enforcement) |
| **Priority** | P1 |
| **Endpoint** | `POST /api/v1/esg/reports/generate` |
| **Auth** | Bearer token (operator — has only esg:read, not esg:write) |
| **Steps** | 1. Login as `operator`<br>2. POST `/esg/reports/generate` with operator token |
| **Expected** | `403 Forbidden` |
| **Pass Criteria** | Backend enforces esg:write scope check |
| **Note** | Currently returning `202` — operator CAN generate reports; scope check may be missing at backend |
| **Bug** | BUG-BE-005 — Missing scope enforcement on POST /esg/reports/generate |

---

### TC-S3-BE-009 — Actuator Prometheus Restricted to Authenticated Users
| Field | Value |
|---|---|
| **Type** | Security |
| **Story** | MVP2-02 (Security) |
| **Priority** | P1 |
| **Endpoint** | `GET /actuator/prometheus` |
| **Auth** | None |
| **Steps** | 1. GET `/actuator/prometheus` without any token |
| **Expected** | `401 Unauthorized` |
| **Pass Criteria** | Prometheus metrics not publicly exposed |

---

### TC-S3-BE-010 — Cache TTL Isolation per Tenant
| Field | Value |
|---|---|
| **Type** | Integration |
| **Story** | MVP2-22 (ADR-015 cache key isolation) |
| **Priority** | P1 |
| **Steps** | 1. Login as Tenant A (`default`)<br>2. GET `/esg/summary`<br>3. Login as Tenant B (if available)<br>4. GET `/esg/summary`<br>5. Verify data is NOT shared between tenants |
| **Expected** | Cache keyed by `tenant_id`; cross-tenant data isolation maintained |
| **Pass Criteria** | Each tenant sees only their own data |

---

### TC-S3-BE-011 — TriggerConfig Cache: Cache Hit Serves from Redis
| Field | Value |
|---|---|
| **Type** | Integration |
| **Story** | MVP2-03b (TriggerConfigCacheService) |
| **Priority** | P0 — BLOCKER |
| **Test Class** | `TriggerConfigCacheServiceIT` |
| **Steps** | 1. Save TriggerConfig via repository<br>2. First call: `getByTopic()` — hits DB<br>3. Second call: `getByTopic()` — must be served from cache without DB call |
| **Expected** | Repository called only once for 2 identical requests |
| **Pass Criteria** | `@Cacheable` working for TriggerConfig |
| **Note** | Currently FAILING — `SerializationException: LocalDateTime not supported` |

---

### TC-S3-BE-012 — TriggerConfig Cache Evict Works
| Field | Value |
|---|---|
| **Type** | Integration |
| **Story** | MVP2-03b |
| **Priority** | P0 — BLOCKER |
| **Test Class** | `TriggerConfigCacheServiceIT` |
| **Steps** | 1. Cache a TriggerConfig<br>2. Call `evictAll()`<br>3. Next call hits DB again |
| **Expected** | After eviction, repository is called again |
| **Pass Criteria** | `@CacheEvict` properly clears cache |
| **Note** | Currently FAILING — same serialization error as TC-S3-BE-011 |

---

## Frontend Manual Test Cases

### TC-S3-FE-001 — Login Page Renders Correctly (ADR-019 Partner Theme)
| Field | Value |
|---|---|
| **Type** | Manual UI |
| **Story** | FE-07 (Partner Theme) |
| **Priority** | P1 |
| **Steps** | 1. Open `http://localhost:3000/`<br>2. Observe login page |
| **Expected** | UIP logo visible, "UIP Smart City" title, primary color `#1976D2` (blue), dark background |
| **Result** | ✅ PASS — Login page renders with correct branding |

---

### TC-S3-FE-002 — Dashboard Loads After Login (AppShell Navigation)
| Field | Value |
|---|---|
| **Type** | Manual UI |
| **Story** | FE-07 (AppShell + capability flags) |
| **Priority** | P0 |
| **Steps** | 1. Login as `admin`<br>2. Observe dashboard |
| **Expected** | Side nav shows: Dashboard, City Ops, Environment, ESG Metrics, Traffic, Alerts, Citizens, AI Workflows, Trigger Config, Admin |
| **Result** | ✅ PASS — All nav items visible, dashboard shows summary metrics (AQI: 105, Carbon: 19t) |

---

### TC-S3-FE-003 — ESG Metrics Page Loads (scope: esg:read)
| Field | Value |
|---|---|
| **Type** | Manual UI |
| **Story** | FE-08 (useScope) |
| **Priority** | P0 |
| **Steps** | 1. Login as `admin`<br>2. Navigate to ESG Metrics |
| **Expected** | Energy Consumption, Water Usage, Carbon Footprint cards shown<br>"Generate ESG Report" panel present with enabled button |
| **Result** | ✅ PASS — ESG page loads, metrics visible, Generate Report button enabled for admin |

---

### TC-S3-FE-004 — Generate ESG Report Button (useScope esg:write)
| Field | Value |
|---|---|
| **Type** | Manual UI / Scope gate |
| **Story** | FE-08 (useScope) / ADR-011 |
| **Priority** | P1 |
| **Steps** | 1. Login as `admin` (has esg:write)<br>2. Navigate to ESG Metrics<br>3. Observe "Generate Report" button state |
| **Expected** | Button is ENABLED; not disabled, no tooltip "You need esg:write scope" |
| **Result** | ✅ PASS — Generate Report button is enabled |

---

### TC-S3-FE-005 — Alert Management Page Loads
| Field | Value |
|---|---|
| **Type** | Manual UI |
| **Story** | FE-08 (useScope alert:ack) |
| **Priority** | P0 |
| **Steps** | 1. Login as `admin`<br>2. Navigate to Alerts |
| **Expected** | Alert list shows with Severity, Module, Sensor, Status columns<br>Acknowledge action available (admin has alert:ack scope) |
| **Result** | ✅ PASS — Alert Management shows 5 alerts (WARNING/CRITICAL), ESCALATED status, Action column present |

---

### TC-S3-FE-006 — ESG Trend Chart "No energy data" Message
| Field | Value |
|---|---|
| **Type** | Manual UI |
| **Story** | MVP2-22 |
| **Priority** | P2 |
| **Steps** | 1. Login as `admin`<br>2. Navigate to ESG Metrics<br>3. Observe "Trend by Building" chart area |
| **Expected** | Either chart data OR graceful "No energy data" empty state |
| **Result** | ✅ PASS — "No energy data" empty state shown gracefully (no crash) |

---

### TC-S3-FE-007 — Partner Theme Applied from TenantConfig branding
| Field | Value |
|---|---|
| **Type** | Manual UI / Integration |
| **Story** | FE-07 (ADR-019 partner theme + TenantConfigContext) |
| **Priority** | P1 |
| **Steps** | 1. Login as `admin`<br>2. Verify `GET /tenant/config` branding response: `partnerName: "UIP Smart City"`, `primaryColor: "#1976D2"`<br>3. Verify top-left logo/name shows "UIP Smart City" |
| **Expected** | App theme matches TenantConfig `branding.primaryColor` and `branding.partnerName` |
| **Result** | ✅ PASS — Branding applied; primary color #1976D2 matches sidebar and buttons |

---

### TC-S3-FE-008 — Token Not Persisted to localStorage (Security)
| Field | Value |
|---|---|
| **Type** | Security |
| **Story** | ADR-015 / Security |
| **Priority** | P1 |
| **Steps** | 1. Login as `admin`<br>2. Open DevTools → Application → localStorage<br>3. Check for `token` or `accessToken` keys |
| **Expected** | No JWT token in localStorage — in-memory only |
| **Pass Criteria** | localStorage has no token keys (XSS protection by design) |

---

### TC-S3-FE-009 — Session Lost on Full Page Reload (In-memory Token)
| Field | Value |
|---|---|
| **Type** | Manual UI |
| **Story** | ADR-015 / Security |
| **Priority** | P2 (by-design behavior) |
| **Steps** | 1. Login as `admin`<br>2. Navigate to Dashboard<br>3. Full page reload (F5) |
| **Expected** | Redirected to `/login` — in-memory token cleared on reload |
| **Result** | ✅ PASS (by design) — Token not in localStorage; session expires on reload |

---

### TC-S3-FE-010 — Dashboard Active Sensors Card Loading
| Field | Value |
|---|---|
| **Type** | Manual UI |
| **Story** | MVP2-11 (sensor data) |
| **Priority** | P1 |
| **Steps** | 1. Login as `admin`<br>2. Navigate to Dashboard<br>3. Observe "Active Sensors" card |
| **Expected** | A number shown (not spinner) |
| **Result** | ⚠️ PARTIAL — "Active Sensors" card shows spinner, never resolves (calls `/environment/sensors` which returns 500) |
| **Bug** | BUG-FE-001 — Active Sensors card stuck in loading state due to BUG-BE-001 |

---

## Edge Cases / Negative Tests

### TC-S3-BE-013 — Invalid JWT Token Rejected
| Field | Value |
|---|---|
| **Type** | Security |
| **Priority** | P0 |
| **Steps** | 1. Call `GET /api/v1/esg/summary` with `Authorization: Bearer invalid.token` |
| **Expected** | `401 Unauthorized` |

### TC-S3-BE-014 — Missing Authorization Header
| Field | Value |
|---|---|
| **Type** | Security |
| **Priority** | P0 |
| **Steps** | 1. Call `GET /api/v1/esg/summary` with no Authorization header |
| **Expected** | `401 Unauthorized` |
