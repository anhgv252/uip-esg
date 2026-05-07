# Sprint 6 — UAT Preparation Document

**Platform:** UIP Smart City ESG Platform
**Target:** Tier 1 Customer UAT
**Sprint:** MVP2-6 (Buffer + Final UAT)

---

## 1. UAT Scope

| Module | Features to Test |
|--------|-----------------|
| Dashboard | 4 stat cards (sensors, AQI, alerts, carbon), real-time update |
| Environment | AQI gauges, sensor status table, trend chart, live alert banner |
| ESG Metrics | 3 KPI cards, energy/carbon trend chart, report generation |
| Alert Management | Alert list, filter by status/severity, acknowledge, escalate, detail drawer |
| City Ops Center | Sensor map, alert feed panel, district filter, traffic toggle |
| Traffic Management | Vehicle count chart, incident table, intersection filter |
| Tenant Admin | Overview, user invite, building config, usage report, settings |
| Citizen Portal (PWA) | Mobile bills, AQI gauge, push notifications, offline mode |
| AI Workflow | Process definitions, instances, workflow config |
| Multi-tenancy | Tenant isolation, feature flags, partner theme |

---

## 2. UAT Environment Checklist

### Infrastructure
- [ ] Backend running on `http://localhost:8080`
- [ ] Frontend running on `http://localhost:3000`
- [ ] PostgreSQL (TimescaleDB) healthy
- [ ] Redis healthy
- [ ] Kafka healthy

### Test User Accounts
| Username | Password | Role | Purpose |
|----------|----------|------|---------|
| admin | admin_Dev#2026! | ADMIN | Full access |
| tadmin | (set by invite) | TENANT_ADMIN | Tenant admin features |
| operator | (set by invite) | OPERATOR | Operations |
| citizen | (set by invite) | CITIZEN | Citizen portal |

### Test Data Requirements
- [ ] ≥5 sensors seeded (mix ONLINE/OFFLINE)
- [ ] ≥10 alerts (mix severity: LOW/MEDIUM/HIGH/CRITICAL, status: OPEN/ACKNOWLEDGED/ESCALATED)
- [ ] ESG summary data (energy, water, carbon)
- [ ] ≥3 bills for citizen user
- [ ] ≥2 buildings for tenant
- [ ] BPMN processes deployed (7 AI scenarios)

---

## 3. UAT Test Scenarios

### Scenario 1: Login & Dashboard
**Steps:**
1. Navigate to http://localhost:3000
2. Login with admin credentials
3. Verify dashboard loads with 4 stat cards

**Expected:** Dashboard shows Active Sensors, AQI Current, Open Alerts, Carbon values
**Pass / Fail**

### Scenario 2: Environment Monitoring
**Steps:**
1. Click "Environment" in sidebar
2. Verify AQI gauges render for each station
3. Click a sensor to view 24h AQI trend
4. Verify sensor status table shows all sensors

**Expected:** AQI gauges with color-coded values, trend chart renders, sensor table populated
**Pass / Fail**

### Scenario 3: ESG Report Generation
**Steps:**
1. Click "ESG Metrics" in sidebar
2. Verify 3 KPI cards (Energy, Water, Carbon)
3. Toggle between Energy and Carbon chart
4. Click "Generate Report" and verify success notification

**Expected:** KPIs display values, chart renders by building, report generation triggers
**Pass / Fail**

### Scenario 4: Alert Acknowledge & Escalate
**Steps:**
1. Click "Alerts" in sidebar
2. Select an OPEN alert
3. Click Acknowledge
4. Verify alert status changes to ACKNOWLEDGED
5. Click Escalate on another alert

**Expected:** Alert status updates correctly, detail drawer shows updated status
**Pass / Fail**

### Scenario 5: City Operations Center
**Steps:**
1. Click "City Ops" in sidebar
2. Verify map renders with sensor markers
3. Toggle Traffic overlay
4. Verify alert feed panel shows recent alerts

**Expected:** Map displays with sensor markers, traffic segments visible when toggled, alert feed updates
**Pass / Fail**

### Scenario 6: Tenant Admin — User Invite
**Steps:**
1. Login as TENANT_ADMIN
2. Click "Tenant Admin" → "Users"
3. Click "Invite User"
4. Enter email, submit
5. Verify success toast appears
6. Verify new user appears in table

**Expected:** Invite flow completes, user added to list
**Pass / Fail**

### Scenario 7: Tenant Admin — Building Config
**Steps:**
1. Navigate to "Buildings" tab
2. Toggle a building active/inactive
3. Verify switch state changes
4. Verify snackbar feedback

**Expected:** Toggle works, visual feedback confirms change
**Pass / Fail**

### Scenario 8: Tenant Admin — Settings
**Steps:**
1. Navigate to "Settings" tab
2. Verify branding section shows
3. Click "Save Settings"
4. Verify success toast

**Expected:** Settings form displays, save triggers confirmation
**Pass / Fail**

### Scenario 9: Citizen Portal — Mobile PWA
**Steps:**
1. Navigate to /citizen on mobile viewport (or use DevTools mobile mode)
2. Verify bottom tab navigation renders
3. Switch between Bills, AQI, Notifications tabs
4. Enable push notification toggle on Notifications page

**Expected:** Mobile layout with bottom tabs, each tab loads content, push toggle clickable
**Pass / Fail**

### Scenario 10: Citizen Portal — View Bills
**Steps:**
1. On citizen portal, tap "Bills" tab
2. Verify bill list shows
3. Tap a bill to view detail
4. Verify tier breakdown visible

**Expected:** Bill list renders, detail page shows charges and tiers
**Pass / Fail**

### Scenario 11: Multi-tenant Isolation
**Steps:**
1. Login as tenant A admin
2. Note visible data (sensors, alerts, users)
3. Logout, login as tenant B admin
4. Verify no tenant A data visible

**Expected:** Each tenant sees only their own data
**Pass / Fail**

### Scenario 12: Responsive Design
**Steps:**
1. Open dashboard in desktop viewport (1920px)
2. Resize to mobile (375px)
3. Verify layout adapts: cards stack, tables become cards
4. Test on Alerts, Environment, Traffic pages

**Expected:** All pages usable on mobile, touch targets ≥44px
**Pass / Fail**

### Scenario 13: AI Workflow Dashboard
**Steps:**
1. Click "AI Workflow" in sidebar
2. Verify process definitions tab shows 7 AI scenarios
3. Click Process Instances tab
4. Verify instance list renders

**Expected:** 7 process definitions visible, instances tab functional
**Pass / Fail**

### Scenario 14: PWA Offline Mode
**Steps:**
1. On citizen portal, load bills page
2. Open DevTools → Network → Offline
3. Refresh page
4. Verify cached bills still visible
5. Re-enable network

**Expected:** Bills page loads from cache when offline
**Pass / Fail**

### Scenario 15: Logout & Session
**Steps:**
1. Click user menu → Logout
2. Verify redirect to login page
3. Verify protected pages redirect to login
4. Login again, verify session restored

**Expected:** Logout clears session, protected routes require re-auth
**Pass / Fail**

---

## 4. UAT Sign-off Template

### Participant Info
| Field | Value |
|-------|-------|
| Customer Name | |
| Organization | |
| Tester Name | |
| Date | |
| Environment | Dev / Staging |

### Results Summary
| Scenario | Result | Notes |
|----------|--------|-------|
| 1. Login & Dashboard | PASS / FAIL | |
| 2. Environment Monitoring | PASS / FAIL | |
| 3. ESG Report Generation | PASS / FAIL | |
| 4. Alert Ack & Escalate | PASS / FAIL | |
| 5. City Ops Center | PASS / FAIL | |
| 6. Tenant Admin — Users | PASS / FAIL | |
| 7. Tenant Admin — Buildings | PASS / FAIL | |
| 8. Tenant Admin — Settings | PASS / FAIL | |
| 9. Citizen Portal PWA | PASS / FAIL | |
| 10. Citizen Bills | PASS / FAIL | |
| 11. Multi-tenant Isolation | PASS / FAIL | |
| 12. Responsive Design | PASS / FAIL | |
| 13. AI Workflow | PASS / FAIL | |
| 14. PWA Offline | PASS / FAIL | |
| 15. Logout & Session | PASS / FAIL | |

**Pass Rate:** ___/15 (___%)
**Required:** ≥95% (≥15/15 hoặc 14/15)

### Findings

| # | Severity | Description | Module |
|---|----------|-------------|--------|
| 1 | P0/P1/P2/P3 | | |
| 2 | | | |
| 3 | | | |

### Enhancement Requests

| # | Description | Priority |
|---|-------------|----------|
| 1 | | Must/Nice-to-have |
| 2 | | |

### Overall Verdict

- [ ] **PASS** — Ready for production
- [ ] **PASS with conditions** — Minor issues, production ready with fixes
- [ ] **FAIL** — Critical issues, needs re-test

**Customer Sign-off:** _______________ Date: _______________
**Dev Team Lead:** _______________ Date: _______________
