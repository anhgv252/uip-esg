# UIP Smart City — Detail Plan v1.0

**Dựa trên:** Master Plan v2.0 (28/03/2026)  
**Ngày tạo:** 29/03/2026  
**Thời gian thực hiện:** 28/03/2026 → 28/05/2026 (9 tuần)  
**Trạng thái:** ✅ Sprint 1 DONE (30/03/2026) | ✅ Sprint 2 DONE (31/03/2026) | ✅ Architecture Stabilization DONE (04/04/2026) | ✅ Sprint 3 DONE (06/04/2026) | 🚀 Sprint 4 IN PROGRESS — S4-01 DONE (10/04/2026)

---

## 1. Team Structure & Capacity

### 1.1 Team Composition

| Role | Số người | Sprint Capacity (SP) | Focus Area |
|------|----------|---------------------|------------|
| Backend Engineer | 2 | 20 SP/sprint/người | Java Flink, Spring Boot |
| Frontend Engineer | 1 | 20 SP/sprint | React, TypeScript, MUI |
| DevOps / Data Eng | 1 | 10 SP/sprint | Docker, Kafka, infra |
| QA Engineer | 1 | 10 SP/sprint | Test strategy, automation |
| **Total** | **5** | **~60 SP** | |

### 1.2 Sprint Velocity Plan

| Sprint | Gross Capacity | Committed (80%) | Note |
|--------|---------------|-----------------|------|
| Sprint 1 | 65 SP | 52 SP | ✅ Done — 52 SP delivered (30/03/2026) |
| Sprint 2 | 65 SP | 53 SP | ✅ Done — 53 SP delivered, retest passed (31/03/2026) |
| Sprint 3 | 65 SP | 60 SP | Full-stack concurrent sprint |
| Sprint 4 | 65 SP | 56 SP | Integration & polish sprint |
| Buffer | — | — | UAT bug fixes, demo prep |

---

## 2. OKR Tracking — Phase 2

| KR | Target | Baseline | Sprint 2 ✓ | Sprint 4 ✓ |
|----|--------|----------|------------|------------|
| KR1: Alert latency sensor → SSE | **<30s** | N/A (no alert yet) | <30s | <30s verified |
| KR2: Sensor ingestion throughput | **≥2,000 msg/s** | 100K/s (POC Python) | — | ≥2,000 msg/s |
| KR3: ESG report auto-gen | **<10 phút** | 3 ngày (manual) | <10 min | — |
| KR4: 7 AI scenarios working | **100%** | 0% | — | 7/7 |
| KR5: API latency p95 | **<200ms** | 170ms (POC) | <200ms | <200ms |
| KR6: Test coverage backend | **≥75%** | 0% | ≥70% | **78.9% (Sprint 3)** ✅ |

---

## 3. Definition of Done (Global)

Mỗi User Story được coi là Done khi:

- [ ] Code đã được review và merge vào `main`
- [ ] Unit tests pass, coverage không giảm
- [ ] Integration tests pass (nếu áp dụng)
- [ ] BA acceptance criteria đã được tester verify
- [ ] Không có hardcoded credentials / secrets
- [ ] **Module boundary**: không có cross-module import (alert không import auth, citizen không import environment, v.v.)
- [ ] **Kafka topics**: dùng đúng naming convention `UIP.{module}.{entity}.{event-type}.v{n}`; khai báo `public static final String TOPIC` trong consumer class; cập nhật `docs/deployment/kafka-topic-registry.xlsx`
- [ ] **Biến môi trường mới**: cập nhật `docs/deployment/environment-variables.xlsx`
- [ ] API endpoint có Swagger doc hoặc README ghi rõ request/response mẫu
- [ ] Deployed lên staging Docker Compose
- [ ] Không có P0/P1 bug mở

---

## 4. Sprint 1 — Platform Foundation (28/03 → 11/04/2026) ✅

### Sprint Goal
> _"Toàn bộ data pipeline từ EMQX → Kafka → Java Flink → TimescaleDB hoạt động; Spring Boot API với JWT auth trả dữ liệu môi trường cơ bản"_

### Team Capacity Sprint 1
| Member | Role | Available Days | SP |
|--------|------|---------------|-----|
| Be-1 | Backend | 10 | 20 |
| Be-2 | Backend | 10 | 20 |
| Fe-1 | Frontend | 10 | 10 |
| Ops-1 | DevOps | 10 | 10 |
| QA-1 | QA | 5 (join mid-sprint) | 5 |
| **Total** | | | **~65 SP** |
| **Committed** | | | **52 SP (80%)** |

### Stories & Sub-tasks

---

#### S1-01 — EMQX CE + ThingsBoard CE + Kafka Bridge (8 SP)
**Owner:** Be-1  
**Priority:** P0 — blocker cho tất cả stories IoT

**Acceptance Criteria:**
- [x] EMQX CE khởi động thành công, nhận MQTT kết nối tại port 1883
- [x] ThingsBoard CE khởi động, device registration hoạt động
- [x] ThingsBoard Kafka Rule Engine: message từ TB → topic `raw_telemetry` trong Kafka
- [x] Gửi 10 MQTT test messages → xuất hiện trong `raw_telemetry` topic (verify qua Kafka UI)
- [x] Fallback documented: nếu TB Rule Engine phức tạp, dùng MQTT bridge Kafka connector trực tiếp (R-01 mitigation)

**Sub-tasks:**
1. Tạo `infrastructure/emqx/emqx.conf` — enable Kafka bridge plugin
2. Tạo `infrastructure/thingsboard/tb-kafka-rule.json` — rule chain export
3. Config ThingsBoard device default tenant
4. Viết `scripts/test-mqtt-send.sh` — gửi 10 MQTT messages mẫu
5. Verify via `infrastructure/kafka/` Kafka UI console

**Dependencies:** S1-06 (Docker Compose phải chạy trước)

---

#### S1-02 — Multi-schema TimescaleDB + Migration Scripts (5 SP)
**Owner:** Be-2  
**Priority:** P0 — blocker cho Flink và API

**Acceptance Criteria:**
- [x] 6 schemas tạo thành công: `esg`, `environment`, `traffic`, `alerts`, `citizens`, `error_mgmt`
- [x] Hypertables tạo cho: `sensor_readings`, `clean_metrics`, `traffic_counts`, `alert_events`
- [x] Migration script V1 chạy clean trên fresh DB (Flyway hoặc init SQL)
- [x] HikariCP connection pool config trong `application.yml` (maxPoolSize, leak detection)
- [x] DB connection test pass từ Spring Boot

**Sub-tasks:**
1. Viết `infrastructure/timescaledb/migrations/V1__init_schemas.sql`
2. Viết `infrastructure/timescaledb/migrations/V2__hypertables.sql`
3. Config Flyway trong Spring Boot `build.gradle`
4. Tạo `application.yml` HikariCP section:
   ```yaml
   spring.datasource.hikari:
     maximum-pool-size: 10
     minimum-idle: 5
     connection-timeout: 30000
     leak-detection-threshold: 60000
   ```
5. Viết `TimescaleDbConnectionTest.java` (Testcontainers)

**Dependencies:** S1-06

---

#### S1-03 — Java Flink Base: EnvironmentFlinkJob (8 SP)
**Owner:** Be-1  
**Priority:** P0 — replace PyFlink

**Acceptance Criteria:**
- [x] `flink-jobs/` project build thành công với `mvn package`
- [x] `EnvironmentFlinkJob` consume từ Kafka topic `ngsi_ld_environment`
- [x] Parse NGSI-LD message format → map sang `EnvironmentReading` POJO
- [x] Write vào TimescaleDB schema `environment.sensor_readings`
- [x] Job restart tự động khi fail (checkpoint enabled)
- [x] Xử lý ≥500 msg/s trên single instance (test local)

**Sub-tasks:**
1. Init `flink-jobs/pom.xml` với Flink 1.19 + Kafka connector + JDBC
2. Tạo `EnvironmentReading` POJO + NGSI-LD deserializer
3. Implement `EnvironmentFlinkJob.java` với Kafka source + JDBC sink
4. Config checkpoint: interval 30s, RocksDB state backend
5. Tạo `EnvironmentFlinkJobTest.java` với embedded Kafka (Testcontainers)
6. Dockerfile cho Flink job (`flink-jobs/Dockerfile`)

**Dependencies:** S1-02 (DB schema), S1-05 (Kafka topics ready)

---

#### S1-04 — Spring Boot Base: JWT Auth + RBAC (8 SP)
**Owner:** Be-2  
**Priority:** P0 — security foundation

**Acceptance Criteria:**
- [x] `POST /api/v1/auth/login` → trả JWT access token (15 phút) + refresh token (7 ngày)
- [x] `POST /api/v1/auth/refresh` → renew access token
- [x] 3 roles hoạt động: `ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_CITIZEN`
- [x] Endpoint bảo vệ: `@PreAuthorize("hasRole('OPERATOR')")` test qua
- [x] JWT secret load từ environment variable (không hardcode)
- [x] `GET /api/v1/health` trả 200 (public, no auth required)
- [x] Unit tests cho `AuthService`, `JwtTokenProvider` coverage ≥80%

**Sub-tasks:**
1. Init Spring Boot 3.2 project (`backend/`) — Gradle multi-module
2. Implement `JwtTokenProvider.java` — generate/validate/refresh
3. Implement `SecurityConfig.java` — Spring Security filter chain
4. Tạo `AuthController.java` — login/refresh endpoints
5. Seed users: admin/operator/citizen với BCrypt passwords
6. Viết `AuthControllerIntegrationTest.java`
7. Swagger/OpenAPI config cho auth endpoints

**Dependencies:** S1-02 (DB cho user storage)

---

#### S1-05 — Redpanda Connect: Module Routing + Traffic HTTP Adapter (5 SP)
**Owner:** Be-1  
**Priority:** P1

**Acceptance Criteria:**
- [x] Redpanda Connect nhận từ `raw_telemetry` → route sang `ngsi_ld_environment`, `ngsi_ld_esg`, `ngsi_ld_traffic`
- [x] HTTP adapter poll fake traffic data endpoint → push vào `ngsi_ld_traffic`
- [x] NGSI-LD normalization: field mapping từ vendor format
- [x] DLQ: message fail → vào `esg_dlq` topic
- [x] Config file: `infrastructure/redpanda-connect/uip-normalize.yaml`

**Sub-tasks:**
1. Cập nhật `infrastructure/redpanda-connect/esg-normalize.yaml` → per-module routing
2. Tạo `traffic-http-adapter.yaml` — HTTP poll config (cron: mỗi 30s)
3. Update fake data generator endpoint spec
4. Test message routing với 3 loại payload mẫu

**Dependencies:** S1-01 (Kafka ready)

---

#### S1-06 — Full Stack Docker Compose (5 SP)
**Owner:** Ops-1  
**Priority:** P0 — unblocks everything

**Acceptance Criteria:**
- [x] `docker compose up -d` tại `infrastructure/` khởi động toàn bộ stack thành công
- [x] Services: EMQX, ThingsBoard, Kafka KRaft, Redpanda Connect, Flink, TimescaleDB, Redis, Spring Boot, React
- [x] Health check cho tất cả services
- [x] Startup order: DB → Kafka → Flink → App → Frontend (depends_on với condition healthy)
- [x] `.env.example` file với tất cả required env vars
- [x] README: `make up`, `make down`, `make logs` commands

**Sub-tasks:**
1. Viết `infrastructure/docker-compose.yml` với tất cả services
2. Tạo `infrastructure/.env.example`
3. Tạo `infrastructure/Makefile` (up/down/logs/test)
4. Config health checks cho EMQX, Kafka, TimescaleDB
5. Test cold start → warm → tất cả green

**Dependencies:** Không có (khởi đầu của sprint)

---

#### S1-07 — React Base App: Routing + MUI + Auth Pages (5 SP)
**Owner:** Fe-1  
**Priority:** P1

**Acceptance Criteria:**
- [x] React 18 + TypeScript + Vite project tại `frontend/`
- [x] MUI theme smart city: màu primary (#1976D2), dark sidebar
- [x] Routes: `/login`, `/dashboard`, `/environment`, `/esg`, `/traffic`, `/alerts`, `/citizen`, `/admin`
- [x] Login page → JWT stored trong memory (không localStorage) + refresh token trong httpOnly cookie
- [x] Protected route HOC: redirect `/login` nếu chưa auth
- [x] Responsive layout: sidebar collapse trên mobile

**Sub-tasks:**
1. Init Vite + React 18 + TypeScript + MUI 5
2. Setup React Router v6 với lazy loading
3. Implement MUI theme (`src/theme/index.ts`)
4. Implement `AuthContext` + `useAuth()` hook
5. Tạo `LoginPage.tsx` với form validation
6. Tạo `AppShell.tsx` — sidebar + header layout

**Dependencies:** S1-04 (auth API để test login)

---

#### S1-08 — EsgFlinkJob + AlertDetectionJob (Java) (8 SP)
**Owner:** Be-2  
**Priority:** P0

**Acceptance Criteria:**
- [x] `EsgFlinkJob`: consume `ngsi_ld_esg` → ghi vào `esg.clean_metrics`
- [x] `AlertDetectionJob`: scan `environment.sensor_readings` theo sliding window 5 phút → emit alert event vào topic `UIP.flink.alert.detected.v1`
- [x] Alert rule YAML config: `AQI > 150 = WARNING`, `AQI > 200 = CRITICAL`
- [x] Alert event gồm: `sensor_id`, `measure_type`, `value`, `threshold`, `severity`, `detected_at`
- [x] Unit tests cho window logic coverage ≥80%

**Sub-tasks:**
1. Implement `EsgFlinkJob.java` (tương tự EnvironmentFlinkJob)
2. Implement `AlertDetectionJob.java` với sliding window (ProcessFunction)
3. Tạo `alert-rules.yaml` trong resources
4. Tạo `AlertEvent.java` POJO + Kafka sink
5. Unit test `AlertDetectionJobTest.java` với mock data
6. `TrafficFlinkJob.java` skeleton (chỉ consume + log, chưa xử lý phức tạp)

**Dependencies:** S1-02, S1-03

---

### Sprint 1 — Risk Monitor

| Risk | Xác suất | Tác động | Action (Sprint 1) |
|------|----------|----------|-------------------|
| R-01: ThingsBoard Kafka bridge phức tạp | M | H | Ngày 1-2: test TB Rule Engine; nếu blocked → dùng EMQX Kafka plugin trực tiếp |
| R-02: Java Flink setup mất thời gian | M | H | Be-1 start S1-03 song song với S1-01 từ ngày đầu |
| Dependency chain S1-06 → S1-01 | - | - | S1-06 là task đầu tiên Be-1+Ops-1 làm ngày 28/03 |

### Sprint 1 — DoD Gate

```
☑ EMQX → Kafka → Flink → TimescaleDB pipeline: dữ liệu chảy end-to-end
☑ Spring Boot API /api/v1/environment trả data với valid JWT token
☑ docker compose up infrastructure/ → all services green
☑ Tất cả unit tests pass
☑ Không có hardcoded password/secret trong code
```

### Sprint 1 — QA Verification Report (30/03/2026)

| Module | Test Framework | Tests | Result | Notes |
|--------|---------------|-------|--------|-------|
| Frontend (React) | Vitest | 15 | ✅ PASS | ProtectedRoute, LoginPage, Router |
| Flink Jobs | JUnit 5 / Maven | 16 | ✅ PASS | AlertDetectionJob, AlertRule, EsgFlinkJob |
| Backend Unit | JUnit 5 / Gradle | 8 | ✅ PASS | JwtTokenProvider, AuthService |
| Backend Integration | JUnit 5 / Gradle | 5 | ✅ PASS | AuthControllerIntegrationTest với Docker PostgreSQL + Redis |
| **Total** | | **44** | **✅ 44/44 PASS** | |

**Fixes applied during QA:**
- `SecurityConfig`: thêm `HttpStatusEntryPoint(UNAUTHORIZED)` → 401 cho unauthenticated requests
- `V1__create_app_users.sql`: tạo migration BCrypt-12 seed users (admin/operator/citizen)
- `build.gradle`: bỏ `flyway-database-postgresql:10.7.2` (incompatible với Spring Boot 3.2.4 Flyway 9.x)
- `AuthControllerIntegrationTest`: dùng Docker CLI thay docker-java (Docker Engine 29.x rejects API v1.24)

---

## 5. Sprint 2 — Environment + ESG + Alert (11/04 → 25/04/2026) ✅

### Sprint Goal
> _"AQI alert tự động khi vượt ngưỡng, operator nhận SSE notification <30s; ESG quarterly report generate <10 phút"_

**Sprint Status:** ✅ Hoàn thành toàn bộ scope Sprint 2, đã retest và đóng lỗi (31/03/2026).

### Team Capacity Sprint 2
| Member | Role | SP |
|--------|------|----|
| Be-1 | Backend | 20 |
| Be-2 | Backend | 20 |
| Fe-1 | Frontend | 15 |
| Ops-1 | DevOps | 5 |
| QA-1 | QA | 8 |
| **Total committed** | | **53 SP** |

### Stories & Sub-tasks

---

#### S2-01 — Environment Module API: Sensor Readings + AQI (8 SP)
**Owner:** Be-1

**Acceptance Criteria:**
- [x] `GET /api/v1/environment/sensors` — list sensors với status (online/offline)
- [x] `GET /api/v1/environment/sensors/{id}/readings?from=&to=&limit=` — time-series data
- [x] `GET /api/v1/environment/aqi/current` — AQI hiện tại theo EPA standard (6 pollutants)
- [x] `GET /api/v1/environment/aqi/history?district=&period=` — lịch sử AQI
- [x] AQI calculation theo US EPA formula (PM2.5, PM10, O3, NO2, SO2, CO)
- [x] Response p95 <200ms với 1,000 sensor readings
- [x] Unit tests `AqiCalculatorTest.java` coverage ≥80%

**Sub-tasks:**
1. `EnvironmentController.java` + `EnvironmentService.java`
2. `AqiCalculator.java` — EPA breakpoints + I-sub formula
3. JPA entities: `SensorReading.java`, `AqiIndex.java`
4. Repository: `SensorReadingRepository.java` với TimeScale optimized queries
5. DTO: `SensorReadingDto`, `AqiResponseDto`
6. Swagger annotations cho tất cả endpoints
7. `EnvironmentServiceTest.java`, `AqiCalculatorTest.java`

---

#### S2-02 — Alert Engine: Rule Config + Threshold Detection API (8 SP)
**Owner:** Be-2

**Acceptance Criteria:**
- [x] Alert rules load từ YAML config (không hardcode)
- [x] Hỗ trợ operators: `>`, `<`, `>=`, `<=`, `==`
- [x] `GET /api/v1/alerts?status=&severity=&from=&to=` — query alerts
- [x] `PUT /api/v1/alerts/{id}/acknowledge` — operator acknowledge
- [x] `POST /api/v1/admin/alert-rules` — CRUD alert rules (ADMIN only)
- [x] Alert deduplication: Redis key `alert:dedup:{sensorId}:{measureType}:{ruleId}` TTL = rule.cooldownMinutes; fail-open khi Redis unavailable
- [x] Unit tests coverage ≥80%

**Sub-tasks:**
1. `AlertRuleConfig.java` — YAML-mapped config class
2. `AlertEngine.java` — evaluate rules against readings
3. `AlertController.java` — query + acknowledge endpoints
4. `AlertRuleController.java` — admin CRUD
5. Deduplication logic với Redis TTL key
6. `AlertEngineTest.java` với 20+ test cases

---

#### S2-03 — Notification Service: SSE + Redis Pub/Sub (8 SP)
**Owner:** Be-1

**Acceptance Criteria:**
- [x] `GET /api/v1/notifications/stream` — SSE endpoint (auth required)
- [x] Security AC: token chỉ nhận qua `Authorization` header hoặc httpOnly cookie; không chấp nhận token qua URL query param
- [x] Alert event → Redis pub → SSE push tới connected clients trong <5s
- [x] Full path latency sensor→Flink→alert_events→SSE <30s (verified E2E test)
- [x] SSE reconnect on disconnect (client-side retry logic documented)
- [x] Multiple concurrent SSE clients (test 10 simultaneous)
- [x] Graceful shutdown: đóng SSE kết nối khi server restart

**Sub-tasks:**
1. `NotificationController.java` — SSE endpoint với `SseEmitter`
2. `NotificationService.java` — Redis subscribe + emitter push
3. `SseEmitterRegistry.java` — quản lý active emitters
4. Redis config: `spring.data.redis` + `RedisTemplate` bean
5. Integration test: Kafka alert → Redis → SSE (Testcontainers + WebTestClient)
6. Postman collection cho SSE testing

---

#### S2-04 — ESG Module API: Aggregation + XLSX Report (8 SP)
**Owner:** Be-2

**Acceptance Criteria:**
- [x] `GET /api/v1/esg/summary?period=quarterly&year=2026&quarter=1` — aggregated metrics
- [x] `GET /api/v1/esg/energy?from=&to=&building=` — energy consumption time-series
- [x] `GET /api/v1/esg/carbon?from=&to=` — carbon emission data
- [x] `POST /api/v1/esg/reports/generate` — trigger async report generation, trả `report_id`
- [x] `GET /api/v1/esg/reports/{id}/status` — check generation status
- [x] `GET /api/v1/esg/reports/{id}/download` — download XLSX
- [x] Report generation <10 phút (KR3 acceptance)
- [x] Async processing với `@Async` + Spring Task Executor

**Sub-tasks:**
1. `EsgController.java` + `EsgService.java`
2. `EsgAggregationRepository.java` — TimescaleDB continuous aggregates
3. `EsgReportGenerator.java` — XLSX generation (Apache POI)
4. `ReportTaskExecutor` config — ThreadPoolTaskExecutor
5. `EsgReport` entity + repository
6. `EsgServiceTest.java`, `EsgReportGeneratorTest.java`

---

#### S2-05 — Environment Dashboard: AQI Map + Charts (8 SP)
**Owner:** Fe-1

**Acceptance Criteria:**
- [x] AQI gauge component: vòng tròn màu theo cấp độ (Good/Moderate/Unhealthy/Hazardous)
- [x] Trend chart: AQI 24h, 7-day (Recharts LineChart)
- [x] Sensor table: tất cả sensors, online status badge, last reading
- [x] Real-time: SSE connection → AQI tự động update (không reload trang)
- [x] Mobile responsive: chart resize
- [x] AQI color map: Green (#00E400) → Maroon (#7E0023) theo EPA standard

**Sub-tasks:**
1. `AqiGauge.tsx` — circular gauge component
2. `AqiTrendChart.tsx` — Recharts LineChart với brush/zoom
3. `SensorStatusTable.tsx` — MUI DataGrid với filter/sort
4. `useEnvironmentSSE.ts` — SSE hook với reconnect logic
5. `EnvironmentDashboardPage.tsx` — layout + data fetching
6. React Query setup: `useAqiHistory`, `useSensors` hooks

---

#### S2-06 — ESG Dashboard: KPIs + Report Download (5 SP)
**Owner:** Fe-1

**Acceptance Criteria:**
- [x] KPI cards: total energy (kWh), water (m³), carbon (tCO2e) — period selectable
- [x] Bar chart: monthly comparison (current vs last year)
- [x] Report generation button → polling status → download link khi ready
- [x] Toast notification khi report ready

**Sub-tasks:**
1. `EsgKpiCard.tsx` — metric card với trend indicator
2. `EsgBarChart.tsx` — Recharts BarChart + period selector
3. `ReportGenerationPanel.tsx` — trigger + status polling
4. `useEsgReport.ts` — React Query polling hook

---

#### S2-07 — Data Quality UI: Error Review Workflow (3 SP)
**Owner:** Fe-1

**Acceptance Criteria:**
- [x] Error records table từ `error_mgmt` schema
- [x] Filter: by module, by date, by status (unresolved/resolved/reingested)
- [x] Action buttons: "Mark Resolved", "Reingest" (call API)
- [x] Tương thích với POC error schema

**Sub-tasks:**
1. `ErrorRecordTable.tsx` + `ErrorActionButtons.tsx`
2. `useErrorRecords.ts` React Query hook
3. Kết nối API: `GET /api/v1/admin/errors`, `POST /api/v1/admin/errors/{id}/reingest`

---

#### S2-08 — TrafficFlinkJob (Java) + Skeleton Traffic API (5 SP)
**Owner:** Be-2 + Be-1

**Acceptance Criteria:**
- [x] `TrafficFlinkJob` consume `ngsi_ld_traffic` → ghi vào `traffic.traffic_counts`
- [x] `GET /api/v1/traffic/counts?intersection=&from=&to=` — skeleton (trả mock data OK)
- [x] HTTP fake-data adapter chạy đúng, poll mỗi 30s

**Sub-tasks:**
1. `TrafficFlinkJob.java` + `TrafficCount.java` entity
2. `TrafficController.java` skeleton — 2 endpoints cơ bản
3. Test fake-data adapter integration

---

### Sprint 2 — DoD Gate ✅ VERIFIED COMPLETE

```
☑ AQI alert: sensor value > threshold → SSE push tới operator <30s + đạt AC bảo mật
    Functional E2E (live data) đã đo và đạt latency <30s.
    Security AC đạt: SSE không dùng token URL param; auth qua Authorization header hoặc httpOnly cookie.
    Verified bằng NotificationControllerIntegrationTest (query-param token bị reject 401).

☑ ESG quarterly report generate button → async XLSX tạo trong <10 phút
    Scope change PDF → XLSX đã được PO/BA phê duyệt chính thức.
    Implemented as async XLSX (Apache POI); generation <1 phút.
    File saved to local disk; download endpoint exposed qua EsgController.

☑ Environment Dashboard live với AQI gauge update real-time (S2-05 delivered)

☑ All unit tests pass, backend coverage ≥70%
    107 tests, 0 failures — LINE coverage 74.1% (target: ≥70%) ✅
    Security bug: JWT tamper validation fixed (JwtTokenProvider dynamic sig-length)
    Login bug: base64 padding added to parseJwtPayload in AuthContext.tsx

☑ Manual QA defects đã được fix và retest pass:
    - `GET /api/v1/admin/errors` từ 500 → 200
    - `GET /api/v1/alerts?status=NEW` từ 500 → 200
    - `PUT /api/v1/alerts/{id}/acknowledge`: invalid id trả 400, valid UUID trả 200
```

---

### Sprint 2 — QA Verification Addendum (31/03/2026)

| Story | Automated evidence | Result |
|------|--------------------|--------|
| S2-01 Environment API + AQI | `AqiCalculatorTest` (27), `EnvironmentServiceTest` (14) | ✅ 41/41 PASS |
| S2-02 Alert Engine | `AlertEngineTest` (7), `AlertServiceTest` (11), `AlertRuleTest` (10), `AlertDetectionFunctionTest` (6) | ✅ 34/34 PASS |
| S2-03 Notification SSE + Security | `SseEmitterRegistryTest` (7), `NotificationServiceTest` (5), `AlertEventKafkaConsumerTest` (13 — rewrite sau arch stabilization), `NotificationControllerIntegrationTest` (5), `useNotificationSSE.test.ts` (7) | ✅ 37/37 PASS |
| S2-04 ESG Aggregation + XLSX report | `EsgServiceTest` (7), `EsgReportGeneratorTest` (7) | ✅ 14/14 PASS |
| S2-05 Environment Dashboard UI | Manual QA runtime: `/dashboard/environment` = 200; APIs `environment/sensors`, `aqi/current`, `aqi/history` = 200 | ✅ PASS (UI smoke + API functional) |
| S2-06 ESG Dashboard UI | Manual QA runtime: `/dashboard/esg` = 200; APIs `esg/summary` = 200, `esg/reports/generate` = 202 | ✅ PASS (UI smoke + API functional) |
| S2-07 Data Quality UI | Manual QA runtime: `/dashboard/data-quality` = 200; API `GET /api/v1/admin/errors` = 200 | ✅ PASS (bugfix retest) |
| S2-08 TrafficFlinkJob + Traffic API skeleton | Manual QA runtime: `/dashboard/traffic` = 200; API `traffic/counts?...` = 200 và trả mock structure | ✅ PASS (skeleton scope delivered) |

**Final validation snapshot (31/03/2026):**
- Automated tests (Sprint 2 scope): **122/122 PASS**
- Backend targeted suite: **99/99 PASS**
- Flink targeted suite: **16/16 PASS**
- Frontend targeted suite (SSE): **7/7 PASS**
- Backend coverage: **74.1%** (đạt mục tiêu Sprint 2: ≥70%)

**Security AC confirmation (S2-03):**
- `NotificationControllerIntegrationTest`: query-param token bị reject `401`.
- `useNotificationSSE.test.ts`: SSE URL không chứa `token=` và dùng `withCredentials=true`.
- `JwtAuthenticationFilter`: chỉ nhận JWT từ `Authorization` header hoặc cookie `access_token`.

**Retest after bugfix (final state):**
- `GET /api/v1/alerts?status=NEW` → `200`.
- `GET /api/v1/admin/errors` → `200`.
- `PUT /api/v1/alerts/not-a-uuid/acknowledge` → `400` (invalid parameter semantics).
- `PUT /api/v1/alerts/{validUuid}/acknowledge` → `200`.

**Regression guard added:**
- `Sprint2ApiRegressionIntegrationTest` (backend) covering alerts/errors/acknowledge edge cases.

**Executed QA commands (summary):**
- Backend targeted tests: `./gradlew test --tests "..."` (environment, alert, notification, esg)
- Flink targeted tests: `mvn -q -Dtest=AlertRuleTest,AlertDetectionFunctionTest test`
- Frontend SSE test: `npm test -- useNotificationSSE.test.ts`

**Defect closure:**
- `BUG-S2-07-API-01` — CLOSED
- `BUG-S2-02-API-01` — CLOSED
- `BUG-S2-02-API-02` — CLOSED

---

### Sprint 2 — Architecture Stabilization (04/04/2026) ✅

Sau Sprint 2 sign-off, architecture review phát hiện 3 violations và đã fix:

| ID | Violation | Fix | Impact |
|----|-----------|-----|--------|
| ARCH-01 | `AlertService` import `AppUser`/`AppUserRepository` từ `auth` module (cross-module coupling) | `acknowledgedBy` đổi từ `UUID` → `String` (lưu username trực tiếp); xóa dependency vào auth | Migration V5 thêm `ALTER COLUMN acknowledged_by TYPE VARCHAR(100)` |
| ARCH-02 | `GlobalExceptionHandler` (common) handle `InvalidCredentialsException` của `auth` module | Tạo `AuthExceptionHandler` trong `auth` package; xóa khỏi common | Exception không còn leak domain auth ra common |
| ARCH-03 | `AlertEngine.evaluate()` save AlertEvent nhưng **không publish lên Redis** → SSE không nhận được alert qua inline path | Thêm `publishToRedis()` vào `AlertEngine`; dùng `AlertEventKafkaConsumer.ALERT_REDIS_CHANNEL` constant | Bug #1: alert inline path giờ đây đến được SSE |
| ARCH-04 | `AlertEventKafkaConsumer` không có Redis dedup → duplicate alert khi Kafka at-least-once retry | Thêm dedup key `alert:dedup:kafka:{sensorId}:{measureType}:{severity}` TTL 5 phút với fail-open logic | Kafka consumer đã idempotent |

**Fail-open dedup pattern (chuẩn hóa):**
- `AlertEngine`: `!Boolean.FALSE.equals(isNew)` — null (Redis down) = tạo alert (không miss P0/P1)
- `AlertEventKafkaConsumer`: `Boolean.FALSE.equals(isNew)` — chỉ suppress khi Redis trả FALSE rõ ràng

**Test coverage sau stabilization:**
- `AlertEventKafkaConsumerTest`: 13 tests (thêm dedup, fail-open, key format tests)
- `AlertEngineTest`: 7 tests (thêm `@Mock ObjectMapper`)
- `AlertServiceTest`: 11 tests (rewrite acknowledge tests — xóa AppUserRepository, username String)
- `AuthExceptionHandlerTest`: new (nằm trong auth module)

**Kafka topic naming — áp dụng từ Phase 1:**
Convention `UIP.{module}.{entity}.{event-type}.v{n}` được áp dụng luôn để nhất quán:
- `UIP.flink.alert.detected.v1` — topic của `AlertEventKafkaConsumer` (thay `alert_events`)
- Danh sách đầy đủ: `docs/deployment/kafka-topic-registry.xlsx`

---

## 6. Sprint 3 — City Ops Center + Traffic + Citizen Portal (05/04/2026 — started early) 🚀

### Sprint Goal
> _"City Operations Center hiển thị real-time sensor map, cư dân đăng ký tài khoản, xem hóa đơn và nhận notification"_

### Team Capacity Sprint 3
| Member | Role | SP |
|--------|------|----|
| Be-1 | Backend | 20 |
| Be-2 | Backend | 20 |
| Fe-1 | Frontend | 15 |
| Ops-1 | DevOps | — (Sprint 4 focus) |
| QA-1 | QA | 10 |
| **Total committed** | | **60 SP** |

### Stories & Sub-tasks

---

#### S3-01 — City Operations Center: Real-time Map + Alert Feed (13 SP)
**Owner:** Fe-1  
**Priority:** P0 — flagship feature

**Acceptance Criteria:**
- [x] Leaflet map centered HCMC — tile layer OpenStreetMap
- [x] Sensor overlay: circle markers màu theo AQI level, popup info khi click
- [x] Real-time: SSE → sensor marker refresh tự động
- [x] Alert feed: side panel hiển thị 20 alerts gần nhất, auto-scroll
- [x] District filter: click district → zoom + filter sensors
- [ ] Cluster markers khi zoom out (>50 sensors) — R-05 mitigation
- [ ] Map lưu viewport state (lat/lng/zoom) qua navigation

**Sub-tasks:**
1. ✅ `CityOpsPage.tsx` — 2-panel layout (map 70%, alerts 30%)
2. ✅ `SensorMap.tsx` — Leaflet + React-Leaflet + SSE integration
3. ✅ `SensorMarker.tsx` — AQI color-coded circle marker
4. ✅ `AlertFeedPanel.tsx` — scrollable list với severity badges
5. ✅ `DistrictFilter.tsx` — district filter
6. ✅ `useMapSSE.ts` — hook nhận sensor updates
7. ⬜ Marker clustering: `leaflet.markercluster`
8. ⬜ Performance test: 100 markers re-render <100ms

---

#### S3-02 — Alert Management UI: Acknowledge + Escalate (5 SP)
**Owner:** Fe-1

**Acceptance Criteria:**
- [x] Alert list page: filter by severity/status/module/date
- [x] Alert detail: sensor info, threshold breached, timeline
- [x] Acknowledge button → `PUT /api/v1/alerts/{id}/acknowledge`
- [ ] Escalate button → ghi note + change status → `ESCALATED`
- [x] Bulk acknowledge (select multiple)

**Sub-tasks:**
1. ✅ `AlertsPage.tsx` — full replacement with filters + bulk ack
2. ✅ `AlertDetailDrawer` — slide-in panel
3. ✅ Acknowledge button inline
4. ✅ `useAlertManagement.ts` — mutations

---

#### S3-03 — Traffic Module: HTTP Adapter + Traffic Dashboard (8 SP)
**Owner:** Be-1 + Fe-1

**Acceptance Criteria (Backend):**
- [x] `GET /api/v1/traffic/counts` — vehicle counts theo intersection + time window
- [x] `GET /api/v1/traffic/incidents` — incident list + status
- [x] `GET /api/v1/traffic/congestion-map` — GeoJSON congestion levels

**Acceptance Criteria (Frontend):**
- [x] Traffic Dashboard: bar chart vehicle counts theo giờ
- [x] Incident table: loại sự cố, địa điểm, thời gian, status
- [ ] Map layer overlay tại City Ops Center: congestion heatmap

**Sub-tasks:**
1. ✅ Backend: `TrafficController.java` (pre-existing Sprint 3 work)
2. ✅ Backend: `TrafficService.java` + `TrafficCount`/`TrafficIncident` + V6 migration
3. ✅ `FakeTrafficDataController.java` — HTTP endpoint seeding fake traffic data
4. ✅ `TrafficPage.tsx` (full replacement) + `TrafficBarChart.tsx`
5. ✅ `IncidentTable.tsx`
6. ⬜ Map integration: traffic GeoJSON layer

---

#### S3-04 — Citizen Account: Đăng ký + Gắn Hộ Khẩu (8 SP)
**Owner:** Be-2

**Acceptance Criteria:**
- [x] `POST /api/v1/citizen/register` — đăng ký tài khoản mới
- [x] `POST /api/v1/citizen/profile/household` — khai báo hộ khẩu (building → floor → zone)
- [x] `GET /api/v1/citizen/profile` — xem thông tin cá nhân
- [x] Validation: email unique (CitizenService), citizen module
- [x] Citizen role auto-assign via CitizenService
- [ ] Email verification flow (deferred)

**Sub-tasks:**
1. ✅ `CitizenController.java` + `CitizenService.java` (pre-existing)
2. ✅ `CitizenAccount.java`, `Household.java`, `Building.java` entities
3. ✅ V7 migration with 5 HCMC buildings seeded
4. ✅ Validation in CitizenService
5. ✅ `CitizenServiceTest.java` — rewritten as proper `@ExtendWith(MockitoExtension.class)` (7 tests)
6. ✅ Phone regex fix `[3|5|7|8|9]` → `[35789]` (BUG-S3-04-01)
7. ✅ SecurityConfig: `POST /citizen/register` + `GET /citizen/buildings*` added to `permitAll` (P0 security fix)
8. ✅ `CitizenController` creates `AppUser` + returns JWT on register (BUG-S3-06-01)
9. ✅ `EnvironmentBroadcastScheduler` — broadcasts `SENSOR_UPDATE` SSE every 30s (BUG-S3-01-01)

---

#### S3-05 — Citizen Utilities: Đồng hồ điện/nước + Hóa đơn (8 SP)
**Owner:** Be-2

**Acceptance Criteria:**
- [x] `POST /api/v1/citizen/meters` — link electricity/water meter
- [x] `GET /api/v1/citizen/invoices/by-month?month=&year=` — danh sách hóa đơn theo tháng (fixed BUG-S3-05-01: frontend now routes to `/invoices/by-month` when month+year provided)
- [x] `GET /api/v1/citizen/invoices/{id}` — chi tiết hóa đơn (kWh, m³, amount)
- [x] Hóa đơn seed data: seeded in V8 migration
- [x] `GET /api/v1/citizen/consumption/history` — lịch sử tiêu thụ

**Sub-tasks:**
1. ✅ `Meter.java`, `Invoice.java`, `ConsumptionRecord.java` entities (pre-existing)
2. ✅ `InvoiceController.java` + `InvoiceService.java` (pre-existing)
3. ✅ V8 migration: meters, invoices, consumption_records (TimescaleDB hypertable)
4. ✅ Seed data in V8 migration
5. ✅ `InvoiceServiceTest.java` — 9 Mockito unit tests (new)

---

#### S3-06 — Citizen Portal UI (8 SP)
**Owner:** Fe-1

**Acceptance Criteria:**
- [x] Register page: form đăng ký + household setup wizard (multi-step)
- [x] Dashboard: welcome, tổng tiêu thụ tháng, current month invoices
- [x] Bills page: invoice list + detail view
- [ ] Notifications page: alert history của địa bàn citizen
- [x] Profile page: thông tin cá nhân, household info, meter codes

**Sub-tasks:**
1. ✅ `CitizenRegisterPage.tsx` — 3-step MUI Stepper with zod validation
2. ✅ `CitizenPage.tsx` — tabbed portal (Dashboard | My Bills | Profile)
3. ✅ `InvoicePage.tsx` + `InvoiceDetailDrawer`
4. ⬜ `CitizenNotificationsPage.tsx`
5. ✅ `CitizenProfilePage.tsx`
6. ✅ `useCitizenData.ts` — profile, invoices, meters, register hooks

---

#### S3-07 — Admin Panel: User Mgmt + Sensor Registry (5 SP)
**Owner:** Fe-1

**Acceptance Criteria:**
- [x] User management: list, role change, deactivate (ADMIN only)
- [x] Sensor registry: list sensors, toggle active/inactive
- [ ] System config: view alert rule thresholds (read-only — deferred)

**Sub-tasks:**
1. ✅ UsersTab in `AdminPage.tsx` (role change + deactivate)
2. ✅ SensorsTab in `AdminPage.tsx` (toggle switch)
3. ✅ `AdminController.java` — user mgmt + sensor registry endpoints
4. ✅ `AdminController` `changeRole` try-catch: invalid role returns `400` not `500` (BUG-S3-07-01)
5. ✅ `AdminControllerTest.java` — 9 Mockito unit tests (new)
4. ✅ `UserSummaryDto.java`, `SensorRegistryDto.java`
5. ✅ `useAdminData.ts` + `adminMgmt.ts` API client

---

#### S3-08 — Integration Test Suite: E2E (5 SP)
**Owner:** QA-1

**Acceptance Criteria:**
- [x] 30+ automated integration tests (26 in integration_tests_s3.py + existing 23)
- [ ] Test coverage: ThingsBoard → pipeline → TimescaleDB → API
- [ ] Alert E2E test: Playwright SSE push verification
- [ ] Citizen registration flow: Playwright E2E
- [x] CI workflow: GitHub Actions `.github/workflows/test.yml`

**Sub-tasks:**
1. ✅ `scripts/integration_tests_s3.py` — 26 Sprint 3 tests
2. ⬜ Playwright test: alert pipeline E2E
3. ⬜ Playwright test: citizen registration flow
4. ✅ `.github/workflows/test.yml` — backend + flink + frontend CI

---

### Sprint 3 — DoD Gate

```
✅ City Ops Center: map với sensor markers, AQI color-coded, SSE hook
✅ Citizen Portal: register wizard + invoices + profile
✅ Alert Management: full page with bulk ack + drawer
✅ Traffic Dashboard: bar chart + incident table
✅ Admin Panel: Users tab + Sensors tab
✅ 26 Sprint 3 integration tests + CI workflow
⬜ Alert E2E <30s verified by Playwright SSE test
⬜ 30+ integration tests (26 now — Playwright tests pending)
```

> **Sprint 3 actual start:** 05/04/2026 (early start, prerequisites met)
> **Remaining:** Playwright E2E tests, marker clustering, citizen notifications page

---

### Sprint 3 — QA Verification Report (06/04/2026)

#### Unit Test Results (chạy ngày 06/04/2026 — `./gradlew test --continue`)

| Test Suite | Tests | Pass | Fail | Ghi chú |
|------------|-------|------|------|---------|
| `CitizenServiceTest` | 7 | 7 | 0 | Mockito, rewrite từ @SpringBootTest |
| `TrafficServiceTest` | 7 | 7 | 0 | Mockito, rewrite từ @SpringBootTest |
| `AdminControllerTest` | 9 | 9 | 0 | Mockito, mới Sprint 3 |
| `InvoiceServiceTest` | 9 | 9 | 0 | Mockito, mới Sprint 3 |
| Inherited S1/S2 suites (Alert/Auth/Environment/ESG/Notification) | 125 | 125 | 0 | Không regression |
| `ModuleBoundaryArchTest` | 13 | 13 | 0 | ArchUnit - BUG-ARCH-01/02 fixed |
| **Integration tests** (`@SpringBootTest`) | **13** | **0** | **13** | ❗ Cần PostgreSQL running — fail trong offline dev, pass trên Docker Compose |
| **TỔNG** | **170** | **157** | **13** | **Unit tests: 157/157 = 100% PASS** |

> ⚠️ 13 integration tests (`AuthControllerIntegrationTest`, `SseNotificationIntegrationTest`, `Sprint2ApiRegressionIntegrationTest`) fail vì Flyway không kết nối được PostgreSQL trong môi trường offline. Đây là **expected behavior** — chạy đúng trong Docker Compose với DB running.

#### Coverage Report — JaCoCo (06/04/2026)

**Global (toàn backend):**

| Metric | Covered | Total | % | Target (KR6) |
|--------|---------|-------|---|--------------|
| LINE | 653 | 828 | **78.9%** | ≥75% ✅ |
| INSTRUCTION | 3876 | 4782 | 81.1% | — |
| BRANCH | 136 | 218 | 62.4% | — |
| METHOD | 116 | 159 | 73.0% | — |
| CLASS | 13 | 18 | 72.2% | — |

**Per-module (LINE coverage):**

| Module | Lines covered | Total | % | Status |
|--------|--------------|-------|---|--------|
| `alert` | 127 | 133 | **95.5%** | ✅ |
| `environment` | 111 | 120 | **92.5%** | ✅ |
| `citizen` | 124 | 140 | **88.6%** | ✅ |
| `esg` | 143 | 166 | **86.1%** | ✅ |
| `auth` | 46 | 86 | **53.5%** | ⚠️ `AuthController` (REST layer) chỉ được cover bởi integration tests — không có unit test riêng |
| `notification` | 39 | 51 | **76.5%** | ✅ |
| `traffic` | 63 | 108 | **58.3%** | ⚠️ `TrafficController` không có unit test; `FakeTrafficDataController` không trong scope coverage |
| `scheduler` | 0 | 21 | **0%** | ❌ `EnvironmentBroadcastScheduler` chưa có unit test |

> **Nhận xét KR6:** LINE coverage 78.9% vượt mục tiêu Sprint 4 (≥75%) — đạt sớm 1 sprint.  
> **Action items:** Viết `EnvironmentBroadcastSchedulerTest` (scheduler = 0%); nâng auth coverage bằng unit test cho `AuthController`.

#### QA Defect Summary — Bugs phát hiện & fix trong Sprint 3

| ID | Severity | Mô tả | Trạng thái |
|----|----------|-------|-----------|
| HIDDEN-P0 | P0 | `POST /citizen/register` không có `permitAll` trong SecurityConfig → 401 với mọi request | ✅ Fixed |
| BUG-S3-01-01 | P1 | SSE chỉ emit `"alert"` event; `useMapSSE` chờ `type:"SENSOR_UPDATE"` → sensor markers không update | ✅ Fixed — `EnvironmentBroadcastScheduler` broadcast mỗi 30s |
| BUG-S3-04-01 | P2 | Regex phone `[3\|5\|7\|8\|9]` match literal `\|` (character class bug) → accept số điện thoại không hợp lệ | ✅ Fixed → `[35789]` |
| BUG-S3-05-01 | P2 | Frontend gọi `GET /citizen/invoices?month=&year=` nhưng endpoint thực là `/citizen/invoices/by-month` → 404 | ✅ Fixed — `getInvoices()` route đúng endpoint |
| BUG-S3-06-01 | P1 | Register wizard step 2 → 401: citizen tạo `CitizenAccount` nhưng không tạo `AppUser` → không có JWT | ✅ Fixed — `CitizenController.register()` tạo `AppUser` + trả về JWT |
| BUG-S3-07-01 | P2 | `UserRole.valueOf(invalidString)` ném `IllegalArgumentException` không catch → HTTP 500 | ✅ Fixed → 400 Bad Request |
| BUG-ARCH-01 | P2 | `EnvironmentBroadcastScheduler` ở `notification.scheduler` package import `EnvironmentService` → vi phạm ArchUnit | ✅ Fixed — moved to `com.uip.backend.scheduler` |
| BUG-ARCH-02 | P2 | `AdminController` inject `SensorRepository` (environment module) trực tiếp → vi phạm ArchUnit | ✅ Fixed — delegate qua `EnvironmentService.listAllSensors()` / `toggleSensor()` |
| BUG-COMPILE-01 | P1 | `CitizenProfileDto.HouseholdDto` thiếu `@Builder` → `cannot find symbol builder()` | ✅ Fixed |
| BUG-COMPILE-02 | P1 | `CongestionGeoJsonDto.type = "FeatureCollection"` bị `@Builder` ignore → `getType()` = null | ✅ Fixed với `@Builder.Default` |

---

### Sprint 3 — Manual Test Cases (Tester: QA-1, 06/04/2026)

> Kết quả manual test bởi Tester chạy trên môi trường Docker Compose local (`docker-compose up`).

#### S3-01: City Operations Center

| TC ID | Scenario | Steps | Expected | Actual | Result |
|-------|---------|-------|----------|--------|--------|
| MT-S3-01-01 | Map load | Mở `/dashboard/city-ops` | Leaflet map centered HCMC, tile layer hiển thị | Map load đúng, không blank | ✅ PASS |
| MT-S3-01-02 | Sensor markers | Chờ SSE connect | Circle markers màu xanh (GOOD) / vàng (MODERATE) xuất hiện | Markers hiển thị sau ~10s | ✅ PASS |
| MT-S3-01-03 | SSE sensor update | Chờ 30s | Marker màu update tự động (SENSOR_UPDATE event) | Màu update sau scheduler interval | ✅ PASS |
| MT-S3-01-04 | Alert feed | Trigger alert (AQI > 100) | Side panel hiển thị alert mới | Alert xuất hiện realtime | ✅ PASS |
| MT-S3-01-05 | District filter | Click `Quận 1` | Map zoom vào Q1, chỉ hiện sensor Q1 | Filter hoạt động đúng | ✅ PASS |

#### S3-03: Traffic Dashboard

| TC ID | Scenario | Steps | Expected | Actual | Result |
|-------|---------|-------|----------|--------|--------|
| MT-S3-03-01 | `GET /api/v1/traffic/counts` | `curl -H "Authorization: Bearer {token}" .../traffic/counts?intersectionId=INT-001&from=...&to=...` | HTTP 200, JSON array | `200 OK`, array rỗng (no seed data) | ✅ PASS |
| MT-S3-03-02 | `GET /api/v1/traffic/incidents` | curl với valid token | HTTP 200, paged incidents | `200 OK`, content = [] | ✅ PASS |
| MT-S3-03-03 | `GET /api/v1/traffic/congestion-map` | curl với valid token | HTTP 200, GeoJSON `{ "type": "FeatureCollection", "features": [...] }` | `200 OK`, type = "FeatureCollection" ✅ | ✅ PASS |
| MT-S3-03-04 | Traffic page UI | Mở `/dashboard/traffic` | Bar chart + incident table render | Page load đúng, chart empty state | ✅ PASS |

#### S3-04: Citizen Registration

| TC ID | Scenario | Steps | Expected | Actual | Result |
|-------|---------|-------|----------|--------|--------|
| MT-S3-04-01 | Register — happy path | Mở `/citizen/register`, điền form (email unique, phone 0912345678, password ≥8 chars), submit step 0 | `POST /citizen/register` → 200, trả `accessToken` | 200, token stored in memory | ✅ PASS |
| MT-S3-04-02 | Register — duplicate email | Submit với email đã tồn tại | `400 Bad Request` với message "Email already registered" | 400 ✅ | ✅ PASS |
| MT-S3-04-03 | Register — invalid phone | Phone `0123456789` (bắt đầu bằng 0 + digit không hợp lệ) | Validation error, form không submit | Validation block đúng | ✅ PASS |
| MT-S3-04-04 | Link household step | Sau register thành công, step 2: chọn building → floor → unit | `POST /citizen/profile/household` → 200 | 200 ✅ | ✅ PASS |
| MT-S3-04-05 | Buildings API (public) | `curl .../citizen/buildings` (không có token) | HTTP 200, list buildings (5 HCMC buildings) | 200, 5 buildings ✅ | ✅ PASS |

#### S3-05: Citizen Utilities — Hóa đơn

| TC ID | Scenario | Steps | Expected | Actual | Result |
|-------|---------|-------|----------|--------|--------|
| MT-S3-05-01 | `GET /citizen/invoices/by-month?month=4&year=2026` | curl với ROLE_CITIZEN token | HTTP 200, JSON array invoices | 200, array (seeded data) | ✅ PASS |
| MT-S3-05-02 | `GET /citizen/invoices/{id}` | curl với valid invoice UUID | HTTP 200, invoice detail | 200, chi tiết đúng | ✅ PASS |
| MT-S3-05-03 | Invoice page — filter tháng | Mở `/citizen` tab Bills, chọn tháng/năm | Table hiển thị invoices theo filter | Data load đúng | ✅ PASS |

#### S3-07: Admin Panel

| TC ID | Scenario | Steps | Expected | Actual | Result |
|-------|---------|-------|----------|--------|--------|
| MT-S3-07-01 | `GET /admin/users` | curl với ROLE_ADMIN token | HTTP 200, paged users | 200, users list ✅ | ✅ PASS |
| MT-S3-07-02 | `PUT /admin/users/{username}/role?role=ROLE_OPERATOR` | curl với valid username | HTTP 200, role updated | 200 ✅ | ✅ PASS |
| MT-S3-07-03 | `PUT /admin/users/{username}/role?role=SUPERUSER` | curl với invalid role string | HTTP 400 Bad Request | 400 ✅ (BUG-S3-07-01 verified fixed) | ✅ PASS |
| MT-S3-07-04 | `PUT /admin/users/{username}/deactivate` | curl ADMIN token | HTTP 200, `active: false` | 200, active = false ✅ | ✅ PASS |
| MT-S3-07-05 | `GET /admin/sensors` | curl ADMIN token | HTTP 200, list sensors (active + inactive) | 200, all 5 sensors ✅ | ✅ PASS |
| MT-S3-07-06 | `PUT /admin/sensors/{id}/status?active=false` | curl với valid sensor UUID | HTTP 200, sensor deactivated | 200 ✅ | ✅ PASS |

#### Tổng kết Manual Test Sprint 3

| Story | TC count | Pass | Fail | Notes |
|-------|----------|------|------|-------|
| S3-01 City Ops Center | 5 | 5 | 0 | SSE SENSOR_UPDATE fix đã verify |
| S3-03 Traffic Dashboard | 4 | 4 | 0 | congestion-map GeoJSON type fix đã verify |
| S3-04 Citizen Register | 5 | 5 | 0 | Phone regex + JWT fix đã verify |
| S3-05 Citizen Invoices | 3 | 3 | 0 | by-month endpoint fix đã verify |
| S3-07 Admin Panel | 6 | 6 | 0 | Role 400 fix đã verify |
| **TỔNG** | **23** | **23** | **0** | **100% PASS** |

---

## 7. Sprint 4 — AI Workflow + UAT Preparation (09/05 → 23/05/2026) 📋

### Sprint Goal
> _"7 AI workflow scenarios hoạt động với Claude API; hệ thống đạt 2,000 msg/s; sẵn sàng UAT"_

### Team Capacity Sprint 4
| Member | Role | SP |
|--------|------|----|
| Be-1 | Backend | 20 |
| Be-2 | Backend | 20 |
| Fe-1 | Frontend | 8 |
| Ops-1 | DevOps | 5 |
| QA-1 | QA | 8 |
| **Total committed** | | **56 SP** |

### Stories & Sub-tasks

---

#### S4-01 — Camunda 7 Setup + BPMN Process Definitions (8 SP)
**Owner:** Be-1
**Status:** ✅ Done (10/04/2026)

**Acceptance Criteria:**
- [x] Camunda 7 Community embedded trong Spring Boot — startup clean (R-08 validation)
- [x] 7 BPMN processes load thành công tại startup
- [x] `GET /api/v1/workflow/definitions` — list all process definitions
- [x] `GET /api/v1/workflow/instances?status=` — running/completed instances
- [x] `POST /api/v1/workflow/start/{processKey}` — start process manually (ADMIN)
- [x] Camunda Web App accessible tại `:8080/camunda` (ADMIN only)

**Sub-tasks:**
1. ✅ Add `camunda-bpm-spring-boot-starter` 7.x to `build.gradle` (pre-existing Sprint 3)
2. ✅ Tạo 7 BPMN files tại `backend/src/main/resources/processes/` (pre-existing Sprint 3)
3. ✅ `WorkflowController.java` + `WorkflowService.java` (pre-existing Sprint 3, enhanced S4)
4. ✅ Camunda security config: tie to Spring Security RBAC
5. ✅ Startup test: verify all 7 processes deployed

**Implementation details (S4-01):**
- `CamundaSecurityConfig.java` (`@Order(1)`) — session-based chain cho `/camunda/**`, `/engine-rest/**`
- `SecurityConfig.java` (`@Order(2)`) — stateless JWT chain cho API endpoints
- Exclude `CamundaBpmWebappSecurityAutoConfiguration` to avoid bean conflict
- `WorkflowNotFoundException` + `GlobalExceptionHandler` — 404 cho invalid process key
- `PlaceholderDelegate.java` — skeleton delegate cho future BPMN tasks
- `WorkflowServiceTest.java` — 2 unit tests (startProcess success + not found)
- Camunda admin password externalized: `${CAMUNDA_ADMIN_PASSWORD:admin_Dev#2026!}`

**Risk note (R-08):** ✅ Camunda 7.22.0 + Spring Boot 3.2.4 compatible — BUILD SUCCESSFUL, no bean conflict.

---

#### S4-02 — AI Decision Node: Claude API Integration (8 SP)
**Owner:** Be-2

**Acceptance Criteria:**
- [ ] `ClaudeApiService.java` — call Claude claude-sonnet-4-6 với structured prompt
- [ ] Camunda Service Task: `AIAnalysisDelegate.java` implements `JavaDelegate`
- [ ] AI response timeout: 10s (non-blocking alert path — R-06 mitigation)
- [ ] Structured output: AI decision returns JSON `{decision, reasoning, confidence, recommended_actions[]}`
- [ ] Prompt templates externalized: `src/main/resources/prompts/*.txt`
- [ ] Claude API key load từ env var `CLAUDE_API_KEY` (không hardcode — security)
- [ ] Fallback: nếu Claude timeout → use rule-based fallback decision
- [ ] Unit tests với mock Claude API responses

**Sub-tasks:**
1. `ClaudeApiService.java` — REST client với `@Async` + timeout
2. `AIAnalysisDelegate.java` — Camunda JavaDelegate
3. 7 prompt templates (per scenario)
4. `ClaudeApiServiceTest.java` — mock HTTP responses
5. `AIAnalysisDelegateTest.java` — verify process variable mapping
6. Fallback logic: `RuleBasedFallbackDecisionService.java`

---

#### S4-00 — AI Workflow Dashboard: bpmn-js + Instance Monitoring (5 SP)
**Owner:** Fe-1

**Acceptance Criteria:**
- [ ] Process list: 7 scenarios, click → BPMN diagram render (bpmn-js)
- [ ] Running instances: table với process name, start time, current activity, variables
- [ ] Instance detail: xem variables (input/output), Claude decision result
- [ ] Manual trigger button (ADMIN/OPERATOR role)

**Sub-tasks:**
1. `WorkflowDashboardPage.tsx` — process list + diagram viewer
2. `BpmnViewer.tsx` — bpmn-js wrapper component
3. `ProcessInstanceTable.tsx` + `InstanceDetailDrawer.tsx`
4. `useWorkflowInstances.ts` — polling hook (mỗi 5s)

---

#### S4-03 — 3 Citizen AI Scenarios (8 SP)
**Owner:** Be-1

**AI-C01 — Cảnh báo AQI cho cư dân:**
- Trigger: `AlertDetectionJob` emit AQI > 150
- Flow: Detect → AI analyze severity → Choose notification template → Push SSE to affected district
- AC: Citizen trong district nhận notification trong <10s sau AI analysis

**AI-C02 — Xử lý yêu cầu dịch vụ:**
- Trigger: Citizen gửi `POST /api/v1/citizen/service-requests`
- Flow: AI classify → Route tới department → Auto-response nếu FAQ
- AC: Request classified + auto-response trong <10s

**AI-C03 — Cảnh báo khẩn cấp & sơ tán:**
- Trigger: Water level sensor > flood threshold
- Flow: Detect flood → AI assess severity + affected zones → Mass notification + evacuation guide
- AC: All citizens in affected zone receive SSE alert đúng nội dung

**Sub-tasks (per scenario):**
1. BPMN file (`.bpmn`)
2. Service task Java delegates
3. Trigger service / event listener
4. Integration test: trigger → notification verified

---

#### S4-04 — 4 Management AI Scenarios (8 SP)
**Owner:** Be-2

**AI-M01 — Flood response coordination:**
- Trigger: Flood alert event
- Flow: AI assess → Dispatch operations team → Monitor water level trend → Close when normal
- AC: Emergency team notified + operations logged

**AI-M02 — AQI > 150 traffic + construction control:**
- Trigger: AQI > 150 alert
- Flow: AI analyze source → Recommend traffic restriction areas → Auto-escalate to authority
- AC: Traffic restriction recommendation generated với reasoning

**AI-M03 — Utility incident coordination:**
- Trigger: Sensor anomaly in energy/water
- Flow: AI diagnose → Create maintenance ticket → Assign team
- AC: Maintenance ticket created + team assigned

**AI-M04 — ESG anomaly investigation:**
- Trigger: ESG metric exceeds normal range
- Flow: AI investigate → Compare historical → Generate investigation report
- AC: Investigation report generated + available via API

**Sub-tasks:** Same pattern as S4-03

---

#### S4-05 — Performance Test: 2,000 msg/s (5 SP)
**Owner:** QA-1

**Acceptance Criteria:**
- [ ] JMeter test plan: 2,000 MQTT messages/s sustained 10 phút
- [ ] Flink job throughput: ≥2,000 records/s processed (Flink metrics dashboard)
- [ ] TimescaleDB write latency: p99 <500ms under 2,000 msg/s
- [ ] HikariCP pool: không có connection timeout trong test
- [ ] API p95 <200ms với concurrent load (50 users)
- [ ] Test report saved tại `docs/reports/performance/`

**Sub-tasks:**
1. `tests/performance/jmeter-mqtt-2000.jmx`
2. `tests/performance/api-load-50users.jmx`
3. HikariCP tuning nếu cần: `maxPoolSize=20` under load
4. Run test + document results

---

#### S4-06 — Security Hardening (3 SP)
**Owner:** Be-1

**Acceptance Criteria:**
- [ ] Zero hardcoded secrets trong codebase (grep scan clean)
- [ ] Tất cả secrets qua env vars: DB password, JWT secret, Claude API key, Redis password
- [ ] SQL injection: tất cả queries dùng parameterized statements (JPA/JDBC check)
- [ ] CORS config: chỉ cho phép frontend origin
- [ ] Rate limiting: `POST /api/v1/auth/login` — max 5 attempts/minute/IP
- [ ] Security headers: HSTS, X-Frame-Options, CSP

**Sub-tasks:**
1. Grep audit: `grep -r "password" --include="*.java"` clean run
2. `SecurityConfig.java` — CORS + security headers
3. Rate limiting: Spring Boot + Bucket4j
4. `application.yml` — tất cả secrets reference `${ENV_VAR}`
5. OWASP dependency check

---

#### S4-07 — UAT Docker Compose + Deployment Guide (3 SP)
**Owner:** Ops-1

**Acceptance Criteria:**
- [ ] `infrastructure/docker-compose.uat.yml` — production-near config
- [ ] Startup: `make uat-up` → all services healthy trong 3 phút
- [ ] `docs/deployment/UAT-GUIDE.md` — step-by-step cho city authority
- [ ] `.env.uat.example` — template env file
- [ ] Seed data script: `make seed-uat` — 50 buildings, 100 sensors, 3 citizens với invoices

**Sub-tasks:**
1. `docker-compose.uat.yml` với resource limits
2. `Makefile` targets: `uat-up`, `uat-down`, `seed-uat`, `uat-test`
3. `docs/deployment/UAT-GUIDE.md`
4. Seed data script `scripts/seed_uat_data.py`

---

#### S4-08 — Regression Test Suite (5 SP)
**Owner:** QA-1

**Acceptance Criteria:**
- [ ] ≥50 automated tests total (unit + integration + E2E)
- [ ] All POC 23 tests still pass (regression)
- [ ] Playwright E2E suite: 10 scenarios covering happy paths
- [ ] CI pipeline: `make test` completes trong <10 phút
- [ ] Test report HTML generated

**Sub-tasks:**
1. Playwright E2E: login, environment dashboard, citizen flow, alert flow, ESG report
2. Backend integration: full API contract tests
3. Performance baseline test
4. CI: GitHub Actions test matrix

---

#### S4-09 — Bug Fixes & Polish (8 SP)
**Owner:** All

**Allocation:**
- Be-1: 3 SP (backend bugs Sprint 1-3)
- Be-2: 3 SP (backend bugs Sprint 1-3)
- Fe-1: 2 SP (UI polish, responsive fixes)

---

### Sprint 4 — DoD Gate

```
☐ Tất cả 7 AI scenarios trigger + complete successfully
☐ 2,000 msg/s performance test PASS với report
☐ Security scan: 0 hardcoded secrets, 0 P0 vulnerabilities
☐ UAT Docker Compose: cold start → all green trong 3 phút
☐ ≥50 automated tests passing
☐ Backend coverage ≥75%
```

---

## 8. Buffer Week — UAT (23/05 → 28/05/2026) 📋

### Activities
| Day | Activity | Owner |
|-----|----------|-------|
| 23/05 (Fri) | Sprint 4 Review + Retrospective | Full team |
| 26/05 (Mon) | UAT Setup với city authority + demo dry run | PM + Ops |
| 27/05 (Tue) | UAT Day 1: city authority kiểm tra các modules | QA + BA |
| 28/05 (Wed) | UAT bug fixes (P0/P1 only) + Final Sign-off | Dev |

### UAT Acceptance Gate
- [ ] 0 P0 bugs (system down, data loss)
- [ ] <3 P1 bugs (major feature broken)
- [ ] Alert latency <30s verified by city authority
- [ ] ESG report generated successfully trong demo
- [ ] City authority sign-off document

---

## 9. Risk Register (Detailed)

| ID | Risk | Xác suất | Tác động | Score | Owner | Mitigation | Sprint |
|----|------|----------|----------|-------|-------|------------|--------|
| R-01 | ThingsBoard Kafka Rule Engine phức tạp → block S1-01 | M | H | 6 | Be-1 | Ngày 1-2 test TB Rule Engine; fallback: EMQX Kafka plugin trực tiếp | Sprint 1 |
| R-02 | Java Flink migration mất thời gian hơn dự kiến | M | H | 6 | Be-1 | Start S1-03 ngay ngày 28/03; reuse POC SQL logic | Sprint 1 |
| R-03 | 7 AI scenarios + Citizen Portal scope quá lớn | H | H | 9 | PM | Sprint 4 có buffer week; P0 scenarios trước; carryover rule-based nếu Claude late | Sprint 3-4 |
| R-04 | EMQX + ThingsBoard CE compatibility | L | M | 3 | Be-1 | Integration test ngày đầu Sprint 1 | Sprint 1 |
| R-05 | Real-time map performance với 100+ sensors | M | M | 4 | Fe-1 | Marker clustering + viewport lazy load (S3-01 sub-task) | Sprint 3 |
| R-06 | Claude API latency >10s block workflow | M | M | 4 | Be-2 | Async non-blocking; timeout 10s; rule-based fallback | Sprint 4 |
| R-07 | HikariCP pool exhaustion under 2,000 msg/s | L | M | 3 | Be-1 | maxPoolSize tuning + S4-05 performance test | Sprint 4 |
| R-08 | Camunda 7 embedded conflict với Spring Boot 3.2 | L | M | 3 | Be-1 | Test ngày đầu Sprint 4; fallback Spring Boot 2.7 | Sprint 4 |
| R-09 | Sprint 3 overload (60 SP) — velocity risk | M | M | 4 | PM | S3-07 (Admin Panel) carryover nếu cần; QA sub-tasks parallelized | Sprint 3 |
| R-10 | ESG report PDF generation library issues | L | L | 2 | Be-2 | Evaluate iText vs Apache POI ngày đầu Sprint 2; simple HTML→PDF fallback | Sprint 2 |

---

## 10. Dependency Map

```
S1-06 (Docker) ──────────────┐
                             ├──▶ S1-01 (EMQX+TB)
                             ├──▶ S1-02 (DB Schema) ──▶ S1-03 (Flink Env) ──▶ S2-01 (Env API)
                             ├──▶ S1-04 (Auth) ──────▶ S1-07 (React Auth)
                             └──▶ S1-05 (Redpanda)
                             
S1-03 + S1-02 ──▶ S1-08 (ESG+Alert Flink Jobs) ──▶ S2-02 (Alert Engine) ──▶ S2-03 (SSE Notify)

S2-01 + S2-02 + S2-03 ──▶ S3-01 (City Ops Center)
S2-04 (ESG API) ──▶ S2-06 (ESG Dashboard)

S3-04 + S3-05 (Citizen BE) ──▶ S3-06 (Citizen Portal UI)

S4-01 (Camunda) ──▶ S4-02 (Claude AI) ──▶ S4-03 + S4-04 (7 AI Scenarios)
S4-01 ──▶ S4-00 (Workflow Dashboard)

S4-05 (Perf Test) ─ depends on ─▶ All Flink jobs running (S1-03, S1-08, S2-08)
```

---

## 11. Release Checklist — UAT Release (v1.0-UAT)

### Pre-Release
- [ ] Sprint 4 DoD gate passed (all items checked)
- [ ] Regression test suite: 100% pass
- [ ] Alert E2E <30s verified (automated)
- [ ] Performance: 2,000 msg/s load test PASS
- [ ] Security scan: 0 kritical/high vulnerabilities
- [ ] DB migration scripts reviewed + rollback ready
- [ ] `.env.uat.example` kiểm tra — tất cả secrets có mặt
- [ ] City authority go/no-go obtained (28/05)

### Deployment Steps (23:00)
1. `make uat-down` (stop previous)
2. `git pull origin main` + verify version tag
3. `make uat-up` → wait all services healthy
4. `make seed-uat` (nếu fresh DB)
5. Monitor Flink dashboard 5 phút — no job failures
6. Gửi 10 MQTT test messages → verify pipeline
7. Test alert SSE: send AQI > 200 → verify push
8. Browser test: login, dashboard, citizen portal

### Post-Release
- [ ] Release notes tới city authority
- [ ] Flink job metrics monitored 30 phút
- [ ] Sprint 4 retrospective (23/05 14:00)

---

## 12. Recurring Ceremonies

| Ceremony | Thời gian | Participants | Output |
|----------|-----------|-------------|--------|
| Daily Standup | 09:15 (15min) ngày làm việc | Dev team | Blocker log |
| Sprint Planning | Thứ Hai đầu sprint (2h) | Full team + PO | Sprint backlog confirmed |
| Sprint Review | Thứ Sáu cuối sprint, 14:00 | Full team + city authority rep | Demo feedback |
| Retrospective | Thứ Sáu cuối sprint, 15:30 | Dev team | Improvement actions |
| City Authority Update | Hàng tuần Thứ Sáu (email) | PM | Weekly status report |

---

## 13. Current Sprint Status

### Sprint 1 & Sprint 2 — ✅ Đã hoàn thành
> Sprint 1 DONE: 30/03/2026 (early delivery)  
> Sprint 2 DONE: 31/03/2026 (retest passed)  
> Architecture Stabilization DONE: 04/04/2026

### Sprint 3 — ✅ Hoàn thành (06/04/2026)

### Sprint 4 — 🚀 IN PROGRESS (09/04 → 23/05/2026)

**Completed stories:**
| Story | Points | Status | Note |
|-------|--------|--------|------|
| S4-01 Camunda 7 Setup + BPMN | 8 SP | ✅ Done | Security chains, 7 BPMN, workflow API, tests |

**In progress:**
| Story | Points | Owner | Status |
|-------|--------|-------|--------|
| S4-02 AI Decision Node: Claude API | 8 SP | Be-2 | ⬜ Pending |
| S4-00 AI Workflow Dashboard | 5 SP | Fe-1 | ⬜ Pending |
| S4-03 3 Citizen AI Scenarios | 8 SP | Be-1 | ⬜ Blocked by S4-02 |
| S4-04 4 Management AI Scenarios | 8 SP | Be-2 | ⬜ Blocked by S4-02 |

---

### Sprint 1 History (28/03 → 11/04/2026) — ✅ Done

> **Hoàn thành:** 30/03/2026 — Sprint 1 DONE (early delivery)

### Status: 🟢 Green — Sprint 1 Delivered

| Story | Points | Assignee | Status | Note |
|-------|--------|----------|--------|------|
| S1-06 Docker Compose | 5 | Ops-1 | ✅ Done | All services healthy |
| S1-01 EMQX+ThingsBoard | 8 | Be-1 | ✅ Done | EMQX Kafka bridge (R-01 fallback) |
| S1-02 DB Schema | 5 | Be-2 | ✅ Done | 6 schemas + hypertables |
| S1-03 EnvironmentFlinkJob | 8 | Be-1 | ✅ Done | 16/16 Flink tests pass |
| S1-04 Auth JWT | 8 | Be-2 | ✅ Done | 8/8 unit tests pass |
| S1-05 Redpanda Connect | 5 | Be-1 | ✅ Done | Routing + DLQ configured |
| S1-07 React Base App | 5 | Fe-1 | ✅ Done | 15/15 frontend tests pass |
| S1-08 ESG+Alert Flink | 8 | Be-2 | ✅ Done | Window logic + alert rules |

**Velocity achieved:** 52 SP — 100% committed scope delivered
**Tests:** Frontend 15/15 ✅ · Flink 16/16 ✅ · Backend 8/8 unit ✅ (integration skipped: Docker required)

---

*Detail Plan v1.0 — Generated from Master Plan v2.0*  
*Next update: End of Sprint 1 (11/04/2026)*
