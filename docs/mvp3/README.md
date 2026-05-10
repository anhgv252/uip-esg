# MVP3 — Building Cluster + Advanced AI (v3.0)

**Trạng thái:** 🔵 PLANNING  
**Ngày lập kế hoạch:** 2026-05-10  
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

| ID | Feature | SP | Priority | Sprint |
|----|---------|-----|---------|--------|
| v3-01 | Cross-building analytics + aggregate dashboard | 13 | **P0** | Sprint 1-2 |
| v3-05 | BMS integration SDK (Modbus, BACnet, KNX) | 21 | **P0** | Sprint 4 |
| v3-07 | Kong API Gateway + Keycloak IdP | 13 | **P0** | Sprint 4 |
| v3-08 | ClickHouse OLAP + analytics microservice | 13 | **P0** | Sprint 1-2 |
| v3-02 | Advanced ESG: GRI Standards + carbon credit | 21 | P1 | Sprint 3 |
| v3-03 | Predictive AI: energy forecasting ARIMA/LSTM | 13 | P1 | Sprint 3 |
| v3-06 | Mobile operator app (iOS/Android) | 21 | P1 | Sprint 5 |
| v3-09 | Building safety: structural monitoring | 13 | P1 | Sprint 6 (risky) |
| v3-10 | Schema Registry (Apicurio) + Avro migration | 8 | P1 | Sprint 6 |
| v3-04 | Predictive maintenance: sensor anomaly detection | 13 | P2 | Sprint 3 |

**Total: ~149 SP / 12 tuần**

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

### ADRs cần viết cho MVP3

| ADR | Title | Sprint |
|-----|-------|--------|
| ADR-026 | ClickHouse Pre-emptive Adoption (override ADR-012 trigger) | MVP3-1 |
| ADR-027 | Keycloak Hybrid Auth — Issuer Migration Strategy | MVP3-1 |
| ADR-028 | Kong Gateway Scope — Extracted Services Only | MVP3-1 |
| ADR-029 | BMS Protocol Adapter Pattern (Modbus/BACnet/KNX) | MVP3-4 |
| ADR-030 | Mobile Stack — React Native (Operator) + PWA (Citizen) | MVP3-5 |
| ADR-031 | Schema Registry — Avro for Cross-Service Topics | MVP3-6 |
| ADR-032 | Predictive AI — ARIMA In-Process + LSTM External gRPC | MVP3-3 |
| ADR-033 | Cross-Building Tenant Hierarchy — Parent Tenant Aggregation | MVP3-1 |
| ADR-034 | Structural Monitoring — Flink CEP + Welford stddev | MVP3-6 |

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

## 5. Sprint Plan (6 Sprints × 2 Tuần)

### Sprint MVP3-1: Foundation + Multi-Building Core + analytics-service Extraction (2026-05-12 → 2026-05-25)

**Sprint Goal:** Multi-building RLS isolation + ClickHouse foundation + **tách analytics-service** (strangler fig bước 1-3) + Cross-building dashboard skeleton

> **Lý do tách analytics-service ngay Sprint 1:** ClickHouse adoption (ADR-026) tạo trigger cho analytics extraction (ADR-011). analytics-service là stateless query layer dễ tách nhất — shadow deploy cùng monolith để validate trước khi cutover Sprint 2. Nếu không tách, OLAP query spike từ v3-01 dashboard sẽ ảnh hưởng latency của alert engine và citizen API trong cùng JVM.

| ID | Story | SP | Owner | DoD |
|----|-------|----|-------|-----|
| SA-01 | SA Spike: Multi-building isolation (RLS + ADR-033) | 5 | Backend Lead | ADR-033 merged, RLS strategy, schema changes identified |
| SA-02 | SA Spike: ClickHouse + Flink integration (ADR-026) | 5 | Backend Lead | ADR-026 merged, dual-sink job design, retention policy |
| SA-03 | SA Spike: Kong + Keycloak architecture (ADR-027, ADR-028) | 5 | DevOps | ADR-027/028 merged, gateway routing, tenant header propagation |
| v3-BE-01 | Building entity + RLS policies (schema V20) | 8 | Backend Eng 1 | Tests pass, isolation verified với 2 buildings |
| v3-BE-02 | Cross-building aggregation queries (sum, avg per building) | 8 | Backend Eng 1 | Unit + IT, p95 <500ms với 2M rows, caching applied |
| **v3-EXT-01** | **[EXTRACTION — Tier 2 only] Tạo `applications/analytics-service/` trong monorepo** | **5** | **Backend Lead** | **Dockerfile riêng, thin wrapper; `values-tier1.yaml` KHÔNG thay đổi; Tier 1 CI test vẫn pass với monolith** |
| **v3-EXT-02** | **[EXTRACTION — Tier 2 only] `@ConditionalOnProperty(analytics-external, matchIfMissing=true)` + capability flag** | **3** | **Backend Lead** | **Tier 1 config không set flag → `matchIfMissing=true` → beans load bình thường trong monolith** |
| **v3-EXT-03** | **[EXTRACTION — Tier 2 only] Shadow deploy analytics-service song song monolith** | **5** | **DevOps** | **Chỉ deploy lên Tier 2 staging env; Tier 1 env không bị ảnh hưởng** |
| v3-FE-01 | Cross-building dashboard shell (routes + layout) | 5 | Frontend Eng | Skeleton routes, building selector component |
| v3-FE-02 | Multi-building selector (shared, persisted) | 5 | Frontend Eng | localStorage, filters work with API |
| v3-DevOps-01 | ClickHouse single-node POC (Docker) + Flink dual-sink test job | 8 | DevOps | Dual-write TimescaleDB + ClickHouse e2e tested, queries <5s |
| v3-DevOps-02 | Kong reverse proxy + Keycloak realm setup (non-prod) | 8 | DevOps | Token validation, tenant context injected |
| v3-QA-01 | Test strategy + 10M row seeding scripts + shadow validation | 5 | QA | Load test scenarios, shadow mode output diff test, perf thresholds |

**Capacity:** 75 SP | **Warning:** Tăng so với plan gốc — nếu cần cắt, dời v3-FE-01/02 sang Sprint 2 Day 1 (5 SP buffer)

**Gate:**
- ADRs merged (ADR-026, ADR-027, ADR-028, ADR-033)
- Schema V20 tested với 2 buildings × 2 tenants
- `analytics-service` shadow deploy stable trên Tier 2 staging, output diff < 0.01%
- **CI test với `values-tier1.yaml` (flag không set) PASS — Tier 1 monolith không đổi gì**
- Zero Tier 1 regression

---

### Sprint MVP3-2: ClickHouse Analytics Live + analytics-service Cutover (2026-05-26 → 2026-06-08)

**Sprint Goal:** ClickHouse queries live + analytics-service **cutover** (strangler fig bước 4) + cross-building ESG + analytics dashboard với data thực

> **analytics-service cutover Sprint 2:** Sau 1 sprint shadow mode (Sprint 1), set `analytics-external=true` trong `values-tier2.yaml`. Từ đây analytics-service scale độc lập (HPA riêng), không ảnh hưởng monolith JVM heap khi dashboard query nặng.

| ID | Story | SP | Owner | DoD |
|----|-------|----|-------|-----|
| **v3-EXT-04** | **[EXTRACTION] analytics-service cutover: set `analytics-external=true`** | **3** | **DevOps** | **Monolith `AnalyticsAutoConfiguration` không load; analytics-service nhận 100% traffic; monitor 24h** |
| **v3-EXT-05** | **[EXTRACTION] HPA riêng cho analytics-service (CPU 70%, min 2/max 6)** | **2** | **DevOps** | **HPA policy deploy; stress test: analytics spike không ảnh hưởng monolith p95** |
| v3-BE-03 | ClickHouse client + analytics queries (cross-building) | 13 | Backend Eng 1 | p95 <5s @ 10M rows, integration tests |
| v3-BE-04 | Flink → ClickHouse enrichment job | 8 | Backend Eng 2 | Job deployed, exactly-once, metrics in Grafana |
| v3-BE-05 | Cross-building ESG aggregation (city authority format) | 13 | Backend Eng 2 | Tier 2 ESG report, GRI format, cache 10 phút |
| v3-FE-03 | Analytics dashboard (energy + emissions by building) | 13 | Frontend Eng | Charts live, WebSocket updates, <3s load |
| v3-FE-04 | Aggregation filters (date, building, metric) | 8 | Frontend Eng | URL state persistence, 25 filter combos |
| v3-DevOps-03 | ClickHouse cluster 2-node HA + Flink replicas K8s | 13 | DevOps | StatefulSet, PV, HPA riêng, monitoring alerts |
| v3-QA-02 | Cross-building E2E tests + performance suite | 8 | QA | 10 scenarios, load test 500 VU 30 phút; verify analytics spike không lag monolith |

**Gate:**
- `analytics-service` nhận 100% traffic, monolith không load analytics beans
- ClickHouse queries <5s p95 @ 10M rows
- Analytics spike test: 200 concurrent heavy queries → monolith alert API p95 không thay đổi
- City authority ESG format confirmed

---

### Sprint MVP3-3: Predictive AI (2026-06-09 → 2026-06-22)

**Sprint Goal:** ARIMA energy forecast + anomaly detection maintenance + explainability UI

| ID | Story | SP | Owner | DoD |
|----|-------|----|-------|-----|
| v3-BE-06 | Energy forecasting service (ARIMA in-process, LSTM external gRPC) | 21 | Backend Eng 1 | MAPE <10%, API <500ms, ADR-032 merged |
| v3-BE-07 | Maintenance anomaly detector (isolation forest + Flink CEP) | 13 | Backend Eng 2 | Recall >80% seeded anomalies, <100ms per reading |
| v3-BE-08 | Forecast + anomaly dashboard views (backend API) | 8 | Backend Eng 1 | API delivers 7-day forecast + anomaly markers |
| v3-FE-05 | Energy forecast chart + anomaly timeline (recharts + D3) | 13 | Frontend Eng | Actual vs predicted, confidence bands, anomaly markers |
| v3-FE-06 | AI explainability panel (feature importance, contributing sensors) | 8 | Frontend Eng | Tooltip shows why anomaly, training window |
| v3-QA-03 | AI model validation + synthetic anomaly injection tests | 8 | QA | 50 synthetic anomalies injected + detected, MAPE CI gate |

**Gate:** MAPE <10% + recall >80% + anomaly injection test pass

**Contingency:** Nếu LSTM MAPE >15% đến Day 8 → pivot sang ARIMA only (giảm 5 SP)

---

### Sprint MVP3-4: BMS Integration SDK + Kong/Keycloak + iot-ingestion-service Extraction (2026-06-23 → 2026-07-06)

**Sprint Goal:** Modbus/BACnet adapter framework + IAM gateway production-ready + **tách iot-ingestion-service** (strangler fig bước 1-4) + iOS cert

> **Lý do tách iot-ingestion-service Sprint 4:** BMS integration (v3-05) sẽ push throughput lên >50K events/sec với 5 buildings × 1K datapoints × 10s polling — đây là trigger cụ thể trong ADR-011. Nếu iot-ingestion vẫn trong monolith, GC pressure và thread contention sẽ ảnh hưởng alert engine và API. Tách sprint này để BMS traffic đi vào iot-service riêng, scale độc lập.

| ID | Story | SP | Owner | DoD |
|----|-------|----|-------|-----|
| **v3-EXT-06** | **[EXTRACTION — Tier 2 only] Tạo `applications/iot-ingestion-service/` trong monorepo** | **5** | **Backend Lead** | **Dockerfile, thin wrapper; `values-tier1.yaml` KHÔNG thay đổi; Tier 1 CI vẫn pass** |
| **v3-EXT-07** | **[EXTRACTION — Tier 2 only] `@ConditionalOnProperty(iot-ingestion-external, matchIfMissing=true)` + shadow deploy** | **5** | **Backend Lead + DevOps** | **Shadow chỉ trên Tier 2 staging; Tier 1 monolith giữ nguyên IoT beans** |
| v3-BE-09 | BMS SDK framework (Modbus TCP + BACnet/IP) trong iot-ingestion-service | 21 | Backend Eng 2 | Device simulator, 3 adapters tested, DLQ + CB per protocol |
| v3-BE-10 | BMS device discovery + auto-registration (per tenant/building) | 8 | Backend Eng 2 | DHCP scan, manual add via API, RLS enforced |
| v3-DevOps-04 | Kong ingress + Keycloak auth (prod TLS + rate-limiting) | 13 | DevOps | OAuth2 tenant isolation, 1K req/min rate limit, audit log |
| v3-DevOps-05 | Keycloak realm + role mapping (Admin/Operator/TenantAdmin) | 8 | DevOps | LDAP connector, JWT includes tenant + building scopes |
| v3-QA-04 | BMS integration test suite (3 device types + error cases) | 13 | QA | 30 scenarios, timeout/CRC/reconnect handling |
| v3-QA-05 | Kong rate-limit + CORS + token tests + plugin order verification | 8 | QA | 15 test cases, auth bypass prevention, alg=none blocked |
| [INFRA] | iOS cert + Apple dev account (unblock Sprint 5) | 0 | Frontend Eng | Certificate submitted bởi Day 5 Sprint 4 |

**Gate:**
- iot-ingestion-service shadow stable, Kafka output diff < 0.01%
- BMS 10+ devices simulator stable
- Kong alg=none attack blocked, plugin order verified
- `iot-ingestion-external=true` trong `values-tier2.yaml` sẵn sàng cutover Sprint 5

---

### Sprint MVP3-5: Mobile Operator App + iot-ingestion-service Cutover (2026-07-07 → 2026-07-20)

**Sprint Goal:** iOS/Android operator app MVP + **iot-ingestion-service cutover** + FCM/APNs push notifications

| ID | Story | SP | Owner | DoD |
|----|-------|----|-------|-----|
| **v3-EXT-08** | **[EXTRACTION — Tier 2 only] iot-ingestion-service cutover: set `iot-ingestion-external=true` trong `values-tier2.yaml`** | **3** | **DevOps** | **Chỉ áp dụng Tier 2 env; Tier 1 `values-tier1.yaml` không động tới** |
| v3-Mobile-01 | React Native + Expo scaffold (iOS + Android) | 13 | Frontend Eng | Builds cho cả 2 platform, FCM configured |
| v3-Mobile-02 | OAuth2 login + tenant selection (Keycloak) | 8 | Frontend Eng | Token persisted (secure storage), tenant list loads |
| v3-Mobile-03 | Dashboard + alerts list (WebSocket real-time) | 13 | Frontend Eng | Building/sensor alerts, <3s initial load |
| v3-Mobile-04 | Manual control panel (actuator commands + confirmation) | 8 | Frontend Eng | On/off, command logged to audit trail |
| v3-Mobile-05 | FCM + APNs push notification backend | 13 | Backend Eng 1 | Alert → FCM payload → device, deep link to alert detail |
| v3-QA-06 | Mobile E2E tests + push notification tests + iot-service isolation | 8 | QA | Automated UI tests, push tested physical devices; BMS spike test không lag mobile API |

**Gate:**
- iot-ingestion-service nhận 100% traffic, monolith IoT beans disabled
- BMS load test 5K polls/sec không ảnh hưởng monolith alert p95
- APK/IPA builds, 9/10 push received

**Contingency:** Android APK demo-ready trước iOS nếu cert delay

---

### Sprint MVP3-6: Avro + Building Safety + Pilot Prep (2026-07-21 → 2026-08-03)

**Sprint Goal:** Schema Registry + structural monitoring + blue-green deploy + pilot readiness gate

| ID | Story | SP | Owner | DoD |
|----|-------|----|-------|-----|
| v3-BE-11 | Kafka Schema Registry + Avro migration (sensor topic) | 8 | Backend Eng 1 | Schema versioning, backward compat, no data loss |
| v3-BE-12 | Building Safety: structural monitoring alerts (v3-09) | 13 | Backend Eng 2 | Vibration/tilt sensors, Flink CEP, alert <15s |
| v3-FE-07 | Building Safety UI (sensor status + trend + drill-down) | 8 | Frontend Eng | Dashboard section, historical chart |
| v3-DevOps-06 | Blue-green deploy + Istio traffic switch + rollback <30s | 13 | DevOps | Parallel K8s deployments, rollback validated |
| v3-QA-07 | Pilot readiness: full regression + performance gate | 13 | QA | 100+ scenarios, ALL perf thresholds met |
| v3-QA-08 | Executive demo script v2 + City Authority dry-run | 8 | QA + PM | 15 phút demo video, stakeholder sign-off |
| v3-Docs-01 | Runbook + Tier 2 pilot deployment guide | 8 | Backend Lead | Deploy steps, troubleshooting, SLA definitions |

**Gate:** Regression 100% + ALL SLA gates met + demo approved + rollback <30s

**Descope plan:** Nếu capacity thiếu → v3-09 Building Safety → v3.1 post-pilot

---

## 5. Milestone Map

```
2026-05-12  ── Sprint MVP3-1 start ──────────────────────────────────
│             SA spikes Day 1-3 (ADR-026, ADR-027, ADR-033) [BLOCKER]
│             analytics-service: tạo service + capability flag [v3-EXT-01/02]
│             analytics-service: shadow deploy song song monolith [v3-EXT-03]
2026-05-25  ── Sprint MVP3-1 END ──────────────────────────────────
              GATE: ADRs merged + schema V20 + shadow diff < 0.01%

2026-05-26  ── Sprint MVP3-2 start ──────────────────────────────────
│             *** analytics-service CUTOVER (analytics-external=true) [v3-EXT-04] ***
│             analytics-service HPA riêng, scale độc lập [v3-EXT-05]
│             City Authority ESG format phải finalize TẠI ĐÂY
2026-06-08  ── Sprint MVP3-2 END ──────────────────────────────────
              GATE: analytics-service live + ClickHouse <5s p95
              PERF CHECK: analytics spike 200 VU → monolith alert p95 không đổi

2026-06-09  ── Sprint MVP3-3 start ──────────────────────────────────
2026-06-22  ── Sprint MVP3-3 END ──────────────────────────────────
              GATE: MAPE <10%, recall >80% anomalies

2026-06-23  ── Sprint MVP3-4 start ──────────────────────────────────
│             [iOS cert submit bởi Day 5]
│             iot-ingestion-service: tạo service + BMS adapters [v3-EXT-06]
│             iot-ingestion-service: shadow deploy + BMS traffic validation [v3-EXT-07]
2026-07-06  ── Sprint MVP3-4 END ──────────────────────────────────
              GATE: BMS 10+ devices + Kong auth bypass blocked + iot-service shadow stable

2026-07-07  ── Sprint MVP3-5 start ──────────────────────────────────
│             *** iot-ingestion-service CUTOVER (iot-ingestion-external=true) [v3-EXT-08] ***
│             iot-ingestion-service HPA riêng (min 3/max 10), BMS spike isolated
2026-07-20  ── Sprint MVP3-5 END ──────────────────────────────────
              GATE: iot-service live + APK/IPA builds + BMS spike không lag monolith
              DEPLOYED: 2 extracted services (analytics + iot-ingestion), monolith gọn hơn

2026-07-21  ── Sprint MVP3-6 start ──────────────────────────────────
2026-08-03  ── Sprint MVP3-6 END ──────────────────────────────────
              GATE: Regression 100% + ALL SLA metrics + demo approved + rollback <30s

2026-08-04  ── Pilot soft launch ──────────────────────────────────
              5 buildings, 2 tenants live
              Image set: uip-monolith:v3.x + uip-analytics-service:v3.x + uip-iot-service:v3.x
2026-08-10  ── Tier 2 Pilot SIGNED ── TARGET ──────────────────────
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

| ID | Risk | Severity | Prob | Mitigation | Owner |
|----|------|---------|------|-----------|-------|
| R1 | Multi-building RLS query >2s (N+1 queries) | CRITICAL | 30% | SA-01 spike validates + pre-test 10M rows Sprint 1 | Backend Lead |
| R2 | Tier 1 regression từ multi-building schema | CRITICAL | 25% | QA chạy regression Day 1 Sprint 1 + Sprint 4 | QA |
| R3 | LSTM model MAPE >15% | HIGH | 35% | ARIMA fallback, abort LSTM bởi Day 8 Sprint 3 | Backend Eng 1 |
| R4 | ClickHouse cluster HA delays (K8s StatefulSet PV) | HIGH | 40% | Test Sprint 1; fallback ClickHouse Cloud nếu fail Day 10 Sprint 2 | DevOps |
| R5 | City Authority ESG spec thay đổi mid-project | MEDIUM | 60% | Weekly sync stakeholder, finalize spec Sprint 2 EOL | PM |
| R6 | iOS cert + Apple review delays (48-72h) | MEDIUM | 55% | Submit cert Day 5 Sprint 4, Android APK demo-ready first | Frontend Eng |
| R7 | Kong plugin priority error = auth bypass | CRITICAL | 20% | Explicit test verify plugin execution order; fail CI nếu unauthenticated request không 401 | QA + DevOps |
| R8 | Extraction code (`@ConditionalOnProperty`) vô tình break Tier 1 monolith | HIGH | 25% | CI bắt buộc chạy test suite với **`values-tier1.yaml` (không set flag)** mỗi PR có extraction code; `matchIfMissing=true` phải được code review checklist xác nhận | QA |

**Action ngay:** R1 + R2 — Pair DevOps + Backend Eng 1 trên SA-01 trong ngày đầu Sprint 1

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

| Role | Sprint 1-2 | Sprint 3-4 | Sprint 5-6 |
|------|-----------|-----------|-----------|
| Backend Eng 1 | SA spike + BE analytics | Predictive AI + BMS SDK | Push notification + Avro |
| Backend Eng 2 | BE aggregation + Flink | BMS SDK + Anomaly | Building Safety |
| Frontend Eng | Dashboard shell | Forecast UI + Explainability | **Mobile (React Native)** |
| DevOps | ClickHouse + Kong/Keycloak | Kong prod + BMS infra | Blue-green + pilot env |
| QA | Test plan + seeding | AI validation + BMS tests | Regression + pilot gate |

**Total effort:** ~19 person-months | **Team:** 5 FTE + 0.5 PM = 5.5 FTE

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

**Nếu velocity <50 SP/sprint:**

| Feature | Action |
|---------|--------|
| Building Safety (v3-09) | → v3.1 post-pilot |
| BMS SDK KNX adapter | → MVP3 chỉ ship Modbus + BACnet |
| LSTM energy forecast | → ARIMA only |
| Schema Registry (v3-10) | → v3.1 nếu Sprint 6 overloaded |

**Nếu Red Risk R1 xảy ra (RLS >2s):**
1. Triage trong 1 ngày
2. Fixable trong 1 ngày → fix ngay
3. Không fixable → descope Building Safety (v3-09) + anomaly detection (v3-04)
4. Vẫn không fix → delay pilot 1 tuần (2026-08-17)

---

## Cấu trúc Tài liệu MVP3

| Thư mục | Nội dung |
|---------|---------|
| `project/` | Sprint plans chi tiết, demo scripts, roadmap |
| `architecture/` | ADR-026 đến ADR-034 |
| `qa/` | Test plans, performance reports |
| `reports/` | Sprint reviews, UAT sign-off |
| `deployment/` | Pilot deployment guide, runbook |

---

*Tổng hợp bởi: SA + BA + PM + QA (4 agents, 2026-05-10)*  
*Next review: End of Sprint MVP3-1 (2026-05-25)*
