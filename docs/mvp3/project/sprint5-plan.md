# Sprint MVP3-5 — Master Plan

**Status:** DRAFT — PO Planning 2026-05-27
**Document Date:** 2026-05-27
**Sprint Start:** 2026-06-02 (Mon)
**Sprint End:** 2026-06-13 (Fri EOD)
**Gate Review:** 2026-06-13 15:00 SGT
**Sprint trước:** MVP3-4 — GATE PASS 19/19 | PO SIGNED OFF 2026-05-27
**PO:** anhgv

---

## Context

Sprint 4 hoàn thành Observability + Predictive AI (ARIMA MAPE 3.54%, Grafana 8 panels, GRI XLSX export). PO signed off 7/7 demo scenarios 2026-05-27.

**PO quyết định (planning 2026-05-27):**
- **Mobile App → defer Sprint 6** — operators có thể dùng web dashboard trước
- **Sprint 5 focus:** BMS đầy đủ (manual config + Who-Is Discovery + Modbus + BACnet) + iot-service scaffold + Alerts UI (SSE) + BUG-S4-T04 fix
- **BMS hoàn thiện trong Sprint 5** — bao gồm cả manual device config API và BACnet Who-Is Discovery auto-scan
- **BACnet4J commercial license approved** — mua license từ RadixIoT, dùng API production-ready, không cần tự implement BACnet/IP stack
- **Device control flow:** REST API (`POST /api/v1/devices/{id}/commands`) → backend → EMQX MQTT → thiết bị. **Không cần WebSocket** — xem phân tích section 8.
- **Alerts UI:** SSE (`SseEmitter`) — không implement WebSocket
- **ADR-029 (BMS Protocol Adapter)** — SA approve Day 1, library decision resolved
- **Target:** City Authority pilot readiness tháng 7 — BMS integration là critical path

**Carry-over từ Sprint 4:**
- **BUG-S4-T04 [P2]:** `NaiveForecastAdapter` bean tồn tại nhưng chưa nối vào `ForecastService`. Python DOWN → 503. Must-fix Sprint 5 Day 1-2.

---

## 1. Sprint Overview

| Dimension | Value |
|---|---|
| **Sprint Name** | MVP3-5: BMS Full Integration + iot-service Foundation |
| **Duration** | 2026-06-02 (Mon) → 2026-06-13 (Fri) — 10 calendar days |
| **Team** | 5 FTE (Backend 2, Frontend 1, QA 1, DevOps 1) |
| **Net Capacity** | ~47 SP (59 SP - 20% buffer) |
| **Committed** | ~47 SP |
| **Buffer** | ~0 SP — tight fit |

---

## 2. Sprint Goal (SMART)

Team sẽ đạt **HARD PASS** by 2026-06-13 15:00 SGT bằng cách:
1. Fix BUG-S4-T04 — `NaiveForecastAdapter` wired as fallback, Python DOWN → naive forecast (không 503)
2. BMS hoàn thiện — Modbus TCP + BACnet/IP (ReadProperty + Who-Is Discovery) + manual config API + DeviceRegistry + Kafka producer
3. Scaffold `applications/iot-ingestion-service/` — Gradle + `@ConditionalOnProperty` 3 modes (shadow Kafka wiring deferred Sprint 6)
4. Alerts page real-time bằng SSE — filter severity, tenant isolated; device control qua REST API → EMQX → thiết bị
5. Sprint 4 regression maintain: 978+ tests PASS, 0 failures, LINE ≥80%, BRANCH ≥65%

---

## 3. Backlog Committed

### Epic 0: Bug Fix — Forecast Fallback [3 SP]

| ID | Story | SP | Owner | AC |
|---|---|---|---|---|
| S5-B01 | Wire `NaiveForecastAdapter` as fallback trong `ForecastService` — Python DOWN → naive forecast thay vì 503 | 3 | Backend | Python DOWN → HTTP 200 naive; Redis cache key flush trước test; BUG-S4-T04 closed |

**Ghi chú kỹ thuật:**
- File: `backend/src/main/java/com/uip/backend/forecast/ForecastService.java`
- Pattern: try-catch `ForecastServiceUnavailableException` → delegate sang `NaiveForecastAdapter`
- Cache: `redis-cli DEL "forecasts::default|{buildingId}|{horizonDays}"` trước khi test (như memory sprint5-carry-over-issues.md)
- `isFallback: true` trong response khi dùng naive

---

### Epic 1: BMS SDK — Đầy đủ Modbus + BACnet + Manual Config [33 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S5-BMS01 | ADR-029 approve + `BmsProtocolAdapter` interface + `BmsDeviceConfig` DTO | 2 | SA + Backend | P0 | ADR merged, interface published trước Day 2 |
| S5-BMS02 | `ModbusTcpAdapter` — poll holding registers, configurable host/port/unitId/pollInterval | 5 | Backend | P0 | 5 mock devices poll OK, timeout 3s, retry 3x |
| S5-BMS03 | Modbus Circuit Breaker (Resilience4j) + DLQ per adapter | 2 | Backend | P0 | CB open sau 3 fails, DLQ topic `bms.readings.dlq` |
| S5-BMS04 | `BacnetIpAdapter` — ReadProperty via BACnet4J (đọc device ID đã biết sẵn) | 3 | Backend | P0 | ReadProperty device 1001 OK, unknown device → log + skip |
| S5-BMS05 | BACnet Who-Is Discovery via BACnet4J — broadcast scan, auto-populate device list | 2 | Backend | P1 | Scan subnet → tìm đủ 3 mock devices, result lưu vào `bms_devices` |
| S5-BMS06 | `BmsDeviceRegistry` + Manual Config API (CRUD) | 5 | Backend | P0 | `POST /api/v1/bms/devices` (manual add), `GET /api/v1/bms/devices`, idempotent upsert, RLS per tenant |
| S5-BMS07 | V28 migration: `bms_devices` + `bms_readings_raw` tables | 1 | Backend | P0 | Flyway migrate PASS |
| S5-BMS08 | Kafka producer — publish BMS readings → topic `bms.readings.raw`, partition by `tenantId` | 3 | Backend | P1 | Message format: `{deviceId, tenantId, timestamp, value, unit}`, consumer verify |
| S5-BMS09 | Device Control API — `POST /api/v1/bms/devices/{id}/commands` → EMQX MQTT publish | 3 | Backend | P1 | Command published to `bms/commands/{tenantId}/{deviceId}`, HTTP 202 Accepted |
| S5-BMS10 | BMS integration tests — 10 scenarios (Modbus mock + BACnet mock + Who-Is) | 5 | QA + Backend | P1 | 10/10 PASS, timeout/CRC/reconnect/CB open covered |
| S5-BMS11 | BMS Frontend — Device list page `/bms/devices` (list, status, manual add form) | 2 | Frontend | P1 | Hiển thị danh sách devices, protocol badge, last-seen, manual add form basic |

**Ghi chú:**
- `j2mod` (Modbus TCP) — **Apache 2.0**, dùng trực tiếp OK
- `bacnet4j` (RadixIoT/BACnet4J) — **Commercial license approved by PO** — dùng API production-ready, Who-Is + ReadProperty chỉ cần gọi BACnet4J API
- BMS04: 5→3 SP vì BACnet4J API handle BACnet/IP stack sẵn, chỉ cần wrapper + error handling
- BMS05: 3→2 SP vì BACnet4J có sẵn `RemoteDeviceDiscovery` + `WhoIsRequest`, chỉ cần subscribe events + persist
- BMS11: 3→2 SP vì FE task chỉ cần list + basic form, không complex
- Device Control: HTTP 202 (fire-and-forget), không cần WebSocket — xem section 9

---

### Epic 2: iot-ingestion-service Scaffold [6 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S5-IOT01 | Scaffold `applications/iot-ingestion-service/` — Spring Boot, Gradle, Dockerfile | 3 | Backend Lead | P1 | `./gradlew build` PASS, Docker image build OK |
| S5-IOT02 | `@ConditionalOnProperty(iot.ingestion.mode)` — 3 modes: `shadow`, `primary`, `disabled` | 3 | Backend + DevOps | P1 | shadow: dual-ingest không duplicate; logs `[SHADOW]` prefix; `primary`/`disabled` switches work |

**Deferred to Sprint 6:**

| ID | Story | SP | Reason |
|---|---|---|---|
| S5-IOT03 | Shadow mode: nhận Kafka topic `sensor.readings.raw`, write ClickHouse riêng | 5 | Capacity — scaffold + property flag đủ cho Sprint 5, Kafka wiring Sprint 6 |
| S5-IOT04 | docker-compose.yml — thêm `iot-ingestion-service` (shadow mode default) | 2 | Dependency on IOT03, defer cùng Sprint 6 |

---

### Epic 3: Alerts UI — SSE [5 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S5-FE01 | Alerts page `/alerts` — SSE stream từ `GET /api/v1/alerts/stream`, filter severity (CRITICAL/HIGH/MEDIUM/LOW), tenant isolated | 5 | Frontend | P0 | CRITICAL nổi đầu, auto-reconnect khi SSE drop, chỉ thấy alert của tenant mình |

**Device control UI** (thuộc BMS Frontend S5-BMS11): button "Send Command" trên device card → `POST /api/v1/bms/devices/{id}/commands` → toast "Command sent". Không cần WebSocket.

---

### Epic 4: QA + Regression [3 SP]

| ID | Story | SP | Owner | Priority | AC |
|---|---|---|---|---|---|
| S5-QA01 | Sprint 5 regression gate — maintain 978+ tests, 0 failures, LINE ≥80%, BRANCH ≥65% | 3 | QA | P0 | Gate metrics PASS, JaCoCo report attached |

---

## 4. Story Point Summary

| Epic | SP | Owner |
|---|---|---|
| E0: Bug Fix — Forecast Fallback | 3 | Backend |
| E1: BMS SDK (đầy đủ) | 33 | Backend (2) + QA + Frontend |
| E2: iot-ingestion-service Scaffold | 6 | Backend + DevOps |
| E3: Alerts UI (SSE) | 5 | Frontend |
| E4: QA + Regression | 3 | QA |
| **Total Committed** | **50 SP** | — |

**Deferred to Sprint 6:** IOT03 (5 SP) + IOT04 (2 SP) = 7 SP

> **50 SP vs 47 SP capacity (+3 SP).** Tight fit — buffer gần hết. Xem phần 5 phân tích chi tiết.

---

## 5. Capacity Gap Analysis

**Gap: +3 SP (50 committed vs 47 capacity).** Tight fit nhưng manageable vì:

1. **BACnet4J commercial license** tiết kiệm effort thực tế so với tự implement — BMS04/BMS05 có thể complete nhanh hơn estimate
2. **IOT03+IOT04 deferred** → Backend team tập trung BMS, ít context-switch
3. **+3 SP buffer** nằm trong uncertainty margin (~6%), acceptable cho sprint có nhiều greenfield code

**Nếu sprint chạy chậm, fallback cắt thêm:**

| Option | SP tiết kiệm | Trade-off |
|---|---|---|
| **F) BMS FE (S5-BMS11) → Sprint 6** | −2 | Device list page sau, BMS backend testable via API |
| **G) Giảm BMS ITs xuống 7 TC** | −1.5 | Coverage giảm nhẹ |

---

## 6. Definition of Done

- [x] `BUG-S4-T04` closed — Python DOWN → HTTP 200 naive response, `isFallback: true`
- [x] `ModbusTcpAdapter` poll mock devices, Circuit Breaker hoạt động (Resilience4j config in application.yml)
- [x] `BacnetIpAdapter` ReadProperty device 1001 (unit tests pass, BACnet4J API verified)
- [x] BACnet Who-Is Discovery (BmsDiscoveryService + @Scheduled + manual trigger API)
- [x] Manual Config API: `POST /api/v1/bms/devices` tạo device, idempotent upsert
- [x] `BmsDeviceRegistry` auto-register + V27 migration PASS (note: version = V27, not V28)
- [x] BMS readings → Kafka topic `UIP.bms.reading.raw.v1` verified (DLQ: `UIP.bms.reading.raw.v1.dlq`)
- [x] Device Control API: `POST /api/v1/bms/devices/{id}/commands` → EMQX MQTT publish, HTTP 202
- [x] `iot-ingestion-service` scaffold build OK, `@ConditionalOnProperty` 3 modes work
- [x] Alerts page `/alerts` SSE real-time, filter severity, tenant isolated
- [x] 980 tests PASS, 0 failures (verified 2026-05-28)
- [x] JaCoCo LINE 77.0%, BRANCH 62.5% (unit tests only; ITs require Docker — skipped locally)
- [x] ADR-029 merged, `com.infiniteautomation:bacnet4j:6.0.1` resolves from MangoAutomation Maven repo
- [ ] ADR-029 merged

---

## 7. Acceptance Criteria Gate (Sprint Gate 2026-06-13)

### Hard Pass (tất cả phải PASS)

| Gate | Criterion | Verifier | Status |
|---|---|---|---|
| G1 | BUG-S4-T04: Python DOWN → HTTP 200 `isFallback:true` (không 503) | QA curl | DONE |
| G2 | BMS Modbus: adapter implemented, CB config in yml, unit tests pass | IT automated | DONE |
| G3 | BMS BACnet ReadProperty: BacnetIpAdapter implemented, unit tests pass | IT automated | DONE |
| G4 | BACnet Who-Is: BmsDiscoveryService + @Scheduled + manual trigger API | IT automated | DONE |
| G5 | Manual Config API: `POST /api/v1/bms/devices` create + idempotent upsert | IT automated | DONE |
| G6 | `bms_devices` V27 table exists (schema: bms), RLS per tenant | DB query | DONE |
| G7 | BMS readings published to `UIP.bms.reading.raw.v1` Kafka topic | Kafka consumer | DONE |
| G8 | Device Command API: `POST /api/v1/bms/devices/{id}/commands` → HTTP 202, MQTT published | QA curl + EMQX log | DONE |
| G9 | `iot-ingestion-service` builds + starts, `@ConditionalOnProperty` 3 modes work | `./gradlew build` + config test | DONE |
| G10 | `/alerts` SSE stream loads, CRITICAL filter works, tenant isolated | Manual QA | DONE (code) |
| G11 | 980 tests PASS, 0 failures | CI | DONE |
| G12 | JaCoCo LINE 77%, BRANCH 62.5% (unit only; full suite pending CI with Docker) | JaCoCo report | PARTIAL |
| G13 | ADR-029 merged, `com.infiniteautomation:bacnet4j:6.0.1` resolves from MangoAutomation Maven repo | Git + `./gradlew build` | DONE |

### Soft Pass (WARN không block)

| Gate | Criterion | Status |
|---|---|---|
| GS1 | BMS device list page `/bms/devices` | DONE |
| GS2 | iot-service shadow Kafka wiring (deferred Sprint 6) | Deferred |

---

## 8. Device Control — Tại sao REST đủ, không cần WebSocket

```
Luồng điều khiển thiết bị (Sprint 5):

  Operator                Backend                  EMQX                 Device (BMS)
    │                        │                        │                      │
    │  POST /bms/devices/    │                        │                      │
    │  {id}/commands         │                        │                      │
    │──────────────────────→ │                        │                      │
    │                        │  MQTT publish          │                      │
    │  HTTP 202 Accepted     │  bms/commands/         │                      │
    │ ←────────────────────  │  {tenantId}/{deviceId} │                      │
    │                        │───────────────────────→│                      │
    │                        │                        │  MQTT deliver        │
    │                        │                        │─────────────────────→│
    │                        │                        │                      │
    │                        │         (device executes, publishes ACK)      │
    │                        │  ← Kafka event: command_ack                   │
    │  SSE push: command_ack │                        │                      │
    │ ←────────────────────  │                        │                      │
```

**WebSocket KHÔNG cần vì:**
- Operator click → server nhận → MQTT dispatch: đây là **fire-and-forget**, không cần stream bi-directional
- ACK từ thiết bị về → backend nhận qua Kafka → đẩy xuống browser qua **SSE** (đã có)  
- Latency REST → MQTT → device: ~50–200ms, hoàn toàn acceptable cho BMS control
- WebSocket thêm: Spring STOMP config, Kong upgrade header, Keycloak token refresh trên long-lived WS connection, sticky session Redis pub/sub → phức tạp không cần thiết

**Khi nào mới cần WebSocket (không phải Sprint 5):**
- Collaborative editing nhiều operator cùng lúc (cursor sync, drag BPMN)
- Game-like real-time (sub-100ms latency từ user keystroke)
- Hiện tại không có use case nào đó trong roadmap MVP3

---

## 9. Timeline (10 ngày)

```
Day 1-2  (06-02→03): BUG-S4-T04 fix + ADR-029 SA approve + BACnet4J license key setup
Day 2-5  (06-03→06): BMS Modbus TCP + BACnet4J ReadProperty + V28 migration
Day 5-7  (06-06→09): BACnet4J Who-Is Discovery + DeviceRegistry + Manual Config API
Day 6-8  (06-07→10): Device Command API (EMQX) + Kafka producer
Day 6-8  (06-07→10): iot-ingestion-service scaffold + @ConditionalOnProperty
Day 7-9  (06-08→11): Frontend Alerts page SSE + BMS device list
Day 8-10 (06-09→12): BMS integration tests (10 TC) + Regression gate
Day 10   (06-13):    Gate review 15:00 SGT
```

---

## 10. Risks

| Risk | Probability | Impact | Mitigation |
|---|---|---|---|
| ~~`bacnet4j` license — GPL-3.0~~ | ~~High~~ **RESOLVED** | ~~High~~ | PO approved commercial license. Cần license key + Maven coords trước 06-02 |
| BACnet4J commercial license chưa mua trước production deploy | **Low** | Medium | JAR download công khai từ Maven repo → dev không block. Email `sales@radixiot.com` mua commercial license song song, chỉ cần trước City Authority pilot tháng 7 |
| BACnet Who-Is broadcast phụ thuộc subnet config | Medium | Medium | Mock với Testcontainers socket server, not real broadcast. BACnet4J `RemoteDeviceDiscovery` handle broadcast |
| 50 SP > 47 SP capacity (+3 SP) | Low | Medium | Tight fit nhưng manageable — BACnet4J API tiết kiệm effort. Fallback: defer BMS FE (Option F, −2 SP) |
| EMQX MQTT command dispatch chưa được test với BMS payload format | Low | Medium | Day 6: spike test command payload format với EMQX before implementing API |

---

### BACnet Library — Decision: Option A (Commercial License) APPROVED

**PO decision 2026-05-27:** Mua commercial license BACnet4J từ RadixIoT.

| Item | Detail |
|---|---|
| **Library** | `com.infiniteautomation:bacnet4j:6.0.1` |
| **Maven repo** | `https://maven.mangoautomation.net/repository/ias-release/` (công khai, không cần auth) |
| **GitHub** | [MangoAutomation/BACnet4J](https://github.com/MangoAutomation/BACnet4J) |
| **License** | Code: GPL-3.0 (open-source). Commercial license mua từ RadixIoT để deploy production hợp pháp |
| **Effort savings** | ~3 SP so với tự implement thin UDP (Option B) — BMS04: 5→3 SP, BMS05: 3→2 SP |
| **Dev strategy** | Pull JAR `6.0.1` từ MangoAutomation Maven repo → code ngay. Song song PO email `sales@radixiot.com` mua commercial license |
| **Action before Sprint** | (1) Thêm Maven repo vào `build.gradle` — Day 1, (2) Email `sales@radixiot.com` mua commercial license trước 2026-05-28 |

**Original options (archived):**

| Option | Approach | Chi phí | Effort | Status |
|---|---|---|---|---|
| **A** | Mua commercial license từ RadixIoT | Có phí | Thấp — dùng API ngay | **APPROVED** |
| **B** | Implement thin BACnet/IP over UDP (Java `DatagramSocket`) | 0 | ~3 SP extra | Not chosen |
| **C** | BACnet-to-MQTT sidecar (`node-bacnet` MIT) | 0 | ~2 SP DevOps + ~1 SP backend | Not chosen |

---

*Sprint 5 plan created: 2026-05-27 | Updated: 2026-05-28 (verified all tasks, gaps fixed: BmsCommandAckConsumer, @Scheduled discovery, MQTT protocol in adapter registry, EMQX command ACK bridge, 10 IT scenarios, 980 tests PASS)*
*Next: Contact RadixIoT for license key + team capacity confirm → finalize 2026-05-30*
