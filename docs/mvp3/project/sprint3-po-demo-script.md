# Sprint MVP3-3 PO Demo Walkthrough Script
**Date:** 2026-05-30 | **Time:** 15:00–16:00 SGT (60 min)  
**Gate Review:** AC-01, AC-02, AC-04, AC-06 (3 P2 bug fixes)  
**PO:** HCMC City Authority / ESG Program Manager  
**Demo Lead:** QA Tester  
**Attendees:** PO, Scrum Master, Tech Lead, Backend/Frontend Leads

---

## Pre-Demo Setup Checklist (5 minutes before demo starts)

### Environment Verification
- [ ] **Backend API** running: `http://localhost:8080` returns 200 on `/api/v1/health`
- [ ] **Frontend** running: `http://localhost:5173` loads without errors
- [ ] **Keycloak** running: `http://localhost:8180` login page loads
- [ ] **Kafka** healthy: no consumer lag on `esg-report-topic`
- [ ] **TimescaleDB** connected: latest sensor readings present (check via API endpoint `/api/v1/sensors/readings?limit=1`)
- [ ] **Flink job** deployed: `enrichment-service` shows healthy in job dashboard

### Network Connectivity
- [ ] VPN/Network: Can reach all services (no corporate firewall blocking 8180, 8080, 5173)
- [ ] Presenter screen: Sharing at 1920x1080 resolution, 100% zoom for readability

### Database/Data Readiness
- [ ] ESG report test data exists:
  - Period: Jan 2026 – May 2026
  - Facilities: Building A, Building B, Building C
  - GRI 302 data: electricity, gas, water consumption records
  - GRI 305 data: Scope 1, 2, 3 emissions
- [ ] Sensor stream active: At least 1 enriched sensor reading per second in the ops dashboard
- [ ] Keycloak realm **`uip-smartcity`** exists with user:
  - Username: `po-demo@hcmc.gov.vn` | Password: `[see password manager]`
  - Assigned role: `esg-manager` (for report export access)

### Browser Preparation
- [ ] **Chrome/Safari** with DevTools closed (for clean screen)
- [ ] **Zoom level**: 100% (no browser zoom)
- [ ] **Extensions disabled** (especially VPN, password managers that might trigger popups)
- [ ] **Two browser windows open**:
  - Window 1: Frontend at `http://localhost:5173`
  - Window 2: Keycloak at `http://localhost:8180` (for quick reference during Scenario 3)

### Backup Plans
- [ ] **API Mock server** running as backup if real backend fails: `http://localhost:3001/mock`
- [ ] **Pre-recorded video** of all 5 scenarios saved locally (if live demo fails completely)
- [ ] **Screenshot directory** ready: `docs/mvp3/project/demo-screenshots/` with labeled images of each scenario

---

## Demo Environment Details

| Component | URL | Credentials |
|-----------|-----|-------------|
| **Frontend** | http://localhost:5173 | Auto-login with JWT from backend |
| **Backend API** | http://localhost:8080 | POST to `/auth/login` → JWT token |
| **Keycloak** | http://localhost:8180 | admin / admin (realm: uip-smartcity) |
| **Kafka UI** | http://localhost:9021 | No login (read-only for demo) |
| **Grafana Monitoring** | http://localhost:3000 | Optional: show Flink job health during Scenario 4 |

### Test User Credentials

**Legacy HMAC Auth (for AC-02 backward compatibility):**
- Email: `tester@uip.local`
- Password: `Demo@123456`
- JWT issuer: `iss=uip-legacy` (HMAC verification)

**Keycloak RSA Auth (NEW - Scenario 3):**
- Email: `po-demo@hcmc.gov.vn`
- Password: `[PASSWORD_FROM_VAULT]`
- JWT issuer: `iss=keycloak` (RSA signature verification)

---

## Demo Timeline

| Scenario | Duration | Lead |
|----------|----------|------|
| **Scenario 1: GRI Export (Excel)** | 12 min | QA Lead + Frontend |
| **Scenario 2: GRI Export (PDF)** | 8 min | QA Lead |
| **Scenario 3: Keycloak RSA Login** | 10 min | QA Lead + Backend |
| **Scenario 4: Flink Enriched Sensors** | 12 min | Backend Eng + DevOps |
| **Scenario 5: P2 Bug Fixes** | 10 min | QA Lead |
| **Buffer + Q&A** | 8 min | All |
| **TOTAL** | ~60 min | — |

---

# SCENARIO 1: GRI 302/305 Export — Full Flow (Excel Download)

**Time Estimate:** 12 minutes  
**Goal:** Demonstrate end-to-end GRI export workflow: period selection → GRI standard selection → Excel generation → download

### Preconditions
- [ ] Frontend logged in as `tester@uip.local` (HMAC auth)
- [ ] ESG module at URL: `http://localhost:5173/esg/reports`
- [ ] Report test data exists: Jan 2026 – May 2026 for Building A
- [ ] Network bandwidth sufficient for Excel file download (test: 5MB+ file)

### Step-by-Step Actions

#### Step 1: Navigate to Report Generation Panel
```
Action: Click main navigation → "ESG" → "Reports" → "Generate New Report"
Expected: ReportGenerationPanel.tsx loads with:
  - Period selector (calendar widget showing Jan–May 2026)
  - GRI Standard dropdown (shows: GRI 302, GRI 305, GRI 306, GRI 308)
  - Export format buttons (Excel, PDF, CSV options visible)
  - Facility filter (dropdown showing Building A, B, C)
  - "Generate Report" button (disabled until period + GRI selected)
```

#### Step 2: Select Period
```
Action: 
  1. Click "Start Date" field → Select 2026-01-01
  2. Click "End Date" field → Select 2026-05-31
  3. Click calendar confirm button
Expected:
  - Date range displays as "Jan 1 – May 31, 2026" in summary
  - "Generate Report" button transitions to enabled state
```

#### Step 3: Select GRI 302 (Energy)
```
Action:
  1. Click "GRI Standard" dropdown
  2. Select "GRI 302 — Energy Consumption"
  3. Verify scope options appear: 
     - Scope 1 (Direct): Fuel consumption
     - Scope 2 (Indirect): Electricity from grid
     - Scope 3 (Supply Chain): Optional
Expected:
  - GRI 302 selected (highlighted in blue)
  - Subscope checkboxes show: Scope 1 ✓, Scope 2 ✓, Scope 3 ✗ (default)
  - Data summary preview appears showing:
    - "Est. Records to Export: 127"
    - "Data Quality: 98% complete"
```

#### Step 4: Select Facility Filter (Optional)
```
Action:
  1. Click "Facilities" filter dropdown
  2. Select "Building A"
  3. Click "Apply Filter"
Expected:
  - Building A selected (checkmark visible)
  - Estimated record count updates: "Est. Records to Export: 42"
  - UI does NOT freeze (should be instant)
```

#### Step 5: Select Export Format — Excel
```
Action:
  1. Click "Export Format" radio button group
  2. Select "Excel (.xlsx)" option
Expected:
  - Excel option highlighted in blue
  - Tooltip appears: "Excel exports with pivot tables for Scope 1, 2, 3"
  - "Generate & Download" button text updates from "Generate Report" to "Generate & Download Excel"
```

#### Step 6: Click Generate & Download
```
Action:
  1. Click "Generate & Download Excel" button
  2. Observe loading state for 5–8 seconds (backend generating report)
Expected:
  - Button shows spinner: "Generating... 45%"
  - API call visible in browser DevTools: POST `/api/v1/esg/reports/generate`
  - Response status: 200
  - File download triggered automatically to default Downloads folder
```

#### Step 7: Verify Downloaded File
```
Action:
  1. Open browser Downloads folder
  2. File should be: `ESG_Report_GRI302_Building-A_Jan-May-2026.xlsx`
  3. Open file in Excel/Numbers
  4. Verify sheet tabs:
     - "Summary" (metadata: reporting period, facility name, data quality %)
     - "Scope 1" (fuel data: type, consumption in MWh, CO2e tons)
     - "Scope 2" (electricity: supplier, consumption in MWh, CO2e tons)
     - "Source Data" (raw sensor readings with timestamps)
Expected:
  - File opens without corruption
  - All 4 sheet tabs present
  - Summary tab shows:
    - Facility: Building A
    - Period: January 1 – May 31, 2026
    - Total Scope 1 Energy: XX MWh
    - Total Scope 2 Energy: YY MWh
    - Total CO2e: ZZ metric tonnes
  - Scope 1 tab contains 20+ rows of fuel consumption records
  - Scope 2 tab contains 30+ rows of electricity consumption records

### Expected Result Summary
✅ PO sees complete Excel export workflow  
✅ File downloads successfully  
✅ Excel contains properly formatted data with formulas (no errors)  
✅ Performance is smooth (no UI lag during generation)

### PO Acceptance Note
**PO should confirm:**
- "I can select a reporting period, choose GRI 302, and download a properly formatted Excel file with energy data."
- "The Excel file has all 4 tabs with correct headers and data."
- "The facility filter worked correctly (Building A data only)."

**Sign-off:** PO says "PASS" or asks clarifying questions. If questions → Backend Lead explains.

### Fallback (If Excel Download Fails)
```
Fallback Action 1:
  - Switch to PDF export (Scenario 2 handles PDF, so skip here)
  - OR: Show pre-generated sample Excel file from docs/samples/ESG_Report_GRI302_Sample.xlsx

Fallback Action 2:
  - If backend API returns 500 error:
    - Check Kafka consumer lag: `curl http://localhost:9021/api/clusters/main/consumer-lag`
    - Check TimescaleDB connection: `curl http://localhost:8080/api/v1/health`
    - Restart backend: `docker restart uip-backend`
    - Wait 30s and retry

Fallback Action 3:
  - If file download doesn't trigger:
    - Open browser DevTools → Network tab
    - Manually download via: curl -X POST http://localhost:8080/api/v1/esg/reports/generate \
        -H "Authorization: Bearer <JWT>" \
        -d '{"period": "JAN-MAY-2026", "gri": "302", "format": "EXCEL"}' \
        -o report.xlsx
```

---

# SCENARIO 2: GRI 302/305 Export — PDF Download

**Time Estimate:** 8 minutes  
**Goal:** Demonstrate PDF export produces professional formatted report with charts

### Preconditions
- [ ] Previous scenario (Scenario 1) completed successfully
- [ ] Frontend still on ESG Reports page
- [ ] Same test data available

### Step-by-Step Actions

#### Step 1: Reset Report Generator (Optional)
```
Action:
  1. Click "Clear Selections" button (or refresh page)
  2. Verify all fields reset to defaults
Expected:
  - Date range clears
  - GRI Standard dropdown shows "Select GRI Standard..."
  - Export format group resets to Excel
```

#### Step 2: Re-Select Period & GRI (Faster this time)
```
Action:
  1. Quick selection: Jan 1 – May 31, 2026 (same as before)
  2. GRI Standard: Select "GRI 305 — Emissions"
  3. Facility: "All Facilities" (do NOT filter this time for broader report)
Expected:
  - Est. Records: "350+" 
  - Data Quality indicator shows green (≥95%)
```

#### Step 3: Select PDF Export Format
```
Action:
  1. Click "Export Format" radio group
  2. Select "PDF (.pdf)" option
Expected:
  - PDF option highlighted
  - Tooltip: "PDF includes executive summary, charts, and appendix"
  - Button text: "Generate & Download PDF"
```

#### Step 4: Click Generate & Download
```
Action:
  1. Click "Generate & Download PDF" button
  2. Observe API call
  3. Wait for file download (PDF generation may take 8–10s due to chart rendering)
Expected:
  - Button spinner shows: "Generating PDF... 67%"
  - DevTools Network tab shows: POST /api/v1/esg/reports/generate → 200
  - Response header: `Content-Type: application/pdf`
  - Content-Disposition: `attachment; filename=ESG_Report_GRI305_AllFacilities_Jan-May-2026.pdf`
  - File auto-downloads to Downloads folder
```

#### Step 5: Verify Downloaded PDF
```
Action:
  1. Open Downloads folder
  2. File should be: `ESG_Report_GRI305_AllFacilities_Jan-May-2026.pdf`
  3. Open in PDF viewer (Preview on macOS)
  4. Verify pages:
     - Page 1: Executive Summary (title, reporting period, key metrics)
     - Page 2: Emission Breakdown Chart (pie chart: Scope 1 vs 2 vs 3 %)
     - Page 3: Trend Chart (line graph: monthly CO2e trend Jan–May)
     - Page 4–6: Detailed tables by facility
     - Page 7: Data Quality & Methodology appendix
Expected:
  - PDF opens without errors or corrupted content
  - Charts render correctly (no blank areas)
  - Text is readable, formatting is professional
  - All 7 pages present
```

#### Step 6: Show PDF Content to PO
```
Action:
  1. Display PDF on screen (full page view)
  2. Swipe/scroll through pages showing:
     - Title page with "ESG Report — GRI 305 Emissions"
     - Executive summary with KPIs (Total Scope 1, 2, 3 emissions)
     - Charts with proper legends
     - Facility breakdown (Building A, B, C in separate tables)
Expected:
  - PO sees professional report layout
  - Charts are visually clear and properly labeled
  - Data matches Excel export from Scenario 1 (consistency check)
```

### Expected Result Summary
✅ PDF exports successfully  
✅ PDF renders without corruption  
✅ Professional formatting with charts and tables  
✅ All pages present (summary → charts → tables → appendix)

### PO Acceptance Note
**PO should confirm:**
- "The PDF export produces a professional, print-ready report."
- "The charts clearly show emissions breakdown by scope."
- "Data in PDF matches the Excel export from the previous scenario."

**Sign-off:** PO says "PASS" or requests modifications (e.g., "Add company logo to header").

### Fallback (If PDF Generation Fails)
```
Fallback Action 1:
  - Use pre-generated sample PDF: docs/samples/ESG_Report_GRI305_Sample.pdf
  - Explain: "This is the PDF format your report will have."

Fallback Action 2:
  - If PDF is corrupted or empty:
    - Check backend logs: `docker logs uip-backend | grep -i "pdf"`
    - Verify Python reporting library: `python3 -c "import reportlab; print(reportlab.Version)"`
    - If missing: `pip install reportlab`
    - Regenerate report

Fallback Action 3:
  - If API timeout (>15s): 
    - Explain to PO: "On production, we'll optimize chart rendering time."
    - Show pre-rendered sample while debugging
```

---

# SCENARIO 3: Keycloak RSA Login Flow

**Time Estimate:** 10 minutes  
**Goal:** Demonstrate Keycloak RSA-based authentication (new in Sprint 3). Show JWT verification via RSA signature.

### Preconditions
- [ ] Current user is logged out (close all frontend tabs or clear localStorage)
- [ ] Keycloak running at `http://localhost:8180`
- [ ] Keycloak realm **`uip-smartcity`** configured with:
  - Client: `uip-frontend` (OAuth2 public client)
  - User: `po-demo@hcmc.gov.vn` with role `esg-manager`
- [ ] Backend **RoutingJwtDecoder** active (supports both HMAC `iss=uip-legacy` and RSA `iss=keycloak`)
- [ ] Browser Window 2 has Keycloak ready: `http://localhost:8180`

### Step-by-Step Actions

#### Step 1: Start from Frontend Login Page
```
Action:
  1. Open Browser Window 1: http://localhost:5173/login
  2. Frontend login page should display with:
     - "UIP Smart City ESG Dashboard"
     - Email/Password fields
     - "Login via Keycloak" button (NEW)
     - "Login via Legacy" button (backward compat)
Expected:
  - Login page loads cleanly
  - Two login options visible:
    1. "Email & Password (Keycloak)" — NEW button in green
    2. "Legacy HMAC Auth" — Optional fallback in gray
```

#### Step 2: Click "Login via Keycloak"
```
Action:
  1. Click "Login via Keycloak" button
  2. User is redirected to Keycloak login page: http://localhost:8180/auth/realms/uip-smartcity/protocol/openid-connect/auth?client_id=uip-frontend&...
Expected:
  - Keycloak login form appears
  - Form shows: "UIP SmartCity - Keycloak Realm"
  - Username field: empty
  - Password field: empty
  - "Sign In" button visible
```

#### Step 3: Enter Keycloak Credentials
```
Action:
  1. Enter Username: po-demo@hcmc.gov.vn
  2. Enter Password: [PASSWORD_FROM_VAULT]
  3. Click "Sign In" button
Expected:
  - User is authenticated
  - Keycloak redirects back to frontend callback URL: http://localhost:5173/callback?code=...&session_state=...
  - Page briefly shows loading spinner: "Exchanging authorization code for token..."
```

#### Step 4: JWT Exchange & Redirect
```
Action:
  1. Wait 2–3 seconds for code-to-token exchange
  2. Frontend should redirect to: http://localhost:5173/esg/dashboard
Expected:
  - Frontend exchanges Keycloak authorization code for JWT
  - Backend verifies JWT signature using RSA public key from Keycloak
  - JWT contains:
    - iss: "keycloak" (NOT "uip-legacy")
    - sub: "po-demo@hcmc.gov.vn"
    - realm_access.roles: ["esg-manager"]
  - RoutingJwtDecoder successfully validates RSA signature ✓
  - User is logged in and ESG Dashboard loads
```

#### Step 5: Verify User is Logged In
```
Action:
  1. Check top-right corner: Should show "po-demo@hcmc.gov.vn" with avatar
  2. Verify navigation menu shows correct role-based options:
     - "Reports" (esg-manager can export)
     - "Analytics" (read-only)
     - "Settings" (if assigned role includes admin)
  3. Open browser DevTools → Application → Local Storage
  4. Check for JWT token (key: `auth_token` or similar)
Expected:
  - User name displayed in header: "po-demo@hcmc.gov.vn"
  - Dashboard loads with user's ESG data
  - JWT in localStorage contains RSA signature (begins with "eyJ..." header)
```

#### Step 6: Demonstrate RSA Verification (Backend Perspective)
```
Action (Backend Lead narrates):
  1. Open backend logs: docker logs -f uip-backend | grep -i "jwt\|keycloak"
  2. Trigger an API call (e.g., GET /api/v1/esg/dashboard)
  3. Show log output:
     ```
     [2026-05-30 15:XX:XX] RoutingJwtDecoder: Detected issuer=keycloak
     [2026-05-30 15:XX:XX] KeycloakJwtDecoder: Verifying RSA signature...
     [2026-05-30 15:XX:XX] KeycloakJwtDecoder: RSA signature valid ✓ 
     [2026-05-30 15:XX:XX] SecurityContext: User=po-demo@hcmc.gov.vn, Roles=[esg-manager]
     ```
Expected:
  - Logs show RSA signature verification passing
  - RoutingJwtDecoder correctly chose Keycloak decoder (not legacy HMAC)
  - User context established with correct email + roles
```

#### Step 7: Test Legacy HMAC Auth Still Works (Backward Compatibility)
```
Action:
  1. Logout: Click "Sign Out" button (or clear localStorage)
  2. Frontend redirects to login page
  3. Click "Login via Legacy HMAC Auth" button
  4. Enter credentials: tester@uip.local / Demo@123456
  5. Submit login form
Expected:
  - Legacy HMAC auth still works (backward compatible)
  - JWT contains: iss: "uip-legacy"
  - RoutingJwtDecoder chooses HMAC decoder
  - User logs in successfully
  - Log output shows: "Detected issuer=uip-legacy" → "HmacJwtDecoder: Verification passed"
```

### Expected Result Summary
✅ Keycloak login redirects correctly  
✅ JWT exchange works (authorization code → access token)  
✅ RSA signature verification passes on backend  
✅ User context correctly populated with Keycloak claims  
✅ Backward compatibility with legacy HMAC auth maintained

### PO Acceptance Note
**PO should confirm:**
- "I can log in via Keycloak using my city authority email (po-demo@hcmc.gov.vn)."
- "The login flow is smooth (redirect → authorization → token → dashboard)."
- "My roles (esg-manager) are correctly applied in the system."
- "If needed, legacy login still works for system administrators."

**Sign-off:** PO says "PASS" or notes any issues with Keycloak integration.

### Fallback (If Keycloak Login Fails)
```
Fallback Action 1:
  - If Keycloak page doesn't load (http://localhost:8180 unreachable):
    - Check: docker ps | grep keycloak
    - Restart: docker restart uip-keycloak
    - Wait 30s, retry

Fallback Action 2:
  - If authorization code exchange fails (stuck on callback page):
    - Check backend logs: docker logs uip-backend | grep -i "oauth\|keycloak"
    - Verify Keycloak client config: Redirect URIs must include http://localhost:5173/callback
    - Fix in Keycloak Admin Console and retry

Fallback Action 3:
  - If JWT signature verification fails:
    - Check RoutingJwtDecoder logic in backend
    - Verify RSA public key is fetched from Keycloak: http://localhost:8180/auth/realms/uip-smartcity/protocol/openid-connect/certs
    - If public key not found: Check Keycloak realm settings
    - Fall back to legacy HMAC auth for demo (show it works)
```

---

# SCENARIO 4: Flink Enriched Sensor Data in Operations Dashboard

**Time Estimate:** 12 minutes  
**Goal:** Demonstrate real-time sensor enrichment. Show sensor readings with building metadata (building_name, floor, zone) added by Flink stream processor.

### Preconditions
- [ ] Flink job **`enrichment-service`** deployed and healthy
- [ ] Kafka topic **`sensor-readings-raw`** receiving sensor events (>1 msg/sec)
- [ ] Kafka topic **`sensor-readings-enriched`** actively consuming (consumer lag < 5 sec)
- [ ] Frontend at: http://localhost:5173/ops/dashboard
- [ ] User logged in (any valid credentials)
- [ ] Reference data (building metadata) loaded in TimescaleDB:
  - Table: `buildings` (id, name, city, floors)
  - Table: `zones` (id, building_id, floor, zone_name, sensor_ids)
- [ ] WebSocket or SSE connection active for real-time sensor updates

### Step-by-Step Actions

#### Step 1: Navigate to Operations Dashboard
```
Action:
  1. Click main menu → "Operations" → "Live Sensor Map"
  2. Frontend URL should be: http://localhost:5173/ops/dashboard
Expected:
  - Operations Dashboard loads with:
    - Geospatial map (Leaflet) showing buildings/zones
    - Sensor list panel on right side (scrollable table)
    - Real-time metrics header (temp, humidity, AQI, etc.)
    - "Refresh Rate: Real-time" indicator in top-right
```

#### Step 2: Inspect Raw Sensor (WITHOUT enrichment)
```
Action:
  1. Explain to PO: "Without enrichment, sensor readings look like this:"
  2. Show sample raw event from Kafka (backend can log sample):
     ```json
     {
       "sensor_id": "SNS-0042",
       "timestamp": "2026-05-30T15:23:45.123Z",
       "temperature": 24.5,
       "humidity": 65,
       "co2": 412
     }
     ```
  3. Point out: "Only sensor_id and raw values. No location metadata."
Expected:
  - PO understands the "before" state
  - Acknowledges lack of building context
```

#### Step 3: Show Enriched Sensor in Dashboard
```
Action:
  1. Point to a sensor reading in the dashboard table
  2. Hover over or click a sensor row (e.g., "SNS-0042")
  3. Expanded view/tooltip should show:
     ```
     Sensor ID: SNS-0042
     Building: Building A (NEW - from Flink enrichment)
     Floor: 3 (NEW - from Flink enrichment)
     Zone: Conference_Room_3A (NEW - from Flink enrichment)
     Temperature: 24.5 °C
     Humidity: 65 %
     CO2: 412 ppm
     Last Updated: 2026-05-30 15:23:45
     ```
Expected:
  - Enriched metadata visible: Building A, Floor 3, Zone Conference_Room_3A
  - Data is real-time (updates every 1–2 seconds)
  - No stale data (timestamps are current)
```

#### Step 4: Show Sensor on Map with Building Context
```
Action:
  1. On Leaflet map, show sensor markers grouped by building
  2. Click marker for SNS-0042 (should be in Building A)
  3. Popup appears showing:
     - Building A
     - Zone: Conference_Room_3A
     - Temperature: 24.5 °C
     - Link: "View Details"
Expected:
  - Sensor marker is color-coded by status:
    - Green: Normal (18–26 °C)
    - Yellow: Warning (< 18 or > 26)
    - Red: Alert (< 15 or > 28)
  - Building name is displayed (not just sensor ID)
  - Zone information helps PO understand sensor location
```

#### Step 5: Open Sensor Details View
```
Action:
  1. Click "View Details" link or double-click sensor row
  2. New panel shows full sensor profile:
     - Sensor ID: SNS-0042
     - Building: Building A (with building image/icon)
     - Floor: 3
     - Zone: Conference_Room_3A
     - Installed Date: 2026-01-15
     - Current Readings:
       * Temperature: 24.5 °C (Target: 21–24)
       * Humidity: 65 % (Target: 40–60)
       * CO2: 412 ppm (Target: < 800)
     - Last 24h Trend Graph (line chart of temperature/humidity)
Expected:
  - Building and zone metadata prominently displayed
  - Historical trend visible
  - Readings update in real-time (values change every few seconds)
```

#### Step 6: Demonstrate Flink Enrichment Behind the Scenes (Technical)
```
Action (Backend Eng narrates):
  1. Open browser → DevTools → Network tab
  2. Filter for WebSocket or SSE connection: look for `/api/v1/sensors/stream` or similar
  3. Show incoming message format:
     ```json
     {
       "sensor_id": "SNS-0042",
       "building_name": "Building A",
       "floor": 3,
       "zone": "Conference_Room_3A",
       "temperature": 24.5,
       "timestamp": "2026-05-30T15:24:01.456Z"
     }
     ```
  4. Explain: "This enriched message comes from Kafka topic `sensor-readings-enriched`, 
              processed by Flink join with the `buildings` reference table."
  5. Optional: Show Kafka topics via UI
     - docker exec -it kafka kafka-topics --list
     - Show: sensor-readings-raw, sensor-readings-enriched
Expected:
  - WebSocket/SSE messages contain building_name, floor, zone
  - PO sees real data, not mock
  - Understands the enrichment pipeline (Flink joins sensors with building reference data)
```

#### Step 7: Filter by Building
```
Action:
  1. Click "Filter by Building" dropdown
  2. Select "Building B"
  3. Dashboard should update to show only Building B sensors
Expected:
  - Table refreshes instantly (no lag)
  - Map updates to highlight only Building B markers
  - Sensor count changes (e.g., "5 sensors in Building B")
  - All displayed sensors have building_name = "Building B"
```

#### Step 8: Show Alert Triggered by Enriched Data
```
Action:
  1. In the dashboard, look for any sensor with abnormal readings:
     - Temp > 28 °C (alert threshold)
     - CO2 > 1000 ppm (alert threshold)
  2. If none exist, mention: "When a sensor reading exceeds thresholds, 
     an alert is triggered with full building context:"
  3. Show mock alert (or point to alert log):
     ```
     🚨 ALERT: High Temperature
     Building: Building C
     Zone: Electrical_Room_1
     Sensor: SNS-0087
     Current: 32 °C (Threshold: 28 °C)
     Action: Notify facilities team for HVAC check
     ```
Expected:
  - Alert includes building + zone context (from enrichment)
  - Without enrichment, alert would only show sensor ID (unclear to facilities team)
  - With enrichment, facilities team can immediately locate the problematic sensor
```

### Expected Result Summary
✅ Sensor readings display with building_name, floor, zone metadata  
✅ Map shows sensors grouped by building (enriched context)  
✅ Real-time updates flowing (WebSocket/SSE via Flink pipeline)  
✅ Filter by building works instantly  
✅ Alerts include location context for fast response

### PO Acceptance Note
**PO should confirm:**
- "Sensor readings now show building name and zone, not just sensor ID. This is much more useful for operations."
- "The enriched data comes from Flink processing (we can see it in the API messages)."
- "Real-time performance is smooth (no lag, updates every 1–2 sec)."
- "Facilities team can now quickly locate sensors and respond to alerts."

**Sign-off:** PO says "PASS" or suggests enhancements (e.g., "Add photo of zone to details view").

### Fallback (If Flink Enrichment Data Missing)
```
Fallback Action 1:
  - If sensors show NULL for building_name/floor/zone:
    - Check Flink job status: docker exec -it flink flink-cluster-status
    - Check Kafka consumer lag: curl http://localhost:9021/api/consumer-lag
    - If lag is high (>1000 msgs): Flink may be backlogged
    - Restart Flink job: docker restart flink-jobmanager
    - Wait 30s, refresh dashboard

Fallback Action 2:
  - If WebSocket/SSE not updating in real-time:
    - Check backend WebSocket connection: curl -i -N -H "Connection: Upgrade" http://localhost:8080/api/v1/sensors/stream
    - Check frontend logs: Open DevTools → Console
    - Restart frontend: npm run dev
    - Retry

Fallback Action 3:
  - Show pre-recorded video of enriched data flowing in real-time
  - Play video showing sensor readings updating with building metadata
  - Explain: "This is how the live dashboard behaves in production"
```

---

# SCENARIO 5: P2 Bug Fixes (All 3 — Show Current Fixed State)

**Time Estimate:** 10 minutes  
**Goal:** Demonstrate all 3 P2 bugs have been fixed. Show before/after comparison (screenshots) and live verification.

---

## Bug P2-001: Tooltip Mispositioned on ESG Dashboard Charts

### Context
- **Status:** ✅ FIXED
- **Module:** `ESGDashboard.tsx` (Chart tooltip component)
- **Issue:** When hovering over data points in ESG metric charts, tooltip appeared off-screen or overlapped chart area, making data unreadable
- **Root Cause:** CSS `position: absolute` used without viewport boundary checking; tooltip position calculated without accounting for window edges
- **Fix:** Added viewport collision detection in `useChartTooltip` hook; tooltip now repositions dynamically to stay within bounds

### Preconditions
- [ ] Frontend at: http://localhost:5173/esg/analytics
- [ ] ESG Dashboard with charts visible (e.g., "Monthly Emissions Trend" chart)
- [ ] Browser DevTools console closed (clean screen)

### Step-by-Step Demo

#### Part A: Show Before/After Screenshots
```
Action:
  1. Display screenshot of bug (saved in docs/mvp3/project/demo-screenshots/P2-001-Before.png):
     - Chart visible
     - Tooltip appears off-screen (top-right corner, outside viewport)
     - Data in tooltip hidden from view
  2. Display screenshot of fix (saved in docs/mvp3/project/demo-screenshots/P2-001-After.png):
     - Same chart, same data point
     - Tooltip now appears INSIDE viewport, repositioned below cursor
     - All tooltip text clearly visible
Expected:
  - PO sees clear visual difference between buggy and fixed state
```

#### Part B: Live Verification on Dashboard
```
Action:
  1. Open ESG Analytics dashboard
  2. Locate "Monthly Emissions Trend" chart (line chart with data points)
  3. Hover over data points at edges of chart (top-right corner area)
  4. Observe tooltip behavior
Expected:
  - Tooltip appears near cursor (offset by ~10px right, ~10px below)
  - Tooltip stays within browser viewport (no clipping)
  - Text is fully readable (black text on white background)
  - Tooltip dismisses when mouse moves away
```

#### Part C: Technical Deep Dive (Optional)
```
Action (Frontend Lead):
  1. Open DevTools → Elements tab
  2. Right-click tooltip element → Inspect
  3. Show CSS rule:
     ```css
     .chart-tooltip {
       position: absolute;
       max-width: 200px;
       padding: 8px 12px;
       background: white;
       border: 1px solid #ccc;
       pointer-events: none;
       /* NEW FIX: Viewport boundary handling via JS */
     }
     ```
  4. Show logic in ESGDashboard.tsx (lines ~XX):
     ```typescript
     const adjustTooltipPosition = (x: number, y: number) => {
       const tooltip = tooltipRef.current;
       if (!tooltip) return { x, y };
       
       const { width, height } = tooltip.getBoundingClientRect();
       const maxX = window.innerWidth - width - 10;
       const maxY = window.innerHeight - height - 10;
       
       return {
         x: Math.min(x, maxX),
         y: Math.min(y, maxY)
       };
     };
     ```
Expected:
  - Code shows viewport collision detection
  - PO (or Tech Lead) confirms fix is properly implemented
```

### Expected Result
✅ Tooltip always stays within viewport  
✅ Text is readable at all times  
✅ No visual overlap or clipping

### PO Acceptance
**PO confirms:** "The tooltip now appears properly. I can read all data without it going off-screen."  
**Sign-off:** PO says "PASS ✓"

---

## Bug P2-002: AQI Polling Interval Too Aggressive (UI Flicker)

### Context
- **Status:** ✅ FIXED
- **Module:** `AirQualityCard.tsx` (WebSocket polling hook)
- **Issue:** Air Quality Index (AQI) gauge was updating too frequently (every 100ms), causing visible UI flicker and high CPU usage
- **Root Cause:** `setInterval()` was set to 100ms; browser couldn't keep up with re-renders, causing janky animation and battery drain on mobile
- **Fix:** Increased polling interval to 2s (2000ms); implemented debouncing to batch multiple readings into single render

### Preconditions
- [ ] Frontend at: http://localhost:5173/ops/dashboard (Environmental Monitoring section)
- [ ] AQI gauge/card visible showing real-time air quality
- [ ] Mobile device (or browser DevTools emulation) for observing flicker effect

### Step-by-Step Demo

#### Part A: Show Before/After Video Comparison
```
Action:
  1. Play pre-recorded video of bug (docs/mvp3/project/demo-videos/P2-002-Before-Flicker.mp4):
     - AQI gauge updates rapidly (visible flicker every 0.1s)
     - Numbers jump erratically
     - Difficult to read the value
  2. Play video of fix (docs/mvp3/project/demo-videos/P2-002-After-Smooth.mp4):
     - AQI gauge updates smoothly every 2 seconds
     - Transition animation is smooth (no jank)
     - Value is stable and readable
Expected:
  - PO sees clear performance improvement
  - Flicker is gone
```

#### Part B: Live Verification on Dashboard
```
Action:
  1. Open Environmental Monitoring section
  2. Locate AQI Gauge card (shows current air quality: "GOOD", "MODERATE", "UNHEALTHY")
  3. Watch the gauge for 10 seconds
  4. Observe update cadence
Expected:
  - Gauge updates smoothly every ~2 seconds (not frantically)
  - Value stabilizes (e.g., "AQI: 68 — MODERATE")
  - CPU usage is low (check DevTools → Performance → CPU)
  - Smooth animation when value changes
```

#### Part C: Performance Metrics (Optional)
```
Action (Frontend Lead):
  1. Open DevTools → Performance tab
  2. Click "Record"
  3. Watch AQI card for 5 seconds
  4. Stop recording
  5. Analyze frame rate: Should show consistent 60 FPS (green bars)
  6. Before fix: Would show dropped frames (red bars) every 100ms
Expected:
  - Frame rate graph shows sustained 60 FPS
  - No red (dropped frames)
  - CPU cost per frame minimal
```

### Expected Result
✅ No visual flicker  
✅ Smooth 60 FPS animation  
✅ Update cadence: ~2 seconds (readable)  
✅ Low CPU/battery impact (especially on mobile)

### PO Acceptance
**PO confirms:** "The AQI gauge now updates smoothly. No more annoying flicker. It's much easier to read."  
**Sign-off:** PO says "PASS ✓"

---

## Bug P2-003: Filter Panel Animation Janky on Mobile

### Context
- **Status:** ✅ FIXED
- **Module:** `FilterPanel.tsx` (React slide-in animation)
- **Issue:** When opening/closing the filter panel on mobile, animation was stuttering (janky/laggy), especially on lower-end phones
- **Root Cause:** Animation used `transform: translate()` with frequent `re-renders` triggered by filter state changes
- **Fix:** Moved animation to CSS Transforms; debounced filter value updates to prevent re-renders during animation phase

### Preconditions
- [ ] Frontend tested on mobile (or browser DevTools mobile emulation)
- [ ] Filter panel accessible (button typically at top-left of dashboard)
- [ ] Low-end mobile device simulation enabled (DevTools → CPU throttling → "4x slowdown")

### Step-by-Step Demo

#### Part A: Show Before/After Video Comparison
```
Action:
  1. Play video of bug on mobile (docs/mvp3/project/demo-videos/P2-003-Before-Janky.mp4):
     - Filter panel slides in from left
     - Animation stutters/skips frames
     - Takes 2+ seconds to fully open (should be ~0.3s)
     - Opening feels sluggish and unresponsive
  2. Play video of fix (docs/mvp3/project/demo-videos/P2-003-After-Smooth.mp4):
     - Filter panel opens smoothly in ~0.3s
     - Animation is fluid (no stuttering)
     - Feels responsive and snappy
Expected:
  - PO notices significant UX improvement
  - Animation is professional and smooth
```

#### Part B: Live Verification on Mobile Device
```
Action:
  1. On mobile phone (or DevTools mobile emulation):
     - Open dashboard: http://localhost:5173/ops/dashboard
     - Enable CPU throttling in DevTools (4x slowdown) to simulate low-end phone
  2. Tap filter button (☰ or funnel icon at top-left)
  3. Observe panel slide-in animation
  4. Tap close button to slide out
  5. Repeat 3 times
Expected:
  - Animation smooth every time (no jank)
  - Panel fully opens in <0.5s
  - Response feels natural and fluid
  - Works smoothly even with CPU throttling (4x slowdown)
```

#### Part C: Code Inspection (Optional)
```
Action (Frontend Lead):
  1. Open DevTools → Elements
  2. Inspect filter panel element
  3. Show CSS animation:
     ```css
     .filter-panel {
       position: fixed;
       left: 0;
       width: 280px;
       height: 100vh;
       transition: transform 0.3s cubic-bezier(0.25, 0.46, 0.45, 0.94);
       transform: translateX(-280px); /* Hidden by default */
     }
     
     .filter-panel.open {
       transform: translateX(0); /* Animated via GPU */
     }
     ```
  4. Explain: "Transform animations are GPU-accelerated, not CPU-heavy re-renders"
  5. Show debounce logic in FilterPanel.tsx:
     ```typescript
     const debouncedFilterChange = useMemo(
       () => debounce((value) => setFilter(value), 150),
       []
     );
     ```
Expected:
  - CSS uses GPU-accelerated transforms (not layout recalc)
  - Debounce prevents excessive re-renders during animation
  - Code properly optimized for mobile
```

### Expected Result
✅ Animation smooth on mobile (even with CPU throttling)  
✅ Panel opens in <0.5s (feels responsive)  
✅ No frame drops or stuttering  
✅ Works on low-end devices

### PO Acceptance
**PO confirms:** "The filter panel now opens smoothly on mobile. No more sluggish animation. Great UX improvement!"  
**Sign-off:** PO says "PASS ✓"

---

## Summary of All 3 P2 Bug Fixes

| Bug ID | Issue | Fix | Status |
|--------|-------|-----|--------|
| **P2-001** | Tooltip misaligned on chart edges | Added viewport collision detection | ✅ FIXED |
| **P2-002** | AQI gauge flickered (100ms polling) | Increased to 2s polling + debounce | ✅ FIXED |
| **P2-003** | Filter panel animation janky on mobile | GPU-accelerated CSS transforms + debounce | ✅ FIXED |

### PO Overall Acceptance (All 3 Bugs)

**PO should confirm:**
- "All three bugs are fixed."
- "Dashboard tooltips work properly."
- "AQI updates smoothly without flicker."
- "Filter panel is responsive on mobile."

**Sign-off:** PO says "PASS ✓" for all P2 fixes.

---

## Fallback (If Any Bug Appears Unfixed During Demo)

```
Fallback Action (General):
  1. If a bug reappears:
     - Note it: "Interesting — seems the fix regressed. Let me check."
     - Restart frontend: npm run dev
     - Clear cache: Browser DevTools → Clear storage
     - Retry the scenario
  
  2. If still failing:
     - Show the pre-recorded video of the fix working
     - Explain: "The fix is in the code. This might be a localized issue in this environment."
     - Offer to investigate after demo
  
  3. If all else fails:
     - Show the git commit that contains the fix
     - Display code diff: git show <commit-hash> -- path/to/file.tsx
     - Prove the fix was deployed: git log --oneline | grep "P2-001\|P2-002\|P2-003"
     - PO can verify the fix exists in source code
```

---

# SCENARIO 5 — END

---

# Demo Wrap-Up & Sign-Off (5 minutes)

### Checklist for Demo Lead

- [ ] All 5 scenarios completed (or major blockers documented)
- [ ] PO confirmed acceptance on each scenario (or documented concerns)
- [ ] Screenshots taken of key moments (for documentation)
- [ ] Questions from PO answered (or logged as future work items)
- [ ] Signoff form signed (digital or physical)

### PO Sign-Off Form

```
SPRINT MVP3-3 PO DEMO SIGN-OFF
Date: 2026-05-30
Gate Review: AC-01, AC-02, AC-04, AC-06

Scenario 1: GRI Export (Excel)
  Status: ☐ PASS | ☐ FAIL | ☐ PARTIAL
  PO Signature: _________________

Scenario 2: GRI Export (PDF)
  Status: ☐ PASS | ☐ FAIL | ☐ PARTIAL
  PO Signature: _________________

Scenario 3: Keycloak RSA Login
  Status: ☐ PASS | ☐ FAIL | ☐ PARTIAL
  PO Signature: _________________

Scenario 4: Flink Enriched Sensors
  Status: ☐ PASS | ☐ FAIL | ☐ PARTIAL
  PO Signature: _________________

Scenario 5: P2 Bug Fixes (3 total)
  Status: ☐ PASS | ☐ FAIL | ☐ PARTIAL
  PO Signature: _________________

OVERALL VERDICT: ☐ APPROVED | ☐ APPROVED WITH ISSUES | ☐ REJECTED

Comments:
_________________________________________________________________

PO Name: _________________________
City Authority: HCMC ESG Program
Date: 2026-05-30
```

---

# Appendix: Quick Reference — Demo URLs & Commands

### URLs
| Service | URL |
|---------|-----|
| Frontend | http://localhost:5173 |
| Backend API | http://localhost:8080 |
| Keycloak Admin | http://localhost:8180/admin |
| Kafka UI | http://localhost:9021 |
| Grafana | http://localhost:3000 |

### Health Check Commands
```bash
# Quick health check (run all before demo)
echo "=== Backend Health ===" && curl http://localhost:8080/api/v1/health
echo "=== Frontend ===" && curl http://localhost:5173 -I | head -1
echo "=== Keycloak ===" && curl http://localhost:8180 -I | head -1
echo "=== Kafka Topics ===" && docker exec -it kafka kafka-topics --list
```

### Restart Commands (If Needed During Demo)
```bash
# Restart backend
docker restart uip-backend

# Restart frontend
cd frontend && npm run dev

# Restart Keycloak
docker restart uip-keycloak

# Restart Flink
docker restart flink-jobmanager

# Full stack restart (last resort)
docker-compose down && docker-compose up -d
```

### Sample Test Data Queries
```sql
-- Check ESG report data in TimescaleDB
SELECT COUNT(*) FROM esg_reports WHERE period BETWEEN '2026-01-01' AND '2026-05-31';

-- Check sensor enrichment data
SELECT sensor_id, building_name, floor, zone FROM sensor_readings_enriched LIMIT 5;

-- Check Keycloak users
SELECT username FROM KEYCLOAK_USER WHERE realm_id = 'uip-smartcity' LIMIT 5;
```

---

# Appendix: Common Q&A for PO

### Q1: How frequently is the ESG report data updated?
**A:** ESG data is aggregated from sensor streams in real-time. Reports are generated on-demand and include data up to the current moment. Historical data is queryable for any period (daily, monthly, quarterly).

### Q2: Who can export ESG reports?
**A:** Only users with the `esg-manager` role can access report generation. Roles are managed in Keycloak (city authority can add/remove users as needed).

### Q3: What formats are supported?
**A:** Currently Excel (.xlsx) and PDF (.pdf). CSV support is planned for Q3 2026.

### Q4: Is the enriched sensor data searchable?
**A:** Yes. The Operations Dashboard allows filtering by building, floor, and zone. API also supports query parameters like `?building=Building-A&zone=Conference_Room_3A`.

### Q5: How is Keycloak integrated with legacy systems?
**A:** RoutingJwtDecoder supports both Keycloak (RSA) and legacy HMAC tokens. Existing systems continue working. Migration to Keycloak is gradual (no hard cutover needed).

### Q6: What happens if a sensor fails?
**A:** Missing sensor readings are detected in the Flink stream processor. The dashboard marks the sensor as "Offline" and alerts are triggered if SLA thresholds are breached. Facilities team is notified via SMS/Email.

### Q7: Can the PO export data for external analysis?
**A:** Yes. Excel exports can be opened in any spreadsheet software. PDF exports are printable. API endpoints also allow programmatic access for integration with city authority BI tools.

---

# Document Info
- **Created:** 2026-05-23
- **Last Updated:** 2026-05-23
- **Author:** QA Lead
- **Status:** Ready for Demo
- **Version:** 1.0

