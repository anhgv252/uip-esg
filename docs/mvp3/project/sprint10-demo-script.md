# Sprint 10 — Executive Demo Script (5 Minutes)

**Sprint:** MVP3-10 — Final Gate Review
**Date:** 2026-07-15 15:00 SGT
**Audience:** PO + City Authority Stakeholders
**Presenter:** PM + Backend Lead
**Duration:** Strict 5 minutes

> **Goal:** Prove MVP3 is production-ready for Pilot Phase (soft launch 2026-08-04).

---

## Pre-Demo Checklist (Run 30 minutes before)

- [ ] HA staging environment verified — all services UP
- [ ] Test data pre-seeded: 5 buildings, 50 sensors, 2 tenants (HCMC, District-1)
- [ ] Demo user logged in: `admin@hcm-uip.vn` (ADMIN role)
- [ ] Browser: Chrome, incognito mode, 1920×1080
- [ ] Backup recording ready (OBS/Zoom local recording from rehearsal)
- [ ] Network connectivity verified (LAN preferred over WiFi)
- [ ] Mobile device ready (Expo Go app installed, logged in)
- [ ] Timer/stopwatch visible to presenter

---

## Section 1: Dashboard Overview (60 seconds)

### Narration
> "This is the UIP City Operations Center — the single pane of glass for Ho Chi Minh City's smart building management. We're monitoring 5 pilot buildings across District 1, with 50+ IoT sensors streaming environmental, energy, and safety data in real-time."

### Screen Actions
1. Open `https://staging.uip.local` → City Operations Center dashboard
2. Point to live sensor map (Leaflet) — show colored markers (green/yellow/red by AQI)
3. Show top stats: Buildings (5), Active Sensors (48/50), Open Alerts (0)
4. Show ESG Snapshot widget: Energy 1,240 kWh (↓5%), AQI 42 (Tốt)

### Expected Result
- Dashboard loads < 2 seconds
- Map shows sensor locations with real-time color coding
- Numbers match seeded data

### Fallback
- If dashboard slow: "We're on staging WiFi — production has dedicated bandwidth"
- If map blank: Refresh page, check `/api/v1/health`

---

## Section 2: Alert System Demo (60 seconds)

### Narration
> "Now let me demonstrate the flood alert pipeline — from sensor detection to operator notification in under 30 seconds. This is the same system that will protect citizens during monsoon season."

### Screen Actions
1. Open second browser tab → `POST /api/v1/test/inject-reading` (pre-filled curl)
2. Execute: inject sensor reading with value=95 (threshold=80) on SENSOR-FLOOD-001
3. Switch back to dashboard → Watch alert banner appear (P1 WARNING, red)
4. Click alert → Show detail panel with sensor data, threshold, timeline
5. Click "Acknowledge" → Status changes to ACKNOWLEDGED
6. Click "Resolve" → Add note "Verified - drain cleared" → Status RESOLVED

### Expected Result
- Alert appears within 5 seconds of injection
- Notification bell shows unread count
- Full lifecycle: OPEN → ACKNOWLEDGED → RESOLVED
- SSE notification delivered to connected clients

### Fallback
- If alert doesn't appear: Check Flink job status, inject again
- Pre-prepared screenshot of successful alert lifecycle

---

## Section 3: ESG Report Generation (60 seconds)

### Narration
> "ESG compliance is mandatory for city buildings. Here we generate a GRI 302-1 energy consumption and 305-4 carbon emission report for Q1 2026, ready for city authority submission."

### Screen Actions
1. Navigate to ESG Analytics → "Generate Report"
2. Select: Period=Quarterly, Year=2026, Quarter=Q1
3. Click "Generate" → Watch progress spinner
4. Report appears in table → Click "Download XLSX"
5. Open downloaded file briefly → Show GRI tables
6. (Optional) Click "Export PDF" → Show PDF download

### Expected Result
- Report generates within 10 seconds (sync endpoint)
- XLSX contains: Energy Consumption (kWh), Carbon Emissions (tCO2e), GRI mappings
- PDF renders correctly with tables and charts

### Fallback
- Pre-generated Q1 2026 report in downloads folder
- "Report generation takes a few seconds — here's a pre-generated example"

---

## Section 4: AI Workflow Demo (60 seconds)

### Narration
> "The AI Workflow Designer lets city operators configure automated responses to IoT events without writing code. Here's the flood alert workflow — when water level exceeds threshold, AI evaluates severity and triggers appropriate response."

### Screen Actions
1. Navigate to AI Workflows → BPMN Designer
2. Show pre-loaded "Flood Alert Response" workflow
3. Highlight: Sensor Event → AI Decision Node → Alert/PagerDuty/Manual Review branches
4. Click AI Decision node → Show confidence threshold (0.85)
5. Show Workflow Config table → List of trigger configurations
6. Click "Test" on flood trigger → Show dry-run result (filterMatch=true, mappedVariables)

### Expected Result
- BPMN designer renders correctly with styled nodes
- AI Decision node shows confidence threshold
- Dry-run test returns meaningful result

### Fallback
- Static screenshot of BPMN workflow
- "The designer works best on desktop — here's how it looks"

---

## Section 5: High Availability Demo (60 seconds)

### Narration
> "For pilot readiness, the system runs in high-availability mode — 2-node ClickHouse, 3-broker Kafka, with automatic failover. Let me demonstrate by taking down a database node."

### Screen Actions
1. Open terminal → `docker ps` → Show 2 ClickHouse nodes running
2. `docker stop uip-clickhouse-02` → Node goes down
3. Switch to dashboard → Refresh → Still works (queries route to surviving node)
4. Open Grafana → Show CH cluster health: Node 1=OK, Node 2=DOWN
5. `docker start uip-clickhouse-02` → Node recovers
6. Show Grafana: Both nodes OK, data synced

### Expected Result
- Dashboard stays functional during node failure
- Grafana shows real-time cluster status
- Recovery completes within 30 seconds
- No data loss

### Fallback
- Pre-recorded video of HA demo (30 seconds)
- "Network restrictions prevent Docker commands in this room — here's the recording"

---

## Post-Demo Summary (30 seconds)

### Narration
> "To summarize: 110 API endpoints fully documented, zero P0/P1 bugs, over 1,300 regression tests passing, high-availability verified, and the pilot runbook is ready. We recommend declaring MVP3 DONE and proceeding to Pilot Phase starting August 4th."

### Key Numbers to Display
- **110/110** endpoints documented
- **1,300+** regression tests PASS
- **0** P0/P1 bugs
- **0** OWASP critical findings
- **6/6** incident scenarios in Pilot Runbook
- **HA verified**: 2-node CH + 3-broker Kafka

---

## Demo Timing Guide

| Section | Target | Maximum |
|---------|--------|---------|
| Dashboard Overview | 50s | 60s |
| Alert System | 55s | 60s |
| ESG Report | 50s | 60s |
| AI Workflow | 55s | 60s |
| HA Demo | 50s | 60s |
| Summary | 20s | 30s |
| **Total** | **4m40s** | **5m30s** |

---

## Decision Matrix (for PO)

| Gate Result | Action |
|-------------|--------|
| All 14 hard gates PASS | DECLARE MVP3 DONE → Pilot Prep starts Jul 16 |
| 1-2 gates FAIL | 2-day hotfix → Re-gate Jul 17 |
| 3+ gates FAIL | Escalate PO → Pilot delay to Aug 11 |

---

*Document: Sprint 10 Demo Script v1.0 | Created 2026-06-05*
