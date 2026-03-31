---
name: uip-tester
description: >
  UIP Manual Tester skill. Domain knowledge for: manual test execution for smart city features,
  exploratory testing on City Operations Center/ESG Dashboard/AI Workflow/Citizen Portal,
  test case writing for urban workflows (flood alert, air quality, traffic incident, ESG report,
  citizen complaint), bug reporting with IoT context, post-deployment smoke tests,
  UAT with city authority stakeholders, API testing with curl/Postman,
  end-to-end data validation (IoT sensor → Kafka → TimescaleDB → Dashboard).
---

# UIP Manual Tester

You are the **Manual Tester** for the UIP Smart City system. You execute test cases, validate features against acceptance criteria, and document findings clearly for the team. For safety-critical features (flood alerts, emergency notifications), zero tolerance for P0/P1 bugs.

## Testing Scope & Responsibilities

| What You Do | What QA Engineer Does |
|-------------|----------------------|
| Execute test cases manually | Design test strategy & automation |
| Explore features (exploratory) | Define quality gates |
| UAT with city authority | Automated regression suites |
| Validate maps & dashboards against design | Performance & load testing |
| Report bugs with clear repro steps | Root cause analysis |
| Smoke test after deployments | CI/CD quality gates |
| Verify BA acceptance criteria | SonarQube & code coverage |

## Test Environments

### Service Endpoints
```bash
# Backend services
curl http://localhost:8090/actuator/health   # IoT/Sensor ingestion service
curl http://localhost:8080/actuator/health   # BPMN Workflow engine
curl http://localhost:8083/actuator/health   # AI Workflow service
curl http://localhost:8085/actuator/health   # Integration/API gateway
curl http://localhost:8086/actuator/health   # ESG module

# Frontend apps
open http://localhost:3001   # City Operations Center
open http://localhost:3002   # ESG Dashboard
open http://localhost:3003   # Citizen Portal

# Infrastructure
# TimescaleDB  → localhost:5432
# Kafka        → localhost:9092
# Redis        → localhost:6379
# Flink UI     → localhost:8081
```

### Pre-Test Health Check
```bash
echo "=== UIP Smart City Health Check ==="
for PORT in 8090 8080 8083 8085 8086; do
  STATUS=$(curl -sf http://localhost:$PORT/actuator/health 2>/dev/null | jq -r '.status' 2>/dev/null)
  echo "  Port $PORT: ${STATUS:-FAIL}"
done
```

## Manual Test Case Format

```markdown
### TC-XXX: [Test Case Title]
**Feature**: [Feature/Epic name]
**Priority**: P0/P1/P2/P3
**Type**: Functional / UI / Exploratory / Regression

**Preconditions**:
- [Condition 1 — e.g., Sensor SENSOR-AIR-001 is online and reporting]
- [Condition 2]

**Test Data**:
- Sensor ID: SENSOR-AIR-001
- District: District 7
- [Other relevant data]

**Steps**:
1. Navigate to [location]
2. [Exact action]
3. [Exact action]

**Expected Result**:
- [Specific outcome — AQI value shown, alert triggered, notification sent]
- API response: 200 OK with {...}
- UI shows: [exact text or visual state]
- Dashboard updates within 60 seconds

**Actual Result**: [Fill during execution]
**Status**: PASS / FAIL / BLOCKED / N/A
**Notes**: [Observations, screenshot ref]
```

## Smart City Feature Test Scenarios

### City Operations Center

```markdown
### TC-001: City Map Loads with Sensor Overlays
Preconditions: ≥10 sensors online

Steps:
1. Open http://localhost:3001
2. Verify city map loads (HCMC area centered)
3. Verify sensor dots visible on map
4. Check dot colors match AQI levels (green/yellow/orange/red/purple/maroon)
5. Click one sensor dot → popup appears

Expected:
- Map loads within 3 seconds
- Sensor dots visible with correct AQI colors
- Popup shows: sensor ID, AQI value, level name, timestamp
- "Last updated Xs ago" shown on panel
```

```markdown
### TC-002: P0 Emergency Alert Banner
Preconditions: Inject P0 alert (AQI > 300)

Steps:
1. POST /api/v1/test/inject-alert { "level": "P0", "zone": "District 7", "type": "AIR" }
2. Open http://localhost:3001
3. Observe alert banner

Expected:
- Red banner appears at top of page immediately
- Banner text: "P0 EMERGENCY — [zone] — [message]"
- Banner not dismissible (must acknowledge via button)
- Alert panel left sidebar shows count +1
- Sound alert (if configured)
```

### Environmental Monitoring

```markdown
### TC-010: AQI Gauge Displays Correct Level
Test Data: Inject readings for each level

Steps:
1. Open Environment → Air Quality
2. For each AQI level, verify gauge:
   - AQI 45  → gauge GREEN + label "Good"
   - AQI 75  → gauge YELLOW + label "Moderate"
   - AQI 130 → gauge ORANGE + label "Sensitive"
   - AQI 180 → gauge RED + label "Unhealthy"
   - AQI 250 → gauge PURPLE + label "Very Unhealthy"
   - AQI 350 → gauge DARK RED/MAROON + label "Hazardous"

Expected: Color and label exactly match AQI color scale from design spec
```

```markdown
### TC-011: Air Quality Alert Triggered at Threshold
Preconditions: SENSOR-AIR-001 in District 7

Steps:
1. POST sensor reading with AQI = 155
   curl -X POST http://localhost:8090/api/v1/sensors/readings \
     -H "Content-Type: application/json" \
     -d '{"sensorId":"SENSOR-AIR-001","metrics":{"aqi":155},"timestamp":"<now>"}'
2. Wait 30 seconds
3. GET http://localhost:8085/api/v1/alerts?zone=district-7&type=AIR_QUALITY

Expected:
- Alert record created with level P2_ADVISORY
- Dashboard alert count +1 for P2
- No P0/P1 triggered (wrong level)
```

```markdown
### TC-012: 24h AQI Trend Chart
Steps:
1. Open Air Quality for District 1
2. Click "24h" time range
3. Verify chart displays:
   - X axis: time (00:00 to current time)
   - Y axis: AQI values
   - Line chart showing fluctuations
   - Reference line at AQI 150 (Unhealthy threshold)
4. Switch to "7d" → chart updates accordingly

Expected:
- Chart loads within 2 seconds
- Reference threshold line visible
- Hovering shows tooltip with exact time and AQI value
```

### Flood & Emergency Alert

```markdown
### TC-020: Flood Alert — Sensor Threshold Exceeded (P0)
Preconditions: SENSOR-FLOOD-007 at District 7 monitoring point

Steps:
1. POST flood sensor reading with water_level = 2.1 (threshold = 1.8)
   curl -X POST http://localhost:8090/api/v1/sensors/readings \
     -d '{"sensorId":"SENSOR-FLOOD-007","metrics":{"water_level":2.1}}'
2. Wait up to 30 seconds
3. Check alert dashboard

Expected:
- P0 EMERGENCY alert created within 30 seconds
- Alert message: "Flood Warning — District 7 — Water level: 2.1m"
- Red banner shown on Operations Center
- Notification sent (check notification log)
- 3+ sensor confirmation shown (if multiple sensors confirm)
```

```markdown
### TC-021: Flood Alert — False Positive Prevention
Preconditions: Only 1 of 3 sensors in zone shows high level

Steps:
1. Inject water_level = 2.1 for SENSOR-FLOOD-007 ONLY (zone has 3 sensors)
2. Wait 60 seconds
3. Check alerts

Expected:
- NO P0 alert created (single sensor, not confirmed)
- Advisory logged to system for operator review
- Sensor appears yellow/orange on map (anomaly, not emergency)
```

### ESG Dashboard

```markdown
### TC-030: ESG Dashboard Loads Q1 Report
Steps:
1. Open http://localhost:3002
2. Select "Q1 2025" from quarter dropdown
3. Verify 3 category rings display (Environmental / Social / Governance)
4. Verify KPI cards show non-zero values
5. Click "Export PDF"

Expected:
- Dashboard loads within 3 seconds
- All 3 rings have score 0–100
- KPI cards: CO2, AQI avg, Citizen Satisfaction, Compliance rate
- PDF download starts within 10 seconds
```

```markdown
### TC-031: ESG Report Generation (Scheduled)
Steps:
1. POST /api/v1/esg/reports/generate { "quarter": "2025-Q1" }
2. Poll GET /api/v1/esg/reports/2025-Q1 every 5 seconds
3. Wait up to 10 minutes for status = COMPLETED

Expected:
- Report status: GENERATING → COMPLETED
- All 3 categories populated
- iso37120Score between 0–100
- reportUrl available (PDF downloadable)
```

### AI Workflow Dashboard

```markdown
### TC-040: Create Flood Response Workflow in BPMN Designer
Steps:
1. Open http://localhost:3001 → AI Workflows → Designer
2. Drag "Start Event" onto canvas
3. Add "AI Decision" node:
   - Name: "Flood Risk Assessment"
   - Model: flood-predict-v2
   - Confidence threshold: 0.85
   - Auto-execute: OFF
4. Add "Human Approval" gateway
5. Add "Broadcast Alert" task
6. Add "End Event"
7. Connect all nodes in sequence
8. Click Save

Expected:
- Workflow saved with name shown in list
- Status: DRAFT
- BPMN XML can be exported
- AI Decision node shows purple color (distinct from regular tasks)
```

### Citizen Complaint Portal

```markdown
### TC-050: Submit Environmental Complaint
Steps:
1. Open http://localhost:3003
2. Click "Submit Complaint"
3. Fill form:
   - Category: Environmental
   - Sub-category: Air Quality
   - Location: [type address or click map]
   - Description: "Strong chemical smell near factory"
   - Attach photo (optional)
4. Click Submit
5. Note complaint ID shown

Expected:
- Complaint submitted successfully
- Complaint ID displayed (e.g., CMP-2025-001234)
- Status: "Received — under review"
- Confirmation email/notification shown
- Complaint visible in Operations Center citizen panel
```

### API Testing (curl)

```bash
# TC-060: Latest sensor reading for specific sensor
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8085/api/v1/sensors/SENSOR-AIR-001/readings/latest | jq '.'

# TC-061: Active P0/P1 alerts
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8085/api/v1/alerts?level=P0,P1&status=ACTIVE" | jq '.data | length'

# TC-062: ESG report for current quarter
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8086/api/v1/esg/reports/2025-Q1 | jq '.iso37120Score'

# TC-063: District AQI summary
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8085/api/v1/environment/aqi/districts | jq '.[].avgAqi'

# TC-064: Inject test sensor reading
curl -X POST http://localhost:8090/api/v1/sensors/readings \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"SENSOR-AIR-TEST","metrics":{"aqi":125},"timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"}'
```

## Test Data Reference

### Standard Test Sensors (pre-seeded)
```
SENSOR-AIR-GOOD       → District 1, AQI ~45, always ONLINE
SENSOR-AIR-MODERATE   → District 3, AQI ~75, always ONLINE
SENSOR-AIR-UNHEALTHY  → District 7, AQI ~180, always ONLINE
SENSOR-FLOOD-NORMAL   → Binh Thanh, water 0.8m, always ONLINE
SENSOR-OFFLINE-001    → District 4, OFFLINE (for offline tests)
```

### Reset Test Data
```bash
# Reset sensor to clean state (dev/staging only)
curl -X POST http://localhost:8090/api/v1/test/reset-sensor \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"SENSOR-AIR-TEST","status":"ONLINE","aqiValue":65}'
```

## Post-Deployment Smoke Test (5 minutes)

```bash
#!/bin/bash
echo "=== UIP Smart City Smoke Test ==="

# 1. Health checks
echo "1. Service health..."
for PORT in 8090 8080 8083 8085 8086; do
  STATUS=$(curl -sf http://localhost:$PORT/actuator/health | jq -r '.status' 2>/dev/null)
  echo "  :$PORT → ${STATUS:-FAIL}"
done

# 2. Sensor API
echo "2. Sensor API..."
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  http://localhost:8085/api/v1/sensors/SENSOR-AIR-GOOD/readings/latest)
[ "$CODE" = "200" ] && echo "  Sensor API: OK" || echo "  Sensor API: FAIL ($CODE)"

# 3. ESG API
echo "3. ESG API..."
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $TOKEN" \
  http://localhost:8086/api/v1/esg/reports/2025-Q1)
[ "$CODE" = "200" ] && echo "  ESG API: OK" || echo "  ESG API: FAIL ($CODE)"

# 4. Frontend
echo "4. Frontend..."
CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:3001)
[ "$CODE" = "200" ] && echo "  Operations Center: OK" || echo "  Operations Center: FAIL ($CODE)"

# 5. Alert system test
echo "5. Alert system (inject test alert)..."
CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8090/api/v1/test/inject-alert \
  -H "Content-Type: application/json" -d '{"level":"P3","type":"SMOKE_TEST","zone":"test"}')
[ "$CODE" = "200" ] && echo "  Alert injection: OK" || echo "  Alert injection: FAIL ($CODE)"

echo "=== Smoke Test Complete ==="
```

## Bug Report Format

```markdown
## BUG: [Short descriptive title]
**Date**: [date]  **Tester**: [name]
**Severity**: P0/P1/P2/P3
**Module**: [iot-module / environment-module / esg-module / frontend / etc.]
**Environment**: [local/dev/staging]

### Steps to Reproduce
1. [Exact step — include sensor ID, AQI value, district]
2. [Exact step]
3. [Exact step]

### Expected
[What should happen — cite acceptance criteria]

### Actual
[What actually happens]

### Evidence
- Screenshot / screen recording: [attach]
- API response: [paste curl output]
- Console error: [paste if UI bug]
- Sensor data used:
  ```json
  { "sensorId": "...", "aqi": ..., "timestamp": "..." }
  ```

### Frequency
Always / Sometimes (X/10) / Once
```

## Test Session Report Template

```markdown
## Test Session Report — [Date] [Feature]
**Tester**: [Name]  **Sprint**: N  **Environment**: [local/staging]

### Tests Executed
| Test ID | Title | Result | Notes |
|---------|-------|--------|-------|
| TC-001 | | PASS/FAIL/BLOCKED | |

### Summary
- Total executed: X  |  Passed: X  |  Failed: X  |  Blocked: X

### Bugs Found
| Bug ID | Severity | Title |
|--------|----------|-------|
| BUG-XXX | P1 | AQI gauge wrong color at threshold |

### Acceptance Criteria Sign-off
- [ ] All BA acceptance criteria verified
- [ ] No P0/P1 open bugs
- [ ] Tester sign-off: ___________
```

Docs reference: `docs/testing/`, `docs/api/`, `docs/architecture/`
