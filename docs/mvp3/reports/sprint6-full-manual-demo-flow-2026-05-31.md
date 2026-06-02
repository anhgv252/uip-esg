# Sprint 6 — Full Manual Demo Flow (Happy Case)

**Date:** 2026-05-31  
**Audience:** PO, City Authority Stakeholders, Internal Tech Leads  
**Demo Owner:** Tester  
**Goal:** Demo full luong Sprint 6 voi narrative ro rang, bang chung API, va tieu chi pass/fail cu the.

## 0) Demo Duration and Structure

- Total: 25-30 minutes
- Segment A (5 min): System warm-up and login
- Segment B (12 min): Core Sprint 6 capabilities
- Segment C (8 min): ESG + Forecast + closeout evidence
- Segment D (5 min): Q&A and sign-off checklist

## 1) Scope Covered (Sprint 6)

1. Dashboard operational snapshot
2. Alerts management and flood/critical visibility
3. BMS Devices module entry and management surface
4. AI Workflow dashboard navigation (instances/definitions/live demo tabs)
5. ESG metrics screen and report generation action
6. Energy Forecast section behavior
7. API evidence trail for core backend viability

## 2) Pre-Demo Setup (3-5 minutes)

### 2.1 Browser tabs

1. Main UI: http://localhost:3000/login
2. Optional backup UI tabs:
- http://localhost:3000/dashboard
- http://localhost:3000/alerts
- http://localhost:3000/bms/devices
- http://localhost:3000/ai-workflow
- http://localhost:3000/esg

### 2.2 Terminal preparation (no narration yet)

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | /usr/bin/python3 -c "import sys,json; print(json.load(sys.stdin)['accessToken'])")

curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/alerts
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/esg/carbon?year=2025"
curl -s -H "Authorization: Bearer $TOKEN" -H "X-Tenant-ID: hcm" http://localhost:8080/api/v1/buildings
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/forecast/energy?buildingId=65c06d23-3cf3-4490-96a6-ac8ff2a17f2c&horizonDays=30"
```

## 3) Full Demo Script (Happy Case)

## Segment A — Login and Operations Snapshot (5 min)

### Step A1: Login

- Action:
  1. Open http://localhost:3000/login
  2. Login with:
     - username: admin
     - password: admin_Dev#2026!

- Presenter line:
  - "Sprint 6 da san sang luong van hanh chinh, toi se di qua tu dashboard den workflows va ESG analytics."

- Expected:
  - Redirect to /dashboard

### Step A2: Dashboard verification

- Action:
  1. Stay on /dashboard
  2. Point to KPI cards: Active Sensors, Open Alerts, Carbon

- Presenter line:
  - "Dashboard tong hop hien trang thai he thong theo thoi gian gan thuc, va se duoc doi chieu bang API ngay sau do."

- Expected:
  - Dashboard load stable, KPI cards render

## Segment B — Core Sprint 6 Capabilities (12 min)

### Step B1: Alerts management

- Action:
  1. Navigate to http://localhost:3000/alerts
  2. Show list, severity/status badges, filter controls

- Presenter line:
  - "Day la alert management cho operations team, bao gom muc do nghiem trong va trang thai xu ly."

- Expected:
  - Alert list renders
  - Filter controls responsive
  - Note: stream co the hien Offline neu realtime stream bi gian doan

### Step B2: BMS Devices surface

- Action:
  1. Navigate to http://localhost:3000/bms/devices
  2. Show page header and Add Device action

- Presenter line:
  - "Sprint 6 da mo rong BMS management surface de tiep nhan va quan ly thiet bi toa nha."

- Expected:
  - Header BMS Devices visible
  - Add Device button visible

### Step B3: AI Workflow dashboard

- Action:
  1. Navigate to http://localhost:3000/ai-workflow
  2. Show tabs: Process Instances, Process Definitions, Live Demo
  3. Switch at least 2 tabs

- Presenter line:
  - "AI workflow layer cho phep quan sat instance va cau hinh process definitions, la nen tang cho automation phase tiep theo."

- Expected:
  - Tabs visible and navigable
  - Data table/panels render without crash

## Segment C — ESG and Forecast (8 min)

### Step C1: ESG Metrics and report action

- Action:
  1. Navigate to http://localhost:3000/esg
  2. Show trend chart by building
  3. Show Generate ESG Report controls (year/quarter + button)

- Presenter line:
  - "ESG page gom trend theo building va luong tao bao cao dinh ky cho city authority."

- Expected:
  - ESG Metrics screen renders
  - Trend chart visible
  - Generate report controls visible

### Step C2: Energy Forecast (Happy Case target)

- Action:
  1. In Energy Forecast section, choose one building
  2. Show forecast data points and curve/list

- Presenter line (happy case):
  - "Forecast module tra ve du bao nang luong theo building voi horizon 30 ngay."

- Happy-case expected:
  - API returns `points.length > 0`
  - UI renders forecast points (not empty state)

### Step C3: API evidence on terminal (live trust layer)

- Action:
  1. Show login call result
  2. Show alerts API result
  3. Show ESG carbon result
  4. Show forecast API result for selected building

- Presenter line:
  - "Toan bo UI vua demo co doi chieu voi API responses de dam bao tinh xac thuc cua du lieu."

- Expected:
  - HTTP 200 for login/alerts/carbon
  - Forecast endpoint returns non-empty points in happy case

## Segment D — Closeout and Sign-off (5 min)

### Step D1: Sprint 6 acceptance checklist live

- [ ] Login and dashboard pass
- [ ] Alerts management pass
- [ ] BMS surface pass
- [ ] AI Workflow tabs pass
- [ ] ESG metrics/report UI pass
- [ ] Forecast non-empty points pass
- [ ] API evidence consistency pass

### Step D2: Final statement template

- If all pass:
  - "Sprint 6 full happy-case flow da demo thanh cong va dat criteria cho gate tiep theo."
- If forecast fails:
  - "Core flow dat, nhung Forecast chua dat happy case. Chung toi da co bug record va gate re-test truoc full sign-off."

## 4) Current Environment Note (Important)

Tai thoi diem 2026-05-31, forecast dang co blocker:
- `GET /api/v1/forecast/energy` tra ve:
  - model = NONE
  - isFallback = true
  - points = []
- UI hien: "No forecast data available"

Vi vay, full happy case cho forecast hien tai chua dat.  
Tham chieu bug: docs/mvp3/testing/bug-energy-forecast-empty-points-2026-05-31.md

## 5) Re-Test Gate for Full Approval

Chi sign-off full happy case khi dat dong thoi:
1. Forecast API tra du lieu du bao (`points.length > 0`) cho it nhat 1 building hop le
2. ESG Energy Forecast UI render du lieu du bao thay vi empty state
3. Luong demo Segment A-D chay lien mach khong blocker P1

## 6) Optional Extended Scenario (if stakeholder asks)

1. Show Alerts API count and correlate with UI count.
2. Demonstrate AI Workflow tab switch and explain operational purpose.
3. Demonstrate ESG report generation action and expected lifecycle.
4. Explain forecast degradation handling and mitigation plan transparently.

---

**Demo Status Template**

- Core flow (Dashboard/Alerts/BMS/AI/ESG): PASS/FAIL
- Forecast happy case: PASS/FAIL
- Overall Sprint 6 full manual demo: PASS/FAIL
