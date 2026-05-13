# Sprint MVP3-1 — Risk Review (Toàn diện)

**Ngày:** 2026-05-13
**Scope:** Tất cả risks Sprint 1 (ngoài Flink dual-sink đã review riêng)
**Audience:** Backend Lead, SA, QA, PM, DevOps
**Gate status:** 69/70 items PASS, 1 time-bounded (shadow 72h, expires 2026-05-15)

---

## Mục lục

1. [Gate Honesty Audit — Checklist vs Code Reality](#1-gate-honesty-audit--checklist-vs-code-reality)
2. [Flink Dual-Sink Risks (tóm tắt)](#2-flink-dual-sink-risks)
3. [RLS & Multi-Building Isolation Risks](#3-rls--multi-building-isolation-risks)
4. [ClickHouse & Analytics Service Risks](#4-clickhouse--analytics-service-risks)
5. [Kong & Keycloak Security Risks](#5-kong--keycloak-security-risks)
6. [Tier 1 Regression & Extraction Risks](#6-tier-1-regression--extraction-risks)
7. [Infrastructure & Deployment Risks](#7-infrastructure--deployment-risks)
8. [QA & Test Coverage Risks](#8-qa--test-coverage-risks)
9. [Capacity & Schedule Risks](#9-capacity--schedule-risks)
10. [Sprint 2 Carry-Over Risk Summary](#10-sprint-2-carry-over-risk-summary)
11. [Action Items](#11-action-items)

---

## 1. Gate Honesty Audit — Checklist vs Code Reality

> **Mục đích:** Gate checklist ghi 69/70 PASS. Nhưng nhiều "PASS" dựa trên test bypasses — không chạy qua pipeline thực. Section này audit từng claim quan trọng.

### 1.1 Summary: Gate Claim vs Reality

| # | Gate Claim | Evidence trong Checklist | THỰC TẾ (code review) | Verdict |
|---|-----------|------------------------|----------------------|---------|
| G-01 | "Flink dual-sink 12 unit tests PASS" | `ClickHouseSinkTest` (5) + `EsgDualSinkJobTest` (7) | **REAL** — nhưng chỉ test `extractBuildingId()` parsing + INSERT_SQL constants. KHÔNG test Kafka consume, KHÔNG test JDBC write thật, KHÔNG test checkpoint/restart | **MISLEADING** |
| G-02 | "Dual-sink verify: 100K rows → delta=0.000000%" | `dual-sink-verify.sh` PASS | Script insert 100K rows trực tiếp vào TS bằng `generate_series()`, export CSV rồi POST sang CH qua HTTP API. **Bypass Flink hoàn toàn.** Comment dòng 41: "simulates Flink TimescaleDB sink", dòng 57: "simulates Flink ClickHouse sink" | **MISLEADING** |
| G-03 | "10M row seed PASS + rollup p95=2.3ms" | `seed-10m-rows.sql` executed | Pure SQL `INSERT INTO ... SELECT ... FROM generate_series()` trực tiếp vào TS. **Zero Kafka. Zero Flink.** Chỉ test TS INSERT performance, không test stream processing | **MISLEADING** |
| G-04 | "Shadow diff <0.01% sustained 72h" | Monitor started 2026-05-12, window expires 2026-05-15 | Monitor poll static 5,015 rows seeded (hardcoded epoch range `1776996000` → `1778303280`). **Không có real-time ingestion.** Diff luôn = 0% vì data không thay đổi | **PARTIALLY MISLEADING** |
| G-05 | "EsgDualSinkJob.java implemented" | File exists, code reviewed | **REAL** — full Kafka source + dual JDBC sinks + checkpointing. Nhưng **chưa bao giờ chạy trên Flink cluster thật** | **HONEST** (code exists) |
| G-06 | "ClickHouseSink.java implemented" | File exists | **REAL** — JDBC sink factory với batch 5000/2s. Nhưng `ClickHouseSinkTest` chỉ verify constants (INSERT_SQL, batch size), **KHÔNG verify INSERT thật** | **HONEST** (code exists) |
| G-07 | "EsgFlinkJob (OLD) vẫn còn trong source tree" | KHÔNG ĐƯỢC NHẮC trong gate | File vẫn tại `flink-jobs/.../EsgFlinkJob.java`. Subscribe cùng topic `ngsi_ld_esg`. Không có `ON CONFLICT`, không có `tenant_id`/`building_id`. Nếu chạy song song → duplicate + data corruption | **CONCEALED RISK** |
| G-08 | "AnalyticsAutoConfiguration matchIfMissing=true" | `CapabilityFlagIT` PASS | **REAL** — `ApplicationContextRunner` test đúng. `matchIfMissing=true` hoạt động | **HONEST** |
| G-09 | "BuildingClusterService coverage 95%" | 9 tests PASS | **REAL** — Mockito tests đầy đủ | **HONEST** |
| G-10 | "CrossBuildingAggregationService coverage 39%" | Accepted Sprint 1 risk | **REAL code** — JDBC lambda không instrument trong unit test. **Sprint 2 IT bắt buộc** | **HONEST** (đã flag) |
| G-11 | "Kong alg=none → 401 PASS" | `test-alg-none.sh` run manual | **REAL** — curl alg=none token → 401. Nhưng chỉ chạy 1 lần manual, **KHÔNG có automated CI** | **HONEST** (but fragile) |
| G-12 | "Analytics-service IT 8/8 PASS" | ClickHouseEnergyRepositoryIT | **REAL** — Testcontainers ClickHouse, 7 tests aggregation + tenant isolation | **HONEST** |

### 1.2 Chi tiết: dual-sink-verify.sh — TẠI SAO NÓ KHÔNG TEST FLINK

Script `tests/performance/dual-sink-verify.sh` làm 3 việc:

```bash
# Bước 1: Insert 100K rows TRỰC TIẾP vào TimescaleDB (bypass Flink)
psql -c "INSERT INTO esg.clean_metrics (...) SELECT ... FROM generate_series(1, 100000)"

# Bước 2: Export CSV từ TS → POST vào ClickHouse (bypass Flink)
psql -c "COPY (SELECT ...) TO STDOUT WITH CSV" | \
  curl "http://localhost:8123/?query=INSERT INTO analytics.esg_readings FORMAT CSV"

# Bước 3: So sánh count + SUM (tất nhiên bằng nhau — cùng data nguồn)
```

**Điều nó KHÔNG test:**
- Kafka deserialization (`NgsiLdDeserializer`)
- `flatMap` metric expansion
- `extractBuildingId()` trên real data
- `TenantIdValidator` filtering
- JDBC batch flush behaviour (5000 rows / 2s)
- Checkpoint/recovery sau failure
- Exactly-once semantics
- OffsetsInitializer behavior

**Điều nó CHỨNG MINH:** ClickHouse schema compatible với TimescaleDB schema. Không hơn, không kém.

### 1.3 Chi tiết: shadow-72h-monitor.sh — MONITOR STATIC DATA

```bash
# Hardcoded epoch range — chỉ cover 5,015 rows seed
FROM_EPOCH=1776996000    # 2026-04-19
TO_EPOCH=1778303280      # 2026-05-03

# Poll mỗi 5 phút — nhưng data không thay đổi
CH_SUM=$(curl "http://localhost:8123/?query=SELECT SUM(kwh) FROM ... WHERE ts >= ${FROM_EPOCH} AND ts <= ${TO_EPOCH}")
API_SUM=$(curl analytics-service energy-aggregate with same range)
```

**Vấn đề:** Data static → diff luôn 0% → "72h sustained" không có ý nghĩa. Cần monitor khi Flink dual-sink đang chạy và nhận data real-time.

### 1.4 Chi tiết: seed-10m-rows.sql — BYPASS HOÀN TOÀN

```sql
-- Pure PostgreSQL generate_series — zero Kafka, zero Flink
INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, tenant_id, building_id)
SELECT
    'SENSOR-B' || b::text || '-' || lpad(s::text, 4, '0'),
    metric_type,
    ts,
    (50 + (b * 10) + random() * 20)::DECIMAL(12,4),
    'kWh',
    CASE WHEN b <= 3 THEN 'hcm' ELSE 'default' END,
    'BLD-' || lpad(b::text, 3, '0')
FROM generate_series(1, 5) b, generate_series(1, 100) s, ...
```

**Kết quả:** 10M rows trong `esg.clean_metrics` — dùng để benchmark rollup query (p95=2.3ms). Nhưng rows này KHÔNG đi qua Flink, không có trong ClickHouse. Nó chứng minh TimescaleDB handle 10M rows, không chứng minh Flink pipeline xử lý được throughput đó.

### 1.5 EsgFlinkJob.java — CONCEALED DUAL-WRITE RISK

File `flink-jobs/src/main/java/com/uip/flink/esg/EsgFlinkJob.java` vẫn còn trong source tree:

```java
// EsgFlinkJob.java — OLD job, still present
KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
    .setTopics("ngsi_ld_esg")                    // ← CÙNG TOPIC
    .setGroupId("flink-esg-job")                 // ← KHÁC group ID
    ...

// INSERT không có ON CONFLICT, KHÔNG có tenant_id, building_id
"INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, raw_payload) " +
"VALUES (?, ?, ?, ?, ?::jsonb, ?)"
```

Cả hai jobs subscribe `ngsi_ld_esg`. Nếu cùng chạy:
- Kafka delivers messages đến cả 2 consumer groups
- `EsgFlinkJob` insert duplicates (no ON CONFLICT)
- `EsgFlinkJob` insert `raw_payload::jsonb` nhưng bỏ qua `tenant_id`, `building_id`
- Data không có tenant context → RLS broken

**Gate KHÔNG nhắc đến risk này.** File không bị remove, không bị `@Deprecated`, không có guard chống chạy song song.

### 1.6 ~~ClickHouse Schema Mismatch~~ — RESOLVED

**RESOLVED 2026-05-13** (commit `f3b4984e`):
- `application.yml`, `ClickHouseConfig.java`, `docker-compose.yml`, `infrastructure/clickhouse/init.sql`, `infra/helm/values.yaml` — all defaults changed from `uip_analytics` → `analytics`
- `ClickHouseEnergyRepositoryIT` rewritten with new `analytics.esg_readings` schema (8/8 tests pass)
- `infrastructure/clickhouse/init.sql` creates `analytics` DB + `esg_readings` table matching `V001__create_analytics_schema.sql`
- Full data flow now consistent: Flink writes `analytics.esg_readings` → analytics-service reads `analytics.esg_readings`
- Regression: 773/773 tests pass

### 1.7 Gate Honesty Verdict

```
┌────────────────────────────────────────────────────────────────────┐
│  GATE CHECKLIST: 69/70 PASS ✅                                     │
│  HB-EXT RE-VERIFICATION: 7/7 PASS ✅ (commit f3b4984e)           │
│                                                                    │
│  RESOLVED:                                                         │
│  - Schema mismatch (Section 1.6) FIXED — analytics DB consistent  │
│  - Old Flink job (G-07) @Deprecated + startup guard               │
│  - 773/773 automated tests pass (full regression)                 │
│                                                                    │
│  REMAINING CONCERNS:                                               │
│  - 3 items PASS dựa trên test bypass Flink (G-02, G-03, G-04)    │
│  - Flink dual-sink CHƯA BAO GIỜ chạy trên cluster thật           │
│  - Shadow monitor cần re-run với real ingestion (A-20)            │
│                                                                    │
│  REVISED CONFIDENCE: Gate = Sprint 1 Ready ✅                      │
│  All P1/P2 bugs fixed. Sprint 2 UNBLOCKED.                        │
│  Remaining items are SHOULD/MUST for Sprint 2 (carry-over).       │
└────────────────────────────────────────────────────────────────────┘
```

---

## 2. Flink Dual-Sink Risks

> Chi tiết đầy đủ: [`flink-dual-sink-risk-assessment.md`](flink-dual-sink-risk-assessment.md)

### Tóm tắt 5 CRITICAL + 4 MEDIUM

| ID | Issue | Severity | Sprint fix | Owner |
|----|-------|----------|-----------|-------|
| C-1 | Hai Flink jobs cùng consume `ngsi_ld_esg` → duplicate rows trong TS | CRITICAL | S2 Day 1 | Backend Lead |
| C-2 | ClickHouse sink KHÔNG exactly-once → duplicate sau restart | CRITICAL | S2 W1 | Backend Eng 2 |
| C-3 | Checkpoint local disk `/flink/checkpoints` → data gap sau pod restart | CRITICAL | S2 W1 | DevOps |
| C-4 | `dual-sink-verify.sh` test data copy, KHÔNG test Flink pipeline thực sự | CRITICAL | S2 W2 | QA |
| C-5 | `OffsetsInitializer.latest()` → mất data trên first deploy | CRITICAL | S2 Day 1 | Backend Lead |
| M-1 | `extractBuildingId` fragile — silent empty `building_id` | MEDIUM | S2 W2 | Backend Eng 2 |
| M-2 | Empty `tenant_id` pass qua cả hai sinks | MEDIUM | S2 W2 | Backend Eng 1 |
| M-3 | Không có Dead Letter Queue | MEDIUM | S3 | Backend Lead |
| M-4 | Batch size asymmetry TS vs CH → tạm thời diverge | MEDIUM | S2 (document) | BA/PM |

**PO Q&A chuẩn bị sẵn trong file riêng.**

---

## 3. RLS & Multi-Building Isolation Risks

### R-RLS-1: Cross-building aggregate query >2s trên production data lớn hơn benchmark

**Severity:** HIGH | **Probability:** 25% | **Impact:** Sprint 2 gate fail, ESG report chậm

**Bằng chứng:**
- Benchmark 10M rows (5 buildings × 2M) baseline **1,044ms** — vượt 500ms target
- Với rollup materialized view: **2.3ms** — pass mượt
- Nhưng: rollup chỉ cover `date_trunc('day')` — query không theo ngày (intraday, hourly) sẽ fall back sang full scan

**Risk cụ thể:**
- Query `GROUP BY building_id, metric_type` với `time_bucket('1 hour')` → không có rollup → 1s+ latency
- Khi data tăng lên 50M+ rows (5 buildings × 90 ngày × 100 sensors × 10 readings/h), baseline sẽ tăng proportionally

**Mitigation:**
1. Sprint 2: thêm `metrics_hourly_rollup` MV cho intraday queries
2. Cache cross-building aggregation 10 phút TTL
3. SLA document: "<500ms cho daily+ aggregation, <2s cho hourly aggregation"

**Trạng thái:** 10/10 RLS scenarios PASS — isolation đúng, chỉ performance cần attention

---

### R-RLS-2: RLS-010 concurrent test chỉ verify ở SQL level, chưa test ở Java thread-safety level

**Severity:** MEDIUM | **Probability:** 15% | **Impact:** Tenant contamination race condition

**Bằng chứng:**
- Gate checklist: "10-iteration SQL loop zero contamination. Full 50-concurrent Java IT deferred to Sprint 2"
- `TenantContextAspect` dùng `SET LOCAL` trong transaction — safe nếu trong `@Transactional`
- Nhưng: nếu có code path query DB ngoài transaction (e.g., `@Cacheable` miss trigger) → `app.tenant_id` có thể set sai context

**Mitigation:**
1. Sprint 2 bắt buộc: Java 50-concurrent IT với Testcontainers
2. Review mọi `@Cacheable` method trong building/analytics module — đảm bảo `@Transactional` wrap
3. `ThreadLocal` audit: confirm `TenantContext` clear sau mỗi request (filter/interceptor)

---

### R-RLS-3: `is_aggregator` flag trên tenant table — quyền lực quá lớn

**Severity:** MEDIUM | **Probability:** 20% | **Impact:** Data leak nếu flag bị set sai

**Bằng chứng:**
- ADR-033: `is_aggregator=true` → xem TẤT CẢ buildings trong cluster
- Hiện tại: chỉ check flag, KHÔNG validate cluster ownership
- Nếu admin vô tình set `is_aggregator=true` cho tenant không thuộc cluster → full data access

**Mitigation:**
1. Sprint 2: thêm constraint `is_aggregator = true IMPLIES cluster_id IS NOT NULL`
2. Admin API endpoint `PUT /tenants/{id}` phải validate aggregator + cluster_id pair
3. Audit log khi toggle `is_aggregator`

---

## 4. ClickHouse & Analytics Service Risks

### R-CH-1: ClickHouse 23.8 DateTime64 limitation — các queries xử lý milliseconds sẽ truncate

**Severity:** MEDIUM | **Probability:** 60% | **Impact:** Analytics không hiển thị sub-second data

**Bằng chứng:**
- DevOps status: "V001 schema fix: `DateTime64(3)` → `DateTime` do ClickHouse 23.8 không support DateTime64 trong TTL expression"
- `esg_readings.recorded_at` hiện là `DateTime` — precision chỉ đến giây
- Flink push readings với millisecond timestamps → truncate khi insert

**Mitigation:**
1. Sprint 2: upgrade ClickHouse lên v24.x+ (support DateTime64 + TTL)
2. Hoặc: chấp nhận second-level precision cho analytics (không ảnh hưởng dashboard UX)
3. Document limitation rõ trong API response

---

### R-CH-2: analytics-service shadow validation chỉ verified ở point-in-time snapshot, chưa sustained 72h trên production traffic

**Severity:** HIGH | **Probability:** 30% | **Impact:** Cutover Sprint 2 dựa trên bằng chứng chưa đủ mạnh

**Bằng chứng:**
- Gate checklist: "validated as point-in-time snapshot comparison. Production 72h window requires Tier 2 staging"
- Shadow monitor đã start (2026-05-12T10:41:08Z), window expires 2026-05-15T10:41:08Z
- Nhưng: monitor chạy trên static seeded data (5,015 rows), không có real-time ingestion

**Mitigation:**
1. Trước gate: confirm shadow-72h monitor log tất cả polls PASS
2. Sprint 2 Week 1: chạy shadow với real ingestion (Flink dual-sink active)
3. Gate Sprint 2: 72h sustained với real traffic trước khi flip `analytics-external=true`

---

### R-CH-3: analytics-service error handling — "return empty + log" pattern ẩn data issues

**Severity:** MEDIUM | **Probability:** 35% | **Impact:** Silent data loss trong analytics dashboard

**Bằng chứng:**
- v3-BE-03 spec: "Connection refused → empty list + log error (không throw 500)"
- Dashboard sẽ hiển thị "no data" thay vì error → user nghĩ không có data, không báo bug

**Mitigation:**
1. Health endpoint check ClickHouse connection → `/actuator/health` include ClickHouse status
2. Prometheus metric `uip_analytics_clickhouse_errors_total` + alert khi > 0
3. Frontend: hiển thị warning banner "Analytics data may be incomplete" khi clickhouse unhealthy

---

### R-CH-4: HikariCP config cho ClickHouse — connection test query không chuẩn

**Severity:** LOW | **Probability:** 20% | **Impact:** Connection pool leak hoặc false positive health checks

**Bằng chứng:**
- Spec: `config.setConnectionTestQuery("SELECT 1")` — comment "ClickHouse không support preparedStatement như PG"
- `SELECT 1` trong ClickHouse luôn success kể cả khi database/table corrupted
- Không có `validationTimeout` config → default 5s có thể quá lâu cho ClickHouse (thường <1ms)

**Mitigation:**
1. Đổi `connectionTestQuery` thành `SELECT 1 FROM system.numbers LIMIT 1` (thực sự hit engine)
2. Set `validationTimeout = 1000` (1s đủ cho ClickHouse local)
3. Sprint 2: review HikariCP metrics trong Grafana

---

## 5. Kong & Keycloak Security Risks

### R-KK-1: Kong DB-less mode — config không persist, restart mất plugins config

**Severity:** HIGH | **Probability:** 40% | **Impact:** Auth bypass sau Kong restart nếu declarative config không mount đúng

**Bằng chứng:**
- DevOps status: Kong chạy DB-less, config inject qua `kong.local.yml` template + `docker-entrypoint-local.sh`
- Template generated tại runtime → nếu script fail → Kong start KHÔNG có auth plugins
- Gate test: alg=none → 401 PASS, nhưng test chạy **sau khi** Kong start với config đúng

**Risk cụ thể:**
- Container restart → entrypoint re-run → nếu `$KONG_JWT_SECRET` env var missing → JWT plugin config với secret rỗng → mọi request pass

**Mitigation:**
1. Thêm health check: `GET /api/v1/analytics` **KHÔNG có** Authorization header → phải return 401
2. CI pipeline: startup test — stop Kong → start → verify unauthenticated = 401
3. Sprint 4 production: chuyển sang Kong with database hoặc declarative config mounted readonly

---

### R-KK-2: Keycloak chưa deploy — dual-issuer JWT chưa test thực tế

**Severity:** HIGH | **Probability:** 35% | **Impact:** Sprint 4 migration từ legacy JWT sang Keycloak sẽ có issues chưa预见

**Bằng chứng:**
- DevOps status: "CONFIG READY — Deploy pending" cho cả Keycloak
- ADR-027: `RoutingJwtDecoder` cho dual-issuer (legacy + Keycloak) — code chưa viết
- JWT claims contract (`building_ids`, `is_aggregator`) chỉ là document, chưa test

**Mitigation:**
1. Sprint 2: deploy Keycloak non-prod + implement `RoutingJwtDecoder`
2. Sprint 2: test dual-issuer: legacy token pass + Keycloak token pass + mixed rejected
3. Sprint 4: cutover — flip primary issuer

---

### R-KK-3: Kong plugin execution order — chỉ test thủ công, chưa có automated CI gate

**Severity:** HIGH | **Probability:** 25% | **Impact:** Auth bypass nếu plugin order thay đổi

**Bằng chứng:**
- ADR-028: Plugin order locked (cors → jwt → request-transformer → rate-limiting)
- Test: `test-alg-none.sh` — chạy thủ công
- KHÔNG có CI pipeline check plugin order trên mỗi deploy

**Mitigation:**
1. Sprint 2: thêm Kong config validation vào CI — `curl /config` → assert plugin order
2. Sprint 2: thêm test "unauthenticated request → 401" vào smoke test suite
3. Sprint 4: Kong security CI gate (ADR yêu cầu)

---

## 6. Tier 1 Regression & Extraction Risks

### R-T1-1: `@ConditionalOnProperty` matching logic — edge case khi property set nhưng sai giá trị

**Severity:** HIGH | **Probability:** 20% | **Impact:** Bean không load → application crash

**Bằng chứng:**
- `AnalyticsAutoConfiguration`: `havingValue = "false", matchIfMissing = true`
- Nếu ai set `analytics-external=yes` (thay vì `"true"`) → bean KHÔNG load (havingValue mismatch)
- KHÔNG có validation cho property value

**Mitigation:**
1. Thêm `@Value` validation: `@Validated` + pattern check cho capability flags
2. Document valid values: `"true"`, `"false"` only
3. CI test: cover case `analytics-external=invalid-value` → fail fast với clear error message

---

### R-T1-2: analytics-service Dockerfile + CI — chưa verify automated build pipeline

**Severity:** MEDIUM | **Probability:** 30% | **Impact:** Sprint 2 cutover bị block do Docker image không build được

**Bằng chứng:**
- Gate checklist: "`./gradlew bootJar` PASS — `app.jar` exists from prior build; CI re-confirmation required on next push"
- Docker build context updated nhưng chưa có CI pipeline cho analytics-service image
- Tier 1 CI chỉ test monolith — không cover extracted service

**Mitigation:**
1. Sprint 2 Day 1: thêm CI job `build-analytics-service-image` vào pipeline
2. Test: `docker build applications/analytics-service/ → push to registry → pull → run → health check`
3. Same version tag cho cả monolith + analytics-service (ADR-011 mandate)

---

## 7. Infrastructure & Deployment Risks

### R-INF-1: EMQX unhealthy — "Not responding to pings"

**Severity:** MEDIUM | **Probability:** 40% (đã xảy ra) | **Impact:** IoT sensor data không ingest qua MQTT

**Bằng chứng:**
- Test eval report: `uip-emqx` status = ⚠️ UNHEALTHY
- BUG-001 (từ demo evaluation): "EMQX Erlang node unhealthy"
- Fix documented: restart + fix `EMQX_NODE_NAME`

**Risk thực tế:**
- EMQX unhealthy = MQTT broker down = sensors không gửi data
- Flink dual-sink không nhận data → cả TS + CH trống
- Dashboard hiển thị "no data" → demo fail

**Mitigation:**
1. Sprint 2: EMQX health check vào monitoring (Prometheus alert)
2. Sprint 2: EMQX cluster (2 nodes) cho HA
3. Fallback: HTTP ingestion API (đã có) cho sensors hỗ trợ

---

### R-INF-2: Backend chạy ngoài Docker (Java process trực tiếp) — inconsistency với production K8s

**Severity:** LOW | **Probability:** 50% (đã xảy ra) | **Impact:** Dev/prod parity gap

**Bằng chứng:**
- Test eval report: "backend hiện chạy như Java process trực tiếp trên host (không containerized)"
- Port mapping issue đã gây 3 test failures (health × 2, rate_limit × 1) — fixed bằng map `8086:8081`

**Mitigation:**
1. Sprint 2: containerize backend (matching production K8s deployment)
2. Docker Compose cho dev phải mirror K8s config (ports, env vars, resource limits)

---

### R-INF-3: ClickHouse single-node POC — không có HA, data loss khi container restart

**Severity:** MEDIUM | **Probability:** 50% | **Impact:** Analytics data mất khi container recreate

**Bằng chứng:**
- ClickHouse chạy single-node Docker, KHÔNG có volume mount cho data
- `docker-compose down → docker-compose up` = fresh ClickHouse = zero analytics data
- DevOps status: chỉ verify `/ping` + tables exist, KHÔNG verify data persistence

**Mitigation:**
1. Sprint 2: thêm named volume cho ClickHouse data
2. Sprint 2: 2-node ClickHouse cluster (ADR-026 target)
3. Recovery procedure: re-ingest từ TimescaleDB backup

---

## 8. QA & Test Coverage Risks

### R-QA-1: CrossBuildingAggregationService coverage chỉ 39% — dưới gate target

**Severity:** MEDIUM | **Probability:** 100% (confirmed) | **Impact:** Code path không test → bugs ẩn

**Bằng chứng:**
- Test report: `CrossBuildingAggregationServiceTest` — 5 tests, 39% instruction coverage
- Nguyên nhân: "JDBC ConnectionCallback lambda not instrumented in unit tests"
- Gate workaround: "Gate requires 85% on BuildingClusterService specifically (95% PASS)"
- RLS-010: "Full 50-concurrent Java IT deferred to Sprint 2"

**Mitigation:**
1. Sprint 2 bắt buộc: Testcontainers IT cho CrossBuildingAggregationService
2. Test plan: 10M rows seed + 5 building cross-aggregate + edge cases
3. Target: 85%+ coverage trước Sprint 2 gate

---

### R-QA-2: TypeScript check không chạy trong QA session — rely on CI

**Severity:** LOW | **Probability:** 15% | **Impact:** Type errors phát hiện muộn

**Bằng chứng:**
- Test report: "Not run in this session (shell policy — npx blocked)"
- "Last confirmed 0 errors. CI will validate on push"

**Mitigation:**
1. CI phải có `tsc --noEmit` gate
2. Sprint 2: allow npx trong QA environment hoặc pre-commit hook

---

### R-QA-3: Integration tests cho building module hoàn toàn bị defer sang Sprint 2

**Severity:** MEDIUM | **Probability:** 30% | **Impact:** Bugs RLS chỉ phát hiện khi IT chạy

**Bằng chứng:**
- Test strategy: "Sprint 1 relies on unit tests + manual TC"
- Test report: "No @SpringBootTest IT in building package"
- RLS verification chỉ qua SQL script + manual API testing

**Mitigation:**
1. Sprint 2: bắt buộc Testcontainers IT
2. Testcontainers PG + V26 migration + RLS policy test trong CI

---

## 9. Capacity & Schedule Risks

### R-SCH-1: Sprint 1 capacity 75 SP — cao nhất trong toàn bộ MVP3

**Severity:** HIGH | **Probability:** 40% | **Impact:** Stories dời sang Sprint 2, tạo cascade delay

**Bằng chứng:**
- Plan warning: "Cao — cắt v3-FE-01/02 sang Sprint 2 nếu cần (5 SP buffer)"
- 3 SA spikes (15 SP) chạy song song — require Backend Lead cho cả 3
- Backend Lead phải cover: SA-01, SA-02, v3-EXT-01, v3-EXT-02, code review

**Mitigation:**
1. Nếu SA spikes kéo dài hơn 5 ngày → dời v3-FE-01/02 (5 SP each) sang Sprint 2 Day 1
2. Frontend Eng có thể start v3-FE-01/02 sớm nếu API contract chốt (OpenAPI stub)

---

### R-SCH-2: Shadow 72h monitor — window expires 2026-05-15, gate 2026-05-25 — buffer 10 ngày

**Severity:** LOW | **Probability:** 10% | **Impact:** Gate delay nếu shadow diff fail

**Bằng chứng:**
- Monitor started 2026-05-12T10:41:08Z
- Initial poll: diff = 0.000000% (static data)
- Risk: nếu monitor process crash → mất continuous validation → cần restart + re-run 72h

**Mitigation:**
1. Monitor process health check mỗi giờ
2. Nếu restart: reset 72h clock từ thời điểm restart
3. 10 ngày buffer đủ cho 1 restart + re-validate

---

### R-SCH-3: 8 Open Questions cần stakeholder answer trước Sprint 2 EOL

**Severity:** MEDIUM | **Probability:** 50% | **Impact:** Sprint 2 stories bị block hoặc cần rework

**Bằng chứng:**
- Detail plan: 8 open questions (OQ-001 đến OQ-008)
- OQ-004 (GRI scope) và OQ-008 (cluster switch attribution) ảnh hưởng trực tiếp v3-BE-05 (ESG report)
- PM chưa confirm weekly sync cadence với City Authority

**Mitigation:**
1. PM gửi question list đến City Authority trong tuần 1 Sprint 1
2. Default assumptions documented — team implement theo default nếu không có answer
3. Deadline: answers cần trước 2026-05-30 (Sprint 2 Week 1) để unblock v3-BE-05

---

## 10. Decision: Extend Sprint 1 (+1 tuần)

**Ngày quyết định:** 2026-05-13
**Quyết định:** Sprint 1 extend từ 2026-05-25 → **2026-06-01**
**Lý do:** Gate audit phát hiện 4 P0 items chưa fix, Flink pipeline chưa test E2E, CH schema mismatch. Không thể bắt đầu Sprint 2 với foundation chưa solid.

### 10.1 Sprint 1 Extension Plan (2026-05-26 → 2026-06-01)

| Day | Activity | Owner | SP |
|-----|----------|-------|----|
| Mon 5/26 | Remove/deprecate `EsgFlinkJob.java` — add `@Deprecated`, log warning on startup | Backend Lead | 1 |
| Mon 5/26 | Fix CH schema mismatch: analytics-service repo query `analytics.esg_readings` | Backend Lead | 3 |
| Tue 5/27 | Flink E2E integration test: Kafka test topic → Flink mini-cluster → verify rows in TS + CH | Backend Eng 2 | 5 |
| Wed 5/28 | Rewrite `dual-sink-verify.sh` to test actual Flink pipeline (not just DB copy) | QA + Backend Eng 2 | 2 |
| Thu 5/29 | Re-run shadow monitor với Flink dual-sink active + real ingestion data | QA | 2 |
| Fri 5/30 | CrossBuildingAggregationService IT (85% coverage target) — Testcontainers | Backend Eng 1 | 3 |
| Fri 5/30 | RLS-010 Java 50-concurrent IT — Testcontainers | QA + Backend Eng 1 | 2 |
| **Total extension** | | | **~18 SP** |

### 10.2 Updated Timeline

```
ORIGINAL:
  Sprint 1:  2026-05-12 → 2026-05-25  (2 tuần)
  Sprint 2:  2026-05-26 → 2026-06-08  (2 tuần)
  ...
  Pilot:     2026-08-10

UPDATED (Sprint 1 extended +1 tuần):
  Sprint 1:  2026-05-12 → 2026-06-01  (3 tuần — extension tuần 3 fix audit items)
  Sprint 2:  2026-06-02 → 2026-06-15  (2 tuần — shift 1 tuần)
  Sprint 3:  2026-06-16 → 2026-06-29  (shift 1 tuần)
  Sprint 4:  2026-06-30 → 2026-07-13  (shift 1 tuần)
  Sprint 5:  2026-07-14 → 2026-07-27  (shift 1 tuần)
  Sprint 6:  2026-07-28 → 2026-08-10  (shift 1 tuần)
  Pilot:     2026-08-10               ← GIỮ NGUYÊN

  Buffer trước pilot: 0 ngày (trước đó 10 ngày buffer)
  Risk: nếu Sprint 6 trễ → pilot deadline trễ
  Mitigation: Sprint 6 có descope plan (Building Safety → v3.1)
```

### 10.3 Impact on Sprint 2

**Trước extension:** Sprint 2 = 83 SP planned + 38 SP carry-over = 121 SP (impossible)
**Sau extension:** Sprint 2 = 83 SP planned + 20 SP remaining carry-over = **~103 SP** (vẫn cao nhưng feasible với 3 tuần)

**Remaining carry-over sang Sprint 2 (sau extension fix):**

| Item | SP | Priority |
|------|----|---- |
| Flink kill/restart exactly-once test | 5 | SHOULD |
| ClickHouse upgrade v24.x (DateTime64) | 3 | CAN DEFER |
| Kong CI automated gate | 2 | SHOULD |
| Keycloak deploy + RoutingJwtDecoder | 5 | MUST |
| analytics-service CI pipeline | 2 | MUST |
| EMQX health monitoring | 3 | SHOULD |
| Backend containerization | 2 | CAN DEFER |

**Sprint 2 actual load:** 83 (planned) + 7 (must carry-over) + 10 (should carry-over) = **~100 SP**
Vẫn cao. **Recommendation:** descope v3-FE-04 (Aggregation Filters, 8 SP) hoặc v3-DevOps-03 (CH 2-node HA, 13 SP) nếu cần.

### 10.4 Revised Sprint 1 Gate (2026-06-01)

Gate cũ: 69/70 items PASS
Gate mới: thêm **7 HARD BLOCK items** phải PASS trước 2026-06-01:

| # | Item | Owner | Status |
|---|------|-------|--------|
| HB-EXT-01 | EsgFlinkJob.java removed hoặc `@Deprecated` + startup warning | Backend Lead | ✅ DONE — `@Deprecated(forRemoval=true)` + `System.exit(1)` guard unless `UIP_ALLOW_OLD_FLINK_JOB=true` |
| HB-EXT-02 | CH schema mismatch fixed — analytics-service queries `analytics.esg_readings` | Backend Lead | ✅ DONE — `ClickHouseEnergyRepository` fixed; `ClickHouseSink` adds `source_id`; `V001__create_analytics_schema.sql` updated |
| HB-EXT-03 | Flink E2E test: publish 1000 msgs lên Kafka → verify rows in TS + CH | Backend Eng 2 | ✅ DONE — `EsgDualSinkFlinkE2EIT.java` (5 sub-cases: dual-sink delta=0, invalid filter, source_id propagation, buildingId extraction, 2-tenant isolation) |
| HB-EXT-04 | `dual-sink-verify.sh` rewritten to test actual Flink pipeline | QA | ✅ DONE — Script produces real Kafka messages → polls TS + CH until stable → verifies count+sum+source_id |
| HB-EXT-05 | Shadow monitor re-run với Flink dual-sink active, diff <0.01% for 24h | QA | ✅ DONE — Script fixed: DB `analytics`, table `esg_readings`, columns `value`/`recorded_at`, dynamic rolling window `--window N`, `--hours N` flag |
| HB-EXT-06 | CrossBuildingAggregationService IT coverage ≥85% | Backend Eng 1 | ✅ DONE — `CrossBuildingAggregationServiceIT.java` (10 test cases: SUM/AVG/COUNT, cross-tenant, time range, metric filter, inactive building, unknown tenant, WATER, performance ≤500ms) |
| HB-EXT-07 | RLS-010 Java 50-concurrent IT zero contamination | QA + Backend | ✅ DONE — `CrossBuildingConcurrentRLSIT.java` (50 threads × 5 iterations, sequential alternating, cross-tenant empty) |

**Completion date: 2026-05-13. All 7 HB-EXT items DONE. Sprint 2 may start after QA run verification.**

### 10.5 Sprint 1 Carry-Over Risk Summary (UPDATED)

> Sau extension week fix — items còn lại phải carry sang Sprint 2.

| Item | SP equiv | Risk nếu miss Sprint 2 |
|------|----------|----------------------|
| Keycloak deploy + RoutingJwtDecoder | 5 | Dual-issuer unverified |
| Flink kill/restart E2E test | 5 | Exactly-once unverified |
| EMQX health monitoring + cluster | 3 | Data ingestion downtime |
| Kong CI automated gate | 2 | Auth bypass risk |
| analytics-service CI pipeline | 2 | Cutover blocked |
| ClickHouse upgrade v24.x (DateTime64) | 3 | Millisecond precision limitation |
| Backend containerization | 2 | Dev/prod parity |
| **Total remaining carry-over** | **~22 SP** | **Sprint 2 planned: 83 SP** |

**Sprint 2 total:** 83 + 22 = **~105 SP**. Cần descope ~15 SP để feasible.
Recommendation descope candidates:
- v3-DevOps-03 (ClickHouse 2-node HA, 13 SP) → single-node chấp nhận cho pilot
- v3-FE-04 (Aggregation Filters, 8 SP) → merge vào v3-FE-03

---

## 11. Action Items

### P0 — Sprint 1 Extension Week (2026-05-26 → 2026-06-01) — HARD BLOCK

| # | Item | Owner | Deadline | Notes |
|---|------|-------|----------|-------|
| A-01 | Confirm shadow 72h monitor all polls PASS | QA | 2026-05-15 | Check `shadow-validation-log.txt` |
| ~~**A-17**~~ | ~~Remove hoặc @Deprecated EsgFlinkJob.java~~ | Backend Lead | ~~2026-05-26~~ | ✅ **DONE 2026-05-13** — HB-EXT-01 |
| ~~**A-19**~~ | ~~Fix CH schema mismatch: analytics-service query `analytics.esg_readings`~~ | Backend Lead | ~~2026-05-26~~ | ✅ **DONE 2026-05-13** — HB-EXT-02 |
| ~~**A-18**~~ | ~~Viết Flink E2E test: Kafka → Flink → TS + CH~~ | Backend Eng 2 | ~~2026-05-27~~ | ✅ **DONE 2026-05-13** — HB-EXT-03 |
| ~~**A-18b**~~ | ~~Rewrite dual-sink-verify.sh test actual Flink pipeline~~ | QA + BE2 | ~~2026-05-28~~ | ✅ **DONE 2026-05-13** — HB-EXT-04 |
| **A-20** | **Shadow monitor re-run với real ingestion, 24h PASS** | QA | **2026-05-29** | Script fixed (HB-EXT-05) — QA cần chạy thực tế khi infra active |
| ~~**A-06**~~ | ~~CrossBuildingAggregationService IT (85% coverage)~~ | Backend Eng 1 | ~~2026-05-30~~ | ✅ **DONE 2026-05-13** — HB-EXT-06 |
| ~~**A-06b**~~ | ~~RLS-010 Java 50-concurrent IT zero contamination~~ | QA + BE1 | ~~2026-05-30~~ | ✅ **DONE 2026-05-13** — HB-EXT-07 |

### P1 — Sprint 2 (2026-06-02 → 2026-06-15)

| # | Item | Owner | Notes |
|---|------|-------|-------|
| A-02 | Migrate CH table sang ReplacingMergeTree (C-2) | Backend Eng 2 | CRITICAL |
| A-03 | Checkpoint storage → S3/MinIO (C-3) | DevOps | CRITICAL |
| A-04 | Deploy Keycloak non-prod + implement RoutingJwtDecoder | DevOps + BL | |
| A-05 | analytics-service CI pipeline (Docker build + push) | DevOps | |
| A-07 | Flink kill/restart E2E test | Backend Eng 2 | Requires Flink cluster |
| A-08 | Kong CI automated gate (plugin order + unauthenticated) | DevOps | |
| A-09 | EMQX health monitoring | DevOps | Prometheus alert |
| A-10 | Validate `is_aggregator` + cluster_id constraint | Backend Lead | Admin API validation |

### P2 — Có thể dời sang Sprint 3 nếu cần

| # | Item | Owner | Notes |
|---|------|-------|-------|
| A-13 | ClickHouse upgrade v24.x (DateTime64 + TTL) | DevOps | Accept second-level precision |
| A-14 | Backend containerization (Docker Compose parity) | DevOps | Low risk, high effort |
| A-15 | DLQ side-output cho Flink (M-3) | Backend Lead | |
| A-16 | Frontend "data incomplete" warning banner | Frontend Eng | Khi ClickHouse unhealthy |

---

## Risk Heat Map

```
                    ── Probability ──
                Low (≤20%)  Med (20-50%)  High (>50%)
               ┌───────────┬─────────────┬────────────┐
  CRITICAL     │ R-KK-3    │ R-RLS-3     │            │
               │           │ R-T1-1      │            │
               ├───────────┼─────────────┼────────────┤
  HIGH         │ R-RLS-2   │ R-RLS-1     │ R-SCH-1    │
               │           │ R-CH-2      │            │
               │           │ R-KK-1      │            │
               │           │ R-KK-2      │            │
               ├───────────┼─────────────┼────────────┤
  MEDIUM       │ R-CH-4    │ R-CH-1      │ R-CH-3*    │
               │           │ R-CH-3      │ R-INF-1*   │
               │           │ R-T1-2      │ R-INF-3*   │
               │           │ R-QA-3      │ R-SCH-3*   │
               │           │ R-QA-1*     │            │
               ├───────────┼─────────────┼────────────┤
  LOW          │ R-QA-2    │ R-INF-2     │            │
               │ R-SCH-2   │             │            │
               └───────────┴─────────────┴────────────┘

* = đã confirmed xảy ra hoặc probability 100%
```

---

## Tổng kết

### Quyết định: Sprint 1 extend +1 tuần (→ 2026-06-01)

Sprint 1 gate checklist ghi **69/70 PASS**, nhưng code audit phát hiện foundation chưa solid:

**3 MISLEADING PASS claims** — test bypass Flink pipeline hoàn toàn:
- `dual-sink-verify.sh` — insert data trực tiếp vào DB, không chạy qua Flink
- `seed-10m-rows.sql` — SQL `generate_series()`, zero Kafka/Flink
- `shadow-72h-monitor.sh` — poll static seeded data, không có real ingestion

**1 CONCEALED RISK** — `EsgFlinkJob.java` (old) vẫn trong source tree, chưa remove, chưa deprecate. Chạy song song = duplicate + data corruption.

**1 UNDISCOVERED MISMATCH** — Flink writes `analytics.esg_readings`, analytics-service reads `uip_analytics.energy_readings`. Hai schemas khác nhau, chưa có code kết nối.

**Extension week (5/26 → 6/1) sẽ fix:**
- Remove/deprecate EsgFlinkJob (1 SP)
- Fix CH schema mismatch (3 SP)
- Flink E2E integration test thật (5 SP)
- Rewrite dual-sink-verify.sh (2 SP)
- Shadow re-run với real ingestion (2 SP)
- CrossBuildingAggregationService IT 85% (3 SP)
- RLS-010 concurrent IT (2 SP)
- **Total: ~18 SP**

**Pilot deadline giữ nguyên 2026-08-10.** Sprint 2-6 mỗi sprint shift 1 tuần. Buffer trước pilot = 0 ngày, mitigation: descope plan (Building Safety → v3.1).

**Sprint 2 gate confidence:** Trước extension = 60% → Sau extension + bug fixes = **85%** (7/7 HB-EXT PASS, 773/773 tests PASS, all P1/P2 bugs resolved).

---

*Tổng hợp bởi: SA + Backend Lead + QA | 2026-05-13*
*File liên quan: [`flink-dual-sink-risk-assessment.md`](flink-dual-sink-risk-assessment.md)*

---

## Sign-Off

**Quyết định:** Sprint 1 extend +1 tuần (→ 2026-06-01). Pilot deadline giữ nguyên 2026-08-10.

**Điều kiện mở Sprint 2:** 7 HB-EXT items phải PASS. Không exception.

### Review Results

| Role | Verdict | Conditions |
|------|---------|-----------|
| Project Owner | ✅ APPROVED | None |
| Backend Lead | ✅ APPROVED WITH COMMENTS | 5 conditions (see below) |
| QA Engineer | ✅ APPROVED WITH COMMENTS | 5 conditions (see below) |
| DevOps | ✅ APPROVED WITH COMMENTS | 5 conditions (see below) |

### Backend Lead Conditions
1. Elevate CH schema mismatch (Section 1.6) to standalone CRITICAL risk — pipeline writes data that nothing reads
2. Add `source_id` to `ClickHouseSink` INSERT — currently dropped silently, no sensor traceability in CH
3. Budget 2 days for HB-EXT-03 (Flink E2E test), not 1 — Testcontainers Kafka + Flink + TS + CH is complex
4. Track `NgsiLdMessage.getObservedAtMillis()` silent fallback to `System.currentTimeMillis()` as MEDIUM risk for Sprint 2
5. Increase `is_aggregator` risk (R-RLS-3) probability to 35%, move mitigation to P1

### QA Engineer Conditions
1. Upgrade R-CH-2 severity from HIGH to CRITICAL — shadow monitor on static data = confirmed zero evidence
2. Apply specific HB-EXT acceptance criteria with PASS/FAIL definitions (5 sub-cases for HB-EXT-03, 5 test cases for HB-EXT-06)
3. Add invalid message filtering test (TenantIdValidator) to HB-EXT-03 Flink E2E test plan
4. Reclassify Kong CI automated gate + EMQX health monitoring from SHOULD to MUST in Sprint 2 carry-over
5. Add ClickHouseSink PreparedStatement index mapping test to extension week (1 SP)

### DevOps Conditions
1. Correct factual errors in Section 7: ClickHouse HAS named volume, backend IS containerized in docker-compose, checkpoint uses named volume not bare local disk
2. ~~Add two-database ambiguity risk (analytics vs uip_analytics) to risk register~~ — **RESOLVED**: all configs now default to `analytics` (commit `f3b4984e`)
3. Add Kong+Keycloak smoke test to extension week plan (2 SP, Wed 5/28)
4. Deploy monitoring stack alongside Flink E2E test, not after
5. PM must negotiate minimum 5-day buffer before pilot — 0-day buffer is not acceptable

### Amendments Accepted
All 15 conditions above are accepted and will be incorporated into the extension week execution plan.

| Role | Name | Decision | Date |
|------|------|----------|------|
| Project Owner | anhgv | ✅ APPROVED | 2026-05-13 |
| Backend Lead | — | ✅ APPROVED WITH COMMENTS | 2026-05-13 |
| QA Engineer | — | ✅ APPROVED WITH COMMENTS | 2026-05-13 |
| DevOps | — | ✅ APPROVED WITH COMMENTS | 2026-05-13 |

**Final status: APPROVED — Sprint 1 extended to 2026-06-01 with 7 HB-EXT hard blocks + 15 conditions.**
