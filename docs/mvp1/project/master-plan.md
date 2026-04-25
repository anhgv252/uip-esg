# UIP Smart City — Master Plan v2.0

 **Phiên bản:** 2.0 (Approved)  
 **Ngày cập nhật:** 28/03/2026  
 **Giai đoạn:** POC → Product Development  
 **Trạng thái:** APPROVED ✅

---

## 1. Bối cảnh & Chuyển đổi

### 1.1 Từ POC → Product

POC `uip-esg-poc` đã **validate thành công** toàn bộ pipeline kỹ thuật:

```
IoT Devices → Kafka → Redpanda Connect (NGSI-LD) → Flink → TimescaleDB → FastAPI
```

Kết quả POC (100K messages, 23 integration tests pass) được lưu trữ tại `poc/`.

**Quyết định chuyển sang Product:** Hệ thống sẽ được phát triển thành sản phẩm hoàn chỉnh, không còn là POC. Toàn bộ stack kỹ thuật được nâng cấp để đáp ứng yêu cầu production.

### 1.2 Thay đổi kiến trúc so với POC

| Thành phần | POC | Product |
|---|---|---|
| IoT Broker | Kafka Producer script | **EMQX CE** (MQTT/HTTP/CoAP) |
| IoT Platform | — | **ThingsBoard CE** (Device Management) |
| Stream Processing | **PyFlink** (Python) | **Java Flink** (production-grade) |
| Backend API | FastAPI (Python) | **Spring Boot** (Java) |
| DB Connection (UAT) | psycopg2 direct | **HikariCP** (embedded, Spring Boot default) |
| DB Connection (Prod) | — | **PgBouncer** + HikariCP (multi-instance scale-out) |
| Notification | — | **SSE** (in-app, extensible to email/push) |
| Auth | Không có | **JWT** + Spring Security (RBAC) |
| Traffic Data | — | **HTTP Adapter** (external system fake data) |
| CI/CD | Không có | GitHub Actions (sau) |

---

## 2. Platform Scope (MVP1)

### 2.1 Modules

| Module | Mô tả | Ưu tiên |
|---|---|---|
| **IoT Foundation** | EMQX + ThingsBoard CE + Kafka pipeline | P0 - Core |
| **Environment Module** | AQI, nhiệt độ, độ ẩm, CO2, cảm biến môi trường | P0 - Core |
| **ESG Module** | Điện, nước, carbon tracking + báo cáo tự động | P0 - Core |
| **Alert System** | Threshold detection → notification <30s | P0 - Core |
| **City Operations Center** | Bản đồ real-time, sensor overlay, alert feed | P0 - Core |
| **Traffic Module** | HTTP adapter từ hệ thống ngoài (fake data) | P1 |
| **Citizen Portal** | Tài khoản cư dân, hộ khẩu, tiện ích, thông báo | P1 |
| **AI Workflow Engine** | **Camunda 7** (embedded) + BPMN + Claude API, 7 scenarios | P1 |
| **Notification Service** | SSE in-app, extensible (email, push) | P0 - Core |
| **Auth/RBAC** | JWT, 3 roles: Admin / Operator / Citizen | P0 - Core |

### 2.2 Citizen Portal — Scope chi tiết

Đây là ứng dụng dành cho **cư dân đô thị**:

| Tính năng | Mô tả |
|---|---|
| Đăng ký tài khoản | Gắn với hộ khẩu/căn hộ (building → floor → zone) |
| Thông tin hộ gia đình | Khai báo nhân khẩu, liên kết đồng hồ điện/nước |
| Hóa đơn & thanh toán | Xem hóa đơn điện, nước, phí dịch vụ |
| Nhận thông báo | Alert môi trường (AQI, lũ lụt) + thông báo dịch vụ |
| Yêu cầu dịch vụ | Báo cáo sự cố, yêu cầu sửa chữa |

### 2.3 AI Workflow Scenarios (7 scenarios)

**Dành cho Cư dân (3):**
| # | Scenario | Trigger |
|---|---|---|
| AI-C01 | Cảnh báo chất lượng không khí cho cư dân | AQI > ngưỡng nguy hiểm |
| AI-C02 | Xử lý yêu cầu dịch vụ cư dân tự động | Citizen gửi request |
| AI-C03 | Thông báo khẩn cấp & hướng dẫn sơ tán | Sensor ngập lũ |

**Dành cho Ban quản lý (4):**
| # | Scenario | Trigger |
|---|---|---|
| AI-M01 | Phản ứng cảnh báo lũ lụt — điều phối khẩn cấp | Water level sensor |
| AI-M02 | AQI vượt ngưỡng — kiểm soát giao thông & công trình | AQI > 150 |
| AI-M03 | Sự cố tiện ích — điều phối bảo trì | Sensor anomaly |
| AI-M04 | Báo cáo ESG bất thường — điều tra tự động | ESG threshold breach |

---

## 3. Architecture (Product — UAT Docker)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    UIP Smart City — Product Architecture (UAT)          │
│                                                                         │
│  IoT Layer                                                              │
│  ┌─────────────┐    MQTT/HTTP    ┌──────────────────────────────────┐  │
│  │ IoT Devices │ ──────────────▶│  EMQX CE (MQTT Broker, :1883)   │  │
│  │ BMS Sensors │                └──────────────┬───────────────────┘  │
│  │ Cameras     │                               │ MQTT bridge           │
│  │ Third-party │                ┌──────────────▼───────────────────┐  │
│  └─────────────┘                │  ThingsBoard CE (:8080)          │  │
│                                 │  Device Registry, Rule Engine    │  │
│  Traffic (External)             └──────────────┬───────────────────┘  │
│  ┌─────────────┐  HTTP adapter                 │ Kafka Rule Engine     │
│  │ Fake Data   │ ──────────────────────────────┤                      │
│  │ Generator   │                               │                      │
│  └─────────────┘                               │                      │
│                                                ▼                      │
│  ┌─────────────────────────────────────────────────────────────────┐  │
│  │  Apache Kafka 3.7 KRaft                                        │  │
│  │  UIP.iot.sensor.reading.v1          (raw → Redpanda Connect)   │  │
│  │  UIP.environment.aqi.threshold-exceeded.v1  (Flink → alert)   │  │
│  │  UIP.flink.alert.detected.v1        (Flink → backend)          │  │
│  │  UIP.esg.report.generated.v1        (async report done)        │  │
│  │  uip.dlq.v1                         (dead letter queue)        │  │
│  └──────────────────────────┬──────────────────────────────────────┘  │
│                             │                                          │
│  ┌──────────────────────────▼──────────────────────────────────────┐  │
│  │  Redpanda Connect (Benthos ETL)                                 │  │
│  │  • Vendor normalization → NGSI-LD                               │  │
│  │  • Per-module topic routing                                     │  │
│  │  • HTTP adapter for Traffic data                                │  │
│  └──────────────────────────┬──────────────────────────────────────┘  │
│                             │                                          │
│  ┌──────────────────────────▼──────────────────────────────────────┐  │
│  │  Apache Flink (Java) — Multi-job                                │  │
│  │  ├─ EnvironmentFlinkJob  → env schema (AQI, sensors)            │  │
│  │  ├─ EsgFlinkJob          → esg schema (energy/water/carbon)     │  │
│  │  ├─ TrafficFlinkJob      → traffic schema                       │  │
│  │  └─ AlertDetectionJob    → alerts schema (threshold events)     │  │
│  └──────────────────────────┬──────────────────────────────────────┘  │
│                             │                                          │
│  ┌──────────────────────────▼──────────────────────────────────────┐  │
│  │  TimescaleDB (:5432)  [UAT: HikariCP direct]                   │  │
│  │  [Prod: PgBouncer proxy → TimescaleDB]                         │  │
│  │  schema: esg          clean_metrics, aggregate_metrics          │  │
│  │  schema: environment  sensor_readings, aqi_index                │  │
│  │  schema: traffic      traffic_counts, incidents                 │  │
│  │  schema: alerts       alert_events, notification_log            │  │
│  │  schema: citizens     accounts, households, meters, invoices    │  │
│  │  schema: error_mgmt   error_records (từ POC, giữ nguyên)        │  │
│  └──────────────────────────┬──────────────────────────────────────┘  │
│                             │                    Redis (:6379)         │
│                             │                    ├─ Alert pub/sub      │
│                             │                    └─ Session cache      │
│                             │                                          │
│  ┌──────────────────────────▼──────────────────────────────────────┐  │
│  │  Backend API — Spring Boot (Java)                               │  │
│  │  Modular Monolith → Microservices-ready                         │  │
│  │  + Camunda 7 (embedded BPM engine)                              │  │
│  │  /api/v1/environment   /api/v1/esg   /api/v1/traffic            │  │
│  │  /api/v1/alerts        /api/v1/citizen  /api/v1/workflow        │  │
│  │  /api/v1/auth          /api/v1/admin                            │  │
│  └──────────────────────────┬──────────────────────────────────────┘  │
│                             │  SSE / WebSocket                         │
│  ┌──────────────────────────▼──────────────────────────────────────┐  │
│  │  Frontend — React 18 + TypeScript                               │  │
│  │  ├─ City Operations Center (Leaflet, real-time SSE)             │  │
│  │  ├─ Environment Dashboard (AQI gauges, trend charts)            │  │
│  │  ├─ ESG Dashboard (energy/water/carbon + report download)       │  │
│  │  ├─ Traffic Dashboard (vehicle counts, incidents)               │  │
│  │  ├─ AI Workflow Dashboard (bpmn-js + Camunda + Claude decisions)│  │
│  │  ├─ Alert Management (operator review)                          │  │
│  │  └─ Citizen Portal (account, bills, notifications)              │  │
│  └─────────────────────────────────────────────────────────────────┘  │
│                                                                        │
│  Monitoring: Flink Dashboard (:8081) | Kafka UI (:8080) | TB (:8080) │
└────────────────────────────────────────────────────────────────────────┘
```

### 3.1 Module Boundary Rules (Phase 1 — enforced)

Mỗi package = 1 bounded context. Quy tắc bắt buộc:

1. **No cross-module imports** — module `alert` không được import class từ `auth`, `citizen`, v.v.
2. **Giao tiếp sync** — chỉ qua Port Interface (Java interface trong `common`) hoặc REST API contract
3. **Giao tiếp async** — chỉ qua Kafka topic theo naming convention `UIP.{module}.{entity}.{event-type}.v{n}`
4. **No cross-module DB query** — module không được query schema của module khác; dùng event hoặc API
5. **acknowledgedBy, assignedTo, v.v.** — lưu username String, không lưu User entity UUID (tránh coupling với auth)

> Danh sách topics và biến môi trường được quản lý tập trung tại:
> - `docs/deployment/kafka-topic-registry.xlsx` — topic registry (producer/consumer/class/groupId)
> - `docs/deployment/environment-variables.xlsx` — env vars per module (Backend/Frontend/Flink)

### 3.2 Backend Structure (Spring Boot Modular Monolith)

```
backend/
├── build.gradle
├── src/main/java/com/uip/backend/
│   ├── UipApplication.java
│   ├── common/          # shared: exception, pagination, response wrapper
│   ├── auth/            # JWT auth, RBAC, user management
│   ├── environment/     # Environment module (sensors, AQI)
│   ├── esg/             # ESG module (energy/water/carbon, reports)
│   ├── traffic/         # Traffic module (counts, incidents)
│   ├── alert/           # Alert engine + rules + Kafka consumer
│   │   ├── domain/      # AlertEvent, AlertRule entities
│   │   ├── api/         # REST controllers + DTOs
│   │   ├── service/     # AlertService, AlertEngine
│   │   ├── repository/  # AlertEventRepository, AlertRuleRepository
│   │   └── kafka/       # AlertEventKafkaConsumer
│   ├── notification/    # SSE emitter, Redis pub/sub subscriber
│   ├── citizen/         # Citizen portal accounts, households, invoices
│   ├── workflow/        # Camunda 7 + AI workflow engine
│   └── admin/           # Admin panel APIs
```

### 3.3 Tech Stack

| Layer | Technology | Version |
|---|---|---|
| IoT MQTT Broker | EMQX CE | 5.x |
| IoT Platform | ThingsBoard CE | 3.x |
| Message Broker | Apache Kafka KRaft | 3.7 |
| ETL | Redpanda Connect (Benthos) | latest |
| Stream Processing | **Apache Flink (Java)** | 1.19 |
| Database | TimescaleDB (PostgreSQL 16) | latest-pg16 |
| Connection Pool (UAT) | **HikariCP** (Spring Boot embedded) | default |
| Connection Pool (Prod) | **PgBouncer** + HikariCP | 1.22 |
| Cache / PubSub | Redis | 7.x |
| Backend | **Spring Boot** | 3.2 |
| ORM | Spring Data JPA + JDBC | — |
| Security | Spring Security + JWT | — |
| Workflow Engine | **Camunda 7 Community** (embedded) | 7.x |
| Frontend | React 18 + TypeScript | — |
| UI Components | MUI (Material UI) | 5.x |
| Map | Leaflet + React-Leaflet | — |
| Charts | Recharts | — |
| BPMN Viewer | bpmn-js | — |
| Real-time | SSE (Server-Sent Events) | — |
| AI | Claude API (claude-sonnet-4-6) | — |
| Testing (BE) | JUnit 5 + Mockito + Testcontainers | — |
| Testing (E2E) | Playwright | — |

---

## 4. Delivery Plan — 4 Sprints (28/03 → 28/05/2026)

### Sprint 1: Platform Foundation (28/03 → 11/04)

**Sprint Goal:** "Toàn bộ data pipeline từ EMQX → Kafka → Flink Java → TimescaleDB hoạt động; Spring Boot API với JWT auth trả dữ liệu cơ bản"

| ID | Story | Points | Owner |
|---|---|---|---|
| S1-01 | EMQX CE + ThingsBoard CE Docker setup, Kafka Rule Engine bridge | 8 | Backend |
| S1-02 | Multi-schema TimescaleDB + PgBouncer configuration | 5 | Backend |
| S1-03 | Java Flink base project: EnvironmentFlinkJob (replace PyFlink) | 8 | Backend |
| S1-04 | Spring Boot base project: JWT auth, RBAC (Admin/Operator/Citizen) | 8 | Backend |
| S1-05 | Redpanda Connect extended: per-module routing + HTTP traffic adapter | 5 | Backend |
| S1-06 | Docker Compose full stack (EMQX + ThingsBoard + Redis + tất cả) | 5 | DevOps |
| S1-07 | React base app: routing, MUI theme, auth pages | 5 | Frontend |
| S1-08 | EsgFlinkJob + AlertDetectionJob (Java) | 8 | Backend |
| **Total** | | **52 SP** | |

**DoD Sprint 1:** EMQX nhận MQTT → dữ liệu vào TimescaleDB qua Java Flink → API `/api/v1/environment` trả data với JWT token

---

### Sprint 2: Environment + ESG + Alert (11/04 → 25/04)

**Sprint Goal:** "AQI alert tự động khi vượt ngưỡng, cư dân nhận SSE notification; ESG report generate <10 phút"

| ID | Story | Points | Owner |
|---|---|---|---|
| S2-01 | Environment module API: sensor readings, AQI calculation (EPA standard) | 8 | Backend |
| S2-02 | Alert engine: per-measure-type rules (YAML config), threshold detection | 8 | Backend |
| S2-03 | Notification service: SSE endpoint, Redis pub/sub, alert dispatch | 8 | Backend |
| S2-04 | ESG module API: energy/water/carbon aggregation, quarterly report gen | 8 | Backend |
| S2-05 | Environment Dashboard: AQI gauge, sensor map layer, trend charts | 8 | Frontend |
| S2-06 | ESG Dashboard: KPI cards, time-series charts, report download | 5 | Frontend |
| S2-07 | Data Quality UI: error review workflow (từ POC) | 3 | Frontend |
| S2-08 | TrafficFlinkJob (Java) + skeleton Traffic API | 5 | Backend |
| **Total** | | **53 SP** | |

**DoD Sprint 2:** AQI > ngưỡng → SSE push tới operator dashboard <30s; ESG quarterly PDF generate được

---

### Sprint 3: City Operations + Traffic + Citizen Portal (25/04 → 09/05)

**Sprint Goal:** "City Operations Center hiển thị real-time map; Cư dân đăng ký tài khoản, xem hóa đơn và nhận notification"

| ID | Story | Points | Owner |
|---|---|---|---|
| S3-01 | City Operations Center: Leaflet map, sensor overlay, real-time SSE feed | 13 | Frontend |
| S3-02 | Alert management UI: acknowledge, escalate, operator notes | 5 | Frontend |
| S3-03 | Traffic module: HTTP fake-data adapter + Traffic Dashboard | 8 | Backend+FE |
| S3-04 | Citizen account: đăng ký, gắn hộ khẩu (building/floor/zone) | 8 | Backend |
| S3-05 | Citizen utility: liên kết đồng hồ điện/nước, xem hóa đơn | 8 | Backend |
| S3-06 | Citizen Portal UI: dashboard, bills, alert subscription | 8 | Frontend |
| S3-07 | Admin panel: user management, sensor registry, system config | 5 | Frontend |
| S3-08 | Integration test suite: ThingsBoard → dashboard end-to-end | 5 | QA |
| **Total** | | **60 SP** | |

**DoD Sprint 3:** Cư dân login → xem hóa đơn tháng; Map hiển thị 10+ sensors live; Alert <30s verified

---

### Sprint 4: AI Workflow + UAT Preparation (09/05 → 23/05)

**Sprint Goal:** "7 AI workflow scenarios hoạt động với Claude API; hệ thống đạt 2,000 msg/s; sẵn sàng UAT"

| ID | Story | Points | Owner |
|---|---|---|---|
| S4-01 | Camunda 7 setup (embedded Spring Boot) + BPMN process definitions | 8 | Backend |
| S4-02 | AI Decision node: Claude API integration vào Camunda Service Task | 8 | Backend |
| S4-00 | AI Workflow Dashboard: bpmn-js viewer + process instance monitoring | 5 | Frontend |
| S4-03 | 3 Citizen AI scenarios (AI-C01, C02, C03) | 8 | Backend |
| S4-04 | 4 Management AI scenarios (AI-M01, M02, M03, M04) | 8 | Backend |
| S4-05 | Performance test: 2,000 msg/s sustained (Java Flink + HikariCP) | 5 | QA |
| S4-06 | Security hardening: secrets via env vars, remove hardcoded passwords | 3 | Backend |
| S4-07 | UAT Docker Compose + deployment guide | 3 | DevOps |
| S4-08 | Regression test suite (extend POC 23 tests + new modules) | 5 | QA |
| S4-09 | Bug fixes & polish từ Sprint 1–3 | 8 | All |
| **Total** | | **56 SP** | |

**Buffer (23/05 → 28/05):** UAT issues, final sign-off, demo prep

---

## 5. KPIs & Acceptance Criteria

| KPI | Target | Measurement |
|---|---|---|
| Alert latency (sensor → SSE notification) | **<30 giây** | E2E test tự động |
| Sensor data ingestion (Java Flink) | **≥2,000 msg/s** | JMeter load test |
| API latency (p95) | **<200ms** | Load test |
| ESG report generation | **<10 phút** | Manual test |
| Test coverage (backend) | **≥75%** | JUnit report |
| P0/P1 bugs tại UAT sign-off | **0 / <3** | Bug tracker |
| AI workflow response time | **<10 giây** | Integration test |
| 7 AI scenarios hoạt động | **100%** | Manual test |

---

## 6. Risk Register

| ID | Risk | Xác suất | Tác động | Mitigation |
|---|---|---|---|---|
| R-01 | ThingsBoard ↔ Kafka Rule Engine config phức tạp | M | H | Sprint 1 buffer; fallback: direct MQTT → Kafka bridge không qua TB |
| R-02 | Java Flink migration mất nhiều thời gian hơn PyFlink | M | H | Bắt đầu Sprint 1 ngay; reuse POC SQL logic, chỉ đổi runtime |
| R-03 | Scope 7 AI scenarios + Citizen Portal quá lớn | H | H | Phân loại P0/P1; Sprint 4 có thể carryover sang buffer week |
| R-04 | EMQX + ThingsBoard CE compatibility issues | L | M | Test integration ngày đầu Sprint 1 |
| R-05 | Real-time map performance với nhiều sensors | M | M | Cluster markers + viewport lazy loading |
| R-06 | Claude API latency ảnh hưởng workflow | M | M | Async non-blocking; timeout 10s; không block alert path |
| R-07 | HikariCP pool exhaustion dưới tải cao | L | M | Config maxPoolSize phù hợp; PgBouncer cho production |
| R-08 | Camunda 7 embedded conflict với Spring Boot version | L | M | Dùng camunda-bpm-spring-boot-starter 7.x official |

---

## 7. Cấu trúc Thư mục (Product)

```
uip-esg-poc/               ← root (sẽ rename thành uip-platform)
├── poc/                   ← POC gốc (archived)
│   ├── README.md
│   ├── docker-compose.yml
│   ├── flink/ (PyFlink)
│   ├── esg-api/ (FastAPI)
│   └── ...
├── backend/               ← Spring Boot (Java)
│   └── src/main/java/com/uip/
├── flink-jobs/            ← Apache Flink (Java)
│   └── src/main/java/com/uip/esg/
├── frontend/              ← React + TypeScript
├── infrastructure/        ← Docker Compose + configs
│   ├── docker-compose.yml ← Full stack UAT
│   ├── emqx/
│   ├── thingsboard/
│   ├── kafka/
│   ├── timescaledb/
│   └── redis/
├── docs/
│   ├── architecture/      ← Architecture docs
│   ├── project/           ← Master plan, sprint reports
│   └── api/               ← API documentation
└── .claude/               ← AI agents configuration
```

---

## 8. Sprint Ceremonies

| Ceremony | Thời điểm | Mục đích |
|---|---|---|
| Sprint Planning | Đầu mỗi sprint | PO xác nhận goal + stories |
| Sprint Review | Cuối mỗi sprint | Demo tính năng + feedback |
| Sprint Retrospective | Cuối mỗi sprint | Cải tiến process |

---

## 9. Timeline Tổng quan

```
28/03 ────── SPRINT 1 ────── 11/04
             Platform Foundation
             EMQX + ThingsBoard + Java Flink + Spring Boot + Auth

11/04 ────── SPRINT 2 ────── 25/04
             Environment + ESG + Alert + SSE Notification
             AQI alerts, ESG reports, Core dashboards

25/04 ────── SPRINT 3 ────── 09/05
             City Ops Center + Traffic + Citizen Portal
             Real-time map, Resident accounts, Bills

09/05 ────── SPRINT 4 ────── 23/05
             AI Workflow (7 scenarios) + UAT Prep
             BPMN + Claude API, Performance tests

23/05 ────── BUFFER/UAT ──── 28/05
             Bug fixes, sign-off, demo
```

---

## Appendix A: Quyết định của Product Owner

| # | Quyết định | Nội dung |
|---|---|---|
| PO-00 | Workflow Engine | **Camunda 7 Community** (embedded Spring Boot) |
| PO-01 | Hướng phát triển | **Product** (không còn POC) |
| PO-02 | Stream Processing | **Java Flink** (thay PyFlink) |
| PO-03 | Backend | **Spring Boot Java** (thay FastAPI Python) |
| PO-04 | Connection Pool (UAT) | **HikariCP** embedded (Spring Boot default) |
| PO-04b | Connection Pool (Prod) | **PgBouncer** + HikariCP (khi scale multi-instance) |
| PO-05 | IoT Platform | **EMQX CE + ThingsBoard CE** (cả hai bản free) |
| PO-06 | Traffic Data | **HTTP Adapter** (fake data từ hệ thống ngoài) |
| PO-07 | Notification | **SSE in-app** (extensible: email, push notification) |
| PO-08 | Citizen Account | **Bắt buộc** — gắn hộ khẩu, hóa đơn tiện ích |
| PO-09 | AI Scenarios | **7 scenarios** (3 citizen + 4 management) |
| PO-10 | Data Residency | On-Premise, đảm bảo tự động |
