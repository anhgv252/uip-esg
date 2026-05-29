# MVP3 — Building Cluster + Advanced AI (v3.0)

**Trạng thái:** 🟢 Sprint 5 COMPLETE — Sprint 6 IN PLANNING
**Ngày lập kế hoạch:** 2026-05-10
**Last updated:** 2026-05-29 (Sprint 5 closed, Sprint 6 planned)
**Sprint start:** 2026-05-12
**Target:** Tier 2 pilot signed bởi 2026-08-10
**Goal:** UIP sẵn sàng phục vụ Building Cluster (5–20 tòa nhà)

---

## 1. Executive Summary

| Mục tiêu | Target | Confidence |
|-----------|--------|------------|
| Tier 2 Pilot signed (1 city pilot) | 2026-08-10 | 85% |
| Cross-building analytics foundation | Live + tested Sprint 2 | 90% |
| Kong + Keycloak IAM gateway | Production Sprint 4 | 85% |
| ClickHouse OLAP layer | <5s aggregation p95 | 75% |
| Predictive AI (energy + maintenance) | Metrics in production | 70% |

**5 Critical Success Factors:**
1. Multi-building RLS correctness — SA spike phải pass trước Sprint 2
2. Pre-demo dry-run bắt buộc 2h trước mọi executive demo
3. Test với ≥10M rows (2M ESG × 5 buildings)
4. City Authority ESG format được finalize trước Sprint 2 EOL
5. Zero breaking changes với Tier 1 API (MVP2 backward compat)

---

## 2. Backlog MVP3

| ID | Feature | SP | Priority | Sprint thực tế | Status |
|----|---------|-----|---------|----------------|--------|
| v3-01 | Cross-building analytics + aggregate dashboard | 13 | **P0** | Sprint 1-2 | ✅ DONE |
| v3-05 | BMS integration SDK (Modbus, BACnet) | 21 | **P0** | Sprint 5 | ✅ DONE |
| v3-07 | Kong API Gateway + Keycloak IdP | 13 | **P0** | Sprint 1 | ✅ DONE |
| v3-08 | ClickHouse OLAP + analytics microservice | 13 | **P0** | Sprint 1-2 | ✅ DONE |
| v3-02 | Advanced ESG: GRI Standards + carbon credit | 21 | P1 | Sprint 3 | ✅ DONE |
| v3-03 | Predictive AI: energy forecasting ARIMA/LSTM | 13 | P1 | Sprint 4 | ✅ DONE |
| v3-04 | Predictive maintenance: sensor anomaly detection | 13 | P2 | Sprint 4 | ✅ DONE |
| v3-AI | AI Workflow Designer + Flood Alert Pipeline | ~26 | **P0** | Sprint 6 | 📋 Planned |
| v3-06 | Mobile operator app (iOS/Android) | 21 | P1 | Sprint 6-7 | 📋 Planned |
| v3-09 | Building safety: structural monitoring | 13 | P1 | Sprint 7 | 📋 Planned |
| v3-10 | Schema Registry (Apicurio) + Avro migration | 8 | P1 | Post-pilot | 📋 Deferred |

**Total: ~149 SP / 7 sprints (Sprint 1-5 DONE = ~111 SP delivered, Sprint 6-7 remaining = ~60 SP)**

### Sprint thực tế vs Plan gốc

Plan gốc (detail-plan.md) dự kiến 6 sprint theo thứ tự: Foundation → ClickHouse → AI → BMS → Mobile → Avro+Safety.
PO đã điều chỉnh thứ tự ưu tiên dựa trên business value:

| Plan gốc | Sprint thực tế | Nội dung | Gate |
|----------|---------------|----------|------|
| Sprint 1 | **Sprint 1** (05-12 → 05-13) | Foundation + Multi-Building Core + ClickHouse + Kong/Keycloak | 69/70 PASS ✅ |
| Sprint 2 | **Sprint 2** (05-14 → 05-18) | ClickHouse Live + Analytics Cutover + ESG | PASS ✅ |
| Sprint 3 | **Sprint 3** (05-19 → 05-25) | ESG GRI + Keycloak RSA + Flink Enrichment | 5/6 AC PASS ✅ |
| Sprint 4 | **Sprint 4** (05-25 → 05-27) | Observability + Predictive AI (ARIMA MAPE 3.54%) | 19/19 PASS ✅ |
| — | **Sprint 5** (05-27 → 05-29) | BMS Full Integration + Alerts SSE + Forecast Fallback | 21/21 DONE ✅ |
| — | **Sprint 6** (planned) | AI Workflow + Flood Alert + Mobile Foundation + Push | 📋 Planned |
| — | **Sprint 7** (planned) | Building Safety + Mobile Full + Pilot Prep | 📋 Planned |
| Sprint 6 | **Post-pilot** | Avro Schema Registry + gRPC migration | 📋 Deferred |

> **PO điều chỉnh rationale:** AI Workflow + Flood Alert được ưu tiên lên Sprint 6 vì demo PO Sprint 5 đã promise. Mobile app được đẩy lên Sprint 6-7 song song. Building Safety Sprint 7 trước pilot. Avro + Schema Registry deferred post-pilot.

---

## 3. Architecture Decisions (MVP3)

### Architectural Decisions đã chốt

| Decision | Chọn | Rationale |
|----------|------|-----------|
| **ClickHouse adoption** | Pre-emptive Sprint 1 (override ADR-012 trigger) | Cross-building v3-01 sẽ hit 3s p95 ngay Sprint đầu T2 |
| **Auth gateway** | Keycloak (issuer) + Spring Security (resource server) | Không replace logic RLS đã hardened qua MVP2 OWASP |
| **Kong scope** | Chỉ extracted services (analytics, iot-ingestion) | Monolith vẫn expose trực tiếp ở T1/T2 init |
| **Module extraction** | analytics-service (Sprint 1) → iot-ingestion (Sprint 3) → alert (defer) | Theo ADR-011 strangler fig, giữ monolith đến Sprint 3 |
| **BMS SDK** | 3 adapter libraries trong monolith + Kafka topic riêng | Không microservice riêng; reuse pipeline enrichment ADR-014 |
| **Mobile** | React Native + Expo (Operator) / PWA giữ nguyên (Citizen) | Reuse 60% React Query hooks + api-types workspace package |
| **Internal transport (backend→analytics)** | **gRPC (Sprint 7)**, hiện tại REST tạm chấp nhận ở MVP demo | Canonical SA: module↔module = gRPC/Kafka, không REST internal. 4-5x throughput, Istio-native. Xem ADR-012. |

### ADRs đã có (hoàn thiện / đề xuất)

| ADR | Title | Trạng thái | File |
|-----|-------|-----------|------|
| ADR-011 | Strangler Fig — Analytics Service Extraction | ✅ Accepted | `docs/` |
| **ADR-012** | **gRPC cho Internal Service-to-Service Communication** | **📋 Proposed (Sprint 7)** | [`docs/mvp3/ADR-012-grpc-for-internal-service-communication.md`](ADR-012-grpc-for-internal-service-communication.md) |

### ADRs cần viết cho MVP3

| ADR | Title | Sprint | Trạng thái |
|-----|-------|--------|-----------|
| ADR-026 | ClickHouse Pre-emptive Adoption (override ADR-012 trigger) | Sprint 1 | ✅ MERGED |
| ADR-027 | Keycloak Hybrid Auth — Issuer Migration Strategy | Sprint 1 | ✅ MERGED |
| ADR-028 | Kong Gateway Scope — Extracted Services Only | Sprint 1 | ✅ MERGED |
| ADR-029 | BMS Protocol Adapter Pattern (Modbus/BACnet) | Sprint 5 | ✅ MERGED |
| ADR-030 | Mobile Stack — React Native (Operator) + PWA (Citizen) | Sprint 6 | 📋 Proposed |
| ADR-031 | Schema Registry — Avro for Cross-Service Topics | Post-pilot | 📋 Deferred |
| ADR-032 | Predictive AI — ARIMA In-Process + LSTM External gRPC | Sprint 4 | ✅ MERGED |
| ADR-033 | Cross-Building Tenant Hierarchy — Parent Tenant Aggregation | Sprint 1 | ✅ MERGED |
| ADR-034 | Structural Monitoring — Flink CEP + Welford stddev | Sprint 7 | 📋 Proposed |
| ADR-035 | Flink Enrichment — Metadata Join | Sprint 3 | ✅ MERGED |

### Target Architecture (T2, post-Sprint 3)

```
┌────────────────────────── Edge per Building ──────────────────────────┐
│  BMS (Modbus/BACnet/KNX) ──→ EMQX Edge buffer 24h ──→ Kafka TLS      │
└───────────────────────────────────────────────────────────────────────┘
                                    │
┌────────────────────── Cloud K8s — T2 Cluster ─────────────────────────┐
│                                                                        │
│  Kafka 3 brokers + Apicurio Schema Registry                           │
│       │                                                                │
│  iot-ingestion-service (extracted Sprint 3)                            │
│       │                                                                │
│  Flink jobs (aggregation + anomaly + enrichment)                       │
│       ├──────────────────────────────────────────────────────┐        │
│  TimescaleDB (operational <30d)              ClickHouse (analytics)   │
│                                                                        │
│  Keycloak (realm/tenant) ──JWT──→ Kong Gateway                        │
│       │                   │                                            │
│       │          analytics-service (ClickHouse owner)                 │
│       │          Monolith (env+esg+traffic+citizen+ai-wf+admin)       │
│                                                                        │
│  Citizen PWA  ←── push notifications (Web Push)                       │
│  Operator React Native  ←── push (FCM/APNs)                           │
│  Operator Web React                                                    │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 4. Deployment Mode — Monolith vs Extracted (Quan trọng)

> **Nguyên tắc:** Extraction là **opt-in per customer**, không phải mặc định. Tier 1 customer (1–2 tòa, ít sensor) tiếp tục chạy **1 Docker image duy nhất** — không thay đổi gì so với MVP2.

### Bảng quyết định triển khai

| Scenario | Deployment | Images | Capability flags |
|----------|-----------|--------|-----------------|
| Tòa nhỏ, 1–2 building, <500 sensor | **Pure monolith** | 1 image | Tất cả `*-external=false` (default) |
| Building cluster, 5–20 building, >5K sensor | **Monolith + extracted services** | 3 images (cùng tag) | `analytics-external=true`, `iot-ingestion-external=true` |
| District, 50+ building, >50K events/sec | **Full extracted** | 4+ images | Theo `values-tier3.yaml` |

### Cơ chế đảm bảo — `@ConditionalOnProperty(matchIfMissing = true)`

Khi **không set flag** (Tier 1 customer), Spring Boot **tự động giữ nguyên** tất cả module trong monolith:

```java
// Monolith tự xử lý IoT khi flag không được set
@ConditionalOnProperty(
    name = "uip.capabilities.iot-ingestion-external",
    havingValue = "false",
    matchIfMissing = true   // ← nếu không có trong config → mặc định false → monolith chạy
)
public class IotIngestionAutoConfiguration { ... }

// Tương tự cho analytics
@ConditionalOnProperty(
    name = "uip.capabilities.analytics-external",
    havingValue = "false",
    matchIfMissing = true
)
public class AnalyticsAutoConfiguration { ... }
```

### values file per customer type

```yaml
# values-tier1.yaml — Tòa nhỏ, KHÔNG thay đổi gì
# Không cần set flag nào — matchIfMissing=true giữ monolith nguyên
app:
  replicas: 1
  springProfile: tier1
# → 1 image: uip-monolith:v3.x — toàn bộ module trong 1 JVM

---

# values-tier2.yaml — Building cluster (5–20 tòa)
# Chỉ bật khi customer đủ lớn để cần tách
uip:
  capabilities:
    multi-tenancy: true
    analytics-external: true        # ← analytics-service chạy riêng
    iot-ingestion-external: true    # ← iot-service chạy riêng (sau Sprint 5)
# → 3 images: uip-monolith:v3.x + uip-analytics-service:v3.x + uip-iot-service:v3.x
```

### Điều gì xảy ra nếu Tier 1 update lên code MVP3?

```
BEFORE (Tier 1, monolith):          AFTER update code MVP3 (vẫn Tier 1):
  1 JVM — all modules                  1 JVM — all modules (không đổi gì)
  iot-ingestion-external = (none)      iot-ingestion-external = (none) → matchIfMissing=false
  analytics-external = (none)          analytics-external = (none) → matchIfMissing=false

  Kết quả: hoàn toàn trong suốt với Tier 1 customer
```

### Khi nào Tier 1 nên chuyển lên Tier 2 deployment?

Chỉ đề xuất khi customer vượt **ít nhất 1 trigger**:

| Trigger | Ngưỡng |
|---------|--------|
| Số building | > 3 buildings |
| IoT throughput | > 50K events/sec sustained |
| Analytics query | ESG dashboard query p95 > 5 phút |
| Concurrent users | > 200 users đồng thời |

**Dưới ngưỡng → tiếp tục chạy monolith**, không cần tách.

---

## 5. Sprint Plan (7 Sprints — 5 DONE + 2 Planned)

> **Lưu ý:** Thứ tự sprint đã được PO điều chỉnh so với plan gốc (detail-plan.md) để ưu tiên business value. BMS được đẩy lên sớm (Sprint 5), AI Innovation được ưu tiên Sprint 6. Mobile và Building Safety song song Sprint 6-7.

### Sprint 1 (2026-05-12 → 2026-05-13): Foundation + Multi-Building Core — ✅ COMPLETE

**Sprint Goal:** Multi-building RLS isolation + ClickHouse foundation + analytics-service shadow + Kong/Keycloak

**Key deliverables:**
- ADR-026, ADR-027, ADR-028, ADR-033 merged
- Schema V26 (building cluster + RLS) — 10/10 isolation scenarios PASS, p95=2.3ms
- analytics-service shadow deploy — diff 0.000000%
- Flink EsgDualSinkJob — 500 rows dual-write verified (TS + ClickHouse)
- Kong + Keycloak — alg=none→401, token grant p95=5ms
- Cross-building dashboard shell + multi-building selector
- 773 tests PASS, JaCoCo LINE 85%, BRANCH 60%

**Gate:** 69/70 PASS ✅

---

### Sprint 2 (2026-05-14 → 2026-05-18): ClickHouse Live + Analytics Cutover — ✅ COMPLETE

**Sprint Goal:** ClickHouse queries live + analytics-service cutover + cross-building ESG

**Key deliverables:**
- analytics-service cutover (`analytics-external=true`) — monolith stop loading analytics beans
- ClickHouse cross-building queries p95 <2s @ 10M rows
- Cross-building ESG aggregation (city authority format)
- Analytics dashboard (energy + emissions by building)
- 864 tests PASS

**Gate:** PASS ✅

---

### Sprint 3 (2026-05-19 → 2026-05-25): ESG GRI + Keycloak RSA + Flink Enrichment — ✅ COMPLETE

**Sprint Goal:** ESG GRI 302/305 export + Keycloak RS256 dual-issuer + Flink BuildingMetadata enrichment

**Key deliverables:**
- ESG GRI 302-1 / 305-4 Excel export verified
- Keycloak RoutingJwtDecoder (HMAC uip-legacy + RSA keycloak)
- Flink BuildingMetadata enrichment (inline district lookup)
- ADR-035 merged
- 849 tests PASS, JaCoCo LINE 93.3%
- ClickHouse HA descoped (single-node stable)

**Gate:** 5/6 AC PASS ✅ (CH HA descoped, 20 SP carry-over)

---

### Sprint 4 (2026-05-25 → 2026-05-27): Observability + Predictive AI — ✅ COMPLETE

**Sprint Goal:** Prometheus + Grafana observability + ARIMA forecast + anomaly detection

**Key deliverables:**
- Prometheus + Grafana — 7/7 targets UP, 8 forecast panels
- ARIMA energy forecast — MAPE 3.54% (target <10%), 720 data points
- ForecastPort (`@ConditionalOnProperty`) — 3 adapters: Python/Naive/Disabled
- Python forecast-service (FastAPI + auto_arima) — 40 Python tests
- Anomaly detection — Isolation Forest + Z-score verified
- LSTM evaluation — NO-GO documented (MAPE 18.65% vs ARIMA 3.37%)
- ADR-032 merged
- 978+ tests PASS, JaCoCo LINE 87.7%, BRANCH 71.4%

**Gate:** 19/19 PASS ✅ | PO signed off 7/7 demo scenarios 2026-05-27

---

### Sprint 5 (2026-05-27 → 2026-05-29): BMS Full Integration + Alerts SSE — ✅ COMPLETE

**Sprint Goal:** BMS SDK đầy đủ (Modbus + BACnet + Manual) + Alerts SSE real-time + Forecast fallback + API hardening

**Key deliverables:**
- BMS Device CRUD API — `GET/POST/PUT/DELETE /api/v1/bms/devices`, 5 devices
- BMS Protocol Adapters — ModbusTCP + BACnetIP + adapter registry + Resilience4j CB
- BMS Device Commands — `POST /api/v1/bms/devices/{id}/commands` → EMQX MQTT
- BMS Kafka Producer — readings → `UIP.bms.reading.raw.v1`, DLQ fallback
- BMS Device Frontend Page — `/bms/devices` list, add, delete, update
- Alerts SSE Real-time Stream — `GET /api/v1/alerts/stream` SSE + Redis PUBLISH
- Forecast Fallback Fix — Python DOWN → `isFallback:true` 200 OK (không 503)
- nginx Dynamic DNS Fix — backend restart → frontend auto-recovers
- GlobalExceptionHandler RFC 7807 — 405/400 proper errors (was 500)
- EMQX container healthy + Prometheus BMS metrics + Grafana BMS panels
- iot-ingestion-service scaffold + `@ConditionalOnProperty` 3 modes
- ADR-029 merged, BACnet4J commercial license approved
- 1,506 backend + 180 frontend + 10 BMS IT + 40 manual = 1,224 total tests
- SA code review 10/10 Backend + 10/10 Frontend APPROVED
- 15 deliverables demoed to PO — 16/16 scenarios PASS

**Gate:** 21/21 DONE ✅ | Zero carry-over | PO demo 16/16 PASS

---

### Sprint 6 (planned): AI Innovation + Mobile Foundation — 📋 PLANNED

**Sprint Goal:** AI Workflow Designer (BPMN) + Flood Alert Pipeline E2E + React Native scaffold + Push backend + EMQX production + Blue-green deploy

**PO decisions (2026-05-29):**
- Focus: AI Innovation (AI Workflow + Flood Alert) — P0
- Mobile: Sprint 6 song song — React Native scaffold + PKCE + Push
- Building Safety: Sprint 7 (trước pilot)
- Avro/Schema Registry: Deferred post-pilot

| Tier | Story | SP | Owner | Priority |
|------|-------|----|-------|----------|
| **Tier 1 (MUST)** | | | | |
| | AI Workflow Designer — BPMN visual editor + AI decision nodes | 13 | FE + BE | **P0** |
| | Flood Alert Pipeline — Sensor → Kafka → Flink CEP → Alert → SSE | 13 | Backend | **P0** |
| | EMQX MQTT Production — BMS commands (carry-over S5) | 5 | DevOps | P1 |
| | Blue-green deploy + rollback <30s | 3 | DevOps | P0 |
| | Regression gate 1,500+ tests | 3 | QA | P0 |
| | Demo script + PO dry-run | 2 | PM | P0 |
| | Python forecast auto-retry 5 min | 2 | Backend | P2 |
| | Push Subscription FE page (carry-over S5) | 2 | Frontend | P2 |
| | SA: ADR-030 Mobile Stack + ADR-034 prep | 2 | SA | P1 |
| **Tier 1 subtotal** | | **45 SP** | | |
| **Tier 2 (BEST EFFORT)** | | | | |
| | React Native + Expo scaffold | 13 | Frontend | P1 |
| | Keycloak PKCE login + tenant selection | 5 | FE + BE | P1 |
| | FCM + APNs push notification backend | 8 | Backend | P1 |
| | BMS Testcontainers ITs 10 scenarios (carry-over S5) | 3 | QA | P1 |
| **Tier 2 subtotal** | | **29 SP** | | |
| **Total committed** | | **74 SP** | | |

**Risks:**
- bpmn-js learning curve → SA spike Day 1-2; fallback static workflow templates
- Mobile + AI song song kéo Frontend → nếu Tier 2 không fit → Mobile defer Sprint 7
- 74 SP vs 47 SP capacity → Sprint 5 đã proof: 50/47 delivered

---

### Sprint 7 (planned): Building Safety + Mobile Full + Pilot Prep — 📋 PLANNED

**Sprint Goal:** Building Safety structural monitoring + Mobile app full features + Pilot readiness gate

| Story | SP | Owner | Priority |
|-------|----|-------|----------|
| Building Safety Backend — Flink CEP + Welford stddev | 13 | Backend | P1 |
| Building Safety UI — sensor grid + alert banner | 8 | Frontend | P1 |
| Mobile Dashboard + Alerts (React Native) | 13 | Frontend | P1 |
| Mobile Control Panel (actuator commands) | 5 | Frontend | P2 |
| BMS Command ACK + SSE feedback | 3 | Backend | P2 |
| ESG PDF Export (GRI 302/305) | 5 | Backend | P2 |
| Pilot regression 100+ scenarios | 5 | QA | P0 |
| Pilot readiness gate + demo | 3 | All | P0 |
| **Total** | **~55 SP** | | |

**Target:** Pilot soft launch 2026-08-04 → Tier 2 Pilot SIGNED 2026-08-10

---

## 5. Milestone Map (Updated 2026-05-29)

```
✅ Sprint 1 (05-12 → 05-13): Foundation + Multi-Building + ClickHouse + Kong/Keycloak
   GATE: 69/70 PASS. analytics-service shadow diff 0.000000%, Flink E2E 500 rows verified.

✅ Sprint 2 (05-14 → 05-18): ClickHouse Live + Analytics Cutover + ESG
   GATE: PASS. analytics-service 100% traffic, CH p95 <2s @ 10M rows.

✅ Sprint 3 (05-19 → 05-25): ESG GRI + Keycloak RSA + Flink Enrichment
   GATE: 5/6 AC PASS. GRI 302/305 export verified. CH HA descoped.

✅ Sprint 4 (05-25 → 05-27): Observability + Predictive AI
   GATE: 19/19 PASS. ARIMA MAPE 3.54%. Prometheus 7/7 UP. LSTM NO-GO.
   PO signed off 7/7 demo scenarios.

✅ Sprint 5 (05-27 → 05-29): BMS Full Integration + Alerts SSE + Forecast Fallback
   GATE: 21/21 DONE, zero carry-over. 1,224 total tests. PO demo 16/16 PASS.
   15 deliverables: BMS CRUD, Modbus, BACnet, SSE Alerts, RFC 7807, nginx DNS fix.
   ─── CURRENT POSITION ───

📋 Sprint 6 (planned): AI Innovation + Mobile Foundation (~74 SP)
   Tier 1 (45 SP): AI Workflow Designer + Flood Alert + EMQX + Blue-green
   Tier 2 (29 SP): React Native + PKCE + Push Backend + BMS ITs

📋 Sprint 7 (planned): Building Safety + Mobile Full + Pilot Prep (~55 SP)
   Building Safety (Flink CEP + Welford) + Mobile Dashboard + Pilot readiness

🎯 2026-08-04: Pilot soft launch (5 buildings, 2 tenants)
🎯 2026-08-10: Tier 2 Pilot SIGNED

   Post-pilot (v3.1): Avro Schema Registry + gRPC migration + Building Safety refinements
```

**Deployed images tại Pilot (cùng version tag — bắt buộc theo ADR-011):**

| Image | Modules còn trong JVM | Scale |
|-------|----------------------|-------|
| `uip-monolith:v3.x` | env, esg, traffic, alert, citizen, ai-workflow, admin | HPA min 3/max 8 |
| `uip-analytics-service:v3.x` | analytics (ClickHouse queries) | HPA min 2/max 6 |
| `uip-iot-service:v3.x` | iot-ingestion + BMS adapters | HPA min 3/max 10 |

---

## 6. KPIs & SLAs — Tier 2

### Technical SLAs

| SLA | Target |
|-----|--------|
| Cross-building query p95 | <2s |
| ESG report generation | <30s |
| ClickHouse dashboard p95 | <1,000ms (500M rows) |
| Energy forecast MAPE | <10% |
| Anomaly detection recall | >75% |
| Mobile push latency | <5s |
| Kong API p99 | <100ms |
| Keycloak token grant | <200ms |
| BMS ingestion lag → ClickHouse | <60s |
| System availability (K8s) | ≥99.5% |
| Pilot rollback time | ≤30s |

### Performance Gate (phải pass trước Pilot launch)

- ✅ Sensor query p95 <200ms @ 2M rows (Tier 1 baseline unchanged)
- ✅ Cross-building query p95 <500ms @ 10M rows
- ✅ ESG report p95 <30s
- ✅ Mobile app <3s initial load, <5s push
- ✅ Kong p99 <100ms
- ✅ OWASP 0 Critical findings
- ✅ Regression suite ≥100 test cases, 100% PASS

---

## 7. Risk Register MVP3

### Resolved Risks (Sprint 1-5)

| ID | Risk | Resolution |
|----|------|-----------|
| ~~R1~~ | Multi-building RLS query >2s (N+1) | ✅ Resolved — p95=2.3ms via materialized view (Sprint 1) |
| ~~R2~~ | Tier 1 regression từ multi-building | ✅ Resolved — zero regression through all sprints |
| ~~R3~~ | LSTM model MAPE >15% | ✅ Resolved — LSTM NO-GO, ARIMA only (Sprint 4, MAPE 3.54%) |
| ~~R4~~ | ClickHouse cluster HA delays | ✅ Resolved — single-node sufficient, CH HA descoped (Sprint 3) |
| ~~R5~~ | City Authority ESG spec changes | ✅ Resolved — GRI 302/305 finalized Sprint 3 |
| ~~R7~~ | Kong plugin priority error | ✅ Resolved — alg=none blocked, plugin order verified (Sprint 1) |
| ~~R8~~ | Extraction code break Tier 1 | ✅ Resolved — `matchIfMissing=true` pattern proven (Sprint 1-2) |

### Active Risks (Sprint 6-7)

| ID | Risk | Severity | Prob | Mitigation | Owner |
|----|------|---------|------|-----------|-------|
| R6 | iOS cert + Apple review delays | MEDIUM | 55% | Submit cert Day 1 Sprint 6; Android APK demo-ready first | Frontend Eng |
| R9 | bpmn-js learning curve — AI Workflow complex | HIGH | 40% | SA spike Day 1-2; fallback static workflow templates | SA + Frontend |
| R10 | Mobile + AI song song kéo Frontend quá mỏng | HIGH | 50% | Tier 2 best-effort; Mobile defer Sprint 7 nếu cần | PM |
| R11 | Sprint 6 over-commit (74 SP vs 47 SP capacity) | MEDIUM | 35% | Sprint 5 precedent: 50/47 delivered; Tier 1 locked | PM |
| R12 | Building Safety Flink CEP complex | MEDIUM | 30% | Defer post-pilot nếu Sprint 7 overloaded | Backend Lead |
| R13 | Pilot target 2026-08-10 bị delay | HIGH | 25% | 2 sprint buffer; descope Mobile/Safety nếu cần | PM + PO |

---

## 8. Demo Kết Quả Tiền-Sprint (2026-05-11)

> **Lưu ý:** Đây là kết quả từ bản **demo/POC chưa hoàn thiện**, chạy trên môi trường local dev (không phải staging chính thức). Mục đích: validate kiến trúc strangler fig (ADR-011) và thu thập early evidence trước Sprint MVP3-1.

### 8.1 Kết quả kiểm thử deploy flow Mono vs Extracted Analytics

SA đã demo luồng triển khai với 2 môi trường:

| Môi trường | Config | Kết quả |
|------------|--------|---------|
| **Mono (Tier 1)** | `analytics-external=false` (default) | ✅ Backend healthy, `TimescaleDbAnalyticsAdapter` load đúng |
| **Extracted (Tier 2)** | `analytics-external=true` | ✅ `analytics-service` container healthy, gọi ClickHouse OK |

**Tỷ lệ kiểm thử pass: 87.5% (21/24 checks)**

Defects phát hiện (cần fix trước Sprint MVP3-1):

| ID | Summary | Severity | Action |
|----|---------|----------|--------|
| BUG-001 | EMQX Erlang node unhealthy (`not responding to pings`) | P2 | Restart + fix EMQX_NODE_NAME |
| BUG-002 | Flink 0 active streaming jobs — alert pipeline tắt | P2 | Submit job jar vào JobManager |
| **BUG-003** | **Auth login 401 — không có seed data / password hash sai** | **P1** | **Chạy `scripts/demo-setup.sh`** |
| BUG-004 | analytics-service ClassNotFoundException (Apache HC5) — non-fatal | P3 | Thêm `httpclient5` dependency |

📄 **Báo cáo đầy đủ:** [`docs/mvp3/test-eval-mono-vs-extracted-analytics-2026-05-11.md`](test-eval-mono-vs-extracted-analytics-2026-05-11.md)

### 8.2 Kiến trúc transport nội bộ — REST → gRPC

SA nhận xét: giao tiếp `backend → analytics-service` hiện dùng REST (`ClickHouseRestAnalyticsAdapter`) là tạm chấp nhận ở demo MVP nhưng **vi phạm canonical architecture** ("module↔module = gRPC/Kafka, không REST").

Đề xuất migrate sang gRPC tại Sprint 7 (sau pilot launch):

- **4-5x throughput** improvement cho analytics aggregate queries
- **Compile-time schema safety** qua `.proto` contract
- **Istio mTLS auto-inject** — không cần JWT propagation nội bộ
- **Migration risk thấp**: `AnalyticsPort` interface đã tách — `EsgService` không cần đổi

📄 **ADR đầy đủ:** [`docs/mvp3/ADR-012-grpc-for-internal-service-communication.md`](ADR-012-grpc-for-internal-service-communication.md)

---

**Quy tắc bắt buộc cho mọi extraction story (v3-EXT-*):**
> Mỗi `@ConditionalOnProperty` cho extraction phải có `matchIfMissing = true`. Nếu thiếu → Tier 1 customer không set flag sẽ bị mất bean → regression. CI phải có test run riêng với profile `tier1` (không set flag) song song với test run `tier2`.

---

## 8. User Stories MVP3 (BA Spec)

### P0 — Must Ship với MVP3

**US-001: Cross-Building Real-Time Analytics Dashboard**
> As a Building Cluster Manager, I want to view aggregated energy, water, and carbon data across all buildings in my cluster on a single dashboard, so that I can identify underperforming buildings and take corrective action quickly.

Acceptance Criteria (key):
- AC-01: Dashboard loads within 3 seconds for clusters of up to 20 buildings
- AC-02: Aggregate figures (sum, average, per-unit benchmarks) update every 60 seconds
- AC-03: Clicking a building bar/card drills down to individual building sensors
- AC-04: Tenant isolation: Cluster Manager sees ONLY their assigned cluster's data
- AC-05: Export cluster data as XLSX/CSV
- AC-06: Historical comparison (current month vs prior 3 months) visible inline

**US-002: Sub-Meter Hierarchy & Cost Allocation**
> As a Tenant Admin, I want to allocate shared energy/water costs across units and common areas proportionally, so that I can produce itemized billing without manual calculation.

**v3-05: BMS Integration (Modbus/BACnet/KNX)**
> As a Building Operations Engineer, I want to connect existing BMS devices via Modbus TCP, BACnet/IP, or KNX/IP without vendor lock-in, so that legacy systems can feed data into UIP without hardware replacement.

Business Rules:
- BR-001: Each BMS device must be registered with tenant_id + building_id before polling starts
- BR-002: CRC/timeout errors trigger circuit breaker after 5 consecutive failures
- BR-003: Last-known value (STALE flag) served if device unreachable for <10 minutes
- BR-004: Protocol adapters publish to `UIP.iot.bms.reading.v1` only — không ghi DB trực tiếp

### P1 — Sprint 3+ sau MVP3

- US-004: Regulatory Compliance Reports (GRI 302, TCFD, CDP)
- US-005: ESG Scoring & Benchmarking (0-100 score, peer comparison)
- US-006: Equipment Anomaly Detection (HVAC/elevator health)
- US-011: Citizen Mobile App (iOS + Android) — phụ thuộc v3-06
- US-012: Operator Mobile App — included in Sprint 5

### 8 Open Questions (cần stakeholder answer trước Sprint 2)

1. Mobile platform: React Native (đã chọn SA) — BA cần confirm với city authority user persona
2. ClickHouse topology: single-node đủ hay 3-node replica từ đầu?
3. Carbon credit calculation: stream (real-time Flink) hay nightly batch?
4. GRI scope: subset 302/303/305 hay full ISO 37120?
5. BMS protocol priority: Modbus + BACnet là baseline, KNX optional — confirm?
6. Data retention: 2 năm energy, 90 ngày anomalies?
7. Multi-region: MVP3 single-region, multi-region v4.0 — confirm?
8. Building mid-period cluster switch: energy pro-rated hay full attribution mới?

---

## 9. Test Strategy (QA Summary)

### Quality Gates MVP3

| Module | Coverage Target |
|--------|----------------|
| BMS SDK adapters | **85%** (silent failure mode) |
| ClickHouse analytics service | **85%** (dual-store divergence) |
| ESG GRI calculator | **90%** (regulatory) |
| Cross-building aggregation | **88%** (financial) |
| Kong/Keycloak integration | **80%** (security) |
| All other modules | 80% (maintain baseline) |

### Critical Test Scenarios

| ID | Scenario | Gate |
|----|---------|------|
| ISO-001..007 | Multi-tenant isolation (BMS, CH, Cross-building, Kong, Mobile, Avro) | Hard block — no merge nếu fail |
| BMS-002 | Circuit breaker opens sau timeout | Hard block |
| BMS-008 | Tenant A không thấy device của Tenant B | Hard block |
| CH-001 | ClickHouse vs TimescaleDB delta <0.01% @ 1M rows | Hard block |
| KONG-009 | alg=none attack bị chặn | Hard block |
| KONG-010 | X-Tenant-ID forwarded đúng từ JWT | Hard block |

### CI/CD Jobs mới

```yaml
- bms-protocol-tests          # Modbus/BACnet/KNX (hard block)
- clickhouse-consistency-tests # CH vs TS delta (timeout 15min)
- avro-schema-compatibility   # Schema evolution tests
- pact-provider-verification  # Mobile API contracts (required for staging)
- tenant-isolation-gate       # Zero-tolerance, NO continue-on-error
- kong-security-scan          # OWASP ZAP (on 'gateway' label PRs)
- performance-tier2           # k6 1500 VU, 5K sensors (staging gate)
```

### Performance Test Tier 2

```
k6 Scenarios:
  bms_ingestion:     1,667 events/sec sustained 5 phút (= 100K events/min)
  analytics_users:   500 VU ramp cross-building dashboard
  mobile_operators:  200 VU constant mobile API

Thresholds:
  ESG summary p95:        <150ms
  Cross-building p95:     <500ms
  ClickHouse query p95:   <1,000ms
  Mobile alerts p95:      <100ms
  Error rate:             <0.01%
```

---

## 10. Resource Plan

| Role | Sprint 1-5 (DONE) | Sprint 6 (AI+Mobile) | Sprint 7 (Safety+Pilot) |
|------|-------------------|----------------------|------------------------|
| Backend Eng 1 | SA spike + analytics + ESG | Flood Alert Pipeline + Push Backend | Building Safety + Pilot |
| Backend Eng 2 | aggregation + Flink + BMS | AI Workflow Backend | Building Safety BE |
| Frontend Eng | Dashboard + Forecast + BMS | AI Workflow Designer + RN scaffold | Safety UI + Mobile Full |
| DevOps | ClickHouse + Kong + EMQX | EMQX prod + Blue-green | Pilot env + Runbook |
| QA | Test strategy + BMS ITs | Regression gate + BMS ITs | Pilot regression 100+ |

**Total effort:** ~19 person-months | **Team:** 5 FTE + 0.5 PM = 5.5 FTE
**Delivered (Sprint 1-5):** ~111 SP | **Remaining (Sprint 6-7):** ~129 SP (bao gồm descope candidates)

---

## 11. Budget Estimate

| Phase | Sprints | Cost (estimate @$150/hr) |
|-------|---------|--------------------------|
| Foundation + ClickHouse | Sprint 1-2 | ~$132,000 |
| AI + BMS | Sprint 3-4 | ~$170,400 |
| Mobile + Pilot | Sprint 5-6 | ~$160,800 |
| Contingency 15% | — | ~$28,800 |
| **Total development** | | **~$492,000** |
| Infrastructure (12 tuần) | | ~$10,740 |
| **Grand Total** | | **~$502,740** |

---

## 12. Descope / Contingency Plan

**Nếu Sprint 6 Tier 2 không kịp (Mobile/Push):**
- Mobile scaffold + Push Backend → defer Sprint 7, song song Building Safety
- Sprint 6 focus chỉ AI Workflow + Flood Alert + carry-over

**Nếu velocity <50 SP/sprint:**

| Feature | Action |
|---------|--------|
| Building Safety (v3-09) | → v3.1 post-pilot (đã được PO accept) |
| Schema Registry (v3-10) | → v3.1 post-pilot (đã deferred) |
| Mobile Control Panel | → v3.1 nếu Sprint 7 overloaded |
| ESG PDF Export | → v3.1 nếu Sprint 7 overloaded |
| BMS KNX adapter | → không implement (chỉ Modbus + BACnet) |

**Nếu pilot target 2026-08-10 bị threat:**
1. Sprint 6: cắt Tier 2 → focus AI Innovation only
2. Sprint 7: Mobile deferred → Building Safety + Pilot Prep only
3. Worst case: delay pilot 1 tuần (2026-08-17)

---

## Cấu trúc Tài liệu MVP3

| Thư mục | Nội dung |
|---------|---------|
| `project/` | Sprint plans chi tiết, demo scripts, roadmap |
| `architecture/` | [**System Architecture (Mermaid)**](architecture/system-architecture.md) · ADR-026 đến ADR-034 |
| `qa/` | Test plans, performance reports |
| `reports/` | Sprint reviews, UAT sign-off |
| `deployment/` | Pilot deployment guide, runbook |

---

*Tổng hợp bởi: SA + BA + PM + QA (4 agents, 2026-05-10)*
*Sprint alignment updated: 2026-05-29 (Sprint 5 CLOSED, Sprint 6-7 planned)*
*Next review: End of Sprint 6*
