# Sprint 6 — UI Full Exploration & Bug Fix Summary
**Date:** 2026-06-01  
**Session:** Multi-session continuation (WorkflowConfigPage bug fixes + full UI sweep)  
**Tester:** GitHub Copilot (automated Playwright session)

---

## 1. Bug Fixes Implemented

### BUG-005 — Escape Key Propagation in FilterConditionBuilder
**File:** `frontend/src/pages/WorkflowConfigPage.tsx`  
**Symptom:** Pressing Escape while an Op dropdown was open in FilterConditionBuilder would close the entire parent Dialog.  
**Root cause:** MUI Select emits a keyboard Escape event that bubbled up to the Dialog's `onClose` handler.  
**Fix:** Added `SelectProps={{ onKeyDown: (e) => { if (e.key === 'Escape') e.stopPropagation(); } }}` to both Field and Op Select inputs.  
**Verified:** `dialog still open: true` after pressing Escape with dropdown open. ✅

### BUG-006 — FilterConditionBuilder Conditions Not Synced on Edit Re-open
**File:** `frontend/src/pages/WorkflowConfigPage.tsx`  
**Symptom:** Opening an existing Trigger Config for editing showed an empty conditions list, even though the backend had saved conditions.  
**Root cause:** FilterConditionBuilder maintained local `conditions` state initialized from `value` prop once (empty `[]` on mount). Re-opening the edit dialog updated `filterConditions` JSON in the parent, but the stale local state was never refreshed.  
**Fix:** Removed local `conditions` state entirely. Component is now fully controlled — `conditions` derived from `JSON.parse(value)` on every render.  
**Verified:** Existing 3 conditions loaded correctly on edit re-open. ✅

### Round-Trip Test (BUG-006 continuation)
**Test:** Added a 4th condition `districtCode EQ "HCM"` → clicked Update → re-opened for edit.  
**API response confirmed:** `filterConditions: [{"op":"EQ","field":"module","value":"ENVIRONMENT"},{"op":"EQ","field":"measureType","value":"AQI"},{"op":"GT","field":"value","value":150},{"op":"EQ","field":"districtCode","value":"HCM"}]`  
**Result:** All 4 conditions persist and reload correctly. ✅

---

## 2. Pages Explored

### Dashboard (`/dashboard`) ✅
| KPI | Value |
|-----|-------|
| Active Sensors | 8 |
| AQI Current | 154 |
| Open Alerts | 0 |
| Carbon (tCO₂e) | 0 t |

Dashboard loads data from API. KPI cards rendered correctly.

---

### Alerts (`/alerts`) ✅ with known issues

**What works:**
- Alert table renders with correct columns: Severity, Rule, Module, Sensor, Value, Status, Detected, Action
- Detail drawer opens on row click showing: severity badge, status badge, module, measureType, value, threshold, sensorId, detectedAt, escalation info (acknowledgedBy + at)
- Status filter and severity filter dropdowns working
- Checkbox selection for bulk acknowledge
- Pagination chip showing total count

**Known issues (data/design gaps, not UI bugs):**

| # | Issue | Type | Detail |
|---|-------|------|--------|
| 1 | Rule column shows "—" | Data gap | Backend API returns `ruleId: null, ruleName: null` for all seed alerts. Alerts not linked to Trigger Config rules. |
| 2 | Action column empty for ESCALATED | By design | Backend has only `acknowledge` and `escalate` endpoints — no "resolve" or "close" action. With all 10 test alerts in ESCALATED status, neither Ack (OPEN only) nor Escalate (OPEN or ACKNOWLEDGED) buttons render. This is a UX gap but not a frontend bug. |
| 3 | SSE status: "Offline" | Infrastructure | EventSource connection not established in current Docker deployment. Non-blocking for functionality. |

**API verification:**
```json
{
  "ruleId": null,
  "ruleName": null,
  "status": "ESCALATED",
  "sensorId": "ENV-002",
  "module": "ENVIRONMENT",
  "value": 305.0,
  "threshold": 300.0
}
```

---

### BMS Devices (`/bms/devices`) ✅
- 5 devices displayed in table: ELEC-METER-FLOOR1, HVAC-AHU-B2, WATER-METER-ROOF, IOT-GATEWAY-FLOOR3, UPS-SERVER-ROOM
- Protocols: MODBUS_TCP, BACNET_IP, MANUAL, MQTT
- "Send command" and "Delete" action buttons present per row
- "Add Device" button in header
- Minor cosmetic: WATER-METER-ROOF shows "—:" in Host column (empty host field with colon suffix)
- All devices status UNKNOWN / Last Seen "Never" — expected (no real BMS connectivity in POC)

---

### AI Workflows (`/ai-workflow`) ✅ (all 4 tabs)

**Process Instances tab:**
- 39 total instances, paginated
- Detail drawer: shows full instance info including AI decision variables (confidenceScore, actionType, routingDecision, notifiedResidents, dataSource)
- "All Variables" accordion expandable

**Process Definitions tab:**
- 7 process definitions loaded from Camunda
- BPMN diagram view functional per definition

**Designer tab:**
- bpmn-js editor loaded with Workflow 1
- Node Palette visible on left
- Non-blocking console warning: `keyboard.bindTo` — known upstream bpmn-io issue (#661)

**Live Demo tab (Citizen mode):**
- Manual event fired successfully (HTTP 202)
- DecisionRouter: confidence 82% → OPERATOR_QUEUE routing
- Process instance created and visible in Instances tab

**Live Demo tab (IoT mode):**
- Full pipeline triggered: MQTT → Redpanda → Kafka → Flink → Alert → Camunda → NOTIFY_CITIZENS
- Confidence 91% → AUTO_EXECUTE
- 1,500 residents notified

---

### Trigger Config (`/workflow-config`) ✅ (post bug fix)
- FilterConditionBuilder fully controlled (no local state)
- Edit dialog loads existing conditions correctly
- Escape key no longer closes parent Dialog
- CRUD round-trip verified with 4 conditions

---

### Citizens (`/citizen`) ✅ (correct behavior)
- Route guarded by `ROLE_CITIZEN` — admin correctly redirected to `/dashboard`
- Mobile citizen portal (bills, AQI, notifications, profile) visible in route config but not accessible with admin credentials

---

### Admin (`/admin`) ✅ (from previous sessions)
- Tenants tab: feature flag management, user table, invite user dialog
- Role fix: ROLE values corrected in invite dialog
- EmailService: graceful degradation when mail server unavailable

---

## 3. Backend Domain Issues Found

### Alert ruleId Not Populated (Data Gap)
- **Location:** `AlertEvent` entity / `AlertEventKafkaConsumer` or seed data
- **Issue:** All alerts in DB have `ruleId = null` / `ruleName = null`
- **Impact:** Rule column in Alerts page always shows "—"
- **Recommendation:** Link alert events to Trigger Config rules when alert is generated from a rule evaluation. Populate `ruleId` and `ruleName` fields in `AlertService.createAlert()` or `AlertEventKafkaConsumer.consume()`.

### No Alert Resolution Endpoint (Design Gap)
- **Location:** `AlertController`
- **Issue:** Only `PUT /{id}/acknowledge` and `PUT /{id}/escalate` exist. No endpoint to resolve/close an ESCALATED alert.
- **Impact:** ESCALATED alerts are terminal — no further state transitions possible via UI.
- **Recommendation:** Add `PUT /{id}/resolve` endpoint + `RESOLVED` status. Update UI Action column to show Resolve button for ESCALATED alerts.

---

## 4. UI Design Observations

| Component | Observation |
|-----------|-------------|
| FilterConditionBuilder | Now fully controlled — safe for future extensions |
| Alert detail drawer | Clean layout, all key fields visible |
| Alert SSE chip | Shows "Offline" in Docker deployment — no action from user POV |
| BMS Devices table | All devices UNKNOWN — production would need BMS integration |
| Dashboard KPIs | Open Alerts shows 0 (correct, all are ESCALATED not OPEN) |
| AI Workflow timestamps | New instances show "about 7 hours ago" — timezone artifact, non-critical |

---

## 5. Deployment Notes

- **Frontend:** Docker container `frontend`, port 3000 (nginx)
- **Backend:** Docker container `uip-backend`, port 8080
- **Auth:** JWT via `POST /api/v1/auth/login` (`admin` / `admin_Dev#2026!`)
- **JWT refresh:** Navigate to `/admin` if 401 errors appear

---

## 6. Recommended Next Steps

| Priority | Item |
|----------|------|
| P2 | Add `RESOLVED` status + `PUT /{id}/resolve` backend endpoint |
| P2 | Populate `ruleId`/`ruleName` when creating alert from Trigger Config evaluation |
| P3 | Fix BMS Device Host display for MANUAL protocol (show "—" without colon suffix) |
| P3 | Resolve SSE EventSource connectivity in Docker deployment |
| P3 | Add Resolve action button to Alert desktop table (when `RESOLVED` endpoint added) |
