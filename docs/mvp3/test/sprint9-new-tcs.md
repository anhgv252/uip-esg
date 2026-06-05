# Sprint 9 — New Test Cases (+34 TCs)

**QA Engineer:** UIP QA | **Task ID:** S9-QA-TC-NEW  
**Sprint:** MVP3-9 | API Contract + HA Validation + Pilot Security  
**Date:** 2026-06-04  
**Target Baseline:** 319 total TCs (285 existing + 34 new)  
**Sprint 8 Baseline:** 285 TCs (285/285 PASS, 40 BLOCKED)

---

## Executive Summary

Sprint 9 adds **34 new test cases** to expand coverage for production readiness and pilot user validation. These TCs address critical gaps identified in Sprint 8 retrospective:

- **40 TCs BLOCKED** in Sprint 8 due to missing HA infrastructure and mobile test environment
- **Pilot user workflows** not comprehensively tested (external stakeholder access)
- **Mobile offline mode** not validated
- **ClickHouse HA failover** not verified under load
- **ESG multi-quarter reporting** edge cases not covered
- **Welford vibration anomaly detection** end-to-end flow not validated

### Distribution by Category

| Category | Count | TC Range | Priority | Environment |
|----------|-------|----------|----------|-------------|
| **Pilot User Workflows** | 12 | TC-286 to TC-297 | P0-P2 | Standard |
| **Mobile Offline Mode** | 6 | TC-298 to TC-303 | P1-P2 | Mobile Simulator |
| **ClickHouse HA Failover** | 5 | TC-304 to TC-308 | P0-P2 | HA Staging Only |
| **ESG Multi-Quarter** | 8 | TC-309 to TC-316 | P0-P2 | Standard + Seed Data |
| **Welford Vibration E2E** | 3 | TC-317 to TC-319 | P0-P1 | Standard + Seed Data |
| **TOTAL** | **34** | TC-286 to TC-319 | | |

### Priority Breakdown

- **P0 (Critical):** 15 TCs — Must pass before pilot external access
- **P1 (High):** 14 TCs — Required for production confidence
- **P2 (Medium):** 5 TCs — Nice-to-have for full coverage

### Execution Prerequisites

**Required Infrastructure:**
- ✅ Standard stack: `docker-compose.yml` (all TCs except HA and mobile)
- ⚠️ HA stack: `docker-compose.ha.yml` with `docker-compose.yml` (TC-304 to TC-308)
- ⚠️ Mobile simulator: Expo Go on iOS/Android emulator (TC-298 to TC-303)

**Required Seed Data:**
- `scripts/seed-esg-multi-quarter.sql` — ESG data for Q1-Q4 2026 (TC-309 to TC-316)
- `scripts/seed-vibration-1000.py` — 1000 vibration samples for Welford baseline (TC-317 to TC-319)

**Environment Variables:**
- `WELFORD_MIN_SAMPLES=3` for TC-319

---

## Test Case Summary Table

| TC ID | Category | Title | Priority | Env | AC Summary |
|-------|----------|-------|----------|-----|------------|
| TC-286 | Pilot User | Pilot user login via Keycloak → Dashboard loads within 3s | P0 | Standard | Auth + dashboard render <3s, no console errors |
| TC-287 | Pilot User | Pilot viewer (read-only) cannot trigger alert acknowledge | P0 | Standard | 403 OR button disabled; status unchanged |
| TC-288 | Pilot User | Pilot operator can acknowledge P1 alert | P0 | Standard | Status → ACKNOWLEDGED; audit log entry |
| TC-289 | Pilot User | ESG Report generation for Q1 2026 (multi-quarter data) | P1 | Standard + Seed | 3 metrics × 20 buildings; PDF download |
| TC-290 | Pilot User | ESG Dashboard shows trend comparison Q1 vs Q4 2026 | P1 | Standard + Seed | Q4 < Q1 (improvement trend) |
| TC-291 | Pilot User | Pilot user session timeout after inactivity (30 min) | P1 | Standard | 401 after 31 min; redirect to login |
| TC-292 | Pilot User | Pilot user can export sensor data CSV | P1 | Standard | CSV downloaded; correct headers/data |
| TC-293 | Pilot User | Multi-tenant isolation — pilot-operator cannot see another tenant's data | P0 | Standard | Empty result or 403; no cross-tenant leak |
| TC-294 | Pilot User | City operator creates new alert rule | P1 | Standard | Rule saved; appears in list; audit log |
| TC-295 | Pilot User | Pilot user views building energy consumption heatmap | P2 | Standard | Buildings colored; legend shows kWh/m² |
| TC-296 | Pilot User | Pilot operator invites new pilot viewer via email | P2 | Standard | New user created; can login; read-only |
| TC-297 | Pilot User | Dashboard refresh shows real-time sensor update within 60s | P1 | Standard | Dashboard updates <60s without reload |
| TC-298 | Mobile Offline | Mobile app loads cached dashboard when offline | P1 | Mobile Sim | Cached data shown; "Offline mode" indicator |
| TC-299 | Mobile Offline | Mobile app shows "Offline" banner when network lost | P1 | Mobile Sim | Orange banner <5s |
| TC-300 | Mobile Offline | Mobile offline alert feed shows last known alerts | P1 | Mobile Sim | Last 20 alerts cached; no infinite spinner |
| TC-301 | Mobile Offline | Mobile app reconnects automatically when network restored | P1 | Mobile Sim | "Online" banner; data refresh <30s |
| TC-302 | Mobile Offline | Mobile sensor detail shows "Last updated: X min ago" when offline | P2 | Mobile Sim | Stale data with timestamp indicator |
| TC-303 | Mobile Offline | Mobile offline — pending acknowledge action queued and synced on reconnect | P2 | Mobile Sim | Action queued; synced on reconnect |
| TC-304 | CH HA Failover | CH node-1 kill → writes still succeed via node-2 | P0 | HA Staging | 10/10 writes PASS; no 500 errors |
| TC-305 | CH HA Failover | CH node-1 recovery → replication catches up | P0 | HA Staging | Both nodes same count; lag <60s |
| TC-306 | CH HA Failover | CH Keeper node kill (1 of 3) → quorum maintained | P0 | HA Staging | Writes succeed; 2/3 quorum; election <10s |
| TC-307 | CH HA Failover | CH distributed table fan-out — query returns data from both shards | P1 | HA Staging | COUNT matches sum of shards; no timeout |
| TC-308 | CH HA Failover | CH high-write load (1000 inserts/s for 60s) → no data loss | P2 | HA Staging | ≥59K rows; <1% loss; nodes healthy |
| TC-309 | ESG Multi-Quarter | ESG API returns data for all 4 quarters | P0 | Standard + Seed | 240 records (20×3×4); all quarters |
| TC-310 | ESG Multi-Quarter | ESG Q4 energy consumption lower than Q1 (improvement trend) | P1 | Standard + Seed | Q4 avg < Q1 (10% improvement) |
| TC-311 | ESG Multi-Quarter | ESG PDF export includes all 4 quarters in annual report | P1 | Standard + Seed | PDF >50KB; Q1-Q4 sections |
| TC-312 | ESG Multi-Quarter | ESG building-level drill-down — single building 4 quarters | P1 | Standard + Seed | 12 records (3×4); all present |
| TC-313 | ESG Multi-Quarter | ESG dashboard chart renders 4-quarter trend line | P1 | Standard + Seed | Line chart Q1→Q4; 4 points visible |
| TC-314 | ESG Multi-Quarter | ESG missing quarter handled gracefully (partial data) | P1 | Standard + Seed | 6 records (Q1-Q2 only); no 500 error |
| TC-315 | ESG Multi-Quarter | ESG aggregate: city-wide carbon footprint Q4 2026 | P2 | Standard + Seed | Sum of all buildings; Q4 < Q1 total |
| TC-316 | ESG Multi-Quarter | ESG comparison widget — two buildings side by side | P2 | Standard + Seed | Two trend lines; values match API |
| TC-317 | Welford Vibration | Welford algorithm computes correct running mean after 1000 samples | P0 | Standard + Seed | Mean ≈5.2±0.1; std ≈0.8±0.1; n=1000 |
| TC-318 | Welford Vibration | Welford alert triggers when vibration exceeds μ+3σ threshold | P0 | Standard + Seed | Alert P1+ created; message refs anomaly |
| TC-319 | Welford Vibration | Welford handles WELFORD_MIN_SAMPLES=3 — alert suppressed before baseline established | P1 | Standard + Env Var | No alert before 3 samples; alert after 3rd |

---

## Category 1: Pilot User Workflows (12 TCs)

**Context:** Pilot users are external city authority stakeholders using the system for ESG reporting and monitoring. These TCs validate end-to-end pilot user journeys for the external pilot phase starting Sprint 9.

**Risk:** If these fail, pilot external access must be delayed. **P0** TCs are gate-blocking.

---

### TC-286: Pilot user login via Keycloak → Dashboard loads within 3s

| Field | Value |
|-------|-------|
| **Story** | Pilot prep — external access validation |
| **Priority** | P0 |
| **Type** | E2E User Flow |
| **Precondition** | - Keycloak realm `uip` configured<br>- Pilot-operator credentials valid (`pilot1@hcmc.gov.vn` / test password)<br>- Frontend accessible at `http://localhost:5173` |
| **Environment** | Standard stack |

**Test Steps:**

1. Open browser, navigate to `http://localhost:5173`
2. Click "Login with Keycloak" button
3. Enter credentials: `pilot1@hcmc.gov.vn` / `Test@1234`
4. Click "Sign In"
5. Measure time from redirect back to app until dashboard fully renders
6. Open DevTools Console tab

**Expected Results:**

- ✅ Keycloak login page loads without SSL warnings
- ✅ After successful auth, redirect to `/dashboard` 
- ✅ Dashboard renders with sensor counts visible within **3 seconds**
- ✅ Console has **0 errors** (warnings acceptable)
- ✅ User name "Pilot Operator 1" visible in header
- ✅ JWT token stored in localStorage/sessionStorage

**Fail Criteria:**
- Dashboard load >3s = P1 performance issue (investigate)
- Dashboard load >5s = P0 BLOCKER (do not proceed with pilot)
- Console errors = P0 BLOCKER

---

### TC-287: Pilot viewer (read-only) cannot trigger alert acknowledge

| Field | Value |
|-------|-------|
| **Story** | Role-based access control validation |
| **Priority** | P0 |
| **Type** | Authorization |
| **Precondition** | - Logged in as pilot-viewer role (`viewer1@hcmc.gov.vn`)<br>- At least 1 ACTIVE alert exists |
| **Environment** | Standard stack |

**Test Steps:**

1. Login as pilot-viewer: `viewer1@hcmc.gov.vn` / `Test@1234`
2. Navigate to Alerts page (`/alerts`)
3. Find first ACTIVE alert in list
4. Attempt to click "Acknowledge" button
5. If button enabled, click it and observe API response
6. Verify alert status in database

**Expected Results:**

- ✅ **Option A (UI-level block):** "Acknowledge" button is **disabled** OR not visible for pilot-viewer
- ✅ **Option B (API-level block):** Button clickable but API returns **HTTP 403 Forbidden**
- ✅ Alert status remains **ACTIVE** (unchanged)
- ✅ Console shows error message: "Insufficient permissions to acknowledge alerts"
- ✅ No audit log entry created for this action

**Fail Criteria:**
- Viewer can acknowledge alert = **P0 SECURITY BLOCKER**
- Alert status changes = **P0 DATA INTEGRITY BLOCKER**

---

### TC-288: Pilot operator can acknowledge P1 alert

| Field | Value |
|-------|-------|
| **Story** | Operator workflow validation |
| **Priority** | P0 |
| **Type** | E2E User Flow |
| **Precondition** | - P1 alert exists with status ACTIVE<br>- Logged in as pilot-operator (`pilot1@hcmc.gov.vn`) |
| **Environment** | Standard stack |

**Test Steps:**

1. Login as pilot-operator: `pilot1@hcmc.gov.vn` / `Test@1234`
2. Navigate to Alerts page (`/alerts`)
3. Filter alerts by severity = P1
4. Click "Acknowledge" on first ACTIVE alert
5. Confirm action in modal dialog
6. Wait 2 seconds for UI update
7. Query audit log: `GET /api/v1/audit/logs?action=ALERT_ACKNOWLEDGED&limit=1`
8. Verify alert status in database

**Expected Results:**

- ✅ Alert status changes to **ACKNOWLEDGED** in UI within 2 seconds
- ✅ "Acknowledged by: pilot1@hcmc.gov.vn at [timestamp]" visible
- ✅ API response: `200 OK` with updated alert object
- ✅ Audit log entry created:
  ```json
  {
    "action": "ALERT_ACKNOWLEDGED",
    "user": "pilot1@hcmc.gov.vn",
    "alertId": "<alert_id>",
    "timestamp": "<ISO_8601>"
  }
  ```
- ✅ Database: alert table shows `status = 'ACKNOWLEDGED'`, `acknowledged_by`, `acknowledged_at`

**Fail Criteria:**
- Alert not acknowledged = P0 BLOCKER (operator workflow broken)
- No audit log = P0 COMPLIANCE BLOCKER

---

### TC-289: ESG Report generation for Q1 2026 (multi-quarter data)

| Field | Value |
|-------|-------|
| **Story** | ESG multi-quarter reporting |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Multi-quarter seed data loaded (`scripts/seed-esg-multi-quarter.sql`)<br>- Logged in as pilot-operator |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Execute seed script: `psql -U postgres -d uip < scripts/seed-esg-multi-quarter.sql`
2. Verify seed data count: 
   ```sql
   SELECT COUNT(*) FROM esg_metrics 
   WHERE year = 2026 AND quarter = 'Q1';
   -- Expected: 60 rows (20 buildings × 3 metrics)
   ```
3. Navigate to ESG Dashboard (`/esg/dashboard`)
4. Select period: Year = 2026, Quarter = Q1
5. Click "Generate Report" button
6. Wait for report generation (max 10 seconds)
7. Click "Download PDF" button

**Expected Results:**

- ✅ Report shows **3 metric types** (energy, water, carbon) for **20 buildings**
- ✅ Total of **60 data points** visible in summary table
- ✅ Charts render without errors
- ✅ PDF download triggers (file size >50KB)
- ✅ PDF filename: `ESG_Report_Q1_2026_<timestamp>.pdf`
- ✅ PDF contains:
  - Cover page with report metadata
  - Building-level breakdown tables
  - Trend charts for each metric type
  - Footer with generation timestamp

**Fail Criteria:**
- Report generation fails = P1 (investigate data integrity)
- Missing data points = P1 (seed script issue)
- PDF not downloadable = P2 (investigate export service)

---

### TC-290: ESG Dashboard shows trend comparison Q1 vs Q4 2026

| Field | Value |
|-------|-------|
| **Story** | ESG trend analysis |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Multi-quarter seed data loaded<br>- Seed data has improvement trend (Q4 < Q1) |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Navigate to ESG Dashboard (`/esg/dashboard`)
2. Select "Trend Comparison" view
3. Select baseline: Q1 2026
4. Select comparison: Q4 2026
5. Choose metric: Energy Consumption (kWh/m²)
6. Click "Compare"
7. Observe chart rendering

**Expected Results:**

- ✅ Two trend lines displayed: Q1 (blue) and Q4 (green)
- ✅ Q4 values are **lower than Q1 values** (improvement trend per seed data design)
- ✅ Improvement percentage displayed: e.g., "Q4 improved by 10% vs Q1"
- ✅ Tooltip on hover shows exact values for each quarter
- ✅ Legend clearly labels Q1 and Q4
- ✅ No console errors during chart render

**Fail Criteria:**
- Q4 > Q1 (data integrity issue) = P1 BLOCKER
- Chart does not render = P1

---

### TC-291: Pilot user session timeout after inactivity (30 min)

| Field | Value |
|-------|-------|
| **Story** | Security — session management |
| **Priority** | P1 |
| **Type** | Security |
| **Precondition** | - Valid pilot-operator session<br>- Keycloak token expiry configured to 30 minutes |
| **Environment** | Standard stack |

**Test Steps:**

1. Login as pilot-operator: `pilot1@hcmc.gov.vn`
2. Note current timestamp
3. **Option A (Fast test):** Use browser DevTools to manipulate JWT token expiry to 1 minute in the future
4. **Option B (Real test):** Wait 31 minutes with no user interaction
5. After timeout period, attempt API call: `GET /api/v1/sensors`
6. Observe response

**Expected Results:**

- ✅ API returns **401 Unauthorized** after token expiry
- ✅ Frontend detects 401 and redirects to login page
- ✅ Error message: "Your session has expired. Please log in again."
- ✅ No data leak after session expiry
- ✅ After re-login, user can access system normally

**Fail Criteria:**
- Token still valid after 31 min = **P0 SECURITY BLOCKER**
- User remains logged in indefinitely = **P0 SECURITY BLOCKER**

---

### TC-292: Pilot user can export sensor data CSV

| Field | Value |
|-------|-------|
| **Story** | Data export feature |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Logged in as pilot-operator<br>- Sensor TEMP-001 has ≥10 readings |
| **Environment** | Standard stack |

**Test Steps:**

1. Navigate to Sensor Detail page: `/sensors/TEMP-001`
2. Scroll to readings table
3. Click "Export CSV" button
4. Wait for download to complete
5. Open downloaded CSV file in Excel or text editor

**Expected Results:**

- ✅ CSV file downloads successfully
- ✅ Filename format: `sensor_TEMP-001_readings_<timestamp>.csv`
- ✅ CSV headers: `timestamp,value,unit,sensor_id,sensor_type`
- ✅ Data rows match readings visible in UI table
- ✅ At least 10 rows present (excluding header)
- ✅ Timestamp format: ISO 8601 (e.g., `2026-06-04T10:30:00Z`)
- ✅ No auth errors (403/401)

**Fail Criteria:**
- Export fails with 403 = P1 (auth issue)
- CSV empty despite data in UI = P1 (export service bug)

---

### TC-293: Multi-tenant isolation — pilot-operator cannot see another tenant's data

| Field | Value |
|-------|-------|
| **Story** | Multi-tenancy security |
| **Priority** | P0 |
| **Type** | Security |
| **Precondition** | - Two tenants exist: tenant-A, tenant-B<br>- Each tenant has distinct sensor data<br>- Logged in as tenant-A pilot-operator |
| **Environment** | Standard stack |

**Test Steps:**

1. Setup: Create tenant-B sensor in database:
   ```sql
   INSERT INTO sensors (id, name, tenant_id, type) 
   VALUES ('TEMP-B001', 'Tenant B Sensor', 'tenant-B', 'TEMPERATURE');
   ```
2. Login as tenant-A pilot-operator: `pilotA@hcmc.gov.vn`
3. Attempt to query tenant-B sensor via API:
   ```
   GET /api/v1/sensors/TEMP-B001/readings
   ```
4. Observe response status
5. Attempt UI navigation: direct URL `/sensors/TEMP-B001`

**Expected Results:**

- ✅ API returns **403 Forbidden** OR **404 Not Found** (no data leak)
- ✅ Response body does NOT contain sensor details
- ✅ UI shows "Sensor not found" OR redirects to 403 error page
- ✅ No tenant-B data visible in tenant-A dashboard
- ✅ Database audit log (if enabled) records unauthorized access attempt

**Fail Criteria:**
- Tenant-A can see tenant-B data = **P0 SECURITY BLOCKER — DO NOT PROCEED WITH PILOT**
- Any data leakage across tenants = **P0 BLOCKER**

---

### TC-294: City operator creates new alert rule

| Field | Value |
|-------|-------|
| **Story** | Alert rule management |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Logged in as city-operator (not pilot)<br>- Alert Rules page accessible |
| **Environment** | Standard stack |

**Test Steps:**

1. Login as city-operator: `operator@hcmc.gov.vn` / `Test@1234`
2. Navigate to Alert Rules page (`/admin/alert-rules`)
3. Click "Create New Rule" button
4. Fill form:
   - Rule Name: "High AQI Alert"
   - Metric: Air Quality Index (AQI)
   - Condition: `value > 150`
   - Severity: P1
   - Action: Send notification
5. Click "Save"
6. Wait for confirmation
7. Verify rule appears in rules list
8. Query audit log: `GET /api/v1/audit/logs?action=ALERT_RULE_CREATED`

**Expected Results:**

- ✅ Rule saved successfully (200 OK response)
- ✅ Rule appears in rules list with correct parameters
- ✅ Rule ID generated (e.g., `RULE-001`)
- ✅ Rule status: ACTIVE (enabled by default)
- ✅ Audit log entry created:
  ```json
  {
    "action": "ALERT_RULE_CREATED",
    "user": "operator@hcmc.gov.vn",
    "ruleId": "RULE-001",
    "details": { "metric": "AQI", "threshold": 150 }
  }
  ```
- ✅ Database: alert_rules table contains new row

**Fail Criteria:**
- Rule not saved = P1 (investigate API/DB issue)
- No audit log = P1 (compliance requirement)

---

### TC-295: Pilot user views building energy consumption heatmap

| Field | Value |
|-------|-------|
| **Story** | GIS visualization |
| **Priority** | P2 |
| **Type** | Feature |
| **Precondition** | - ESG seed data loaded<br>- Map tiles available (Leaflet/MapLibre)<br>- Logged in as pilot-operator |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Navigate to Map View (`/map`)
2. Click on layer selector dropdown
3. Select "Energy Consumption" layer
4. Wait for map to render with building overlays
5. Observe building color coding
6. Hover over a building to see tooltip

**Expected Results:**

- ✅ Map renders without errors
- ✅ Buildings are colored by energy consumption level:
  - Green: Low consumption (<50 kWh/m²)
  - Yellow: Medium (50-100 kWh/m²)
  - Red: High (>100 kWh/m²)
- ✅ Legend visible showing kWh/m² scale
- ✅ Tooltip on hover shows:
  - Building name
  - Energy consumption value
  - Last updated timestamp
- ✅ All 20 seed data buildings visible on map

**Fail Criteria:**
- Map does not render = P2 (investigate map tile service)
- Buildings not colored = P2 (layer data issue)

---

### TC-296: Pilot operator invites new pilot viewer via email

| Field | Value |
|-------|-------|
| **Story** | User management |
| **Priority** | P2 |
| **Type** | Feature |
| **Precondition** | - `/api/v1/auth/invite` endpoint available<br>- Email service configured<br>- Logged in as pilot-operator |
| **Environment** | Standard stack |

**Test Steps:**

1. Navigate to User Management page (`/admin/users`)
2. Click "Invite User" button
3. Fill form:
   - Email: `newviewer@hcmc.gov.vn`
   - Role: pilot-viewer (read-only)
4. Click "Send Invite"
5. Check email inbox (test email account)
6. Click invite acceptance URL from email
7. Set password for new account
8. Login with new credentials
9. Verify role permissions

**Expected Results:**

- ✅ Invite sent successfully (200 OK response)
- ✅ Email received at `newviewer@hcmc.gov.vn` within 1 minute
- ✅ Email contains invite acceptance URL
- ✅ Acceptance URL is valid and not expired (<24 hours)
- ✅ New user can set password
- ✅ After login, user has pilot-viewer role (read-only)
- ✅ New user **cannot** acknowledge alerts (verified by TC-287)
- ✅ Audit log entry: `USER_INVITED`

**Fail Criteria:**
- Email not received = P2 (email service issue)
- New user has wrong role = P1 (security issue)

---

### TC-297: Dashboard refresh shows real-time sensor update within 60s

| Field | Value |
|-------|-------|
| **Story** | Real-time data updates |
| **Priority** | P1 |
| **Type** | E2E |
| **Precondition** | - Dashboard open with sensor data visible<br>- Sensor TEMP-001 active |
| **Environment** | Standard stack |

**Test Steps:**

1. Open Dashboard (`/dashboard`)
2. Locate sensor TEMP-001 current value display
3. Note current value and timestamp (e.g., "25.3°C at 10:30:00")
4. Inject new reading via test endpoint:
   ```
   POST /api/v1/sensors/TEMP-001/readings
   { "value": 28.5, "unit": "C", "timestamp": "<now>" }
   ```
5. Start timer
6. Observe dashboard (do NOT manually refresh page)
7. Wait for dashboard to update automatically

**Expected Results:**

- ✅ Dashboard updates with new value **within 60 seconds**
- ✅ New value displayed: "28.5°C"
- ✅ Timestamp updates to new reading time
- ✅ No full page reload required (via WebSocket/SSE or polling)
- ✅ Update indicator visible briefly (e.g., flash animation on value change)
- ✅ Console shows no errors during update

**Fail Criteria:**
- Update takes >60s = P1 (real-time requirement not met)
- Requires manual page refresh = P1 (WebSocket/polling broken)
- Update never appears = P0 BLOCKER (investigate data pipeline)

---

## Category 2: Mobile Offline Mode (6 TCs)

**Context:** Mobile app (React Native/Expo) must support offline operation for field operators with unstable network. These TCs validate caching, offline indicators, and sync behavior.

**Test Environment:** iOS Simulator (preferred) or Android Emulator with Expo Go app installed.

**Risk:** These TCs validate critical mobile UX. Failures are P1 (degrade mobile experience).

---

### TC-298: Mobile app loads cached dashboard when offline

| Field | Value |
|-------|-------|
| **Story** | Mobile offline support |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Mobile app installed and opened at least once while online<br>- Dashboard data cached from previous session<br>- Device network disabled |
| **Environment** | Mobile Simulator + offline mode |

**Test Steps:**

1. Open mobile app on simulator while **online**
2. Navigate to Dashboard tab
3. Wait for data to load (sensor counts, alert summary)
4. Close app (swipe up to kill)
5. **Disable network:** iOS: Airplane mode ON | Android: Disable WiFi + Mobile data
6. Reopen mobile app
7. Navigate to Dashboard tab

**Expected Results:**

- ✅ Dashboard loads with **cached data** from previous session
- ✅ "Offline mode" indicator visible (e.g., orange banner at top)
- ✅ Sensor counts match last known values
- ✅ No infinite loading spinner
- ✅ No error message "Network unavailable"
- ✅ Data staleness indicator: "Last updated: X minutes ago"

**Fail Criteria:**
- App crashes on offline launch = P0 BLOCKER
- Dashboard shows empty state despite cached data = P1
- No offline indicator shown = P1 (confusing UX)

---

### TC-299: Mobile app shows "Offline" banner when network lost

| Field | Value |
|-------|-------|
| **Story** | Mobile offline indicators |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Mobile app open and online<br>- Dashboard or any tab visible |
| **Environment** | Mobile Simulator |

**Test Steps:**

1. Open mobile app with network enabled
2. Navigate to Dashboard (online data visible)
3. While app is active, disable device network (Airplane mode ON)
4. Observe app UI response within 5 seconds

**Expected Results:**

- ✅ **Orange banner** appears at top of screen within **5 seconds**
- ✅ Banner text: "Offline — Showing cached data" or similar
- ✅ Banner is persistent (stays visible until network restored)
- ✅ App does not crash or freeze
- ✅ User can still navigate between tabs
- ✅ Cached data remains visible

**Fail Criteria:**
- No offline indicator = P1 (poor UX)
- Banner appears after >10s = P2 (slow network detection)
- App freezes = P0 BLOCKER

---

### TC-300: Mobile offline alert feed shows last known alerts

| Field | Value |
|-------|-------|
| **Story** | Mobile offline alert access |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Alert feed loaded while online (at least 5 alerts cached)<br>- Device network disabled |
| **Environment** | Mobile Simulator + offline mode |

**Test Steps:**

1. Open mobile app **online**
2. Navigate to Alerts tab
3. Wait for alerts to load (verify ≥5 alerts visible)
4. Note top 3 alert titles
5. Disable network (Airplane mode ON)
6. Navigate away (Dashboard tab)
7. Navigate back to Alerts tab
8. Observe alerts display

**Expected Results:**

- ✅ Alerts tab shows **last 20 alerts** from cache
- ✅ Top 3 alert titles match what was visible online
- ✅ Timestamps preserved
- ✅ No infinite loading spinner
- ✅ "Offline — Showing cached data" banner visible
- ✅ Pull-to-refresh gesture shows message: "Cannot refresh while offline"

**Fail Criteria:**
- Alerts tab shows empty state despite cache = P1
- Infinite loading spinner = P1 (bad UX)
- App crashes = P0 BLOCKER

---

### TC-301: Mobile app reconnects automatically when network restored

| Field | Value |
|-------|-------|
| **Story** | Mobile auto-reconnect |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Mobile app in offline mode (orange banner visible)<br>- Dashboard or Alerts tab open |
| **Environment** | Mobile Simulator |

**Test Steps:**

1. Mobile app in offline mode (TC-299 completed)
2. Orange "Offline" banner visible
3. Re-enable device network (Airplane mode OFF)
4. Wait up to 30 seconds
5. Observe app behavior (do NOT manually trigger refresh)

**Expected Results:**

- ✅ **Within 30 seconds**, "Online" indicator appears (green banner or toast)
- ✅ Orange offline banner disappears
- ✅ Data automatically refreshes without user action
- ✅ Sensor values update to latest
- ✅ Alert feed updates if new alerts available
- ✅ No app crash during reconnection

**Fail Criteria:**
- Auto-refresh takes >60s = P2 (slow)
- Requires manual pull-to-refresh = P1 (auto-reconnect broken)
- App crashes on reconnect = P0 BLOCKER

---

### TC-302: Mobile sensor detail shows "Last updated: X min ago" when offline

| Field | Value |
|-------|-------|
| **Story** | Mobile staleness indicator |
| **Priority** | P2 |
| **Type** | UX |
| **Precondition** | - Network disabled<br>- Sensor TEMP-001 detail page cached |
| **Environment** | Mobile Simulator + offline mode |

**Test Steps:**

1. Mobile app online, navigate to Sensor Detail: TEMP-001
2. Note current value and timestamp
3. Disable network (Airplane mode ON)
4. Navigate away, then back to TEMP-001 detail
5. Observe timestamp display

**Expected Results:**

- ✅ Sensor value shown (cached)
- ✅ Staleness indicator visible: **"Last updated: N minutes ago"**
- ✅ Indicator updates every minute (e.g., "5 min ago" → "6 min ago")
- ✅ Offline banner present
- ✅ No "real-time" or "live" badge shown while offline

**Fail Criteria:**
- No staleness indicator = P2 (confusing UX — user may think data is live)
- Incorrect timestamp = P2

---

### TC-303: Mobile offline — pending acknowledge action queued and synced on reconnect

| Field | Value |
|-------|-------|
| **Story** | Mobile offline action queue |
| **Priority** | P2 |
| **Type** | Feature |
| **Precondition** | - Mobile app offline<br>- At least 1 ACTIVE alert in cached feed |
| **Environment** | Mobile Simulator |

**Test Steps:**

1. Mobile app in offline mode (network disabled)
2. Navigate to Alerts tab
3. Tap "Acknowledge" button on first alert
4. Observe UI response
5. Note any "queued" or "pending" indicator
6. Re-enable network (Airplane mode OFF)
7. Wait 30 seconds
8. Verify alert status via API: `GET /api/v1/alerts/<alert_id>`

**Expected Results:**

- ✅ While offline, tap triggers local UI update:
  - Alert shows "Pending acknowledgment (queued)" status
  - Orange badge or icon indicates action is queued
- ✅ Action stored in local queue (AsyncStorage/SecureStore)
- ✅ When network reconnects:
  - Queued action automatically sent to backend within 30s
  - API response updates alert to ACKNOWLEDGED
  - UI shows confirmed status
- ✅ Alert status in backend database = ACKNOWLEDGED

**Fail Criteria:**
- Queued action lost on reconnect = P1 (data loss)
- Action never syncs = P1 (offline queue broken)
- Multiple duplicate acknowledge requests sent = P2 (idempotency issue)

---

## Category 3: ClickHouse HA Failover (5 TCs)

**Context:** ClickHouse 2-node cluster with Keeper for replication. These TCs validate HA behavior under node failure scenarios.

**⚠️ CRITICAL:** These TCs require **HA staging environment only** — `docker-compose.ha.yml` + `docker-compose.yml` stacked. DO NOT run on production.

**Risk:** HA stack failures block pilot production deployment. All TCs are **P0-P1**.

---

### TC-304: CH node-1 kill → writes still succeed via node-2

| Field | Value |
|-------|-------|
| **Story** | S8-OPS01 — ClickHouse HA validation |
| **Priority** | P0 |
| **Type** | Infrastructure Resilience |
| **Precondition** | - HA stack running: `docker-compose -f docker-compose.yml -f docker-compose.ha.yml up -d`<br>- Both CH nodes healthy (`clickhouse-01` + `clickhouse-02`)<br>- Distributed table `analytics.sensor_reading_hourly_dist` exists |
| **Environment** | **HA Staging Only** |

**Test Steps:**

1. Verify both CH nodes healthy:
   ```bash
   curl "http://localhost:8123/?query=SELECT version()"
   curl "http://localhost:8124/?query=SELECT version()"
   ```
   Both should return version string.

2. Stop CH node-1:
   ```bash
   docker stop uip-clickhouse-1
   ```

3. Send 10 sensor readings via backend API:
   ```bash
   for i in {1..10}; do
     curl -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
       -H "Content-Type: application/json" \
       -d "{\"value\": 25.$i, \"unit\": \"C\"}"
   done
   ```

4. Wait 5 seconds for Flink aggregation

5. Query analytics via node-2:
   ```bash
   curl "http://localhost:8124/?query=SELECT COUNT(*) FROM analytics.sensor_reading_hourly WHERE sensor_id='TEMP-001'"
   ```

**Expected Results:**

- ✅ All **10 POST requests return 200 OK** (no 500 errors)
- ✅ Analytics query returns **COUNT ≥ 10** (may include previous data)
- ✅ No data loss during node-1 downtime
- ✅ Backend logs show reconnection to node-2 (if connection pool configured correctly)
- ✅ Flink job continues processing (check Flink UI: job status = RUNNING)

**Fail Criteria:**
- Any write fails with 500 = **P0 BLOCKER** (HA not working)
- Data loss >0 = **P0 BLOCKER**
- Flink job crashes = **P0 BLOCKER**

---

### TC-305: CH node-1 recovery → replication catches up

| Field | Value |
|-------|-------|
| **Story** | ClickHouse replication validation |
| **Priority** | P0 |
| **Type** | Infrastructure Resilience |
| **Precondition** | - TC-304 completed (node-1 stopped, data written to node-2) |
| **Environment** | **HA Staging Only** |

**Test Steps:**

1. Start CH node-1:
   ```bash
   docker start uip-clickhouse-1
   ```

2. Wait 60 seconds for node to recover and replicate

3. Query both nodes for same data:
   ```bash
   # Node-1
   COUNT1=$(curl -s "http://localhost:8123/?query=SELECT COUNT(*) FROM analytics.sensor_reading_hourly WHERE sensor_id='TEMP-001'" | tr -d '\n')
   
   # Node-2
   COUNT2=$(curl -s "http://localhost:8124/?query=SELECT COUNT(*) FROM analytics.sensor_reading_hourly WHERE sensor_id='TEMP-001'" | tr -d '\n')
   
   echo "Node-1 count: $COUNT1"
   echo "Node-2 count: $COUNT2"
   ```

4. Check replication lag:
   ```sql
   SELECT 
     table, 
     absolute_delay 
   FROM system.replicas 
   WHERE database = 'analytics';
   ```

**Expected Results:**

- ✅ Node-1 and Node-2 return **same COUNT** (±1 acceptable due to timing)
- ✅ Replication lag < 60 seconds (`absolute_delay < 60`)
- ✅ No replication errors in ClickHouse logs
- ✅ System.replicas shows `is_readonly = 0` for both replicas

**Fail Criteria:**
- COUNT differs by >5 = P0 BLOCKER (replication broken)
- Replication lag >120s = P1 (investigate CH Keeper connectivity)
- Node-1 remains in readonly mode = P0 BLOCKER

---

### TC-306: CH Keeper node kill (1 of 3) → quorum maintained

| Field | Value |
|-------|-------|
| **Story** | S9-TD-KEEPER — 3-node Keeper quorum |
| **Priority** | P0 |
| **Type** | Infrastructure Resilience |
| **Precondition** | - 3-keeper quorum running: `clickhouse-keeper-01`, `keeper-02`, `keeper-03`<br>- `docker-compose.ha.yml` includes all 3 keepers<br>- **Requires S9-TD-KEEPER task completed** |
| **Environment** | **HA Staging Only** |

**Test Steps:**

1. Verify 3-keeper quorum healthy:
   ```bash
   echo "ruok" | nc localhost 9181  # keeper-01
   echo "ruok" | nc localhost 9182  # keeper-02
   echo "ruok" | nc localhost 9183  # keeper-03
   ```
   All should respond `imok`.

2. Stop keeper-02:
   ```bash
   docker stop uip-clickhouse-keeper-02
   ```

3. Wait 10 seconds for leader election

4. Send 5 sensor readings via API:
   ```bash
   for i in {1..5}; do
     curl -X POST http://localhost:8080/api/v1/sensors/TEMP-002/readings \
       -H "Content-Type: application/json" \
       -d "{\"value\": 30.$i, \"unit\": \"C\"}"
   done
   ```

5. Verify writes succeeded:
   ```bash
   curl "http://localhost:8123/?query=SELECT COUNT(*) FROM analytics.sensor_reading_hourly WHERE sensor_id='TEMP-002'"
   ```

6. Check Keeper status:
   ```bash
   echo "stat" | nc localhost 9181
   ```

**Expected Results:**

- ✅ All 5 writes succeed (200 OK)
- ✅ Keeper quorum maintained with **2/3 nodes** (keeper-01 + keeper-03)
- ✅ Leader election completes within **10 seconds**
- ✅ `echo "stat"` shows new leader (may be keeper-01 or keeper-03)
- ✅ CH nodes remain operational (no readonly mode)

**Fail Criteria:**
- Writes fail = **P0 BLOCKER** (quorum lost)
- Keeper election takes >30s = P1 (investigate keeper config)
- CH nodes go readonly = P0 BLOCKER

---

### TC-307: CH distributed table fan-out — query returns data from both shards

| Field | Value |
|-------|-------|
| **Story** | ClickHouse distributed query validation |
| **Priority** | P1 |
| **Type** | Data Integrity |
| **Precondition** | - Both CH nodes running<br>- Data exists in both shards (from previous TCs) |
| **Environment** | **HA Staging Only** |

**Test Steps:**

1. Insert data into shard-1 (via node-1):
   ```bash
   curl "http://localhost:8123/?query=INSERT INTO analytics.sensor_reading_hourly_local FORMAT CSV" \
     --data-binary @- <<EOF
   2026-06-04 10:00:00,TEMP-SHARD1,25.5,C,20
   EOF
   ```

2. Insert data into shard-2 (via node-2):
   ```bash
   curl "http://localhost:8124/?query=INSERT INTO analytics.sensor_reading_hourly_local FORMAT CSV" \
     --data-binary @- <<EOF
   2026-06-04 10:00:00,TEMP-SHARD2,30.5,C,15
   EOF
   ```

3. Query distributed table from any node:
   ```bash
   curl "http://localhost:8123/?query=SELECT sensor_id, AVG(value) FROM analytics.sensor_reading_hourly_dist WHERE sensor_id IN ('TEMP-SHARD1', 'TEMP-SHARD2') GROUP BY sensor_id"
   ```

**Expected Results:**

- ✅ Query returns **both sensors** (TEMP-SHARD1 and TEMP-SHARD2)
- ✅ Average values match inserted data:
  - TEMP-SHARD1: 25.5
  - TEMP-SHARD2: 30.5
- ✅ No query timeout (<5 seconds)
- ✅ Distributed engine correctly fans out query to both shards

**Fail Criteria:**
- Query returns only 1 sensor = P1 (shard routing broken)
- Query timeout = P1 (investigate network/config)
- Wrong aggregation results = P1 (distributed table config issue)

---

### TC-308: CH high-write load (1000 inserts/s for 60s) → no data loss

| Field | Value |
|-------|-------|
| **Story** | ClickHouse load testing |
| **Priority** | P2 |
| **Type** | Performance + Resilience |
| **Precondition** | - HA stack healthy<br>- Load test tool available (k6 or JMeter) |
| **Environment** | **HA Staging Only** + k6 load test tool |

**Test Steps:**

1. Prepare k6 load test script:
   ```javascript
   // load-test-ch-write.js
   import http from 'k6/http';
   export let options = {
     vus: 50,  // 50 virtual users
     duration: '60s',
     thresholds: {
       http_req_failed: ['rate<0.01'], // <1% errors
     },
   };
   export default function () {
     const payload = JSON.stringify({
       value: Math.random() * 50,
       unit: 'C',
       sensorId: 'LOAD-TEST-001'
     });
     http.post('http://localhost:8080/api/v1/sensors/LOAD-TEST-001/readings', payload, {
       headers: { 'Content-Type': 'application/json' },
     });
   }
   ```

2. Run load test:
   ```bash
   k6 run load-test-ch-write.js
   ```

3. After test completes, query total count:
   ```bash
   curl "http://localhost:8123/?query=SELECT COUNT(*) FROM analytics.sensor_reading_hourly WHERE sensor_id='LOAD-TEST-001'"
   ```

4. Check both CH nodes health:
   ```bash
   docker ps | grep clickhouse
   # Both should show status "healthy"
   ```

5. Calculate expected count: 1000 inserts/s × 60s = 60,000

**Expected Results:**

- ✅ k6 reports **≥59,000 successful requests** (≤1% error rate acceptable)
- ✅ ClickHouse COUNT returns **≥59,000 rows** (≤1% data loss acceptable)
- ✅ Both CH nodes remain **healthy** (not crashed/restarted)
- ✅ No OOM (Out of Memory) errors in CH logs
- ✅ Average latency <100ms (check k6 summary)

**Fail Criteria:**
- Error rate >5% = P1 (investigate CH capacity)
- Data loss >5% = P1 (investigate Kafka/Flink/CH pipeline)
- CH node crashed = P0 BLOCKER

---

## Category 4: ESG Multi-Quarter (8 TCs)

**Context:** ESG dashboard must support multi-quarter trend analysis (Q1-Q4 2026). These TCs validate API and UI behavior with seed data spanning 4 quarters.

**Precondition:** Run seed script: `psql -U postgres -d uip < scripts/seed-esg-multi-quarter.sql`

**Seed Data Design:**
- 20 buildings × 3 metrics (energy, water, carbon) × 4 quarters = **240 data points**
- Quarterly improvement trend: Q4 values are 10% lower than Q1 (simulates efficiency gains)

---

### TC-309: ESG API returns data for all 4 quarters

| Field | Value |
|-------|-------|
| **Story** | ESG multi-quarter API validation |
| **Priority** | P0 |
| **Type** | API |
| **Precondition** | - Seed data loaded: `scripts/seed-esg-multi-quarter.sql`<br>- Verify seed count:<br>`SELECT COUNT(*) FROM esg_metrics WHERE year = 2026;`<br>Expected: 240 rows |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Query all ESG data for 2026:
   ```bash
   curl "http://localhost:8080/api/v1/esg/metrics?year=2026"
   ```

2. Parse response JSON and count:
   - Total records
   - Distinct quarters
   - Records per quarter

3. Verify quarters present:
   ```bash
   curl "http://localhost:8080/api/v1/esg/metrics?year=2026" | jq '[.[] | .quarter] | unique'
   ```

**Expected Results:**

- ✅ API returns **240 metric records** (20 buildings × 3 metrics × 4 quarters)
- ✅ All **4 quarters present**: `["Q1", "Q2", "Q3", "Q4"]`
- ✅ Each quarter has **60 records** (20 buildings × 3 metrics)
- ✅ Response includes fields:
  - `buildingId`
  - `metricType` (energy / water / carbon)
  - `value`
  - `unit`
  - `quarter`
  - `year`

**Fail Criteria:**
- Missing quarters = P0 BLOCKER (seed script issue)
- Record count mismatch = P0 BLOCKER
- Missing required fields = P0 (API schema issue)

---

### TC-310: ESG Q4 energy consumption lower than Q1 (improvement trend)

| Field | Value |
|-------|-------|
| **Story** | ESG trend validation |
| **Priority** | P1 |
| **Type** | Data Integrity |
| **Precondition** | - Seed data loaded with improvement trend design |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Query Q1 energy consumption:
   ```bash
   Q1_AVG=$(curl -s "http://localhost:8080/api/v1/esg/metrics?quarter=Q1&year=2026&metric=energy" | jq '[.[] | .value] | add / length')
   echo "Q1 Average: $Q1_AVG"
   ```

2. Query Q4 energy consumption:
   ```bash
   Q4_AVG=$(curl -s "http://localhost:8080/api/v1/esg/metrics?quarter=Q4&year=2026&metric=energy" | jq '[.[] | .value] | add / length')
   echo "Q4 Average: $Q4_AVG"
   ```

3. Calculate improvement percentage:
   ```bash
   echo "Improvement: $(( (Q1_AVG - Q4_AVG) * 100 / Q1_AVG ))%"
   ```

**Expected Results:**

- ✅ Q4 average value is **lower than Q1 average** (improvement trend)
- ✅ Improvement percentage ≈ **10%** (±2% acceptable due to seed data design)
- ✅ All 20 buildings show improvement trend (no regressions)

**Fail Criteria:**
- Q4 > Q1 (trend reversed) = P1 BLOCKER (seed data error)
- Improvement <5% or >15% = P2 (verify seed script logic)

---

### TC-311: ESG PDF export includes all 4 quarters in annual report

| Field | Value |
|-------|-------|
| **Story** | ESG PDF export |
| **Priority** | P1 |
| **Type** | Feature |
| **Precondition** | - Seed data loaded |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Generate annual ESG report:
   ```bash
   curl -X POST "http://localhost:8080/api/v1/esg/reports/pdf" \
     -H "Content-Type: application/json" \
     -d '{ "year": 2026, "includeAllQuarters": true }' \
     --output esg_report_2026.pdf
   ```

2. Verify file size:
   ```bash
   ls -lh esg_report_2026.pdf
   ```

3. Open PDF and inspect:
   - Table of contents
   - Quarter sections (Q1, Q2, Q3, Q4)
   - Building-level data tables

**Expected Results:**

- ✅ PDF generated successfully (200 OK response)
- ✅ File size **>50KB** (contains meaningful content)
- ✅ PDF contains:
  - Cover page with metadata (year, generation date)
  - Q1 section with 60 data points
  - Q2 section with 60 data points
  - Q3 section with 60 data points
  - Q4 section with 60 data points
  - Summary charts showing quarterly trends
  - Footer with page numbers and generation timestamp

**Fail Criteria:**
- PDF generation fails = P1 (investigate export service)
- PDF <20KB = P1 (likely error PDF, not real report)
- Missing quarter sections = P1 (template issue)

---

### TC-312: ESG building-level drill-down — single building 4 quarters

| Field | Value |
|-------|-------|
| **Story** | ESG building drill-down |
| **Priority** | P1 |
| **Type** | API |
| **Precondition** | - Seed data loaded for building B-001 |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Query building B-001 for all quarters:
   ```bash
   curl "http://localhost:8080/api/v1/esg/metrics?buildingId=B-001&year=2026"
   ```

2. Count records by quarter:
   ```bash
   curl -s "http://localhost:8080/api/v1/esg/metrics?buildingId=B-001&year=2026" | jq 'group_by(.quarter) | map({quarter: .[0].quarter, count: length})'
   ```

**Expected Results:**

- ✅ API returns **12 records** total (3 metrics × 4 quarters)
- ✅ Records grouped by quarter:
  - Q1: 3 records (energy, water, carbon)
  - Q2: 3 records
  - Q3: 3 records
  - Q4: 3 records
- ✅ All data points present (no nulls)
- ✅ Values show improvement trend (Q4 < Q3 < Q2 < Q1)

**Fail Criteria:**
- Missing quarters = P1 (data integrity issue)
- Missing metrics = P1 (seed script incomplete)

---

### TC-313: ESG dashboard chart renders 4-quarter trend line

| Field | Value |
|-------|-------|
| **Story** | ESG dashboard visualization |
| **Priority** | P1 |
| **Type** | UI |
| **Precondition** | - Seed data loaded<br>- Browser open at ESG Dashboard |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Navigate to ESG Dashboard: `http://localhost:5173/esg/dashboard`
2. Select building: B-001
3. Select metric: Energy Consumption
4. View trend chart
5. Inspect chart data points

**Expected Results:**

- ✅ Line chart renders without errors
- ✅ Chart shows **4 data points** (Q1, Q2, Q3, Q4)
- ✅ All 4 points are visible and labeled
- ✅ Trend line shows **downward slope** (improvement)
- ✅ X-axis labels: Q1 2026, Q2 2026, Q3 2026, Q4 2026
- ✅ Y-axis: Energy consumption values (kWh/m²)
- ✅ Tooltip on hover shows exact values

**Fail Criteria:**
- Chart does not render = P1 (investigate frontend/API)
- Missing data points = P1 (data fetch issue)
- Flat trend (no improvement) = P2 (check if using correct data)

---

### TC-314: ESG missing quarter handled gracefully (partial data)

| Field | Value |
|-------|-------|
| **Story** | ESG partial data handling |
| **Priority** | P1 |
| **Type** | Error Handling |
| **Precondition** | - Building B-002 manually seeded with Q1-Q2 data only (no Q3, Q4) |
| **Environment** | Standard stack + partial seed data |

**Test Steps:**

1. Setup partial data for B-002:
   ```sql
   -- Delete Q3-Q4 for B-002
   DELETE FROM esg_metrics WHERE building_id = 'B-002' AND quarter IN ('Q3', 'Q4');
   ```

2. Query B-002:
   ```bash
   curl "http://localhost:8080/api/v1/esg/metrics?buildingId=B-002&year=2026"
   ```

3. Navigate to dashboard, select B-002

**Expected Results:**

- ✅ API returns **6 records** (Q1 and Q2 only, 3 metrics each)
- ✅ No **500 Internal Server Error**
- ✅ Response JSON structure intact (no nulls for missing quarters)
- ✅ Dashboard chart shows only 2 data points (Q1, Q2)
- ✅ UI message: "Partial data available for 2026 (Q1-Q2)"
- ✅ No console errors

**Fail Criteria:**
- API returns 500 error = P0 BLOCKER (must handle missing data)
- Chart crashes = P1 (frontend error handling needed)

---

### TC-315: ESG aggregate: city-wide carbon footprint Q4 2026

| Field | Value |
|-------|-------|
| **Story** | ESG city-wide aggregation |
| **Priority** | P2 |
| **Type** | API |
| **Precondition** | - Seed data loaded for all 20 buildings |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Query city-wide carbon footprint for Q4:
   ```bash
   curl "http://localhost:8080/api/v1/esg/aggregate?metric=carbon&quarter=Q4&year=2026"
   ```

2. Verify aggregation logic (manual check):
   ```sql
   SELECT SUM(value) FROM esg_metrics 
   WHERE metric_type = 'carbon' AND quarter = 'Q4' AND year = 2026;
   ```

3. Compare Q4 total vs Q1 total:
   ```bash
   curl "http://localhost:8080/api/v1/esg/aggregate?metric=carbon&quarter=Q1&year=2026"
   ```

**Expected Results:**

- ✅ API returns city-wide carbon total for Q4
- ✅ Response format:
  ```json
  {
    "metric": "carbon",
    "quarter": "Q4",
    "year": 2026,
    "totalValue": <sum>,
    "unit": "tCO2e",
    "buildingCount": 20
  }
  ```
- ✅ Q4 total is **lower than Q1 total** (improvement trend)

**Fail Criteria:**
- Aggregation returns wrong sum = P2 (SQL logic issue)
- Q4 > Q1 = P2 (trend reversed)

---

### TC-316: ESG comparison widget — two buildings side by side

| Field | Value |
|-------|-------|
| **Story** | ESG building comparison UI |
| **Priority** | P2 |
| **Type** | UI |
| **Precondition** | - Seed data loaded for B-001 and B-002 |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Navigate to ESG Dashboard
2. Click "Compare Buildings" button
3. Select Building 1: B-001
4. Select Building 2: B-002
5. Select metric: Energy Consumption
6. Click "Compare"
7. Observe chart rendering

**Expected Results:**

- ✅ Chart shows **two trend lines**:
  - B-001 (blue line)
  - B-002 (green line)
- ✅ Both lines span Q1→Q4
- ✅ Values match API response:
  ```bash
  curl "http://localhost:8080/api/v1/esg/metrics?buildingId=B-001&year=2026&metric=energy"
  curl "http://localhost:8080/api/v1/esg/metrics?buildingId=B-002&year=2026&metric=energy"
  ```
- ✅ Legend clearly labels B-001 and B-002
- ✅ Tooltip shows values for both buildings on hover

**Fail Criteria:**
- Chart does not render = P2 (UI bug)
- Values mismatch API = P2 (data binding issue)

---

## Category 5: Welford Vibration E2E (3 TCs)

**Context:** Welford running variance algorithm for vibration anomaly detection. These TCs validate end-to-end flow: baseline establishment → anomaly detection → alert generation.

**Precondition:** Run seed script: `python scripts/seed-vibration-1000.py` to create baseline (1000 samples, μ=5.2, σ=0.8)

**Algorithm:** Welford's online variance algorithm computes running mean and std dev without storing all samples.

---

### TC-317: Welford algorithm computes correct running mean after 1000 samples

| Field | Value |
|-------|-------|
| **Story** | Welford baseline establishment |
| **Priority** | P0 |
| **Type** | Algorithm Validation |
| **Precondition** | - Seed script executed: `python scripts/seed-vibration-1000.py`<br>- Seed generates 1000 readings for VIBE-001 with μ=5.2, σ=0.8 |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Run seed script:
   ```bash
   python scripts/seed-vibration-1000.py
   ```
   Expected output: "Inserted 1000 vibration readings for VIBE-001"

2. Query sensor statistics:
   ```bash
   curl "http://localhost:8080/api/v1/sensors/VIBE-001/statistics"
   ```

3. Verify statistics calculation:
   ```json
   {
     "sensorId": "VIBE-001",
     "sampleCount": 1000,
     "runningMean": <mean>,
     "runningStdDev": <stddev>,
     "lastUpdated": "<timestamp>"
   }
   ```

**Expected Results:**

- ✅ `sampleCount = 1000`
- ✅ `runningMean ≈ 5.2` (tolerance: **±0.1**)
- ✅ `runningStdDev ≈ 0.8` (tolerance: **±0.1**)
- ✅ Statistics stored in database table `sensor_statistics`
- ✅ Welford algorithm state persists (can resume after restart)

**Fail Criteria:**
- Mean outside 5.1-5.3 range = P0 BLOCKER (algorithm bug)
- StdDev outside 0.7-0.9 range = P0 BLOCKER
- Statistics not persisted = P1 (algorithm state loss on restart)

---

### TC-318: Welford alert triggers when vibration exceeds μ+3σ threshold

| Field | Value |
|-------|-------|
| **Story** | Welford anomaly detection |
| **Priority** | P0 |
| **Type** | Algorithm + Alert Integration |
| **Precondition** | - TC-317 completed (baseline established: μ=5.2, σ=0.8)<br>- Threshold: μ + 3σ = 5.2 + 3×0.8 = **7.6** |
| **Environment** | Standard stack + seed data |

**Test Steps:**

1. Verify baseline exists:
   ```bash
   curl "http://localhost:8080/api/v1/sensors/VIBE-001/statistics"
   # Confirm: mean=5.2, stddev=0.8, sampleCount=1000
   ```

2. Send anomalous reading (value **8.0** > threshold 7.6):
   ```bash
   curl -X POST "http://localhost:8080/api/v1/sensors/VIBE-001/readings" \
     -H "Content-Type: application/json" \
     -d '{ "value": 8.0, "unit": "mm/s", "sensorType": "STRUCTURAL_VIBRATION" }'
   ```

3. Wait 10 seconds for Flink processing

4. Check alerts:
   ```bash
   curl "http://localhost:8080/api/v1/alerts?module=STRUCTURAL&limit=1"
   ```

**Expected Results:**

- ✅ Alert created with:
  - Severity: **P1 or higher** (critical anomaly)
  - Message includes: "vibration anomaly detected" OR "exceeds threshold"
  - Sensor ID: VIBE-001
  - Value: 8.0 mm/s
  - Threshold: 7.6 mm/s (μ + 3σ)
- ✅ Alert created within **10 seconds** of reading POST
- ✅ Kafka topic `UIP.structural.alert.critical.v1` contains alert event

**Fail Criteria:**
- No alert generated = **P0 BLOCKER** (Welford detection broken)
- Alert generated for non-anomalous value = P0 (false positive)
- Alert delay >30s = P1 (investigate Flink processing)

---

### TC-319: Welford handles WELFORD_MIN_SAMPLES=3 — alert suppressed before baseline established

| Field | Value |
|-------|-------|
| **Story** | Welford cold start handling |
| **Priority** | P1 |
| **Type** | Algorithm Edge Case |
| **Precondition** | - New sensor VIBE-NEW-001 with **0 readings**<br>- Environment variable: `WELFORD_MIN_SAMPLES=3` (configured in backend) |
| **Environment** | Standard stack + env var |

**Test Steps:**

1. Verify new sensor has no baseline:
   ```bash
   curl "http://localhost:8080/api/v1/sensors/VIBE-NEW-001/statistics"
   # Expected: 404 or sampleCount=0
   ```

2. Send 1st high reading (value 8.0):
   ```bash
   curl -X POST "http://localhost:8080/api/v1/sensors/VIBE-NEW-001/readings" \
     -d '{ "value": 8.0, "unit": "mm/s" }'
   ```
   Wait 10s, check alerts → should be **0 alerts** (sampleCount < 3)

3. Send 2nd high reading (value 8.0):
   ```bash
   curl -X POST "http://localhost:8080/api/v1/sensors/VIBE-NEW-001/readings" \
     -d '{ "value": 8.0, "unit": "mm/s" }'
   ```
   Wait 10s, check alerts → should be **0 alerts** (sampleCount < 3)

4. Send 3rd high reading (value 8.0):
   ```bash
   curl -X POST "http://localhost:8080/api/v1/sensors/VIBE-NEW-001/readings" \
     -d '{ "value": 8.0, "unit": "mm/s" }'
   ```
   Wait 10s, check alerts → **alert should be created** (sampleCount ≥ 3)

**Expected Results:**

- ✅ **After 1st and 2nd reading:** No alert generated (insufficient samples)
- ✅ Sensor statistics updated but alert suppressed
- ✅ **After 3rd reading:** Alert generated (baseline established with n≥3)
- ✅ Alert message: "Vibration anomaly detected for VIBE-NEW-001"
- ✅ `sensor_statistics` table shows `sampleCount = 3` after 3rd reading

**Fail Criteria:**
- Alert generated before 3rd sample = P1 (false alarm on cold start)
- No alert after 3rd sample = P1 (algorithm not engaging)
- Backend crashes on sampleCount=0 edge case = P0 BLOCKER

---

## Execution Prerequisites Summary

### Infrastructure Requirements

| Environment | Description | Required For |
|-------------|-------------|--------------|
| **Standard Stack** | `docker-compose.yml` | All TCs except HA and Mobile |
| **HA Stack** | `docker-compose.yml` + `docker-compose.ha.yml` | TC-304 to TC-308 only |
| **Mobile Simulator** | iOS Simulator or Android Emulator + Expo Go | TC-298 to TC-303 only |

### Seed Data Requirements

| Script | Description | Required For |
|--------|-------------|--------------|
| `scripts/seed-esg-multi-quarter.sql` | ESG data for Q1-Q4 2026 (20 buildings × 3 metrics × 4 quarters) | TC-309 to TC-316 |
| `scripts/seed-vibration-1000.py` | 1000 vibration samples (μ=5.2, σ=0.8) for Welford baseline | TC-317 to TC-319 |

### Environment Variables

| Variable | Value | Required For |
|----------|-------|--------------|
| `WELFORD_MIN_SAMPLES` | `3` | TC-319 only |

---

## Test Execution Checklist

**Before Starting Sprint 9 Regression:**

- [ ] Standard stack UP and healthy: `docker-compose up -d`
- [ ] HA stack UP (for HA TCs): `docker-compose -f docker-compose.yml -f docker-compose.ha.yml up -d`
- [ ] Mobile simulator configured (iOS/Android)
- [ ] Seed data loaded:
  - [ ] `psql -U postgres -d uip < scripts/seed-esg-multi-quarter.sql`
  - [ ] `python scripts/seed-vibration-1000.py`
- [ ] Environment variables set (if testing TC-319)
- [ ] Keycloak realm configured with pilot users:
  - [ ] `pilot1@hcmc.gov.vn` (pilot-operator role)
  - [ ] `viewer1@hcmc.gov.vn` (pilot-viewer role)
  - [ ] `operator@hcmc.gov.vn` (city-operator role)

**During Execution:**

- [ ] Track PASS/FAIL/BLOCKED status for each TC
- [ ] For each FAIL: create bug report in `docs/mvp3/qa/bug-tracker.md`
- [ ] For each BLOCKED: document reason (env missing, dependency issue, etc.)

**After Execution:**

- [ ] Generate execution report: `docs/mvp3/qa/sprint9-test-execution-report.md`
- [ ] Update regression baseline count (target: 319 TCs)
- [ ] Upload evidence artifacts (screenshots for mobile TCs, logs for HA TCs)

---

## Success Criteria (Sprint 9 Goal)

**GATE-PASS Requirements:**

1. ✅ **≥30/34 TCs PASS** (88% pass rate minimum)
2. ✅ **All P0 TCs PASS** (15 TCs — blocking for pilot external access)
3. ✅ **<5 TCs BLOCKED** (down from 40 in Sprint 8)
4. ✅ **All FAIL TCs have bug reports** with root cause analysis

**Risk Thresholds:**

- **RED (BLOCKER):** ≥3 P0 TCs FAIL → Do not proceed with pilot
- **YELLOW (CONCERN):** ≥5 P1 TCs FAIL → Review with SA before pilot
- **GREEN (OK):** All P0 PASS, ≤2 P1 FAIL → Pilot can proceed

---

## Notes

1. **HA TCs (TC-304 to TC-308)** are infrastructure-heavy and may require dedicated HA staging server. Do NOT run on local dev laptop if performance is limited.

2. **Mobile TCs (TC-298 to TC-303)** require Expo Go app installed on simulator. If simulator setup is blocked, these TCs remain BLOCKED (acceptable for Sprint 9 as P1/P2).

3. **ESG Multi-Quarter TCs** depend on seed data quality. If seed script has bugs, TCs may fail — fix seed script first, then retest.

4. **Welford TCs** validate mathematical correctness of algorithm. If TC-317 fails (mean/stddev out of range), Welford implementation has a bug — P0 BLOCKER.

5. **Pilot User TCs** are critical for external pilot readiness. Any P0 failure in this category blocks pilot launch.

---

**Document Owner:** UIP QA Engineer  
**Last Updated:** 2026-06-04  
**Review Status:** DRAFT — Pending QA Lead Approval

---

_End of Sprint 9 New Test Cases Document_
