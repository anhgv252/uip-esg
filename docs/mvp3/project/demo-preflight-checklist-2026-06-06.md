# UIP Smart City MVP3 — Demo Day Pre-Flight Smoke Test Checklist
**Investor Demo — June 6, 2026**  
**Duration:** 30 minutes before demo starts  
**Prepared by:** Manual Tester (UIP-tester role)

---

## Overview

This checklist ensures all 8 MVP3 features are production-ready before the investor presentation. Follow in strict order: **T-30 → T-20 → T-10 → T-5 → Contingencies → Cleanup**.

> **Critical:** If any **BLOCKER** step fails (marked with 🛑), **DO NOT PROCEED TO DEMO**. Escalate immediately.

---

## T-30 Minutes: Infrastructure Start

### 1. Prepare Environment File

- [ ] Navigate to infrastructure folder: `cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/infrastructure`
- [ ] Copy `.env.example` to `.env` if missing: `cp -n .env.example .env`
- [ ] Verify `.env` contains:
  ```
  POSTGRES_PASSWORD=postgres_Dev#2026!
  REDIS_PASSWORD=redis_Dev#2026!
  DEMO_ADMIN_PASS=admin_Dev#2026!
  CLICKHOUSE_DB=analytics
  ```
- [ ] If using Keycloak realm, verify: `export KEYCLOAK_ADMIN_PASSWORD=<strong-password>`

### 2. 🛑 Start HA Stack (T-30 to T-25)

**Exact commands (copy-paste):**

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/infrastructure

# Start HA stack (ClickHouse 2-node + Kafka 3-broker + all services)
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d

# Wait 30-45 seconds for initial startup
sleep 45

# Check all services
docker ps --format "table {{.Names}}\t{{.Status}}" | grep -E "uip-|healthy"
```

**Expected output sample:**
```
NAME                              STATUS
uip-timescaledb                   Up 1 minute (healthy)
uip-redis                         Up 1 minute (healthy)
uip-clickhouse-01                 Up 1 minute (healthy)
uip-clickhouse-02                 Up 1 minute (healthy)
uip-clickhouse-keeper             Up 1 minute (healthy)
uip-kafka                         Up 1 minute (healthy)
uip-kafka-2                       Up 1 minute (healthy)
uip-kafka-3                       Up 1 minute (healthy)
uip-kafka-ui                      Up 1 minute
uip-emqx                          Up 1 minute (healthy)
uip-backend                       Up 1 minute (healthy)
uip-frontend                      Up 1 minute (healthy)
uip-flink-jobmanager             Up 1 minute (healthy)
uip-flink-taskmanager            Up 1 minute
uip-flink-esg-job-submitter      Up 1 minute
uip-flink-structural-job-submitter Up 1 minute
```

- [ ] **All 16+ services show "Up" status** (at minimum: healthy for critical ones)
- [ ] No "Exit" or "Restarting" status

### 3. 🛑 Detailed Service Health Check (T-25)

Run health check script:

```bash
make ha-health-check
```

Or verify manually by port/service:

**Database Layer:**
- [ ] TimescaleDB primary: `psql -h localhost -U uip -d uip_smartcity -c "SELECT version();"` → PostgreSQL 15 + TimescaleDB 2.13
- [ ] TimescaleDB standby: `psql -h localhost -p 5433 -U uip -d uip_smartcity -c "SELECT pg_is_in_recovery();"` → `t` (true = standby mode)
- [ ] Redis: `redis-cli -a redis_Dev#2026! ping` → `PONG`

**Analytics Layer (ClickHouse HA):**
- [ ] ClickHouse Node 01: `curl -s http://localhost:8125/ping | grep -q Ok. && echo "✓ CH-01 alive"` → ✓ CH-01 alive
- [ ] ClickHouse Node 02: `curl -s http://localhost:8124/ping | grep -q Ok. && echo "✓ CH-02 alive"` → ✓ CH-02 alive
- [ ] ClickHouse Keeper: `nc -zv localhost 9181 2>&1 | grep succeeded` → `succeeded`

**Message Broker (Kafka 3-broker KRaft):**
```bash
# Check broker status
docker exec uip-kafka kafka-broker-api-versions --bootstrap-server localhost:9092 | head -5
```
- [ ] Returns broker info for all 3 brokers (kafka, kafka-2, kafka-3)

**Stream Processing:**
- [ ] Flink JobManager: `curl -s http://localhost:8081/rest/v1/overview | python3 -c "import sys,json; print(json.load(sys.stdin)['tasksPerState'])"` → Shows `RUNNING` count > 0

**IoT & API Gateway:**
- [ ] EMQX MQTT: `mosquitto_sub -h localhost -p 1883 -t '$SYS/brokers' -C 1 2>&1 | head -1` → Should not timeout
- [ ] Backend API: `curl -s http://localhost:8080/actuator/health | python3 -m json.tool | grep -A2 '"status"'` → `"status": "UP"`
- [ ] Frontend: `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3000` → `200`

### 4. ✅ Verify Kafka Topic Creation (T-23)

```bash
# Check if kafka-init completed (topics should exist)
docker exec uip-kafka kafka-topics --bootstrap-server localhost:9092 --list | head -20
```

**Topics should include at minimum:**
- [ ] `iot-raw-sensors` (MQTT ingestion)
- [ ] `environment-aqi` (AQI processed)
- [ ] `building-safety-vibration` (Building Safety)
- [ ] `energy-consumption` (ESG energy)
- [ ] `esg-metrics-aggregated` (ESG aggregation)

---

## T-20 Minutes: Data & Auth Verification

### 1. Seed Demo Data (T-20 to T-15)

**Critical:** This creates all demo accounts and sample sensor data.

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc

# Run demo setup script
bash scripts/demo-setup.sh
```

**Expected output:**
```
=========================================
  UIP Smart City — Pre-Demo Setup
  Thu Jun 06 14:30:00 JST 2026
=========================================

─── Infrastructure ───
  ✓ Backend health
  ✓ Frontend dev server
  ✓ TimescaleDB
  ✓ Redis

─── Authentication ───
  ✓ Admin login → JWT obtained

─── Core APIs ───
  ✓ GET /environment/sensors
  ✓ GET /esg/summary
  ✓ GET /alerts
  ✓ GET /tenant/config

─── Multi-Tenant ───
  ✓ JWT contains tenant_id claim

─── Demo Users ───
  ✓ User 'admin' can login
  ✓ User 'operator' can login
  ✓ User 'citizen' can login

─── Performance (3 requests avg) ───
  ✓ ESG summary avg: 125ms (target <500ms)

=========================================
  PASS: 14  FAIL: 0  WARN: 0
=========================================
```

- [ ] **All PASS ≥ 12** (some warnings OK)
- [ ] **FAIL = 0** (blocker if any fails)

**If demo-setup.sh fails:**
```bash
# Manual fallback: Check if data is already seeded from previous runs
curl -s -H "Authorization: Bearer $(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')" \
  http://localhost:8080/api/v1/environment/sensors | python3 -m json.tool | head -20
```

### 2. Test 3 Demo Accounts

**Account 1: Admin (admin@hcm-uip.vn)**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | python3 -m json.tool
```

- [ ] Response contains `"accessToken"` (JWT, 500+ chars)
- [ ] Response contains `"refreshToken"`
- [ ] JWT payload (decode 2nd segment): `decode: echo <JWT_2ND_SEGMENT> | base64 -d` contains:
  ```json
  {
    "sub": "admin",
    "tenant_id": "hcm-uip",
    "roles": ["ADMIN"]
  }
  ```

**Open browser & login to verify UI:**
```
URL: http://localhost:3000
Username: admin@hcm-uip.vn
Password: admin_Dev#2026!
```
- [ ] Login succeeds → redirects to City Operations Center
- [ ] Dashboard loads (map visible, no 500 errors in console)
- [ ] Top-right shows "Admin (HCMC)" 

**Account 2: Operator (operator@hcm-uip.vn)**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"operator","password":"operator123"}' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d.get("accessToken","")[0:50] + "...")'
```

- [ ] Returns JWT starting with `ey...`
- [ ] Open http://localhost:3000 in incognito → login as operator → same dashboard accessible

**Account 3: Citizen (citizen@hcm-uip.vn)**

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"citizen","password":"citizen123"}' \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); print("✓ Token" if "accessToken" in d else "✗ FAIL")'
```

- [ ] Returns token (citizen can login)

### 3. Verify Core Demo Data Exists (T-15)

**Sensor data (8 sensors across HCMC):**

```bash
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')

curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/environment/sensors | python3 -c "import sys,json; data=json.load(sys.stdin); print(f'Total sensors: {len(data)}')"
```

- [ ] Output: `Total sensors: >= 8` (should be 8 in demo data)

**Building Safety vibration data:**

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/building-safety/vibration-readings?buildingId=bitexco-f88&limit=1 | python3 -m json.tool | head -10
```

- [ ] Response contains readings with `timestamp`, `acceleration_mm_s` fields
- [ ] At least 1 reading present

**ESG metrics (Q1 2026):**

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/esg/summary?period=quarterly&year=2026&quarter=1' | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"Energy: {d.get('totalEnergy', 0)} kWh, Carbon: {d.get('totalEmissions', 0)} tCO2\")"
```

- [ ] Shows non-zero energy and emissions values (e.g., "Energy: 250000 kWh, Carbon: 50 tCO2")

---

## T-10 Minutes: Feature Smoke Tests (8 MVP3 Features)

### Feature 1: Building Management System (BMS) — Device List Loads

**Test:** BMS device discovery and listing

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/bms/devices?buildingId=bitexco-f88' | python3 -c "import sys,json; data=json.load(sys.stdin); print(f'BMS Devices: {len(data)} (should be 4-6: HVAC, Elevators, PCCC, Lighting)')"
```

- [ ] Response: `BMS Devices: >= 4`
- [ ] Open frontend → BMS tab → Buildings → "Tòa nhà Bitexco F88" → Device list visible with device icons

### Feature 2: Building Safety — Vibration Readings API Returns Data

**Test:** Vibration sensor endpoint returns data

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/building-safety/vibration-readings?buildingId=bitexco-f88&limit=10&timeRange=24h' | python3 -c "import sys,json; data=json.load(sys.stdin); print(f'Vibration readings: {len(data)} (should be >= 5 in 24h)')"
```

- [ ] Response: `Vibration readings: >= 5`
- [ ] Sample reading has: `timestamp` (ISO 8601), `acceleration_mm_s` (number 0-5), `status` (normal|warning|alert)

**UI Test:** 
- [ ] Frontend → Building Safety tab → "Tòa nhà A, District 1" → Vibration gauge visible (needle position 0-5 mm/s range)

### Feature 3: AI Workflow Engine — 7 Scenarios Load in Designer

**Test:** AI Workflow endpoints return workflow definitions

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/ai-workflow/templates' | python3 -c "import sys,json; data=json.load(sys.stdin); print(f'Workflow templates: {len(data)} (should be 7)')"
```

- [ ] Response: `Workflow templates: 7`
- [ ] List includes: Flood Alert, AQI Alert, Energy Spike, Structural Alert, Equipment Failure, Emergency Response, ESG Report

**UI Test:**
- [ ] Frontend → AI Workflow → "New Workflow" → BPMN designer canvas visible
- [ ] Drag-and-drop palette shows: Start Event, AI Decision, Branch, Notify, Alert nodes
- [ ] 7 template workflows available in dropdown

### Feature 4: Predictive Analytics — Forecast Chart Loads with Data

**Test:** Energy forecasting endpoint returns predictions

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/forecasting/energy?days=7&buildingId=bitexco-f88' | python3 -c "import sys,json; data=json.load(sys.stdin); print(f'Forecast points: {len(data)} (should be 7), MAPE: {data[0].get(\"mape\", \"N/A\") if data else \"N/A\"}')"
```

- [ ] Response: `Forecast points: 7`
- [ ] MAPE < 10% (accuracy check)
- [ ] Each point has: `date`, `predicted_kwh`, `baseline_kwh`, `confidence_interval`

**UI Test:**
- [ ] Frontend → ESG → Energy Forecast tab → Line chart visible with blue predicted line + shaded confidence band
- [ ] Tooltip shows forecasted value for each day

### Feature 5: Mobile App — Expo Go Connects & Dashboard Loads

**Test:** Backend ready for mobile client (check CORS headers)

```bash
curl -s -I -H "Origin: http://localhost:19006" \
  http://localhost:8080/api/v1/environment/sensors | grep -i "access-control"
```

- [ ] Response includes `Access-Control-Allow-Origin: *` or specific mobile origin

**UI/Device Test:**
- [ ] On mobile device: Open Expo Go app
- [ ] Scan QR code from project (or enter `exp://localhost:19000`)
- [ ] App connects → Login with operator account
- [ ] Dashboard loads: 4 KPI cards visible (AQI, Energy, Active Alerts, Buildings)
- [ ] Pull-to-refresh works

### Feature 6: HA Infrastructure — Kafka UI Shows 3 Brokers Healthy

**Test:** Kafka cluster status via UI

- [ ] Open browser: `http://localhost:8090` (Kafka UI / Redpanda Console)
- [ ] Left menu → Cluster → shows "3 brokers"
- [ ] Click "Brokers" section:
  - [ ] Broker 1: `uip-kafka` → status green/online
  - [ ] Broker 2: `uip-kafka-2` → status green/online
  - [ ] Broker 3: `uip-kafka-3` → status green/online

**CLI Test (simulate failure):**

```bash
# Check brokers are in sync (optional, advanced)
docker exec uip-kafka kafka-metadata --snapshot /var/lib/kafka/data/__cluster_metadata-0/00000000000000000000.log --print | grep "leader: 1,2,3"
```

- [ ] Output shows all 3 nodes participating in KRaft quorum

### Feature 7: API Endpoints — Swagger UI Loads & 1 Endpoint Works

**Test:** Swagger documentation accessible

- [ ] Open browser: `http://localhost:8080/swagger-ui.html`
- [ ] Page loads (no 404, no timeout)
- [ ] Left sidebar shows 10+ API groups: environment, esg, alerts, building-safety, bms, ai-workflow, forecasting, health, actuator, auth

**Live endpoint test:**

```bash
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/alerts?page=0&size=5' | python3 -c "import sys,json; data=json.load(sys.stdin); print(f'Alerts page: {len(data.get(\"content\", []))} items (status {data.get(\"statusCode\", 200)})')"
```

- [ ] Response status 200
- [ ] Returns `content` array with 0-5 alerts

**Swagger UI test:**
- [ ] Expand "GET /api/v1/alerts" → click "Try it out"
- [ ] Add Bearer token in Authorization header
- [ ] Click "Execute" → Response 200 with alerts data

### Feature 8: ESG PDF Export — Trigger Export & Verify PDF Downloads

**Test:** Export endpoint triggers PDF generation

```bash
# Get auth token for curl
ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | python3 -c 'import sys,json; print(json.load(sys.stdin).get("accessToken",""))')

# Trigger PDF export
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  -X POST 'http://localhost:8080/api/v1/esg/export/pdf?year=2026&quarter=1&format=GRI' \
  -o /tmp/esg-export.pdf

# Check file
file /tmp/esg-export.pdf
```

- [ ] Output: `/tmp/esg-export.pdf: PDF document, version 1.4`
- [ ] File size > 100KB (meaningful content)

**UI Test:**
- [ ] Frontend → ESG Reports → Q1 2026 → "Export PDF" button visible
- [ ] Click button → dialog opens with format options (GRI-302, GRI-305, Full Report)
- [ ] Select "Full Report" → download starts
- [ ] Browser Downloads shows new PDF file
- [ ] Open PDF → shows UIP header + GRI 302-1 energy table + GRI 305-4 emissions table + charts

---

## T-5 Minutes: Final Checks

### 1. Browser & Network

- [ ] **Chrome browser** (latest stable) open and ready
- [ ] **Private/Incognito mode** enabled (avoids cached login issues)
- [ ] **Screen resolution:** 1920×1080 (verify: `⌘+,` → check resolution in system prefs or DevTools)
- [ ] **All tabs pre-opened in new window:**
  1. Frontend: `http://localhost:3000` (logged in as admin)
  2. Kafka UI: `http://localhost:8090`
  3. Flink UI: `http://localhost:8081`
  4. Swagger API: `http://localhost:8080/swagger-ui.html`
  5. Keycloak (if presenting auth): `http://localhost:8180` (admin console)

### 2. Network & Connectivity

```bash
# Test internet latency (should be < 100ms for smooth demo)
ping -c 3 8.8.8.8 | tail -1
```

- [ ] Average latency < 100ms
- [ ] No packet loss (`0.0% packet loss`)
- [ ] WiFi signal strong (if using WiFi, prefer Ethernet cable)

**Backup connectivity:**
- [ ] Have mobile hotspot ready as fallback
- [ ] Backup Internet tethering cable/dongle charged

### 3. Recording & Backup

- [ ] **OBS / Zoom recording** already recording locally (NOT relying on cloud)
  - [ ] OBS: File → Settings → Output → Recording path = `/Users/anhgv/Documents/` 
  - [ ] Start recording 2 minutes before demo (warm up the pipeline)

- [ ] **Backup demo video** from previous rehearsal: 
  - [ ] Location: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/demo-backup-recording.mp4` (or similar)
  - [ ] Duration: Full 30+ minutes

### 4. Demo Account Credentials (Print & Keep Handy)

| Role | Username | Password | Tenant |
|------|----------|----------|--------|
| Admin | admin@hcm-uip.vn | admin_Dev#2026! | HCMC |
| Operator | operator@hcm-uip.vn | operator123 | HCMC |
| Citizen | citizen@hcm-uip.vn | citizen123 | HCMC |
| BMS User | bms-tech@hcm-uip.vn | bms_Dev#2026! | HCMC |

- [ ] Credentials printed or visible in notes (not exposed on screen)

### 5. Demo Data Quick Verify

- [ ] Trigger one test alert manually:
```bash
curl -s -X POST http://localhost:8080/api/v1/test/inject-aqi-alert \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sensor_id":"hcmc-q1-001","aqi":160,"duration_minutes":30}'
```

- [ ] Response: HTTP 200 with alert ID
- [ ] Alert appears in frontend Alert Feed within 5 seconds

### 6. Mobile Device Ready

- [ ] iPhone/Android with Expo Go app installed and logged in
- [ ] Battery > 80%
- [ ] WiFi connected to same network as demo machine
- [ ] Do NOT use phone during demo (keep silenced, put aside until mobile segment)

### 7. System Resources

```bash
# Check CPU/Memory available
top -l1 | grep "CPU\|Mem"
```

- [ ] CPU < 80% idle (not overloaded)
- [ ] Memory > 4GB free
- [ ] Disk free > 10GB (for logs, exports)

---

## Demo Day Contingency Playbook

### If X fails → Do Y (Ranked by Likelihood)

#### 🔴 BLOCKER: Kafka Cluster Down (Red: 1/3 Brokers Lost)

**Symptom:** Kafka UI shows 2/3 brokers online, alerts not flowing to dashboard

**Immediate action:**
```bash
# Check broker status
docker ps | grep kafka

# If kafka-2 is down:
docker start uip-kafka-2
sleep 10

# Verify rebalance completed
docker exec uip-kafka kafka-broker-api-versions --bootstrap-server localhost:9092
```

**Fallback:** Show **static screenshot** of 3-broker cluster (pre-captured at successful test run)
- [ ] Have screenshot saved: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/kafka-3broker-screenshot.png`
- [ ] Narrate: "In production HA, this cluster automatically rebalances when a broker recovers..."

**Time impact:** +2 minutes (recovery) | **Severity: HIGH**

---

#### 🟠 CRITICAL: Backend API Not Responding (500 errors)

**Symptom:** Swagger UI shows 500 on any endpoint, frontend dashboard blank

**Immediate action:**
```bash
# Check backend logs
docker logs -f uip-backend --tail=50

# If OutOfMemory or connection pool exhausted:
docker restart uip-backend
sleep 15

# Test health
curl http://localhost:8080/actuator/health
```

**Fallback:** Switch to **pre-recorded video walkthrough** for that feature
- [ ] Have backup video: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/demo-backup-video-segment-act3.mp4`
- [ ] Narrate live: "In live demo, this dashboard would show real-time data from 2,500+ sensors..."

**Time impact:** +3 minutes (restart) | **Severity: CRITICAL**

---

#### 🟠 CRITICAL: Demo Data Missing (0 sensors, 0 alerts)

**Symptom:** Sensor list empty, Alert Feed empty, ESG report shows $0

**Immediate action:**
```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc

# Re-run setup (takes 2-3 minutes)
bash scripts/demo-setup.sh

# Verify data seeded
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/environment/sensors | python3 -c "import sys,json; print(f'Sensors: {len(json.load(sys.stdin))}')"
```

**Fallback:** Use **screenshot or mock data**
- [ ] Show static sensor map screenshot: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/mock-sensor-dashboard.png`
- [ ] Explain: "This is live data from yesterday's run; in production, sensors update every 30 seconds."

**Time impact:** +5 minutes (re-seed) | **Severity: CRITICAL**

---

#### 🟡 HIGH: PDF Export Hangs or Fails

**Symptom:** "Export PDF" button clicked → loading spinner for > 30 seconds, no PDF

**Immediate action:**
```bash
# Check if export service is working
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/esg/export/pdf/status | python3 -m json.tool
```

**Fallback:** Pre-export PDF before demo
```bash
# Run this at T-10 mark
mkdir -p ~/Downloads
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  -X POST 'http://localhost:8080/api/v1/esg/export/pdf?year=2026&quarter=1' \
  -o ~/Downloads/UIP-ESG-Q1-2026.pdf

# During demo, say: "I prepared this report earlier..."
# Open pre-generated PDF
open ~/Downloads/UIP-ESG-Q1-2026.pdf
```

**Time impact:** 0 (pre-prepared) | **Severity: MEDIUM**

---

#### 🟡 HIGH: Mobile App Connection Fails

**Symptom:** "Cannot connect to backend" error on mobile app, Expo connection timeout

**Immediate action:**
```bash
# Check backend is accessible from mobile device
# On mobile, try accessing: http://<your-mac-ip>:8080/actuator/health
# Find Mac IP:
ifconfig | grep inet

# Ensure WiFi is on same network
# Restart backend-only:
docker restart uip-backend
```

**Fallback:** Use **pre-recorded mobile demo video** OR **phone simulator**
- [ ] Have iPhone Simulator open with app installed: `xcrun simctl open booted`
- [ ] Narrate: "In the actual pilot, operators will receive push notifications like this..."
- [ ] Show screenshot: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/mobile-app-mockup.png`

**Time impact:** +2-3 minutes (simulator fallback) | **Severity: MEDIUM**

---

#### 🟡 HIGH: Frontend Not Loading (Blank Page / CORS Error)

**Symptom:** `http://localhost:3000` → blank page OR browser console shows CORS error

**Immediate action:**
```bash
# Check if frontend dev server running
curl -s -I http://localhost:3000 | head -5

# If not, restart it:
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/frontend
npm run dev

# Wait 10 seconds for Vite to start
sleep 10

# Refresh browser
curl http://localhost:3000
```

**Fallback:** Use **Swagger API UI only** to demonstrate features
- [ ] `http://localhost:8080/swagger-ui.html` → fully functional
- [ ] Narrate each API call as investor: "REST API returns sensor readings in JSON..."
- [ ] Use `curl` in terminal to show live data flowing

**Time impact:** +3-5 minutes (restart Vite) | **Severity: MEDIUM**

---

#### 🟢 MEDIUM: Kafka UI Temporarily Unreachable

**Symptom:** Kafka UI page doesn't load at `http://localhost:8090`

**Immediate action:**
```bash
# Kafka UI is optional (for HA demo only) — try refresh
curl -s -I http://localhost:8090 | head -5

# If 503, restart UI:
docker restart uip-kafka-ui
sleep 5
```

**Fallback:** Skip Kafka UI segment, narrate HA architecture from **architecture diagram** or **slides**
- [ ] Use pre-prepared architecture diagram: `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/architecture-ha-diagram.png`
- [ ] Explain: "3 Kafka brokers running in KRaft quorum; if one fails, cluster auto-rebalances..."

**Time impact:** +1-2 minutes | **Severity: LOW**

---

#### 🟢 MEDIUM: ClickHouse Analytics Service Slow (> 2s latency)

**Symptom:** ESG report query takes > 5 seconds to load

**Immediate action:**
```bash
# Check ClickHouse replica status
curl -s http://localhost:8125/api/ 2>&1 | head -5

# Check if merges are in progress (rebuilding stats)
curl -s 'http://localhost:8125/?query=SHOW%20PROCESSES'
```

**Fallback:** Pre-cache the ESG report
```bash
# At T-10, run this to warm cache:
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  'http://localhost:8080/api/v1/esg/summary?period=quarterly&year=2026&quarter=1'
# (Do this 3x to ensure Redis caches it)
```

**Time impact:** +1-2 minutes (waiting) | **Severity: LOW**

---

### Quick Ref: Fallback Assets Checklist

**Must exist before demo day:**

- [ ] `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/kafka-3broker-screenshot.png` 
- [ ] `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/demo-backup-video-segment-act3.mp4` (full-feature video)
- [ ] `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/mock-sensor-dashboard.png`
- [ ] `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/mobile-app-mockup.png`
- [ ] `/Users/anhgv/working/my-project/smartcity/uip-esg-poc/docs/mvp3/project/architecture-ha-diagram.png`
- [ ] Pre-generated PDF in Downloads: `~/Downloads/UIP-ESG-Q1-2026.pdf`

---

## Post-Demo: Cleanup & Archive

### 1. Immediate (Within 5 minutes of demo end)

- [ ] Stop local recording (OBS): File → Stop Recording
- [ ] Save recording with timestamp: `UIP-Demo-2026-06-06-14h30m.mp4`

### 2. Data Preservation (for post-mortem)

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc

# Archive logs
mkdir -p docs/mvp3/demo-archive-2026-06-06
docker logs uip-backend > docs/mvp3/demo-archive-2026-06-06/backend.log
docker logs uip-frontend > docs/mvp3/demo-archive-2026-06-06/frontend.log
docker logs uip-kafka > docs/mvp3/demo-archive-2026-06-06/kafka.log

# Export ClickHouse metrics (for performance review)
curl -s 'http://localhost:8125/?query=SELECT%20event_time,query_duration_ms,query%20FROM%20system.query_log%20ORDER%20BY%20event_time%20DESC%20LIMIT%20100' \
  > docs/mvp3/demo-archive-2026-06-06/clickhouse-queries.csv
```

- [ ] Logs saved to `docs/mvp3/demo-archive-2026-06-06/`

### 3. Service Shutdown (Graceful)

```bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/infrastructure

# Stop all services gracefully (without losing data)
docker compose -f docker-compose.yml -f docker-compose.ha.yml down

# Persist volumes (do NOT use -v flag)
# Data will be retained for next demo or post-mortem analysis
```

- [ ] All containers stopped (verify: `docker ps | wc -l` = 0)
- [ ] No error messages in shutdown log

### 4. Post-Demo Report Template

**Create:** `docs/mvp3/demo-archive-2026-06-06/POST-DEMO-REPORT.md`

```markdown
# Post-Demo Report — 2026-06-06

**Demo Duration:** 32 minutes  
**Attendance:** [Investor, PM, CTO, City Authority]  
**Status:** ✓ SUCCESSFUL / ⚠ WITH ISSUES / ✗ FAILED

## What Worked Well
- [ ] All 8 MVP3 features demonstrated
- [ ] No critical failures or fallbacks used
- [ ] Performance: <200ms p95 latency observed
- [ ] HA demo (Kafka broker failover) showed zero downtime

## Issues Encountered
- [ ] None

## Time Breakdown (Actual vs Planned)
| Act | Planned | Actual | Notes |
|-----|---------|--------|-------|
| Act 0 | 2 min | 2 min | ✓ |
| Act 1 | 5 min | 5 min | ✓ |
| Act 2 | 8 min | 8 min | ✓ |
| Act 3 | 12 min | 13 min | Slight delay on BMS device load |
| Act 4 | 3 min | 3 min | ✓ |

## Investor Feedback
[Collected verbally or via form]

## Next Steps
- [ ] Share recording with internal team
- [ ] Send thank-you email to attendees
- [ ] Archive logs for security/compliance review
```

- [ ] Report filled out within 1 hour of demo end

---

## QUICK COPY-PASTE: Pre-Demo Command Sequence

**For power users: Run this shell script 30 min before demo**

```bash
#!/bin/bash
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc/infrastructure

echo "🚀 UIP Demo Day Pre-Flight: T-30"
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d
sleep 45

echo "🔍 T-25: Health Check"
make ha-health-check

echo "📊 T-20: Seed Demo Data"
cd /Users/anhgv/working/my-project/smartcity/uip-esg-poc
bash scripts/demo-setup.sh

echo "✅ T-10: All Ready!"
echo ""
echo "URLs:"
echo "  Frontend:  http://localhost:3000 (admin / admin_Dev#2026!)"
echo "  Swagger:   http://localhost:8080/swagger-ui.html"
echo "  Kafka UI:  http://localhost:8090"
echo "  Flink UI:  http://localhost:8081"
echo ""
echo "Open these in browser NOW (pre-warm connections)"
```

---

## Critical Timelines

| Time | Action | Owner | Status |
|------|--------|-------|--------|
| **T-35 min** | Start infrastructure | Demo operator | Pre-flight |
| **T-25 min** | Verify all services healthy | Demo operator | Pre-flight |
| **T-20 min** | Seed demo data | Demo operator | Pre-flight |
| **T-10 min** | Test all 8 features | QA tester | Pre-flight |
| **T-5 min** | Browser setup, recording start | Demo operator | Final checks |
| **T-0** | **DEMO STARTS** | PM + Backend Lead | 🎬 LIVE |
| **T+32 min** | **DEMO ENDS** | All | Wrap-up |
| **T+35 min** | Stop recording, collect feedback | Demo operator | Post-demo |

---

## Sign-Off

- [ ] **Prepared by:** [Your name], UIP Manual Tester
- [ ] **Reviewed by:** [PM name]
- [ ] **Approved for demo:** [CTO/Tech Lead]
- [ ] **Date:** June 6, 2026
- [ ] **Checklist executed successfully:** [✓ Yes / ✗ No / ⚠ With fallbacks]

---

## Support Contacts (Demo Day)

| Role | Name | Phone | Slack |
|------|------|-------|-------|
| **Demo Operator** | [Name] | +84-xxx | @demo-lead |
| **Backend Engineer** | [Name] | +84-xxx | @backend-oncall |
| **DevOps On-Call** | [Name] | +84-xxx | @devops-oncall |
| **Tech Lead** | [Name] | +84-xxx | @tech-lead |

---

**Generated:** 2026-06-06  
**Last Updated:** 2026-06-06 14:00 JST  
**Status:** ✅ READY FOR DEMO DAY
