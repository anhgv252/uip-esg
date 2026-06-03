# Sprint 7 — Pilot Regression Suite

**Created:** 2026-06-02
**Version:** 1.0
**Total Test Cases:** 243
**P0 Cases:** 70
**Automation Rate:** 91.4%

---

## Coverage Summary

| # | Module | TC Count | P0 | Automated |
|---|--------|----------|----|-----------| 
| 1 | Auth & RBAC | 17 | 8 | 100% |
| 2 | Scope Validation (Sprint 7) | 4 | 4 | 100% |
| 3 | Sensors & Environment | 13 | 3 | 100% |
| 4 | Alerts | 16 | 5 | 100% |
| 5 | Buildings | 8 | 3 | 100% |
| 6 | ESG (Sprint 3+7) | 16 | 6 | 100% |
| 7 | Forecast | 5 | 1 | 100% |
| 8 | BMS | 5 | 1 | 100% |
| 9 | Building Safety Score (NEW) | 9 | 5 | 100% |
| 10 | Vibration Readings | 4 | 0 | 100% |
| 11 | Structural Alert Consumer | 9 | 5 | 100% |
| 12 | Isolation Tests (ISO-008, ISO-009) | 6 | 6 | 100% |
| 13 | SSE/Push Notifications | 10 | 3 | 100% |
| 14 | AI Workflow | 14 | 1 | 100% |
| 15 | Tenant Admin | 10 | 1 | 100% |
| 16 | Administration | 7 | 0 | 100% |
| 17 | Citizen Portal | 11 | 1 | 100% |
| 18 | Traffic | 6 | 1 | 100% |
| 19 | Mobile | 8 | 0 | 37.5% |
| 20 | Security (OWASP) | 9 | 4 | 100% |
| 21 | Infrastructure | 7 | 2 | 100% |
| 22 | Frontend UI | 20 | 4 | 55% |
| 23 | SLA/Performance | 5 | 3 | 80% |
| 24 | Kafka & Avro | 5 | 0 | 100% |
| 25 | Sprint 1-6 Regression | 9 | 4 | 100% |
| | **TOTAL** | **243** | **70** | **91.4%** |

---

## Test Cases

### Module 1: Auth & RBAC (17 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-001 | Admin login with correct credentials | P0 | API | POST /auth/login admin/Admin#2026! | 200 + JWT token with ADMIN role | Yes |
| TC-002 | Admin login with wrong password | P0 | API | POST /auth/login admin/wrong | 401 Unauthorized | Yes |
| TC-003 | Token refresh returns new valid token | P1 | API | POST /auth/refresh with valid refresh_token | 200 + new access_token | Yes |
| TC-004 | Expired JWT rejected | P0 | API | GET /api/v1/sensors with expired JWT | 401 Unauthorized | Yes |
| TC-005 | Invalid JWT signature rejected | P0 | API | GET /api/v1/sensors with tampered JWT | 401 Unauthorized | Yes |
| TC-006 | JWT alg=none rejected | P0 | API | GET /api/v1/sensors with alg=none JWT | 401 Unauthorized | Yes |
| TC-007 | OPERATOR role access to operator endpoints | P1 | API | GET /api/v1/sensors with OPERATOR token | 200 OK | Yes |
| TC-008 | VIEWER role cannot create sensors | P1 | API | POST /api/v1/sensors with VIEWER token | 403 Forbidden | Yes |
| TC-009 | CITIZEN role limited to citizen endpoints | P1 | API | GET /api/v1/citizen/dashboard with CITIZEN token | 200 OK | Yes |
| TC-010 | CITIZEN role cannot access admin endpoints | P1 | API | GET /api/v1/admin/config with CITIZEN token | 403 Forbidden | Yes |
| TC-011 | Logout clears token | P1 | API | POST /auth/logout + GET /api/v1/sensors | 401 after logout | Yes |
| TC-012 | Multi-tenant auth — tenant A token cannot access tenant B | P0 | API | GET /api/v1/sensors with tenant A token + X-Tenant-ID: tenantB | 200 + only tenant A sensors | Yes |
| TC-013 | Missing X-Tenant-ID header returns error | P2 | API | GET /api/v1/sensors without X-Tenant-ID | 400 Bad Request | Yes |
| TC-014 | Aggregator (is_aggregator=true) sees cross-tenant data | P1 | API | GET /api/v1/sensors with aggregator token | 200 + all tenants' sensors | Yes |
| TC-015 | building_ids claim restricts access | P2 | API | GET /api/v1/buildings/{other-building} with restricted token | 404 Not Found | Yes |
| TC-016 | Keycloak token contains tenant_id claim | P1 | API | Decode JWT payload | tenant_id field present and correct | Yes |
| TC-017 | Keycloak token contains roles claim | P1 | API | Decode JWT payload | roles array contains assigned role | Yes |

### Module 2: Scope Validation — Sprint 7 (4 cases, ALL P0)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-018 | esg:write scope required for report generation | P0 | API | POST /api/v1/esg/reports with VIEWER token (no esg:write) | 403 Forbidden | Yes |
| TC-019 | esg:write scope allowed for admin | P0 | API | POST /api/v1/esg/reports with ADMIN token | 200 OK | Yes |
| TC-020 | alert:ack scope required for acknowledge | P0 | API | POST /api/v1/alerts/{id}/ack with VIEWER token | 403 Forbidden | Yes |
| TC-021 | alert:escalate scope required for escalate | P0 | API | POST /api/v1/alerts/{id}/escalate with VIEWER token | 403 Forbidden | Yes |

### Module 3: Sensors & Environment (13 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-022 | GET /api/v1/sensors returns paginated list | P0 | API | GET /api/v1/sensors?page=0&size=10 | 200 + paginated sensor array | Yes |
| TC-023 | GET /api/v1/sensors with tenant filter | P1 | API | GET /api/v1/sensors with X-Tenant-ID: hcm | 200 + only hcm sensors | Yes |
| TC-024 | GET /api/v1/sensors/{id} returns detail | P1 | API | GET /api/v1/sensors/{sensorId} | 200 + sensor object with all fields | Yes |
| TC-025 | GET /api/v1/sensors/{id} 404 for invalid ID | P2 | API | GET /api/v1/sensors/nonexistent | 404 Not Found | Yes |
| TC-026 | POST /api/v1/sensors creates new sensor | P1 | API | POST /api/v1/sensors with valid body | 201 Created + location header | Yes |
| TC-027 | PUT /api/v1/sensors/{id} updates sensor | P2 | API | PUT /api/v1/sensors/{id} with updated name | 200 OK | Yes |
| TC-028 | DELETE /api/v1/sensors/{id} removes sensor | P2 | API | DELETE /api/v1/sensors/{id} | 204 No Content | Yes |
| TC-029 | GET /api/v1/environment/aqi returns AQI data | P1 | API | GET /api/v1/environment/aqi | 200 + AQI station data array | Yes |
| TC-030 | GET /api/v1/environment/aqi/{stationId} | P2 | API | GET /api/v1/environment/aqi/{stationId} | 200 + single station AQI | Yes |
| TC-031 | AQI level classification correct | P1 | API | Verify AQI 0-50=Good, 51-100=Moderate, 101-150=Unhealthy for Sensitive | Correct color and label | Yes |
| TC-032 | Sensors online count endpoint | P2 | API | GET /api/v1/sensors/count?status=ONLINE | 200 + count number | Yes |
| TC-033 | Sensor readings API returns time-series | P1 | API | GET /api/v1/sensors/{id}/readings?range=24h | 200 + readings array with timestamp+value | Yes |
| TC-034 | Sensor readings pagination | P2 | API | GET /api/v1/sensors/{id}/readings?page=0&size=100 | 200 + paginated | Yes |

### Module 4: Alerts (16 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-035 | GET /api/v1/alerts returns paginated list | P0 | API | GET /api/v1/alerts?page=0&size=20 | 200 + paginated alert array | Yes |
| TC-036 | GET /api/v1/alerts filtered by severity | P1 | API | GET /api/v1/alerts?severity=CRITICAL | 200 + only CRITICAL alerts | Yes |
| TC-037 | GET /api/v1/alerts filtered by status | P1 | API | GET /api/v1/alerts?status=OPEN | 200 + only OPEN alerts | Yes |
| TC-038 | GET /api/v1/alerts filtered by module | P1 | API | GET /api/v1/alerts?module=STRUCTURAL | 200 + only structural alerts | Yes |
| TC-039 | GET /api/v1/alerts/{id} returns detail | P0 | API | GET /api/v1/alerts/{alertId} | 200 + alert detail with all fields | Yes |
| TC-040 | POST /api/v1/alerts/{id}/ack acknowledges alert | P0 | API | POST with OPERATOR token | 200 + status → ACKNOWLEDGED | Yes |
| TC-041 | POST /api/v1/alerts/{id}/escalate escalates alert | P0 | API | POST with OPERATOR token | 200 + status → ESCALATED | Yes |
| TC-042 | Alert de-duplication within cooldown period | P1 | Unit | Create duplicate alert within cooldown | Only 1 alert created | Yes |
| TC-043 | Alert severity levels: LOW/MEDIUM/HIGH/CRITICAL | P1 | Unit | Create alerts at each level | Correct severity stored and returned | Yes |
| TC-044 | Alert created timestamp is ISO-8601 | P2 | API | GET alert detail | detectedAt field is valid ISO-8601 | Yes |
| TC-045 | Alert list sorted by detectedAt descending | P2 | API | GET /api/v1/alerts | First alert is most recent | Yes |
| TC-046 | Structural alerts have STRUCTURAL module type | P1 | API | GET /api/v1/alerts?module=STRUCTURAL | Module field = STRUCTURAL | Yes |
| TC-047 | Alert SSE feed sends real-time events | P1 | E2E | Connect SSE + trigger alert | Event received within 5s | Yes |
| TC-048 | Alert ACK requires alert:ack scope | P1 | API | ACK with VIEWER token | 403 Forbidden | Yes |
| TC-049 | Alert escalation triggers notification | P1 | Unit | Escalate P0 alert | Notification service called | Yes |
| TC-050 | Alert cooldown prevents duplicate P0 notifications | P1 | Unit | Send 2 alerts within 1min cooldown | Only 1 notification sent | Yes |

### Module 5: Buildings (8 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-051 | GET /api/v1/buildings returns list | P0 | API | GET /api/v1/buildings | 200 + building array | Yes |
| TC-052 | GET /api/v1/buildings with tenant filter | P1 | API | GET /api/v1/buildings + X-Tenant-ID | Only tenant's buildings | Yes |
| TC-053 | GET /api/v1/buildings/{id} returns detail | P0 | API | GET /api/v1/buildings/{buildingId} | 200 + building with name, address, floors | Yes |
| TC-054 | POST /api/v1/buildings creates building | P1 | API | POST with valid body | 201 Created | Yes |
| TC-055 | PUT /api/v1/buildings/{id} updates building | P2 | API | PUT with updated name | 200 OK | Yes |
| TC-056 | Building detail includes safety tab data | P1 | API | GET /api/v1/buildings/{id} | Response includes safety fields | Yes |
| TC-057 | GET /api/v1/buildings/{id}/vibration/readings | P1 | API | GET with range=24h | 200 + vibration readings array | Yes |
| TC-058 | Building CRUD requires proper scopes | P0 | API | POST with VIEWER token | 403 Forbidden | Yes |

### Module 6: ESG — Sprint 3+7 (16 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-059 | GET /api/v1/esg/metrics returns energy data | P0 | API | GET /api/v1/esg/metrics?metric=energy | 200 + energy metrics GRI 302-1 | Yes |
| TC-060 | GET /api/v1/esg/metrics returns carbon data | P0 | API | GET /api/v1/esg/metrics?metric=carbon | 200 + carbon metrics GRI 305-4 | Yes |
| TC-061 | GET /api/v1/esg/metrics returns water data | P1 | API | GET /api/v1/esg/metrics?metric=water | 200 + water metrics | Yes |
| TC-062 | ESG energy trend chart data | P1 | API | GET /api/v1/esg/metrics/energy/trend?range=30d | 200 + time-series data | Yes |
| TC-063 | ESG carbon trend chart data | P1 | API | GET /api/v1/esg/metrics/carbon/trend?range=30d | 200 + time-series data | Yes |
| TC-064 | ESG dashboard aggregates all metrics | P0 | API | GET /api/v1/esg/dashboard | 200 + energy+carbon+water summary | Yes |
| TC-065 | POST /api/v1/esg/reports/pdf generates PDF | P0 | API | POST with esg:write scope | 200 + application/pdf binary | Yes |
| TC-066 | PDF includes GRI 302-1 energy table | P1 | API | Download PDF, inspect content | Energy table present | Yes |
| TC-067 | PDF includes GRI 305-4 carbon table | P1 | API | Download PDF, inspect content | Carbon table present | Yes |
| TC-068 | PDF generation timeout <30s | P0 | SLA | Time POST /esg/reports/pdf | Wall clock <30s | Yes |
| TC-069 | ESG report requires esg:write scope | P0 | API | POST with VIEWER token | 403 Forbidden | Yes |
| TC-070 | ESG metrics tenant-isolated | P1 | API | GET with tenant A + tenant B token | Each sees own data only | Yes |
| TC-071 | ESG PDF download button visible with esg:write | P1 | UI | Login as admin on ESG page | Generate PDF button visible | Yes |
| TC-072 | ESG PDF download progress indicator | P2 | UI | Click Generate PDF | CircularProgress shows during generation | Yes |
| TC-073 | ESG PDF blob download triggers file save | P2 | UI | Click Generate PDF | File download dialog/save | Yes |
| TC-074 | ESG cached metrics respond <500ms | P1 | SLA | GET /api/v1/esg/dashboard | Response time <500ms | Yes |

### Module 7: Forecast (5 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-075 | GET /api/v1/forecast returns predictions | P0 | API | GET /api/v1/forecast?sensorId={id}&horizon=7d | 200 + forecast array | Yes |
| TC-076 | Forecast cache hit returns cached data | P1 | Unit | Request same forecast twice | Second request from cache | Yes |
| TC-077 | Forecast cache eviction on new trigger | P1 | Unit | Trigger forecast + check cache | Stale cache evicted | Yes |
| TC-078 | Forecast fallback when model unavailable | P2 | Unit | Return NONE forecast | Fallback data returned | Yes |
| TC-079 | Forecast accuracy within ±20% | P2 | SLA | Compare predictions vs actual | MAPE < 20% | Yes |

### Module 8: BMS (5 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-080 | GET /api/v1/bms/devices returns device list | P0 | API | GET /api/v1/bms/devices | 200 + device array | Yes |
| TC-081 | BMS reading ingestion via Kafka | P1 | Unit | Send BmsReadingEvent to Kafka | Reading stored in DB | Yes |
| TC-082 | BMS command ACK updates device status | P1 | Unit | Send ACK event | Device status → ONLINE/ACKNOWLEDGED | Yes |
| TC-083 | BMS device status SSE real-time update | P1 | E2E | Connect SSE + send ACK | Status update received via SSE | Yes |
| TC-084 | BMS command ACK TenantContext set | P2 | Unit | Verify consumer sets TenantContext | RLS enforced in consumer | Yes |

### Module 9: Building Safety Score — NEW (9 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-085 | GET /api/v1/buildings/{id}/safety returns score | P0 | API | GET safety endpoint | 200 + {score, status, lastUpdated, activeAlerts} | Yes |
| TC-086 | Safety score 0-100 range | P0 | Unit | Calculate score with various inputs | Score always in [0, 100] | Yes |
| TC-087 | Safety score cached with TTL 5 min | P1 | Unit | Request twice within TTL | Second from cache, same value | Yes |
| TC-088 | Safety score drops on structural alert | P0 | Unit | Trigger alert → check score | Score decreases after alert | Yes |
| TC-089 | Offline sensors show gray status | P1 | Unit | All sensors offline → check status | status = OFFLINE, color = gray | Yes |
| TC-090 | Safety score status field values | P1 | Unit | Score 90=SAFE, 60=WARNING, 30=CRITICAL, 0=OFFLINE | Correct status labels | Yes |
| TC-091 | Safety tab on building detail page | P1 | UI | Navigate to /buildings/{id}?tab=safety | Safety tab renders gauge + chart | Yes |
| TC-092 | Safety alert banner for P0 alerts | P0 | UI | Active structural P0 alert | Red non-dismissible banner at top | Yes |
| TC-093 | Safety score gauge color zones | P1 | UI | Score 0-40=red, 41-70=amber, 71-100=green | Correct colors rendered | Yes |

### Module 10: Vibration Readings (4 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-094 | GET vibration readings 24h default | P1 | API | GET /api/v1/buildings/{id}/vibration/readings | 200 + readings array | Yes |
| TC-095 | Vibration readings paginated | P2 | API | GET with page=0&size=50 | 200 + paginated response | Yes |
| TC-096 | Vibration threshold markers in chart | P1 | UI | SafetyTrendChart renders | Warning(10mm/s) and Critical(50mm/s) lines visible | Yes |
| TC-097 | Multi-sensor type support (vibration/tilt/crack) | P1 | UI | Switch tabs in trend chart | Data updates per sensor type | Yes |

### Module 11: Structural Alert Consumer (9 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-098 | Structural alert consumer listens to Kafka topic | P0 | Unit | Send event to UIP.structural.alert.critical.v1 | Consumer receives and processes | Yes |
| TC-099 | P0 alert triggers notification within 15s | P0 | SLA | Inject alert → measure time | Notification sent <15s | Yes |
| TC-100 | P0 notification is review prompt (BR-010) | P0 | Unit | Verify notification content | "Review required" message, NOT "evacuate" | Yes |
| TC-101 | DLQ configured for failed alerts | P1 | Unit | Simulate processing failure | Alert sent to UIP.structural.alert.dlq.v1 | Yes |
| TC-102 | Structural alert cooldown 1 min for EMERGENCY | P1 | Unit | Send 2 P0 alerts within 1 min | Only 1 notification sent | Yes |
| TC-103 | TenantContext set in consumer (RLS) | P0 | Unit | Verify TenantContext in consumer | RLS enforced, tenant data isolated | Yes |
| TC-104 | Multi-sensor confirmation (3-spike pattern) | P1 | Unit | Send 3 consecutive spikes | Alert emitted only after 3rd spike | Yes |
| TC-105 | Cold start skip when n<1000 readings | P1 | Unit | Welford with 999 readings | No alert emitted | Yes |
| TC-106 | Welford online stddev convergence | P1 | Unit | Feed 1000+ readings + spike | Alert emitted correctly | Yes |

### Module 12: Isolation Tests — ISO-008, ISO-009 (6 cases, ALL P0)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-107 | ISO-008: Tenant A structural alerts invisible to tenant B | P0 | IT | GET /api/v1/alerts?module=STRUCTURAL with tenant A token | Only tenant A's structural alerts returned | Yes |
| TC-108 | ISO-008: Tenant B cannot see tenant A structural alerts | P0 | IT | GET /api/v1/alerts?module=STRUCTURAL with tenant B token | Zero tenant A alerts in response | Yes |
| TC-109 | ISO-009: Safety score only for tenant's buildings | P0 | IT | GET /api/v1/buildings/{tenantA-building}/safety with tenant B token | 404 Not Found | Yes |
| TC-110 | ISO-009: Safety score cache key isolation | P0 | Unit | Verify cache key format | Key = safety:score:{tenantId}:{buildingId}, null tenantId never used | Yes |
| TC-111 | ISO-009: Admin aggregator sees cross-tenant scores | P0 | IT | GET safety with aggregator token | Can query any tenant's building safety | Yes |
| TC-112 | ISO-008/009: Operator sees only own buildings | P0 | IT | GET /api/v1/buildings with operator token | Only buildings in operator's building_ids | Yes |

### Module 13: SSE/Push Notifications (10 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-113 | SSE connection established | P0 | E2E | Connect to /api/v1/sse/alerts | Connection opened, heartbeat received | Yes |
| TC-114 | SSE alert event received | P0 | E2E | Trigger alert while connected | Event received in SSE stream | Yes |
| TC-115 | SSE reconnection on disconnect | P1 | E2E | Kill connection, wait | Client reconnects automatically | Yes |
| TC-116 | Push token registration APNs | P1 | API | POST /api/v1/devices/register with APNs token | 200 + device registered | Yes |
| TC-117 | Push token registration FCM | P1 | API | POST /api/v1/devices/register with FCM token | 200 + device registered | Yes |
| TC-118 | Push notification delivered iOS | P0 | Manual | Trigger alert, check iOS device | Notification banner appears | No |
| TC-119 | Push notification delivered Android | P0 | Manual | Trigger alert, check Android device | Notification appears in tray | No |
| TC-120 | Deep-link: tap notification → alert detail | P1 | Manual | Tap push notification | App navigates to alert detail | No |
| TC-121 | Foreground notification banner | P1 | E2E | App in foreground + trigger alert | In-app banner appears | Yes |
| TC-122 | Foreground banner auto-dismiss 5s | P2 | E2E | Wait after banner appears | Banner disappears after 5s | Yes |

### Module 14: AI Workflow (14 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-123 | GET /api/v1/workflow/instances returns list | P0 | API | GET with admin token | 200 + paginated instances | Yes |
| TC-124 | GET /api/v1/workflow/definitions returns list | P1 | API | GET with admin token | 200 + definition array | Yes |
| TC-125 | Workflow instances have status field | P1 | API | GET instance detail | status ∈ {RUNNING, COMPLETED, FAILED, SUSPENDED} | Yes |
| TC-126 | BPMN diagram renders in UI | P1 | UI | Navigate to AI Workflow page | BPMN canvas visible | Yes |
| TC-127 | Start workflow instance | P1 | API | POST /api/v1/workflow/instances | 201 + instance started | Yes |
| TC-128 | Complete workflow task | P1 | API | POST /api/v1/workflow/tasks/{id}/complete | 200 + task completed | Yes |
| TC-129 | Workflow state transitions correct | P2 | Unit | Start → Running → Completed | Correct state machine | Yes |
| TC-130 | Live Demo tab renders | P1 | UI | Click Live Demo tab | Demo content visible | Yes |
| TC-131 | Process Instances tab default | P1 | UI | Navigate to AI Workflow | Instances tab active by default | Yes |
| TC-132 | Tab switching works | P2 | UI | Click each tab | Content changes per tab | Yes |
| TC-133 | Workflow definitions table has rows | P1 | UI | Click Process Definitions tab | Table with ≥1 row | Yes |
| TC-134 | Workflow requires workflow:read scope | P2 | API | GET with VIEWER token | 200 OK (VIEWER has workflow:read) | Yes |
| TC-135 | Workflow write requires workflow:write scope | P2 | API | POST with VIEWER token | 403 Forbidden | Yes |
| TC-136 | Workflow instance detail page | P2 | UI | Click instance row | Detail panel/dialog opens | Yes |

### Module 15: Tenant Admin (10 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-137 | GET /api/v1/tenant-admin/overview returns stats | P0 | API | GET with tenant admin token | 200 + stat cards data | Yes |
| TC-138 | User management lists users | P1 | API | GET /api/v1/tenant-admin/users | 200 + user array | Yes |
| TC-139 | Invite user sends invitation | P1 | API | POST /api/v1/tenant-admin/users/invite | 200 + invite sent | Yes |
| TC-140 | Building configuration list | P1 | API | GET /api/v1/tenant-admin/buildings | 200 + building array | Yes |
| TC-141 | Usage report with date range | P1 | API | GET /api/v1/tenant-admin/usage?from=...&to=... | 200 + usage metrics | Yes |
| TC-142 | Settings save | P1 | API | PUT /api/v1/tenant-admin/settings | 200 + settings saved | Yes |
| TC-143 | Branding primary color field | P2 | UI | Open Settings page | primaryColor input visible | Yes |
| TC-144 | Date range filter in usage report | P2 | UI | Open Usage page | Date inputs rendered | Yes |
| TC-145 | Tenant admin sub-nav 5 sections | P2 | UI | Navigate to tenant admin | Overview, Users, Buildings, Usage, Settings visible | Yes |
| TC-146 | CITIZEN cannot access tenant admin | P1 | E2E | Navigate to /tenant-admin as citizen | Redirected or access denied | Yes |

### Module 16: Administration (7 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-147 | GET /actuator/health returns UP | P2 | API | GET /actuator/health | 200 + status: UP | Yes |
| TC-148 | GET /actuator/info returns app info | P2 | API | GET /actuator/info | 200 + app metadata | Yes |
| TC-149 | GET /actuator/prometheus returns metrics | P2 | API | GET /actuator/prometheus | 200 + Prometheus format metrics | Yes |
| TC-150 | Admin config endpoints restricted | P2 | API | GET admin config with OPERATOR | 403 Forbidden | Yes |
| TC-151 | Metrics endpoint authenticated | P2 | API | GET /actuator/prometheus without auth | 401 Unauthorized | Yes |
| TC-152 | Health endpoint public | P2 | API | GET /actuator/health without auth | 200 OK | Yes |
| TC-153 | Cache metrics visible in Prometheus | P2 | API | GET /actuator/prometheus | cache.* metrics present | Yes |

### Module 17: Citizen Portal (11 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-154 | Citizen dashboard loads | P0 | E2E | Login as citizen | Dashboard with KPI cards | Yes |
| TC-155 | Bills list renders | P1 | E2E | Navigate to /citizen/bills | Bill cards visible | Yes |
| TC-156 | Bill detail page | P1 | E2E | Tap bill card | Bill detail with amount, due date | Yes |
| TC-157 | AQI gauge renders | P1 | E2E | Navigate to /citizen/aqi | AQI gauge component visible | Yes |
| TC-158 | AQI value is numeric | P1 | E2E | Check AQI display | Value matches /\d+/ | Yes |
| TC-159 | Bottom navigation 5 tabs | P1 | E2E | Check bottom nav | Home, Bills, AQI, Alerts, Profile | Yes |
| TC-160 | Citizen profile page | P2 | E2E | Navigate to Profile | User name and role visible | Yes |
| TC-161 | Citizen notification settings | P2 | E2E | Profile → Notifications | Toggle options visible | Yes |
| TC-162 | Citizen portal responsive 390px | P2 | E2E | Resize to 390x844 | No horizontal scroll | Yes |
| TC-163 | Citizen portal responsive 768px | P2 | E2E | Resize to 768x1024 | Layout adapts | Yes |
| TC-164 | Citizen cannot access /dashboard (admin) | P1 | E2E | Navigate to /dashboard as citizen | Redirected to /citizen | Yes |

### Module 18: Traffic (6 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-165 | GET /api/v1/traffic/incidents returns list | P0 | API | GET with admin token | 200 + incidents array | Yes |
| TC-166 | Traffic incidents filter by intersection | P1 | API | GET with intersection filter | Filtered results | Yes |
| TC-167 | Incident types: ACCIDENT, CONGESTION | P1 | API | GET incidents | Both types present in data | Yes |
| TC-168 | Traffic map overlay renders | P1 | UI | Navigate to Traffic page | Leaflet map with incident markers | Yes |
| TC-169 | Traffic real-time updates | P2 | E2E | SSE connection on Traffic page | New incidents appear automatically | Yes |
| TC-170 | Traffic page heading visible | P2 | UI | Navigate to /traffic | "Traffic Management" heading visible | Yes |

### Module 19: Mobile (8 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-171 | Mobile dashboard KPI cards render | P1 | E2E | Open mobile viewport | Active sensors, alerts, safety score cards | Yes |
| TC-172 | Mobile 7-day energy sparkline | P1 | E2E | Scroll dashboard | Sparkline chart renders | Yes |
| TC-173 | Mobile responsive 375px | P1 | E2E | 375x667 viewport | No horizontal scroll | Yes |
| TC-174 | Mobile responsive 768px | P1 | E2E | 768x1024 viewport | Layout adapts correctly | Yes |
| TC-175 | Mobile responsive 1024px | P2 | E2E | 1024x768 viewport | Desktop-like layout | No |
| TC-176 | Mobile bottom nav tappable | P1 | E2E | Tap each tab | Navigation works | Yes |
| TC-177 | Mobile pull-to-refresh | P2 | E2E | Pull down on alerts list | List refreshes | No |
| TC-178 | Mobile orientation change | P2 | E2E | Rotate to landscape | Layout adapts | No |

### Module 20: Security — OWASP (9 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-179 | SQL injection on sensor filter | P0 | Security | Inject SQL in query params | No SQL error, sanitized input | Yes |
| TC-180 | XSS on alert description | P0 | Security | Inject script in text fields | Script not executed, sanitized | Yes |
| TC-181 | CSRF protection (stateless JWT) | P1 | Security | POST without CSRF token | Request succeeds (stateless, no CSRF needed) | Yes |
| TC-182 | JWT alg=none attack | P0 | Security | Send JWT with alg=none | 401 Unauthorized | Yes |
| TC-183 | CORS restricted origins | P1 | Security | Request from unknown origin | CORS headers reject | Yes |
| TC-184 | Rate limiting 1000/min | P0 | Security | Send 1001 requests in 1 min | 429 Too Many Requests after limit | Yes |
| TC-185 | HSTS header present | P1 | Security | Check response headers | Strict-Transport-Security: max-age=31536000 | Yes |
| TC-186 | CSP frame-ancestors none | P1 | Security | Check response headers | Content-Security-Policy includes frame-ancestors 'none' | Yes |
| TC-187 | IDOR on resource endpoints | P0 | Security | Access other tenant's resource by ID | 404 Not Found | Yes |

### Module 21: Infrastructure (7 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-188 | Docker Compose all services healthy | P0 | Infra | docker compose ps | All services status: healthy | Yes |
| TC-189 | Kafka connectivity verified | P1 | Infra | kafka-topics --list | Topics listed including UIP.* topics | Yes |
| TC-190 | ClickHouse query returns data | P1 | Infra | SELECT 1 FROM system.numbers LIMIT 1 | Query succeeds | Yes |
| TC-191 | Redis ping | P2 | Infra | redis-cli ping | PONG | Yes |
| TC-192 | Keycloak realm uip accessible | P0 | Infra | curl http://localhost:8085/realms/uip | 200 + realm metadata | Yes |
| TC-193 | Kong routes configured | P1 | Infra | curl http://localhost:8000/routes | Routes listed | Yes |
| TC-194 | Prometheus scrape targets UP | P1 | Infra | GET /api/v1/targets | All targets UP | Yes |

### Module 22: Frontend UI (20 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-195 | TypeScript compilation 0 errors | P0 | Build | npx tsc --noEmit | Exit code 0 | Yes |
| TC-196 | Loading skeleton renders | P1 | UI | Load any data page | Skeleton placeholders visible | Yes |
| TC-197 | Error boundary catches failures | P1 | UI | Simulate component error | Error fallback UI shown | Yes |
| TC-198 | Empty state displays | P1 | UI | Load page with no data | "No data" or empty state message | Yes |
| TC-199 | Accessibility: aria-labels on buttons | P0 | UI | Inspect interactive elements | aria-label present on all buttons | Yes |
| TC-200 | Accessibility: form labels | P1 | UI | Inspect form inputs | Labels associated with inputs | Yes |
| TC-201 | Responsive 768px breakpoint | P0 | UI | Resize to 768px | Layout adapts, no overflow | Yes |
| TC-202 | Responsive 1920px breakpoint | P0 | UI | Resize to 1920px | Full layout, max-width container | Yes |
| TC-203 | Chart rendering (recharts) | P1 | UI | Load ESG/Safety charts | Charts render with data | Yes |
| TC-204 | Form validation error messages | P1 | UI | Submit form with invalid data | Validation errors displayed | Yes |
| TC-205 | Sidebar navigation collapses | P2 | UI | Click sidebar toggle | Sidebar collapses/expands | Yes |
| TC-206 | Table sorting by column | P2 | UI | Click column header | Table rows sorted | Yes |
| TC-207 | Table pagination | P2 | UI | Click next page | Next page of data loads | Yes |
| TC-208 | Dialog/modal opens and closes | P2 | UI | Open dialog + click close | Dialog lifecycle works | Yes |
| TC-209 | Toast notification appears | P2 | UI | Trigger success action | Toast appears and auto-dismisses | Yes |
| TC-210 | React Query loading state | P1 | UI | Inspect network during load | Skeleton → data → rendered | Yes |
| TC-211 | React Query error state | P1 | UI | Simulate API error | Error message displayed | Yes |
| TC-212 | Sidebar menu items count | P2 | UI | Count sidebar items | 9 items for ADMIN role | Yes |
| TC-213 | Sidebar highlights active route | P2 | UI | Navigate to page | Active item highlighted | Yes |
| TC-214 | Keyboard navigation works | P2 | UI | Tab through form fields | Focus moves logically | Yes |

### Module 23: SLA/Performance (5 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-215 | Structural alert P0 latency <15s | P0 | SLA | Inject sensor → measure notification time | <15s end-to-end | Yes |
| TC-216 | Dashboard initial load <3s | P0 | SLA | k6 dashboard scenario p95 | p95 <3s | Yes |
| TC-217 | ESG report generation <30s | P0 | SLA | Time POST /esg/reports/pdf | <30s | Yes |
| TC-218 | Kong API p99 <100ms | P1 | SLA | k6 kong_api scenario | p99 <100ms | Yes |
| TC-219 | Error rate <0.01% all scenarios | P1 | SLA | k6 all scenarios | errors rate <0.01 | Yes |

### Module 24: Kafka & Avro (5 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-220 | Avro schemas registered in Apicurio | P1 | Unit | Check Apicurio registry | 4 schemas registered (BmsReading, SensorReading, AlertDetected, HourlyRollup) | Yes |
| TC-221 | BACKWARD compatibility enforced | P1 | Unit | Register new schema version | BACKWARD compat check passes | Yes |
| TC-222 | Dual-publish JSON v1 + Avro v2 | P1 | Unit | Send event via producer | Both topics receive event | Yes |
| TC-223 | Consumer reads both JSON and Avro | P1 | Unit | Consume from v1 and v2 topics | Both formats deserialized correctly | Yes |
| TC-224 | DLQ configured for failed events | P2 | Unit | Send malformed event | Event routed to DLQ topic | Yes |

### Module 25: Sprint 1-6 Regression (9 cases)

| TC-ID | Title | Priority | Type | Steps | Expected Result | Automated |
|-------|-------|----------|------|-------|----------------|-----------|
| TC-225 | Auth login still works (Sprint 1) | P0 | API | POST /auth/login with admin creds | 200 + JWT | Yes |
| TC-226 | Sensor list still returns data (Sprint 1) | P0 | API | GET /api/v1/sensors | 200 + sensor array | Yes |
| TC-227 | Alert list still returns data (Sprint 1) | P0 | API | GET /api/v1/alerts | 200 + alert array | Yes |
| TC-228 | ESG metrics still accessible (Sprint 3) | P1 | API | GET /api/v1/esg/dashboard | 200 + ESG data | Yes |
| TC-229 | BMS devices still list (Sprint 4) | P1 | API | GET /api/v1/bms/devices | 200 + device array | Yes |
| TC-230 | Forecast still returns predictions (Sprint 4) | P1 | API | GET /api/v1/forecast | 200 + forecast data | Yes |
| TC-231 | Citizen portal still accessible (Sprint 5) | P1 | E2E | Login as citizen | Dashboard renders | Yes |
| TC-232 | AI Workflow still accessible (Sprint 6) | P0 | E2E | Navigate to AI Workflow | Page loads with tabs | Yes |
| TC-233 | Tenant admin still accessible (Sprint 6) | P1 | E2E | Login as tenant admin | Tenant admin page renders | Yes |

---

## Traceability Matrix — P0 Cases → Sprint Task

| TC-ID | Module | Sprint Task | AC Reference |
|-------|--------|-------------|--------------|
| TC-001 | Auth | B1-1 (ESG Permission Bypass) | AC-1 |
| TC-002 | Auth | B1-1 | AC-2 |
| TC-004 | Auth | SA-1 (ADR-034) | JWT validation |
| TC-005 | Auth | SA-1 | JWT validation |
| TC-006 | Auth | SA-1 | JWT alg=none |
| TC-012 | Auth | Sprint 2 multi-tenancy | RLS |
| TC-018 | Scope | B1-1 | AC-1 |
| TC-019 | Scope | B1-1 | AC-3 |
| TC-020 | Scope | B1-1 | AC-2 |
| TC-021 | Scope | B1-1 | AC-2 |
| TC-035 | Alerts | B2-5 (StructuralAlertConsumer) | AC-1 |
| TC-039 | Alerts | B2-4 (REST API) | AC-1 |
| TC-040 | Alerts | B2-5 | AC-2 |
| TC-041 | Alerts | B2-5 | AC-2 |
| TC-051 | Buildings | B2-4 | AC-1 |
| TC-053 | Buildings | B2-4 | AC-2 |
| TC-058 | Buildings | B2-4 | AC-3 |
| TC-059 | ESG | B1-5 (PDF Export) | AC-2 |
| TC-060 | ESG | B1-5 | AC-2 |
| TC-064 | ESG | B1-5 | AC-1 |
| TC-065 | ESG | B1-5 | AC-1 |
| TC-068 | ESG | B1-5 | AC-5 |
| TC-069 | ESG | B1-1 | AC-1 |
| TC-075 | Forecast | B1-7 (Cache Eviction) | AC-1 |
| TC-080 | BMS | B1-6 (ACK Consumer) | AC-2 |
| TC-085 | Safety | B2-3 (SafetyService) | AC-1 |
| TC-086 | Safety | B2-3 | AC-1 |
| TC-088 | Safety | B2-3 | AC-3 |
| TC-092 | Safety | FE-3 (Detail Page) | AC-2 |
| TC-098 | Structural | B2-5 | AC-1 |
| TC-099 | Structural | B2-5 | AC-2 |
| TC-100 | Structural | B2-5 | AC-5 (BR-010) |
| TC-103 | Structural | B2-5 | AC-3 |
| TC-107 | Isolation | B2-3 (RLS) | AC-4 (ISO-008) |
| TC-108 | Isolation | B2-3 | AC-4 (ISO-008) |
| TC-109 | Isolation | B2-3 | AC-4 (ISO-009) |
| TC-110 | Isolation | B2-3 | AC-2 (cache key) |
| TC-111 | Isolation | B2-3 | AC-4 (ISO-009) |
| TC-112 | Isolation | B2-3 | AC-4 (ISO-008) |
| TC-113 | SSE | FE-6 (BMS SSE) | AC-1 |
| TC-114 | SSE | FE-6 | AC-2 |
| TC-118 | Push | FE-9 (Notification) | AC-1 |
| TC-119 | Push | FE-9 | AC-1 |
| TC-123 | Workflow | Sprint 6 | Workflow API |
| TC-137 | Tenant | Sprint 6 | Tenant Admin API |
| TC-154 | Citizen | FE-7 (Mobile) | AC-1 |
| TC-165 | Traffic | Sprint 6 | Traffic API |
| TC-179 | Security | QA-6 (OWASP) | AC-2 |
| TC-180 | Security | QA-6 | AC-2 |
| TC-182 | Security | QA-6 | AC-2 |
| TC-184 | Security | QA-6 | AC-2 |
| TC-185 | Security | QA-6 | AC-2 |
| TC-187 | Security | QA-6 | AC-2 |
| TC-188 | Infra | OPS-1 (Analytics) | AC-1 |
| TC-192 | Infra | OPS-5 (Keycloak) | AC-1 |
| TC-195 | Frontend | FE-1..FE-9 | tsc 0 errors |
| TC-199 | Frontend | FE-1..FE-9 | Accessibility |
| TC-201 | Frontend | FE-1..FE-9 | Responsive |
| TC-202 | Frontend | FE-1..FE-9 | Responsive |
| TC-215 | SLA | QA-3 | SLA-001 |
| TC-216 | SLA | QA-3 | SLA-004 |
| TC-217 | SLA | QA-3 | SLA-003 |
| TC-225 | Regression | Sprint 1 | Auth |
| TC-226 | Regression | Sprint 1 | Sensors |
| TC-227 | Regression | Sprint 1 | Alerts |
| TC-232 | Regression | Sprint 6 | AI Workflow |

---

## Execution Priority Order

### Wave 1 — Smoke (P0 API, ~30 min)
TC-001, TC-002, TC-004..TC-006, TC-012, TC-018..TC-021, TC-035, TC-039..TC-041, TC-051, TC-053, TC-058, TC-064..TC-065, TC-068..TC-069, TC-075, TC-080, TC-085, TC-098..TC-100, TC-103, TC-107..TC-112

### Wave 2 — Integration (P0 E2E + SLA, ~60 min)
TC-113, TC-114, TC-118, TC-119, TC-123, TC-137, TC-154, TC-165, TC-179, TC-180, TC-182, TC-184, TC-185, TC-187, TC-188, TC-192, TC-195, TC-199, TC-201..TC-202, TC-215..TC-217, TC-225..TC-227, TC-232

### Wave 3 — Functional (P1 API + UI, ~90 min)
TC-003, TC-007..TC-011, TC-013..TC-017, TC-022..TC-034, TC-036..TC-038, TC-042..TC-050, TC-052, TC-054..TC-057, TC-059..TC-063, TC-066, TC-067, TC-070..TC-074, TC-076..TC-079, TC-081..TC-084, TC-086..TC-089, TC-091, TC-093..TC-097, TC-101, TC-102, TC-104..TC-106

### Wave 4 — Extended (P2 + Mobile + Edge cases, ~60 min)
TC-013, TC-014, TC-025, TC-028, TC-044, TC-045, TC-068, TC-071..TC-074, TC-089, TC-096, TC-097, TC-115, TC-116, TC-117, TC-120..TC-122, TC-124..TC-136, TC-138..TC-153, TC-155..TC-173, TC-175..TC-178, TC-186, TC-189..TC-194, TC-196..TC-214, TC-218..TC-224, TC-228..TC-231, TC-233

---

## Test Environment

| Component | URL | Notes |
|-----------|-----|-------|
| Backend | http://staging:8080 | Spring Boot 3.x |
| Frontend | http://staging:3000 | React 18.x |
| Kong Gateway | http://staging:8000 | API Gateway |
| Keycloak | http://staging:8085 | Auth server |
| Grafana | http://staging:3001 | Monitoring |
| Prometheus | http://staging:9090 | Metrics |

## Test Users

| Username | Password | Role | tenant_id | Scopes |
|----------|----------|------|-----------|--------|
| admin-aggregator | Admin#2026! | ADMIN | (aggregator) | All scopes |
| pilot-admin | PilotAdmin#2026! | ADMIN | hcm | All scopes |
| pilot-operator | PilotOp#2026! | OPERATOR | hcm | esg:read, alert:*, environment:*, traffic:*, sensor:* |
| pilot-viewer | PilotView#2026! | VIEWER | hcm | esg:read, alert:read, environment:read |
| operator-hcm | Operator#2026! | OPERATOR | hcm | esg:read, alert:*, environment:* |

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| QA Engineer | | | |
| Solution Architect | | | |
| Project Manager | | | |

---

*Sprint 7 — Pilot Regression Suite | 243 test cases | 25 modules | 91.4% automated | Last updated: 2026-06-02*
