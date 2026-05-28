# MVP3 — Mid-Point Review: 4 Sprint Tổng Hợp & Lộ Trình Tiếp Theo

**Ngày:** 2026-05-27
**Tác giả:** QA Engineer + PM
**PO:** anhgv
**Milestone:** Sau Sprint 4 gate PASS — chuẩn bị PO demo

---

## 1. Tổng Hợp 4 Sprint Hoàn Thành

### 1.1 Sprint Overview

| Sprint | Tên | Thời gian | SP Kế hoạch | SP Thực tế | Gate |
|---|---|---|---|---|---|
| **Sprint 1** | Foundation + Multi-Building Core | 05-12 → 05-13 | 75 SP | ~69 SP | 69/70 PASS |
| **Sprint 2** | ClickHouse Live + Analytics Cutover | 05-14 → 05-18 | ~60 SP | ~54 SP | PASS |
| **Sprint 3** | ESG GRI + Keycloak RSA + Flink Enrichment | 05-19 → 05-25 | ~55 SP | ~40.5 SP | 5/6 AC, CH HA descoped |
| **Sprint 4** | Observability + Predictive AI | 05-25 → 05-27 | 42 SP | ~42 SP | 19/19 PASS |

### 1.2 Tổng hợp Deliverables theo Category

#### Architecture & ADRs (8 ADRs merged)

| ADR | Tiêu đề | Sprint | Trạng thái |
|---|---|---|---|
| ADR-026 | ClickHouse Pre-emptive Adoption | S1 | MERGED |
| ADR-027 | Keycloak Hybrid Auth — Issuer Migration | S1 | MERGED |
| ADR-028 | Kong Gateway Scope — Extracted Services Only | S1 | MERGED |
| ADR-032 | Forecast-service Security, Routing & Tenant Isolation | S4 pre-spike | MERGED |
| ADR-033 | Cross-Building Tenant Hierarchy | S1 | MERGED |
| ADR-029 | BMS Protocol Adapter Pattern | Planned S5 | Proposed |
| ADR-030 | Mobile Stack — React Native + PWA | Planned S6 | Proposed |
| ADR-031 | Schema Registry — Avro Migration | Planned S7+ | Proposed |

#### Infrastructure & Platform

| Thành phần | Trạng thái | Sprint |
|---|---|---|
| TimescaleDB + RLS (V26) | LIVE — 10/10 scenarios PASS | S1 |
| ClickHouse single-node | LIVE — Flink dual-sink verified | S1 |
| ClickHouse 2-node HA | DESCOPE — single-node ổn định | S3→deferred |
| Flink EsgDualSinkJob | RUNNING — dual-write TS+CH | S1 |
| Flink BuildingMetadata enrichment | LIVE — inline district lookup | S3 |
| Kong API Gateway | LIVE — DB-less, JWT plugin | S1 |
| Keycloak (RS256) | LIVE — dual-issuer HMAC+RSA | S3 |
| Prometheus + Grafana | LIVE — 7/7 targets UP, 8 forecast panels | S4 |
| Docker Compose full stack | LIVE — 22 services, 8 core healthy | S1-S4 |

#### Backend Modules

| Module/API | Endpoints | Tests | Sprint |
|---|---|---|---|
| Building entity + RLS | GET/POST /api/v1/buildings | 9 tests, 96% coverage | S1 |
| Cross-building aggregation | Rollup queries p95=2.3ms | RLS 10/10 | S1 |
| analytics-service (extracted) | Shadow → cutover complete | CapabilityFlagIT PASS | S1-S2 |
| ESG GRI 302/305 Export | POST /esg/reports/generate | Excel+PDF verified | S3 |
| Keycloak RoutingJwtDecoder | HMAC uip-legacy + RSA keycloak | Dual-issuer verified | S3 |
| Forecast API (ARIMA) | GET /api/v1/forecast/energy | MAPE 3.54%, 720 points | S4 |
| ForecastPort (Port/Adapter) | @ConditionalOnProperty | 3 adapters: Python/Naive/Disabled | S4 |
| Python forecast-service | FastAPI + auto_arima | 40 Python tests, 100% coverage | S4 |
| Anomaly detection | Isolation Forest + Z-score | Verified | S4 |
| LSTM evaluation | Gate criterion | NO-GO documented | S4 |
| Push Notification (Web Push) | VAPID + subscription API | HTTP status tests | S3 |

#### Frontend

| Component | Chi tiết | Sprint |
|---|---|---|
| Cross-Building Dashboard Shell | /buildings route, Zustand persist | S1 |
| Multi-Building Selector | Max 5, cross-tab sync, URL state | S1 |
| ESG Dashboard + Reports | EsgPage, ReportGenerationPanel | S3 |
| ForecastChart | recharts ComposedChart + CI band | S4 |
| ForecastTooltip | Custom tooltip: predicted/actual/CI | S4 |
| TypeScript strict | 0 errors, 180 tests | S1-S4 |

#### Quality Metrics Tổng Hợp

| Metric | Giá trị | Target |
|---|---|---|
| Tổng automated tests | 978+ (739 Java unit + 19 IT + 180 FE + 40 Python) | — |
| Test failures | 0 | 0 |
| JaCoCo LINE coverage | 87.7% | ≥80% |
| JaCoCo BRANCH coverage | 71.4% | ≥65% |
| ARIMA MAPE | 3.54% | <15% |
| P0/P1 bugs mở | 0 | 0 |
| ADRs merged | 5 | — |
| DB migrations | V26 → V27 (RLS + building cluster) | — |
| Prometheus targets | 7/7 UP | All UP |

---

## 2. Lessons Learned — 4 Sprint

### Điểm mạnh

1. **Strangler Fig pattern hiệu quả** — analytics-service extraction: Shadow → Validate → Cutover. Rollback <5 phút.
2. **Port/Adapter + Capability flag** — ForecastPort với 3 implementations, `@ConditionalOnProperty(matchIfMissing=true)` bảo vệ Tier 1.
3. **SA Spikes pre-sprint** — ADR-032 completed trước Sprint 4 tiết kiệm 2 SP và giải quyết 6 blockers sớm.
4. **Observability-first** — Prometheus + Grafana deploy cùng feature code, không phải sau.
5. **Evidence-based decisions** — LSTM NO-GO dựa trên MAPE 18.65% vs ARIMA 3.37%.

### Điểm cần cải thiện

1. **API contract drift** — Frontend/Backend mock mismatch phát hiện muộn. Cần contract testing (Pact) sớm hơn.
2. **Verify kéo dài** — Sprint 3 code xong 5 ngày nhưng verify mất 5-6 ngày (thiếu automated pipeline).
3. **Hot-copy fragility** — forecast-service code changes chưa build vào Docker image. Cần CI/CD pipeline cho Python.
4. **Keycloak/Backend auth disconnect** — Backend dùng HMAC JWT, Keycloak dùng RS256. RoutingJwtDecoder bridge nhưng gây confusion khi test.

---

## 3. Lộ Trình MVP3 — Còn lại gì?

### 3.1 Theo Detail Plan ban đầu (6 sprint)

Detail Plan gốc dự kiến 6 sprint. **Thực tế Sprint 1-4 đã cover:**

| Gốc Plan | Đã cover? | Sprint thực tế |
|---|---|---|
| S1: Foundation + Multi-Building + ClickHouse + Kong/Keycloak | **90%** | Sprint 1 |
| S2: ClickHouse queries live + analytics cutover + ESG | **85%** (CH HA descoped) | Sprint 2-3 |
| S3: Predictive AI (ARIMA/LSTM) | **100%** | Sprint 4 |
| S4: BMS SDK + Kong prod + iot-service shadow | **0%** — BMS deferred | Chưa |
| S5: Mobile App + iot cutover + Push | **10%** — Web Push only | Chưa |
| S6: Avro + Building Safety + Pilot Prep | **0%** | Chưa |

### 3.2 Original Sprint 4-6 vs Thực tế

**Detail Plan gốc Sprint 4** (BMS SDK + Kong prod + iot-service shadow):
- Đã bị **dịch sang sau** khi PO quyết định ưu tiên AI Foundation + Observability trước
- ADR-029 (BMS Protocol Adapter) vẫn ở trạng thái Proposed
- BMS SDK (Modbus/BACnet) ~21 SP chưa implement

**Detail Plan gốc Sprint 5** (Mobile App + Push + iot cutover):
- Web Push đã có (Sprint 3)
- React Native mobile app ~21 SP chưa implement
- FCM/APNs push backend ~13 SP chưa implement
- iot-ingestion-service extraction ~13 SP chưa implement

**Detail Plan gốc Sprint 6** (Avro + Building Safety + Pilot):
- Schema Registry + Avro ~8 SP chưa implement
- Building Safety (structural monitoring) ~13 SP chưa implement
- Blue-green deployment validation ~5 SP chưa implement

---

## 4. Kế Hoạch Sprint 5-8 (Đề xuất)

### 4.1 Rationale

Dựa trên:
- **City Authority deadline** gần — cần pilot-ready features sớm
- **Tier 2 pilot sign-off** cần: mobile app, BMS integration, production-grade IAM
- **Descope candidates** (nếu cần): Avro migration, Building Safety, KNX adapter
- **Velocity thực tế**: ~40-50 SP/sprint sau 4 sprint

### 4.2 Sprint 5 Đề xuất: Mobile Operator App + iot-service Foundation

**Sprint Goal:** React Native operator app MVP + iot-ingestion-service scaffold + FCM push backend

**Duration:** 2 tuần (~47 SP capacity)

| Epic | Story | SP | Owner | Priority |
|---|---|---|---|---|
| **Mobile App** | React Native + Expo scaffold + shared hooks | 13 | Frontend | P0 |
| **Mobile App** | Keycloak PKCE login + tenant selection | 8 | Frontend + Backend | P0 |
| **Mobile App** | Dashboard + Alerts (polling) | 13 | Frontend | P1 |
| **Push Backend** | FCM + APNs push notification service | 13 | Backend | P0 |
| **iot-service** | Scaffold `applications/iot-ingestion-service/` | 5 | Backend Lead | P1 |
| **iot-service** | @ConditionalOnProperty + shadow mode | 5 | Backend + DevOps | P1 |
| **QA** | Mobile test plan + push SLA validation | 5 | QA | P1 |

**Gate:** APK build success, push notification <5s, iot-service shadow stable 48h

### 4.3 Sprint 6 Đề xuất: BMS SDK + Kong Production + Mobile v1.1

**Sprint Goal:** BMS Modbus/BACnet integration + Kong production TLS + Mobile control panel

| Epic | Story | SP | Owner | Priority |
|---|---|---|---|---|
| **BMS** | BMS SDK (Modbus TCP + BACnet/IP) | 21 | Backend | P0 |
| **BMS** | Device Discovery + auto-register | 8 | Backend | P1 |
| **Kong** | Production TLS + rate-limiting hardening | 5 | DevOps | P0 |
| **Keycloak** | Realm hardening + LDAP connector | 5 | DevOps | P1 |
| **Mobile** | Manual Control Panel (actuator commands) | 8 | Frontend | P1 |
| **QA** | BMS integration test (30 scenarios) | 5 | QA | P1 |

**Gate:** BMS 10+ devices polling, Kong TLS + alg=none CI, APK + IPA builds

### 4.4 Sprint 7 Đề xuất: iot-service Cutover + Building Safety

**Sprint Goal:** iot-ingestion-service production cutover + structural monitoring MVP

| Epic | Story | SP | Owner | Priority |
|---|---|---|---|---|
| **iot-service** | Cutover (double-ingest prevention) | 3 | DevOps | P0 |
| **iot-service** | BMS spike test 5K polls/sec | 5 | QA + DevOps | P0 |
| **Safety** | Structural Monitoring (Flink CEP + Welford) | 13 | Backend | P1 |
| **Safety** | Safety UI (sensor grid + alert banner) | 8 | Frontend | P1 |
| **Migration** | V29 device_push_tokens + V30 structural rules | 2 | Backend | P1 |

**Gate:** iot-service 100% traffic, structural alerts <15s, zero duplicates

### 4.5 Sprint 8 Đề xuất: Avro Migration + Pilot Readiness

**Sprint Goal:** Avro Schema Registry + blue-green deploy + full regression + City Authority demo

| Epic | Story | SP | Owner | Priority |
|---|---|---|---|---|
| **Avro** | Schema Registry (Apicurio) + 4 topics migrated | 8 | Backend Lead | P1 |
| **DevOps** | Blue-green deploy + Istio + rollback <30s | 5 | DevOps | P0 |
| **QA** | Full regression 100+ scenarios | 5 | QA | P0 |
| **QA** | Performance gate: k6 sustained 30 phút | 5 | QA | P0 |
| **Demo** | Executive demo script + City Authority dry-run | 3 | PM + All | P0 |
| **Docs** | Runbook + pilot deployment guide | 3 | DevOps + Backend | P1 |

**Gate (PILOT READINESS):**
- Avro backward compat CI green
- Blue-green rollback <30s
- 100+ regression PASS
- OWASP 0 Critical
- Executive demo approved
- Pilot runbook reviewed

---

## 5. Descope Candidates (nếu velocity thấp)

| Feature | Action | Lý do |
|---|---|---|
| Building Safety (Structural Monitoring) | → v3.1 post-pilot | Không critical cho pilot demo |
| BMS KNX adapter | → chỉ Modbus + BACnet cho pilot | KNX ít phổ biến tại HCMC pilot sites |
| Avro Migration | → giữ JSON nếu Sprint 8 overloaded | JSON ổn định, Avro là optimization |
| Mobile iOS (IPA) | → Android APK first | iOS cert delay rủi ro cao |
| Schema Registry (Apicurio) | → defer nếu Avro descoped | Phụ thuộc Avro decision |

---

## 6. Timeline Tổng Hợp

```
Sprint 1-4 (05-12 → 05-27): ✅ COMPLETE
  ├── Foundation: RLS, ClickHouse, Kong, Keycloak, Flink
  ├── ESG: GRI 302/305 export, Keycloak RSA
  ├── AI: ARIMA forecast (MAPE 3.54%), LSTM NO-GO
  └── Observability: Prometheus + Grafana 8 panels

Sprint 5 (06-02 → 06-13): Mobile App + Push + iot-service Foundation
Sprint 6 (06-16 → 06-27): BMS SDK + Kong Prod + Mobile v1.1
Sprint 7 (06-30 → 07-11): iot Cutover + Building Safety
Sprint 8 (07-14 → 07-25): Avro + Pilot Readiness

PILOT TARGET: 2026-08-01 → 2026-08-10
```

---

## 7. Metrics Dashboard — 4 Sprint

```
                    Sprint 1    Sprint 2    Sprint 3    Sprint 4
Delivered SP:       69          54          40.5        42
Gate PASS:          69/70       PASS        5/6 AC      19/19
Tests:              773         864         849 PASS    978+
Coverage LINE:      ~85%        ~87%        93.3%       87.7%
Coverage BRANCH:    ~60%        ~65%        56.8%       71.4%
ADRs merged:        4           0           1           1
P0/P1 bugs:         0           0           0           0
Services healthy:   12          16          20          22
MAPE (forecast):    N/A         N/A         N/A         3.54%
```

---

## 8. Khuyến Nghị cho PO

1. **Demo Sprint 4 cho City Authority** — ARIMA forecast + Grafana observability + ESG GRI export là 3 features showcase tốt nhất.

2. **Ưu tiên Sprint 5** — Mobile app + Push notification là gap lớn nhất hiện tại. City Authority operators cần mobile access.

3. **BMS Sprint 6 có thể parallel với Mobile** — BMS SDK là Backend-heavy, Mobile là Frontend-heavy → team split hiệu quả.

4. **Pilot readiness không cần Avro** — Nếu velocity thấp, Avro + Schema Registry có thể defer sang post-pilot. JSON topics hoạt động ổn định.

5. **Building Safety có thể descoped** — Structural monitoring không phải yêu cầu bắt buộc cho ESG reporting pilot. Defer sang v3.1.

---

*Báo cáo tổng hợp: 2026-05-27*
*Sprint 1-4: COMPLETE | Sprint 5-8: PLANNED*
*Next: PO demo Sprint 4 → Sprint 5 planning*
