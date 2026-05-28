# Sprint 5 — Test Strategy

**Date:** 2026-05-28
**Sprint:** MVP3-5
**QA Lead:** QA Engineer
**Status:** APPROVED

---

## 1. Test Scope

| Area | Type | Priority |
|------|------|----------|
| BUG-S4-T04 Forecast Fallback | Unit + Manual | P0 |
| BMS CRUD API | Integration | P0 |
| BMS Device Command API | Integration | P0 |
| BMS Kafka Producer | Integration | P1 |
| Modbus TCP Adapter | Integration (mock) | P0 |
| BACnet/IP Adapter | Integration (mock) | P0 |
| BACnet Who-Is Discovery | Integration (mock) | P1 |
| V27 Migration | DB verification | P0 |
| iot-ingestion-service | Build + Unit | P1 |
| Alerts SSE | Manual | P0 |
| BMS Frontend | Manual | P1 |
| Regression Suite | Automated | P0 |

---

## 2. BMS Integration Test Scenarios (10 TCs)

### TC-01: Modbus — Poll 5 Mock Devices
**Gate:** G2
**Steps:**
1. Create 5 Modbus devices via `POST /api/v1/bms/devices` with different unitIds
2. Configure register map: `{ "temperature": "0:1:C", "humidity": "1:1:%" }`
3. Start Modbus mock server (j2mod ModbusSlave) with 5 virtual devices
4. Trigger poll cycle
**Expected:** 5 devices return readings, values match mock registers, response time < 3s per device

### TC-02: Modbus — Timeout + Retry
**Gate:** G2
**Steps:**
1. Create Modbus device pointing to non-existent host
2. Trigger poll
3. Verify retry behavior (3 retries with backoff)
**Expected:** All 3 retries fail within 10s, BmsAdapterException thrown

### TC-03: Modbus — CRC Error + Retry
**Gate:** G2
**Steps:**
1. Create Modbus device with corrupt mock server
2. Trigger poll
**Expected:** CRC error detected, retry attempted, BmsAdapterException on final failure

### TC-04: Modbus — Circuit Breaker Open
**Gate:** G2
**Steps:**
1. Create Modbus device with failing mock server
2. Trigger 3 consecutive poll failures
3. Verify CB state changes to OPEN
4. Verify readings routed to DLQ topic
**Expected:** CB open after 3 failures, DLQ message received on `UIP.bms.reading.raw.v1.dlq`

### TC-05: BACnet — ReadProperty Device 1001
**Gate:** G3
**Steps:**
1. Create BACnet device: `deviceId=1001, host=localhost, port=47808`
2. Configure property map: `{ "temperature": "analogInput:0:C" }`
3. Start BACnet mock device with AnalogInput-0 = 23.5
4. Trigger poll
**Expected:** Reading `{ type: "temperature", value: 23.5, unit: "°C" }` returned

### TC-06: BACnet — Unknown Device → Log + Skip
**Gate:** G3
**Steps:**
1. Create BACnet device with `deviceId=9999` (non-existent)
2. Trigger poll
**Expected:** No exception, empty readings list, WARN log "device 9999 not found"

### TC-07: BACnet — Who-Is Discovery → 3 Devices
**Gate:** G4
**Steps:**
1. Start 3 BACnet mock devices (IDs: 1001, 1002, 1003)
2. Call `POST /api/v1/bms/devices/discover?broadcast=255.255.255.255`
3. Wait 12s (scan timeout 10s + buffer)
4. Query `GET /api/v1/bms/devices`
**Expected:** 3 devices registered with names `BACNET-1001`, `BACNET-1002`, `BACNET-1003`, status=ONLINE

### TC-08: Manual Config API — CRUD + Idempotent Upsert
**Gate:** G5
**Steps:**
1. `POST /api/v1/bms/devices` with `{ deviceName: "AHU-01", protocol: "MODBUS_TCP", host: "192.168.1.10", port: 502 }` → 201 Created
2. `GET /api/v1/bms/devices` → list contains AHU-01
3. `POST /api/v1/bms/devices` same payload again → 200 OK (upsert, not duplicate)
4. `PUT /api/v1/bms/devices/{id}` change host → 200 OK
5. `DELETE /api/v1/bms/devices/{id}` → 204 No Content
6. `GET /api/v1/bms/devices/{id}` → 404 Not Found
**Expected:** All CRUD operations work, upsert idempotent, tenant isolation verified

### TC-09: Kafka — BMS Readings Published
**Gate:** G7
**Steps:**
1. Create Modbus device with mock server returning temperature=25.0
2. Trigger poll
3. Consume from Kafka topic `UIP.bms.reading.raw.v1`
**Expected:** Message received with `{ deviceId, tenantId, readingType: "temperature", value: 25.0, unit: "°C", timestamp }`, partition key = `tenantId:deviceId`

### TC-10: Device Command — HTTP 202 + MQTT Dispatch
**Gate:** G8
**Steps:**
1. Create device with adapter connected
2. `POST /api/v1/bms/devices/{id}/commands` with `{ commandType: "SET_POINT", payload: { value: 22, register: 0 } }`
3. Verify HTTP 202 Accepted with `{ commandId, status: "ACCEPTED", timestamp }`
4. Verify adapter.sendCommand() called with correct payload
**Expected:** HTTP 202, commandId is UUID, adapter receives command

---

## 3. Manual Test Scenarios — Alerts SSE (G10)

### MT-01: SSE Connection Established
**Steps:** Open browser → navigate to `/alerts` → check Live badge
**Expected:** Green "Live" badge, SSE connection active in Network tab

### MT-02: SSE Auto-Reconnect
**Steps:** Connect SSE → kill backend → wait → restart backend
**Expected:** Badge changes to "Offline" → "Connecting..." → "Live" within 30s

### MT-03: Severity Filter
**Steps:** Click severity filter dropdown → select "CRITICAL"
**Expected:** Only CRITICAL alerts displayed, filter persisted in URL

### MT-04: Tenant Isolation
**Steps:** Login as tenant A → check alerts → login as tenant B → check alerts
**Expected:** Each tenant only sees their own alerts, no cross-tenant data

### MT-05: CRITICAL Alerts Sort First
**Steps:** Have alerts with mixed severities → view list
**Expected:** CRITICAL alerts appear at top, sorted by severity descending

---

## 4. Regression Gate

| Check | Threshold | Method |
|-------|-----------|--------|
| Total tests | ≥ 978 | `./gradlew test` |
| Test failures | 0 | CI report |
| JaCoCo LINE | ≥ 80% | `jacocoTestReport` |
| JaCoCo BRANCH | ≥ 65% | `jacocoTestReport` |
| ArchUnit | 0 violations | `ModuleBoundaryArchTest` |
| TypeScript | 0 errors | `npx tsc --noEmit` |
| SpotBugs | 0 CRITICAL | `spotbugsMain` |

---

## 5. Quality Gates

### Gate G11 — Test Count
- Minimum: 978 tests (Sprint 4 baseline)
- Target: 1000+ (adding ~30 BMS + forecast tests)

### Gate G12 — Coverage
- LINE coverage ≥ 80% (backend)
- BRANCH coverage ≥ 65% (backend)

### Gate G13 — ADR
- `ADR-029-bms-protocol-adapter.md` exists in `docs/mvp3/architecture/`
- `com.infiniteautomation:bacnet4j:6.0.1` resolves in build

---

## 6. Risk Areas

| Risk | Probability | Mitigation |
|------|-------------|-----------|
| BACnet4J mock not available in CI | Medium | Use Testcontainers + mock socket, not real BACnet broadcast |
| j2mod ModbusSlave instability | Low | Wrap in try-with-resources, retry in test setup |
| SSE timeout in CI (headless) | Medium | Use curl-based SSE test instead of browser |
| Kafka topic auto-create disabled | Low | Verify topic exists in test setup |
| V27 migration conflicts with existing data | Low | Migration is CREATE IF NOT EXISTS, safe for fresh DB |

---

*Sprint 5 Test Strategy created: 2026-05-28 | 10 IT scenarios + 5 manual tests + regression gate*
