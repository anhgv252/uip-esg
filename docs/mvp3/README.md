# MVP3 — Building Cluster + Advanced AI (v3.0)

**Trạng thái:** 🟢 Sprint 9 BUFFER COMPLETE — Sprint 10 PLANNED (API Contract + Pilot Readiness)
**Ngày lập kế hoạch:** 2026-05-10
**Last updated:** 2026-06-05 (Sprint 9 complete, Sprint 10 planned)
**Sprint start:** 2026-05-12
**Target:** Tier 2 pilot signed bởi 2026-08-10
**Goal:** UIP sẵn sàng phục vụ Building Cluster (5–20 tòa nhà)

---

## 1. Executive Summary

| Mục tiêu | Target | Confidence |
|-----------|--------|------------|
| Tier 2 Pilot signed (1 city pilot) | 2026-08-10 | 90% |
| Cross-building analytics foundation | ✅ Live + tested Sprint 2 | 100% |
| Kong + Keycloak IAM gateway | ✅ Production Sprint 1 | 100% |
| ClickHouse OLAP layer | ✅ <5s aggregation p95 | 100% |
| Predictive AI (energy + maintenance) | ✅ ARIMA MAPE 3.54% in production | 100% |
| Infrastructure HA (CH 2-node + Kafka 3-broker) | ✅ Live Sprint 8-9 | 100% |
| API Contract 100% documented | Sprint 10 | 80% |

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
| v3-AI | AI Workflow Designer + Flood Alert Pipeline | ~26 | **P0** | Sprint 6 | ✅ DONE |
| v3-06 | Mobile operator app (iOS/Android) | 21 | P1 | Sprint 6-8 | ✅ DONE |
| v3-09 | Building safety: structural monitoring | 13 | P1 | Sprint 7 | ✅ DONE |
| — | ClickHouse 2-node HA + Kafka 3-broker KRaft | 13 | **P0** | Sprint 8 | ✅ DONE |
| — | Flink CI/CD + BMS Simulator + Avro Auto-reg | 11 | P1 | Sprint 8 | ✅ DONE |
| — | API Contract Discipline + CI Smoke Tests | 13 | **P0** | Sprint 9 | ✅ DONE |
| — | HA Validation + Keeper 3-node + Keycloak Hardening | 16 | **P0** | Sprint 9 | ✅ DONE |
| v3-10 | Schema Registry (Apicurio) + Avro migration | 8 | P1 | Post-pilot | 📋 Deferred |

**Total: ~220 SP / 10 sprints (Sprint 1-9 DONE = ~220 SP delivered, Sprint 10 remaining = ~40 SP)**

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
| — | **Sprint 6** (~05-30 → 06-01) | AI Workflow + Flood Alert + Mobile Tier 1+2 + Push | GO ✅ |
| — | **Sprint 7** (~06-01 → 06-03) | Building Safety + Avro + Pilot Readiness | GO FOR PILOT ✅ |
| — | **Sprint 8** (06-04 → 06-17) | Mobile + Infra HA (CH 2-node + Kafka 3-broker) + Flink CI/CD | CONDITIONAL GO ✅ |
| — | **Sprint 9** (buffer 06-04 → 06-17) | API Contract + HA Validation + CI Smoke + Security | BUFFER COMPLETE ✅ |
| — | **Sprint 10** (07-02 → 07-15) | API Contract 100% + Pilot Security + Readiness Gate | 📋 PLANNED |
| Sprint 6 | **Post-pilot** | Avro Schema Registry + gRPC migration | 📋 Deferred |

> **PO điều chỉnh rationale:** AI Workflow + Flood Alert được ưu tiên lên Sprint 6. Mobile app song song Sprint 6-8. Building Safety Sprint 7. Infrastructure HA Sprint 8-9. Sprint 10 là sprint hoàn thiện cuối cùng trước Pilot Phase.

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

### Target Architecture (T2, post-Sprint 5)

```
┌────────────────────────── Edge per Building ──────────────────────────┐
│  BMS (Modbus/BACnet) ──→ EMQX Edge buffer 24h ──→ Kafka TLS          │
└───────────────────────────────────────────────────────────────────────┘
                                    │
┌────────────────────── Cloud K8s — T2 Cluster ─────────────────────────┐
│                                                                        │
│  Kafka 3 brokers                                                       │
│       │                                                                │
│  iot-ingestion-service (extracted Sprint 5)                            │
│       │                                                                │
│  Flink jobs (aggregation + anomaly + enrichment)                       │
│       ├──────────────────────────────────────────────────────┐        │
│  TimescaleDB (operational <30d)              ClickHouse (analytics)   │
│                                                                        │
│  Keycloak (realm/tenant) ──JWT──→ Kong Gateway                        │
│       │                   │                                            │
│       │          analytics-service (ClickHouse owner)                 │
│       │          Monolith (env+esg+traffic+citizen+ai-wf+bms+admin)   │
│       │                     │                                          │
│       │          ┌──────────┘  (Tier 2 opt-in)                        │
│       │          ▼                                                       │
│       │          forecast-service (Python/FastAPI — ARIMA/ML)         │
│       │          ← Tier 1: Java ARIMA (smile-core, in-process)       │
│       │          ← Fallback: NaiveForecastAdapter (rolling avg)       │
│                                                                        │
│  Citizen PWA  ←── push notifications (Web Push)                       │
│  Operator React Native  ←── push (FCM/APNs)                           │
│  Operator Web React                                                    │
└───────────────────────────────────────────────────────────────────────┘

Tier 1 Deployment (1 image):
  uip-monolith:v3.x  ←  Java only, ARIMA in-process, không cần Python

Tier 2 Deployment (3+ images):
  uip-monolith:v3.x              ←  Java core
  uip-analytics-service:v3.x     ←  Java (ClickHouse)
  uip-iot-service:v3.x           ←  Java (BMS adapters)
  uip-forecast-service:v3.x      ←  Python (ML enhancement, opt-in)
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

### Python Services & Monolith — Làm thế nào chạy 1 image duy nhất?

> **Vấn đề:** Dự án có Java/Spring Boot (monolith chính) và Python/FastAPI (forecast-service, AI/ML). Hai runtime khác nhau (JVM vs CPython) — không thể build thành 1 Docker image theo cách thông thường.

**Giải pháp: Port/Adapter + Java-native fallback + Python opt-in enhancement.**

Mỗi Python service phải tuân theo pattern sau:

```
                    ForecastPort interface
                    (Java — trong monolith)
                         │
              ┌──────────┼──────────┐
              │          │          │
    ArimaForecastAdapter  │  NaiveForecastAdapter
    (smile-core, Java)    │  (rolling avg, Java)
    MAPE 3.54% ✅         │  MAPE ~8-12%
    matchIfMissing=true   │  fallback khi Python DOWN
              │          │
              │  PythonForecastAdapter
              │  (HTTP → FastAPI service)
              │  MAPE 3.54%+ (auto_arima, Prophet future)
              │  Chỉ available ở Tier 2
              │
    ──── Tier 1 ──────────── Tier 2 ────
    Không set flag          uip.forecast.engine=python
    → ARIMA auto-load       → HTTP call Python service
    → 1 image, no Python    → +1 image Python
```

**Nguyên tắc bắt buộc:**

| Quy tắc | Giải thích |
|---------|-----------|
| **Python = Tier 2 opt-in**, KHÔNG phải monolith requirement | Tier 1 hoạt động đầy đủ không cần Python |
| **Mỗi Python service phải có Java-native fallback** | Khi Python DOWN → monolith không crash, trả `isFallback=true` |
| **`@ConditionalOnProperty(matchIfMissing=true)`** trên Java adapter | Tier 1 không set flag → Java adapter auto-load |
| **Graceful degradation** | Python DOWN → HTTP 200 + fallback data, không 500 |

**Implementation thực tế (Sprint 4-5):**

```java
// ForecastService.java — đã implement
@Service
public class ForecastService {
    private final ForecastPort pythonAdapter;   // HTTP call Python (Tier 2)
    private final ForecastPort naiveFallback;   // pure Java rolling average

    public ForecastResult forecast(...) {
        try {
            return pythonAdapter.forecast(...);      // thử Python trước
        } catch (ForecastServiceUnavailableException e) {
            return naiveFallback.forecast(...);       // Python DOWN → Java fallback
            // isFallback = true → operator biết data không phải AI
        }
    }
}

// Tier 1: matchIfMissing=true → ARIMA Java adapter tự load
@ConditionalOnProperty(name = "uip.forecast.engine", havingValue = "arima", matchIfMissing = true)
class ArimaForecastAdapter implements ForecastPort { ... }  // smile-core, MAPE 3.54%
```

**Bảng quyết định deployment:**

| Component | Tier 1 (1 image) | Tier 2 (3+ images) |
|-----------|-------------------|---------------------|
| Forecast engine | `ArimaForecastAdapter` (Java, `smile-core`) | Python `forecast-service` (FastAPI, `auto_arima`) |
| Forecast accuracy | MAPE 3.54% ✅ | MAPE 3.54% (hiện tại) + ML nâng cao (Sprint 6+) |
| Python runtime | **Không cần** | FastAPI container riêng |
| Fallback khi DOWN | N/A (in-process) | `NaiveForecastAdapter` → HTTP 200 `isFallback=true` |
| Flag config | Không set → `matchIfMissing=true` → ARIMA | `uip.forecast.engine=python` |

**Pattern áp dụng cho tương lai (mọi Python service mới):**

| Python service (planned) | Java-native fallback | Sprint |
|--------------------------|---------------------|--------|
| AI Workflow engine (Python) | Java Camunda/Flowable fallback | Sprint 6 |
| NLP complaint classifier (Python) | Java keyword matching fallback | Sprint 7+ |
| Image recognition (Python) | No-op fallback (P3 feature) | Post-pilot |

> **Tóm lại:** Tier 1 = **1 image Java thuần**, hoạt động đầy đủ với ARIMA in-process. Python services là enhancement cho Tier 2, deploy riêng, không ảnh hưởng monolith.

---

## 5. Sprint Plan (10 Sprints — 9 DONE + 1 Planned)

> **Lưu ý:** Thứ tự sprint đã được PO điều chỉnh so với plan gốc (detail-plan.md) để ưu tiên business value. BMS được đẩy lên sớm (Sprint 5), AI Innovation được ưu tiên Sprint 6. Mobile và Building Safety song song Sprint 6-8. Infrastructure HA Sprint 8-9. Sprint 10 là sprint hoàn thiện cuối cùng.

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

### Sprint 6 (~2026-05-30 → 2026-06-01): AI Innovation + Mobile Foundation — ✅ COMPLETE

**Key deliverables:**
- AI Workflow Designer (BPMN) + Flood Alert Pipeline E2E
- React Native + Expo scaffold + PKCE login + Push backend (FCM/APNs)
- Mobile Tier 1+2 implementation — Dashboard, Alerts, Push Settings
- SA code review + QA regression gate
- PO demo verified

**Gate:** GO ✅

---

### Sprint 7 (~2026-06-01 → 2026-06-03): Building Safety + Avro + Pilot Readiness — ✅ COMPLETE

**Key deliverables:**
- Building Safety structural monitoring — Flink CEP + Welford stddev (ADR-034)
- Avro Schema Registry — 4 schemas registered (Apicurio)
- SLA-001 fix — VibrationAnomalyJob RUNNING
- 1,178 backend tests + 375 Playwright E2E tests
- EA Assessment: 4.73/5.0 — GO FOR PILOT

**Gate:** GO FOR PILOT ✅ (UNCONDITIONAL)

---

### Sprint 8 (2026-06-04 → 2026-06-17): Mobile + Infrastructure HA — ✅ CONDITIONAL GO

**Key deliverables:**
- Mobile Dashboard (4 KPI cards + bottom tabs) + Mobile Alerts + Safety Score
- ClickHouse 2-node HA — ReplicatedReplacingMergeTree + Keeper (ADR-036)
- Kafka 3-broker KRaft quorum (ADR-037)
- Flink CI/CD automated submission (ADR-038)
- PG Streaming Replication — lag 0.3s avg
- k6 Load Test — 500 VU / 200 VU sustained 30 min
- BMS Hardware Simulator — Modbus TCP slave + 12 IT tests
- Keycloak pilot realm — 3/3 users verified
- Avro auto-registration — 4 schemas on deploy
- 1,221 tests PASS, 285/285 regression PASS

**Gate:** CONDITIONAL GO ✅ — 40 TCs BLOCKED (HA env + mobile simulator), all bugs fixed same-day

---

### Sprint 9 (buffer 2026-06-04 → 2026-06-17): API Contract + HA Validation — ✅ BUFFER COMPLETE

**Key deliverables:**
- HA stack LIVE (21 healthy containers) — 14 ngày sớm
- CH Keeper 3-node quorum — kill 1 keeper → queries OK
- Kafka broker kill test — 2 remaining brokers handle traffic
- `packages/api-types/` generated từ OpenAPI spec
- CI contract drift check deployed (GitHub Actions)
- Config smoke tests — Keycloak + CH + Kafka verified per CI run
- Keycloak production realm hardening — brute-force protection, localhost URIs removed
- `packages/hooks/` monorepo boundary for web + mobile
- ADR-039 (OpenAPI-First) + ADR-040 (Monorepo Boundary)
- API Contract Audit — 49 undocumented endpoints identified
- SA Code Review APPROVED (1 MAJOR + 1 MINOR fixed)

**Gate:** BUFFER COMPLETE ✅ — remaining: 49 undocumented endpoints + Keycloak live rotation

---

### Sprint 10 (2026-07-02 → 2026-07-15): API Contract Completion + Pilot Readiness — 📋 PLANNED

**Sprint Goal:** 110/110 API endpoints documented + Pilot security hardening + Declare MVP3 DONE

**Tier 1 (28 SP — MUST DONE):**

| Epic | Story | SP | Owner | Priority |
|------|-------|----|-------|----------|
| API Contract | Fix P0 paths (Workflow + Alert resolve + Admin sensors) | 4 | Backend-1 | **P0** |
| API Contract | Document Tenant Admin (10 endpoints) | 3 | Backend-2 | **P0** |
| API Contract | Document BMS module (7 endpoints) | 2 | Backend-2 | **P1** |
| API Contract | Document 9 remaining modules (34 endpoints) | 4 | Backend-1+2 | **P1** |
| API Contract | Error response codes (15 critical endpoints) | 2 | Backend-1 | **P0** |
| API Contract | Gate debug endpoints `@Profile("!production")` | 1 | Backend-2 | **P0** |
| API Contract | Resolve dual SSE + Regenerate types | 2 | Backend-1 + FE | **P1** |
| Pilot Security | Keycloak live secret rotation | 1 | DevOps | **P0** |
| Pilot Security | iOS cert + Production profile review + OWASP | 4 | DevOps + BE | **P1** |
| Pilot Readiness | Runbook + Regression ≥1,300 + Demo dry-run | 5 | DevOps+QA+PM | **P0** |

**Tier 2 (12 SP — BEST EFFORT):**

| Story | SP | Owner |
|-------|----|-------|
| Mobile offline UX spike (wireframes) | 2 | Frontend + BA |
| BPMN Workflow Designer UX polish | 3 | Frontend |
| ESG PDF Export (sync endpoint) | 3 | Backend-1 |
| Android APK build pipeline | 2 | DevOps + Frontend |
| Mobile Control Panel UX spike | 2 | Frontend + BA |

**Plan chi tiết:** → [sprint10-plan.md](project/sprint10-plan.md)

**Gate criteria:** 110/110 endpoints documented | CI contract check PASS | Keycloak rotation verified | Regression ≥1,300 PASS | SA APPROVED | **DECLARE MVP3 DONE**

---

## 5. Milestone Map (Updated 2026-06-05)

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

✅ Sprint 6 (~05-30 → 06-01): AI Workflow + Flood Alert + Mobile Foundation + Push
   GATE: GO. AI Workflow Designer + Flood Alert Pipeline + Mobile Tier 1+2.

✅ Sprint 7 (~06-01 → 06-03): Building Safety + Avro + Pilot Readiness
   GATE: GO FOR PILOT (UNCONDITIONAL). 1,178 tests. EA Score 4.73/5.0.

✅ Sprint 8 (06-04 → 06-17): Mobile + Infrastructure HA
   GATE: CONDITIONAL GO. CH 2-node + Kafka 3-broker + PG replication. 1,221 tests.

✅ Sprint 9 (buffer 06-04 → 06-17): API Contract + HA Validation
   GATE: BUFFER COMPLETE. HA live 14 days early. 49 endpoints identified.
   ─── CURRENT POSITION ───

📋 Sprint 10 (07-02 → 07-15): API Contract 100% + Pilot Security + Readiness Gate
   Tier 1 (28 SP): API Contract completion + Security hardening + Runbook + Regression
   Tier 2 (12 SP): UX spikes + ESG PDF + APK build
   → DECLARE MVP3 DONE

🎯 2026-07-16 → 07-31: Pilot Preparation (env setup + data migration + training)
🎯 2026-08-01 → 08-03: Pilot Soft Launch (internal)
🎯 2026-08-04: Pilot Soft Launch (5 buildings, 2 tenants)
🎯 2026-08-10: Tier 2 Pilot SIGNED

   Post-pilot (v3.1): Avro full migration + gRPC + Mobile offline + Control Panel + ESG PDF
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

### Resolved Risks (Sprint 1-9)

| ID | Risk | Resolution |
|----|------|-----------|
| ~~R1~~ | Multi-building RLS query >2s (N+1) | ✅ Resolved — p95=2.3ms via materialized view (Sprint 1) |
| ~~R2~~ | Tier 1 regression từ multi-building | ✅ Resolved — zero regression through all sprints |
| ~~R3~~ | LSTM model MAPE >15% | ✅ Resolved — LSTM NO-GO, ARIMA only (Sprint 4, MAPE 3.54%) |
| ~~R4~~ | ClickHouse cluster HA delays | ✅ Resolved — CH 2-node HA deployed Sprint 8, Keeper 3-node Sprint 9 |
| ~~R5~~ | City Authority ESG spec changes | ✅ Resolved — GRI 302/305 finalized Sprint 3 |
| ~~R6~~ | iOS cert + Apple review delays | ⚠️ Partial — still pending, Android APK ready, Sprint 10 submit |
| ~~R7~~ | Kong plugin priority error | ✅ Resolved — alg=none blocked, plugin order verified (Sprint 1) |
| ~~R8~~ | Extraction code break Tier 1 | ✅ Resolved — `matchIfMissing=true` pattern proven (Sprint 1-2) |
| ~~R9~~ | bpmn-js learning curve | ✅ Resolved — AI Workflow Designer functional Sprint 6 |
| ~~R10~~ | Mobile + AI song song kéo Frontend | ✅ Resolved — Mobile delivered Sprint 6-8 |
| ~~R11~~ | Sprint 6 over-commit | ✅ Resolved — 74 SP delivered across S6-S7 |
| ~~R12~~ | Building Safety Flink CEP complex | ✅ Resolved — Delivered Sprint 7, VibrationAnomalyJob RUNNING |
| ~~R14~~ | 40 TCs BLOCKED từ Sprint 8 | ✅ Resolved — HA stack live Sprint 9, infra TCs PASS |

### Active Risks (Sprint 10)

| ID | Risk | Severity | Prob | Mitigation | Owner |
|----|------|---------|------|-----------|-------|
| R15 | 49 undocumented endpoints — contract fix breaks frontend | MEDIUM | 30% | Regenerate types + `tsc --noEmit` verify Day 3 | Backend + Frontend |
| R16 | Keycloak live rotation breaks staging login | LOW | 15% | Test on staging first; have rollback (re-import old realm) | DevOps |
| R17 | iOS Apple review rejects cert | MEDIUM | 25% | Start Day 2 Sprint 10; Android APK as fallback | DevOps |
| R18 | Regression reveals new bugs — fix cycle delays gate | MEDIUM | 30% | S8 precedent: 13 bugs fixed same-day; 2-day buffer | QA + Backend |
| R19 | Pilot target 2026-08-10 bị delay | LOW | 15% | Sprint 10 buffer; worst case delay 1 week (2026-08-17) | PM + PO |

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

| Role | Sprint 1-7 (DONE) | Sprint 8-9 (DONE) | Sprint 10 (Final) |
|------|-------------------|-------------------|-------------------|
| Backend Eng 1 | SA spike + analytics + ESG + Flood Alert | Mobile fixes + Dashboard 404 fix | API Contract P0 fixes + Error codes |
| Backend Eng 2 | Aggregation + FMS + BMS + AI Workflow | BMS Simulator support | Tenant Admin + BMS docs + Profile gating |
| Frontend Eng | Dashboard + Forecast + BMS + AI Workflow + RN | X-Tenant-Id + Dashboard crash fix | Type regeneration + UX polish + APK |
| DevOps | ClickHouse + Kong + EMQX + Blue-green | CH HA + Kafka 3-broker + PG replication | Keycloak rotation + Runbook + OWASP |
| QA | Test strategy + BMS ITs + Regression | k6 500 VU + Pilot regression 285 TCs | Regression ≥1,300 + Smoke tests |

**Total effort:** ~19 person-months | **Team:** 5 FTE + 0.5 PM = 5.5 FTE
**Delivered (Sprint 1-9):** ~220 SP | **Remaining (Sprint 10):** ~40 SP (28 Tier 1 + 12 Tier 2)

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
| `project/` | Sprint plans chi tiết (S1-S10), demo scripts, roadmap |
| `architecture/` | [**System Architecture (Mermaid)**](architecture/system-architecture.md) · ADR-026 đến ADR-040 |
| `qa/` | Test plans, performance reports, bug tracker |
| `reports/` | Sprint reviews, code reviews, UAT sign-off |
| `test/` | Test case execution plans, new TCs |
| `security/` | OWASP scan checklists, security templates |
| `infrastructure/` | HA setup, runbooks |
| `changes/` | Change orders, descope decisions |

---

*Tổng hợp bởi: SA + BA + PM + QA (4 agents, 2026-05-10)*
*Sprint alignment updated: 2026-06-05 (Sprint 9 COMPLETE, Sprint 10 planned)*
*Next review: Sprint 10 Gate (2026-07-15) — DECLARE MVP3 DONE*
