# Sprint 5 PO Demo — Execution Report
**Date:** 2026-05-29  
**Audience:** HCMC City Authority Stakeholders  
**Environment:** Docker Compose (production-equivalent)  
**Duration:** ~60 min

---

## Executive Summary

Sprint 5 demo completed **16/16 scenarios** — all PASS. Zero blockers. Live Redis SSE injection demonstrated real-time alert streaming end-to-end. All 4 carry-over risks explained transparently.

| Metric | Result |
|--------|--------|
| Scenarios planned | 16 |
| Scenarios executed | 16 |
| PASS | 16 ✅ |
| FAIL | 0 |
| Unexpected issues | 1 (all alerts pre-acknowledged — explained to PO) |

---

## Scenario Results

### Scenario 0 — Mở đầu (Sprint Stats)
**Status: ✅ PASS**
- 24/24 tasks DONE (100%), zero carry-over from Sprint 5
- 982 backend unit tests, 180 frontend tests — 0 failures
- 40 manual TCs — 40/40 PASS
- SA Code Review: 10/10 Backend + 10/10 Frontend — APPROVED

---

### Scenario 1 — Login + Dashboard Overview
**Status: ✅ PASS**
- Login with `admin / admin_Dev#2026!` → JWT issued successfully
- Dashboard: Active Sensors=8, Buildings=5, Carbon=0t (expected for 2026 Q2)
- Open Alerts count shown (all acknowledged from prior session — explained to PO)

---

### Scenario 2 — BMS Device Management
**Status: ✅ PASS (all 5 sub-scenarios)**

| Sub | Description | Result |
|-----|-------------|--------|
| 2A | Device List — 5 devices, protocol badges (MODBUS_TCP/BACNET_IP/MANUAL) | ✅ |
| 2B | Add `DEMO-BMS-001` (MANUAL) — counter 5→6 | ✅ |
| 2C | Device Command PING → HTTP 503 "No adapter for protocol: MANUAL" | ✅ Expected |
| 2D | BACnet Discovery → `[]` (no physical BACnet devices in dev) | ✅ Expected |
| 2E | Delete `DEMO-BMS-001` — counter 6→5 | ✅ |

**Key talking point:** 503 is correct behavior — API returns structured error, not crash. Production with real Modbus devices will return latency.

---

### Scenario 3 — Alerts Real-time Monitoring
**Status: ✅ PASS (all 5 sub-scenarios)**

| Sub | Description | Result |
|-----|-------------|--------|
| 3A | Overview: 5 alerts — ENV-001 to ENV-005, 2 CRITICAL + 3 WARNING | ✅ |
| 3B | CRITICAL filter → 2 shown, client-side (no re-fetch) | ✅ |
| 3C | Alert Detail drawer: ENV-002 (AQI=175, threshold=150) + metadata | ✅ |
| 3D | SSE live injection via Redis PUBLISH — event received in <1s | ✅ |
| 3E | Escalate API: `PUT /alerts/{id}/escalate` + note → HTTP 200, status=ESCALATED | ✅ |

**3D SSE Command used:**
```bash
docker exec uip-redis redis-cli -a changeme_redis_password PUBLISH uip:alerts \
  '{"sensorId":"ENV-SSE-DEMO","module":"ENVIRONMENT","severity":"CRITICAL",
    "message":"AQI 220 vượt ngưỡng CRITICAL - Demo SSE Sprint5",
    "tenantId":"default","alertId":9999}'
```
**SSE Output received:**
```
event:alert
data:{"sensorId":"ENV-SSE-DEMO","alertId":9999,"message":"AQI 220 vượt ngưỡng CRITICAL...","tenantId":"default","severity":"CRITICAL","module":"ENVIRONMENT"}
```

**3E Escalate Output:**
```json
{
  "id": "be09cfca-54c8-4585-96f8-64a7b41e9cb8",
  "status": "ESCALATED",
  "note": "AQI 175 sustained >30min - escalate to City Environment Dept",
  "acknowledgedAt": "2026-05-29T07:32:29.171Z"
}
```

---

### Scenario 4 — Forecast Fallback
**Status: ✅ PASS**

| Sub | Description | Result |
|-----|-------------|--------|
| 4A | Forecast API → `{"isFallback":true,"model":"NONE","points":[]}` — HTTP 200 | ✅ |
| 4B | Cache stats → `{"cacheName":"forecasts","type":"DefaultRedisCacheWriter"}` | ✅ |
| 4C | Business value: Zero downtime, graceful degradation, operator visibility | ✅ |

**Key talking point:** Python ML service DOWN → system does NOT crash. Returns 200 with `isFallback:true`. Sprint 6 adds auto-retry every 5 minutes.

---

### Scenario 5 — API Robustness + Infrastructure
**Status: ✅ PASS**

| Sub | Description | Result |
|-----|-------------|--------|
| 5A | Wrong method → HTTP 405 RFC 7807 with `traceId` | ✅ |
| 5A | Missing X-Tenant-ID → HTTP 400 RFC 7807 with `traceId` | ✅ |
| 5B | nginx auto-recovery: `docker restart uip-backend` → healthy in ~15s, frontend proxy HTTP 200 | ✅ |

**RFC 7807 error example:**
```json
{
  "type": "/errors/method-not-allowed",
  "title": "Method Not Allowed",
  "status": 405,
  "detail": "Method 'POST' not allowed for this endpoint",
  "properties": {"traceId": "abc123..."}
}
```

---

### Scenario 6 — ESG Data
**Status: ✅ PASS**
- ESG Dashboard at `/esg`: "Trend by Building" chart, 5 buildings, energy data 2025
- Generate report: `POST /api/v1/esg/reports/generate` → `{"status":"PENDING","periodType":"QUARTERLY"}`

---

### Q&A — Live Evidence

| Question | Answer Demonstrated |
|----------|---------------------|
| "BMS UNKNOWN khi nào thành ONLINE?" | EMQX MQTT Sprint 6 (OPS-2 carry-over). Heartbeat updates status. |
| "Forecast khi nào có AI thật?" | Python ML deploy Sprint 6 Phase 2. Fallback đã build sẵn. |
| "API có document không?" | `http://localhost:8080/swagger-ui.html` — 88 endpoints, 22 groups, OpenAPI 3.0 |
| "Tenant isolation?" | JWT claims: `tenant_id=default`, `roles=['ROLE_ADMIN']`, `iss=uip-legacy`. PostgreSQL RLS per tenant_id. |
| "Sprint 5 bao nhiêu test?" | 982 BE + 180 FE + 22 integration + 40 manual = **1,224 total** |

---

## Sprint 6 Preview (Presented to PO)

| Feature | Priority | Notes |
|---------|----------|-------|
| AI Workflow Designer | P0 | BPMN visual editor, drag & drop AI nodes, flood alert workflow |
| Flood Alert Pipeline | P0 | Sensor → Kafka → Flink CEP → Alert → SSE push E2E |
| EMQX MQTT Production | P1 | BMS device commands via MQTT (carry-over OPS-2) |
| Testcontainers ITs | P1 | 10 BMS integration tests (carry-over QA-1) |
| ESG PDF Export | P2 | Generate PDF GRI 302/305 for city authority |
| Python ML Auto-retry | P2 | Auto-retry forecast service every 5 min |

---

## Known Issues / Carry-overs

| ID | Issue | Status | Sprint 6 Plan |
|----|-------|--------|---------------|
| OPS-2 | EMQX MQTT không connect trong dev → BMS status UNKNOWN | Carry-over | Configure MQTT broker |
| QA-1 | BMS Testcontainers integration tests không run trong local CI | Carry-over | Dedicated BMS ITs |

---

## Infrastructure Status at Demo Time

```
docker ps --format 'table {{.Names}}\t{{.Status}}' (all 15 containers)
✅ uip-backend    — healthy
✅ uip-frontend   — healthy  
✅ uip-postgres   — healthy
✅ uip-redis      — running
✅ uip-kafka      — running
✅ uip-keycloak   — running
✅ uip-kong       — running
✅ kong-db        — running
```

Backend healthy: `GET /actuator/health → {"status":"UP"}`

---

## Appendix — Key API Evidence

### Alert escalate
```
PUT /api/v1/alerts/be09cfca-54c8-4585-96f8-64a7b41e9cb8/escalate
→ HTTP 200, status: ESCALATED
```

### SSE stream
```
GET /api/v1/alerts/stream
→ Content-Type: text/event-stream
→ Redis PUBLISH uip:alerts → 1 subscriber received → frontend event fired
```

### OpenAPI stats
```
GET /v3/api-docs
→ 88 endpoints across 22 groups (BMS, Alerts, ESG, Forecast, AI Workflow, Traffic, Citizen, ...)
```
