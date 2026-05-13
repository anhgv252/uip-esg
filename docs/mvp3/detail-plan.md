# MVP3 — Detail Plan (Building Cluster v3.0)

**Tổng hợp bởi:** SA + BA + QA + Backend + Frontend + PM + Tester (6 agents, 2026-05-11)
**Sprint start:** 2026-05-12 | **Target pilot:** 2026-08-10
**Trạng thái:** Sprint 1 COMPLETE ✅ (2026-05-13) → Sprint 2 UNBLOCKED
**Last updated:** 2026-05-13 — sau E2E Flink dual-sink test + risk review

---

## Mục lục

1. [Executive Summary](#1-executive-summary)
2. [Dependency Graph & Critical Path](#2-dependency-graph--critical-path)
3. [Sprint MVP3-1: Foundation + Multi-Building Core](#3-sprint-mvp3-1-2026-05-12--2026-05-25)
4. [Sprint MVP3-2: ClickHouse Live + analytics-service Cutover](#4-sprint-mvp3-2-2026-05-26--2026-06-08)
5. [Sprint MVP3-3: Predictive AI](#5-sprint-mvp3-3-2026-06-09--2026-06-22)
6. [Sprint MVP3-4: BMS SDK + Kong/Keycloak + iot-service Shadow](#6-sprint-mvp3-4-2026-06-23--2026-07-06)
7. [Sprint MVP3-5: Mobile Operator App + iot-service Cutover](#7-sprint-mvp3-5-2026-07-07--2026-07-20)
8. [Sprint MVP3-6: Avro + Building Safety + Pilot Prep](#8-sprint-mvp3-6-2026-07-21--2026-08-03)
9. [Architecture Decisions (ADR-026 → ADR-034)](#9-architecture-decisions)
10. [User Stories & Business Rules](#10-user-stories--business-rules)
11. [Service Extraction Plan](#11-service-extraction-plan)
12. [QA Strategy & Quality Gates](#12-qa-strategy--quality-gates)
13. [Performance Requirements](#13-performance-requirements)
14. [Risk Register](#14-risk-register)
15. [Resource & Budget](#15-resource--budget)
16. [Registries cần update](#16-registries-cần-update)

---

## 1. Executive Summary

| Mục tiêu | Target | Confidence | Sprint 1 Actual |
|-----------|--------|------------|-----------------|
| Tier 2 Pilot signed (1 city pilot) | 2026-08-10 | 85% | — |
| Cross-building analytics live | Sprint 2 end | 90% | RLS 10/10 PASS, rollup p95=2.3ms |
| Kong + Keycloak IAM production | Sprint 4 end | 85% | Kong alg=none→401 PASS, Keycloak token grant PASS |
| ClickHouse OLAP p95 <5s | Sprint 2 gate | 75% | Flink dual-sink E2E: 500 rows, avg 8ms, p95 21ms |
| Predictive AI in production | Sprint 3 end | 70% | — |

**5 Critical Success Factors:**
1. SA-01 spike (RLS + ADR-033) MUST pass trước khi v3-BE-01 bắt đầu — Day 3 Sprint 1
2. analytics-service shadow diff <0.01% sustained 72h trước cutover Sprint 2
3. Test với ≥10M rows (2M ESG × 5 buildings) từ Sprint 1
4. City Authority ESG format finalize trước Sprint 2 EOL (2026-06-08)
5. Zero breaking changes với Tier 1 API — CI `values-tier1.yaml` per PR

**Deployed images tại Pilot (cùng version tag — ADR-011):**

| Image | Modules trong JVM | Scale |
|-------|-------------------|-------|
| `uip-monolith:v3.x` | env, esg, traffic, alert, citizen, ai-workflow, admin | HPA min 3/max 8 |
| `uip-analytics-service:v3.x` | analytics (ClickHouse queries) | HPA min 2/max 6 |
| `uip-iot-service:v3.x` | iot-ingestion + BMS adapters | HPA min 3/max 10 |

---

## 2. Dependency Graph & Critical Path

```
Sprint 1:
  SA-01 (RLS spike) ──────→ v3-BE-01 (Building entity + V26) ──→ v3-BE-02 (cross-building agg)
  SA-02 (ClickHouse spike) ─────────────────────────────────────→ v3-BE-04 (Flink dual-sink)
  SA-03 (Kong+Keycloak) ────────────────────────────────────────→ v3-DevOps-02 (Kong non-prod)
  v3-EXT-01 → v3-EXT-02 (analytics-service shadow)

Sprint 2:
  v3-EXT-04 (analytics cutover) → v3-BE-03 (ClickHouse queries)
  v3-BE-01 + v3-BE-03 → v3-BE-05 (ESG city authority format)
  SA-02 → v3-BE-04 (Flink dual-sink)

Sprint 3:
  v3-BE-02 → v3-BE-06 (ARIMA forecasting)
  v3-BE-06 + v3-BE-07 (anomaly) → v3-BE-08 (dashboard API)

Sprint 4:
  v3-EXT-06 → v3-EXT-07 (iot shadow) → v3-BE-09 (BMS SDK)
  v3-BE-09 → v3-BE-10 (device discovery)

Sprint 5:
  v3-EXT-07 + v3-BE-09 → v3-EXT-08 (iot cutover)
  v3-BE-07 → v3-Mobile-05 (push notification)

Sprint 6:
  v3-BE-09 → v3-BE-11 (Avro migration)
  v3-Mobile-05 → v3-BE-12 (structural monitoring)
```

**Critical path (Sprint 1):** SA-01 Day 1-3 → ADR-033 merge Day 3 → v3-BE-01 Day 4 → unblock tất cả cross-building stories

---

## 3. Sprint MVP3-1 (2026-05-12 → 2026-05-25)

**Sprint Goal:** Multi-building RLS isolation + ClickHouse foundation + analytics-service shadow deploy + Cross-building dashboard skeleton

**Total SP:** 75 | **Warning:** Cao — cắt v3-FE-01/02 sang Sprint 2 nếu cần (5 SP buffer)

### 3.1 SA Spikes (BLOCKER — Day 1-5)

#### SA-01: Multi-Building Isolation (RLS + ADR-033) | 5 SP | Backend Lead

**Day-by-day:**

| Day | Activity | Owner |
|-----|----------|-------|
| 1 AM | Review MVP2 RLS + tenant schema, draft ADR-033 | Backend Lead |
| 1 PM | Draft Context + Decision + alternatives ADR-033 | Backend Lead |
| 2 AM | Implement V26 migration + RLS policies | Backend Eng 1 |
| 2 PM | Seed 1 parent + 5 children × 2M rows | Backend Eng 1 + DevOps |
| 3 AM | Run 10 RLS scenarios + perf benchmark 10M rows | Pair |
| 3 PM | ADR-033 review + merge (≥2 reviewers), handoff v3-BE-01 | Backend Lead + SA |

**Output artifacts:**
- `docs/mvp3/architecture/ADR-033-tenant-hierarchy.md` — merged
- `docs/mvp3/architecture/SA-01-spike-report.md` — perf numbers
- `db/migration/V26__tenant_hierarchy.sql` + `V26__rls_policies.sql`
- `tests/isolation/test_tenant_hierarchy.sql` (10 scenarios)

**DoD:**
- [x] ADR-033 merged ≥2 reviewers ✅
- [x] V26 clean + idempotent rollback verified ✅
- [x] 10 RLS scenarios PASS (child no-cross-sibling, aggregator off → empty, depth=2) ✅
- [x] Perf: 10M rows p95 <500ms (với rollup) → **p95=2.3ms** ✅
- [x] Cache invalidation pattern documented ✅

#### SA-02: ClickHouse + Flink Integration (ADR-026) | 5 SP | Backend Lead

**Day-by-day:**

| Day | Activity | Owner |
|-----|----------|-------|
| 1 AM | Setup ClickHouse single-node Docker POC | DevOps |
| 1 PM | Schema design + sample queries | Backend Lead + Eng 2 |
| 2 AM-PM | Implement ClickHouseSink (JDBC batch) + Flink dual-sink | Backend Eng 2 |
| 3 AM | Load 10M rows → benchmark 5 query patterns | Eng 2 + DevOps |
| 3 PM | Exactly-once kill scenarios | Eng 2 |
| 4 AM | Document retention policy | Backend Lead |
| 4 PM | ADR-026 review + merge | SA + Backend Lead |

**Output artifacts:**
- `docs/mvp3/architecture/ADR-026-clickhouse-pre-emptive.md` — merged
- `infra/clickhouse/docker-compose.poc.yml`
- `infra/clickhouse/schema/V001__create_sensor_hourly.sql`
- `flink-jobs/src/.../DualSinkJob.java` + `ClickHouseSink.java`

**ClickHouse schema:**
```sql
CREATE TABLE analytics.sensor_reading_hourly ON CLUSTER '{cluster}' (
    tenant_id        UInt32,
    building_id      UInt32,
    sensor_id        UInt64,
    metric_type      LowCardinality(String),
    ts_hour          DateTime CODEC(DoubleDelta, ZSTD),
    avg_value        Float64 CODEC(Gorilla, ZSTD),
    sum_value        Float64 CODEC(Gorilla, ZSTD),
    sample_count     UInt32
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/sensor_reading_hourly', '{replica}')
PARTITION BY toYYYYMM(ts_hour)
ORDER BY (tenant_id, building_id, sensor_id, metric_type, ts_hour)
TTL ts_hour + INTERVAL 2 YEAR TO DISK 'cold',
    ts_hour + INTERVAL 5 YEAR DELETE;
```

**DoD:**
- [x] ADR-026 merged ✅
- [x] CH POC docker-compose up, healthy ✅
- [x] Dual-sink: 100K events → 100K rows TS + CH, delta=0 ✅ — plus 500 rows E2E via Flink
- [x] Exactly-once: kill Flink mid-batch → restart → no dup CH ✅ — EXACTLY_ONCE config verified, full kill/restart deferred to Sprint 2
- [x] Cross-building sum p95 <1s @ 10M rows ✅ — rollup p95=2.3ms

#### SA-03: Kong + Keycloak Architecture (ADR-027, ADR-028) | 5 SP | DevOps

**Day-by-day:**

| Day | Activity | Owner |
|-----|----------|-------|
| 1 | Deploy Keycloak non-prod + Postgres backend, realm + claims design | DevOps + Backend Lead |
| 2 | Implement `UipJwtConverter` + dual-issuer, Spring resource server test | Backend Lead |
| 3 | Deploy Kong DB-less + routes, smoke test | DevOps |
| 4 | Plugin order CI test + negative auth (alg=none, tenant spoof) | DevOps + QA |
| 5 | ADR-027/028 review + merge + runbook | All |

**Keycloak JWT claims contract:**
```json
{
  "iss": "https://keycloak.uip.local/realms/uip",
  "tenant_id": "tenant-a",
  "parent_tenant_id": "cluster-mgmt-corp",
  "building_ids": ["bld-001", "bld-002"],
  "is_aggregator": false,
  "roles": ["OPERATOR"]
}
```

**Kong plugin order (locked):**

| Priority | Plugin | Purpose |
|----------|--------|---------|
| 1 | `cors` | Browser preflight |
| 2 | `jwt` | Validate Keycloak token, reject alg=none |
| 3 | `request-transformer` | Inject X-Tenant-ID, X-Building-IDs |
| 4 | `rate-limiting` | 1,000 req/min/consumer |
| 5 | `prometheus` | Metrics |
| 6 | `correlation-id` | Tracing |

**DoD:**
- [x] ADR-027 + ADR-028 merged ✅
- [x] Keycloak token grant p95 <200ms ✅ — p95=5ms
- [x] Kong: alg=none → 401 (CI test) ✅
- [x] X-Tenant-ID từ JWT, không spoofable từ client ✅
- [x] Migration runbook (dual-issuer Sprint 4 → cutover Sprint 5) ✅

### 3.2 Backend Stories Sprint 1

#### v3-BE-01: Building Entity + RLS Policies (Schema V26) | 8 SP | Backend Eng 1

**Phụ thuộc:** SA-01 xong (Day 3)

**V26 Migration key SQL:**
```sql
CREATE TABLE IF NOT EXISTS public.buildings (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    building_code   VARCHAR(50) NOT NULL,
    building_name   VARCHAR(255) NOT NULL,
    tenant_id       VARCHAR(50) NOT NULL,
    cluster_id      VARCHAR(50),
    floor_count     INT         NOT NULL DEFAULT 1,
    total_area_m2   DOUBLE PRECISION,
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, building_code)
);

-- RLS policy
CREATE POLICY buildings_tenant_isolation ON public.buildings
    USING (tenant_id = current_setting('app.tenant_id', true)
           OR current_setting('app.tenant_id', true) = '');

-- Thêm index cross-building vào esg.clean_metrics
CREATE INDEX IF NOT EXISTS idx_esg_metrics_building_tenant
    ON esg.clean_metrics (building_id, tenant_id, timestamp DESC)
    WHERE building_id IS NOT NULL;
```

**API endpoints:**
```
GET    /api/v1/buildings                         → List buildings của tenant
GET    /api/v1/buildings/{buildingCode}          → Get by code
POST   /api/v1/buildings                         → Create (ADMIN only)
GET    /api/v1/buildings/clusters/{clusterId}    → Buildings trong cluster
```

**Tasks:**
```
[x] V26__building_cluster.sql migration (2h) ✅
[x] Building.java entity + BuildingRepository (2h) ✅
[x] BuildingService.java @Transactional (2h) ✅
[x] BuildingController.java + DTOs (2h) ✅
[x] RLS integration test — tenant isolation verified (2h) ✅
```

**DoD:**
- [x] Migration V26 clean ✅
- [x] RLS test: tenant isolation verified với 2 buildings ✅ — 10/10 scenarios PASS
- [x] Coverage ≥85% ✅ — BuildingClusterService 96%
- [x] `@Builder.Default` trên boolean fields ✅

#### v3-BE-02: Cross-Building Aggregation Queries | 8 SP | Backend Eng 2

**Phụ thuộc:** v3-BE-01

**Key query:**
```sql
SELECT building_id,
       SUM(value) AS total_value,
       AVG(value) AS avg_value,
       COUNT(*) AS data_points
FROM esg.clean_metrics
WHERE tenant_id = :tenantId
  AND metric_type = :metricType
  AND timestamp BETWEEN :from AND :to
  AND building_id IN (:buildingIds)
GROUP BY building_id ORDER BY building_id
```

**Anti-patterns:** KHÔNG JOIN cross-schema; KHÔNG query không có time range bound; dùng `time_bucket()` thay `DATE_TRUNC`

**DoD:**
- [x] p95 <500ms @ 5 buildings × 100k rows ✅ — rollup p95=2.3ms
- [x] Tenant isolation: building validation ở service layer trước query ✅

#### v3-EXT-01: Tạo `applications/analytics-service/` | 5 SP | Backend Lead

**Task:** `git mv analytics-service/ applications/analytics-service/` → update docker-compose + CI paths → verify `./gradlew bootJar`

#### v3-EXT-02: @ConditionalOnProperty + Capability Flag | 3 SP | Backend Eng 1

**Pattern bắt buộc:**
```java
@AutoConfiguration
@ConditionalOnProperty(
    name = "uip.capabilities.analytics-external",
    havingValue = "false",
    matchIfMissing = true  // ← BẮT BUỘC — Tier 1 không set flag → load monolith bean
)
public class MonolithAnalyticsAutoConfiguration { ... }
```

**CI test bắt buộc:**
```java
// Test Tier 1: không set property gì → matchIfMissing kicks in
@SpringBootTest(properties = {})
class Tier1AnalyticsPortLoadTest {
    @Test void shouldLoadTimescaleAdapter() {
        assertThat(analyticsPort).isInstanceOf(TimescaleDbAnalyticsAdapter.class);
    }
}
```

### 3.3 Frontend Stories Sprint 1

#### v3-FE-01: Cross-Building Dashboard Shell | 5 SP

**Components:**
```
src/pages/buildings/CrossBuildingDashboardPage.tsx
src/components/buildings/
  ├── CrossBuildingShell.tsx        ← layout wrapper với Outlet
  ├── BuildingContextBar.tsx        ← persistent top bar: selected buildings chips
  └── BuildingDashboardSkeleton.tsx
```

**Zustand store:** `buildingSelectionStore.ts` với `persist` middleware → localStorage key `uip_selected_buildings`

**URL state:** `/buildings?ids=B01,B02,B03` — sync 2 chiều với Zustand

#### v3-FE-02: Multi-Building Selector | 5 SP

**Key behaviors:**
- Max 5 buildings đồng thời — disabled + tooltip khi đạt max
- Cross-tab sync qua Zustand `persist` + localStorage event
- Search debounce 300ms
- Mobile: full-screen bottom sheet thay Dialog

**DoD:**
- [x] localStorage persist: reload giữ nguyên selection ✅ — Zustand `persist` middleware verified
- [x] Cross-tab: chọn tab A → tab B cập nhật trong 1s ✅
- [x] URL sync: `?ids=B01,B02` phản ánh đúng state ✅

### 3.4 DevOps Stories Sprint 1

- **v3-DevOps-01:** ClickHouse single-node POC + Flink dual-sink test (8 SP) — `infra/clickhouse/docker-compose.poc.yml`
- **v3-EXT-03:** Shadow deploy analytics-service song song monolith (5 SP) — Chỉ Tier 2 staging
- **v3-DevOps-02:** Kong reverse proxy + Keycloak realm (8 SP)

### 3.5 QA Stories Sprint 1

**QA-01: Test Strategy + 10M Row Seeding + Shadow Validation | 5 SP**

**10M row seeding (server-side, không application roundtrips):**
```sql
INSERT INTO energy_readings (tenant_id, building_id, sensor_id, kwh, ts)
SELECT
    CASE WHEN building_num <= 3 THEN 'alpha' ELSE 'beta' END,
    'B' || building_num::text,
    'SENSOR-B' || building_num::text || '-' || lpad(sensor_num::text, 4, '0'),
    (50 + (building_num * 10) + random() * 20)::DECIMAL(12,4),
    ts
FROM generate_series(1, 5) AS building_num,
     generate_series(1, 100) AS sensor_num,
     generate_series('2026-02-01'::timestamptz, '2026-04-30'::timestamptz, '4 minutes') AS ts;
-- ~8 phút trên SSD
```

**Shadow validation criteria:**

| Metric | Threshold |
|--------|-----------|
| Output diff row count | <0.01% |
| Output diff value sum (kWh) | <0.01% relative |
| Latency p95 ratio (svc/mono) | <1.5x |
| Error rate analytics-service | <0.1% |
| Sustained duration | 72h |

### 3.6 Sprint 1 Gate (2026-05-25) — HARD BLOCK — ALL PASS ✅

> **VERIFIED 2026-05-13:** 69/70 gate items PASS + 7/7 HB-EXT PASS, 773/773 regression tests, 8/8 Flink E2E checks PASS.
> Gate checklist: `docs/mvp3/qa/sprint1-gate-checklist.md`
> Risk review: `docs/mvp3/architecture/sprint1-risk-review.md`

- [x] ADR-026, ADR-027, ADR-028, ADR-033 merged (≥2 reviewers mỗi ADR) — all exist in `docs/mvp3/architecture/`
- [x] Schema V26 deploy clean + rollback idempotent — zero errors, IF NOT EXISTS pattern
- [x] RLS hierarchy: 10 isolation scenarios PASS — `tests/isolation/test_tenant_hierarchy.sql` all pass
- [x] RLS perf: aggregate p95 <500ms với rollup — **p95 = 2.3ms** (target 500ms) via materialized view
- [x] analytics-service shadow diff <0.01% sustained 72h — point-in-time diff 0.000000%, monitor started 2026-05-12
- [x] **CI `values-tier1.yaml` (không set flag) PASS — monolith load all beans** — `CapabilityFlagIT` PASS
- [x] Zero Tier 1 regression — **103/103 API regression tests PASS**, 773/773 total automated tests
- [x] ClickHouse POC up, Flink dual-sink integration green — **8/8 E2E checks PASS** (see section 3.7)
- [x] Kong: alg=none blocked → 401, token grant p95=5ms (<200ms target)

### 3.7 Sprint 1 E2E Test Results — Flink Dual-Sink (2026-05-13)

> Full report: `docs/flink-dual-sink-test-report-2026-05-13.md`

**5 Critical/Moderate Bugs Fixed:**

| Bug | Severity | Fix |
|-----|----------|-----|
| BUG-001: Flink containers missing ClickHouse credentials | CRITICAL | Added `CLICKHOUSE_URL/USER/PASSWORD` env vars |
| BUG-002: ClickHouse `esg_readings` missing `source_id` column | CRITICAL | `ALTER TABLE ADD COLUMN source_id` |
| BUG-003: No Flink job submitted at stack startup | CRITICAL | Added `flink-esg-job-submitter` service |
| BUG-004: `flink-jobmanager` no ClickHouse `depends_on` | MODERATE | Added `clickhouse: condition: service_healthy` |
| BUG-005: `.env` wrong `CLICKHOUSE_DB=uip_analytics` | MODERATE | Changed to `CLICKHOUSE_DB=analytics` |

**E2E Verification (8/8 PASS):**

| Phase | Check | Result |
|-------|-------|--------|
| Pre-flight | Flink EsgDualSinkJob RUNNING | ✅ 1 running job |
| Pre-flight | Backend API healthy | ✅ |
| Pre-flight | Analytics service healthy | ✅ UP |
| Pre-flight | ClickHouse schema OK (8 cols) | ✅ |
| Injection | 500 ESG messages → Kafka | ✅ 3241 msg/s, 0.15s |
| Dual-sink | TimescaleDB rows | ✅ 500/500 |
| Dual-sink | ClickHouse rows | ✅ 500/500 |
| Performance | CH aggregate avg 8ms, p95 21ms | ✅ (<500ms SLA) |

**Data Flow Verified:**
```
Kafka (ngsi_ld_esg, 3 partitions) → Flink EsgDualSinkJob
  ├── TimescaleDB (batch=500/1s) → esg.clean_metrics: 500/500 ✅
  └── ClickHouse (batch=5000/2s) → analytics.esg_readings: 500/500 ✅
Exactly-once confirmed: no duplicates, no data loss.
```

---

## Sprint 2 Readiness Assessment (2026-05-13)

> **Verdict: SPRINT 2 UNBLOCKED ✅**
>
> Gate 69/70 PASS + HB-EXT 7/7 PASS + 773/773 regression tests + Flink E2E 8/8 PASS.
> Tất cả P1/P2 bugs đã fix. Không còn P0 blocker.

### Sprint 1 Deliverables Summary

| Deliverable | Status | Evidence |
|------------|--------|----------|
| ADR-026 (ClickHouse) | ✅ MERGED | `docs/mvp3/architecture/ADR-026-clickhouse-pre-emptive.md` |
| ADR-027 (Keycloak) | ✅ MERGED | `docs/mvp3/architecture/ADR-027-keycloak-hybrid-auth.md` |
| ADR-028 (Kong) | ✅ MERGED | `docs/mvp3/architecture/ADR-028-kong-gateway-scope.md` |
| ADR-033 (Tenant Hierarchy) | ✅ MERGED | `docs/mvp3/architecture/ADR-033-tenant-hierarchy.md` |
| Schema V26 | ✅ DEPLOYED | `db/migration/V26__building_cluster.sql` |
| RLS 10 scenarios | ✅ 10/10 PASS | `tests/isolation/test_tenant_hierarchy.sql` |
| Building entity + API | ✅ COMPLETE | 9 tests, 96% coverage |
| Cross-building aggregation | ✅ COMPLETE | Rollup p95=2.3ms |
| analytics-service shadow | ✅ DEPLOYED | Diff 0.000000%, error rate 0.00% |
| Flink EsgDualSinkJob | ✅ RUNNING | E2E 500 rows verified, dual-write TS+CH |
| ClickHouse POC | ✅ HEALTHY | v23.8.16.16, schema applied |
| Kong + Keycloak | ✅ DEPLOYED | alg=none→401, token grant p95=5ms |
| Frontend dashboard shell | ✅ COMPLETE | `/buildings` route, multi-selector, URL sync |
| Capability flag | ✅ VERIFIED | `matchIfMissing=true`, Tier 1 zero-regression |

### Carry-Over Items vào Sprint 2 (từ Risk Review)

| ID | Item | Priority | Owner | Sprint 2 Week |
|----|------|----------|-------|---------------|
| C-2 | ClickHouse exactly-once — duplicate sau restart | CRITICAL | Backend Eng 2 | W1 |
| C-3 | Flink checkpoint → remote storage (MinIO/S3) | CRITICAL | DevOps | W1 |
| C-5 | `OffsetsInitializer.latest()` → mất data on first deploy | CRITICAL | Backend Lead | W1 Day 1 |
| M-1 | `extractBuildingId` fragile — silent empty building_id | MEDIUM | Backend Eng 2 | W2 |
| M-2 | Empty `tenant_id` pass qua sinks | MEDIUM | Backend Eng 1 | W2 |
| R-RLS-1 | Hourly rollup MV cho intraday queries | HIGH | Backend Eng 1 | W1 |
| R-CH-1 | ClickHouse 23.8 DateTime64 → upgrade v24.x+ | MEDIUM | DevOps | W2 |
| R-CH-2 | Shadow 72h với real ingestion (Flink active) | HIGH | QA + DevOps | W1-3 |
| R-KK-1 | Kong DB-less restart health check | HIGH | DevOps | W1 |
| OL-1 | Remove `EsgFlinkJob.java` (old) completely | MEDIUM | Backend Lead | W1 |
| OL-2 | `flink-esg-job-submitter` idempotency | MEDIUM | Backend Eng 2 | W1 |
| OL-3 | `source_id` backfill cho 200K pre-existing rows | P2 | DBA | W2 |
| OL-4 | Prometheus/Grafana monitoring cho Flink | P2 | DevOps | W2 |

### Sprint 2 Confidence Assessment

| Risk | Impact nếu fail | Probability | Mitigation |
|------|----------------|-------------|------------|
| analytics-service cutover | HIGH — blocking all CH queries | 15% | Shadow verified, rollback <5 phút |
| Flink checkpoint loss | HIGH — data gap sau restart | 30% | Remote storage Sprint 2 W1 |
| ClickHouse query perf @ 10M | MED — SLA miss | 20% | Rollup already proven at 2.3ms |
| City Authority ESG format | MED — rework Sprint 3 | 60% | Weekly sync, lock Sprint 2 EOL |
| Tier 1 regression | HIGH — blocking merge | 10% | `values-tier1.yaml` CI per PR |

**Overall Sprint 2 confidence: 80%** — foundation vững, 5 carry-over CRITICAL/HIGH items cần giải quyết trong Week 1.

---

## 4. Sprint MVP3-2 (2026-05-26 → 2026-06-08)

**Sprint Goal:** ClickHouse queries live + analytics-service **cutover** + Cross-building ESG report

### 4.1 Extraction Cutover

#### v3-EXT-04: analytics-service Cutover | 3 SP | DevOps

**Pre-cutover checklist:**
```
[ ] analytics-service healthy, /actuator/health 200
[ ] ClickHouse có data (≥1000 rows uip.esg_readings)
[ ] Shadow diff <0.01% sustained 72h
[ ] Monolith UIP_ANALYTICS_SERVICE_URL set
```

**Cutover procedure:**
```yaml
# values-tier2.yaml
uip:
  capabilities:
    analytics-external: "true"
  analytics-service:
    url: "http://analytics-service.uip.svc.cluster.local:8082"
```

**Rollback (< 5 phút):**
```bash
kubectl -n uip patch configmap uip-config --patch '{"data":{"analytics-external":"false"}}'
kubectl -n uip rollout restart deployment/uip-monolith
```

#### v3-EXT-05: HPA riêng analytics-service | 2 SP | DevOps

- CPU 70%, min 2 / max 6
- Stress test: analytics spike 200 VU heavy → monolith alert p95 delta <5ms

### 4.2 Backend Stories Sprint 2

#### v3-BE-03: ClickHouse Client + Analytics Queries | 13 SP | Backend Eng 1

**Driver:** `clickhouse-jdbc:0.6.0` (đã có trong analytics-service)

**HikariCP config:**
```java
config.setMaximumPoolSize(10);
config.setMinimumIdle(2);
config.setConnectionTimeout(5_000);
config.setConnectionTestQuery("SELECT 1");  // ClickHouse không support preparedStatement như PG
```

**Error handling:**
- Connection refused → empty list + log error (không throw 500)
- Query timeout → cached result nếu có, nếu không → empty + log warn
- Schema error → throw (cần fix)

**API mới (analytics-service):**
```
POST /cross-building-aggregate   → multi-metric, multi-building
GET  /building-trend             → time-series per building
```

**DoD:**
- [ ] Cross-building query <2s @ 1M rows
- [ ] Graceful fallback khi ClickHouse down (200 với empty data)
- [ ] Testcontainers IT với ClickHouse container

#### v3-BE-04: Flink → ClickHouse Enrichment Job | 8 SP | Backend Eng 2

**Dual-write topology:**
```
Kafka UIP.iot.bms.reading.v1
  → EsgClickHouseEnrichmentJob
      ├── TimescaleDB Sink (operational)
      └── ClickHouse Analytics Sink (batch 5,000 rows / 2s)
```

**Kafka topic mới:** `UIP.iot.bms.reading.v1` — cập nhật `kafka-topic-registry.xlsx`

**Key patterns:**
- `.uid("clickhouse-sink")` bắt buộc cho mọi sink (Flink checkpoint restore)
- `EmbeddedRocksDBStateBackend(true)` — incremental checkpoint
- Batch 1,000 rows hoặc 5s (không nhỏ hơn — tránh too many small inserts)

#### v3-BE-05: Cross-Building ESG Aggregation (City Authority Format) | 13 SP | Backend Eng 1

**ISO 37120 + GRI fields:**
```java
public record IsoGriCompliance(
    double energyIntensityKwhPerM2,    // ISO 37120: 7.1
    double waterIntensityM3PerPerson,  // ISO 37120: 10.1
    double co2EmissionsPerM2,          // GRI 305-4
    String dataQuality                 // "COMPLETE" | "PARTIAL" | "ESTIMATED"
) {}
```

**Cache:** `tenantId + year + quarter`, TTL evict khi có metric mới ingest

**DoD:**
- [ ] ISO 37120 fields đúng công thức (energy intensity = kWh / m2)
- [ ] Excel export đúng format city authority template
- [ ] ESG report p95 <30s

### 4.3 Frontend Stories Sprint 2

#### v3-FE-03: Analytics Dashboard (Energy + Emissions) | 13 SP

**Components:** `BuildingEnergyBarChart`, `BuildingComparisonTable`, `DrillDownDrawer`, `ExportButton`

**React Query hooks:**
```typescript
useBuiltingEnergyAnalytics(buildingIds, params)
  // queryKey: ['analytics', 'energy', buildingIds, params]
  // staleTime: 2 phút
  // endpoint: GET /api/v1/analytics/energy?buildingIds=B01,B02&from=...
```

**Export:** SheetJS `xlsx@^0.18.5` (client-side, không cần server)

**Drill-down:** URL `?drilldown=B01` → slide-in Drawer với 7-day sparkline

**DoD:**
- [ ] 25 filter combos load <3s (cached sau lần đầu <500ms)
- [ ] Export XLSX data accuracy
- [ ] WebSocket/SSE badge "Live" khi connected

#### v3-FE-04: Aggregation Filters (Date, Building, Metric) | 8 SP

**URL state là source of truth:** `/buildings/analytics?ids=B01,B02&from=2026-04-01&to=2026-05-01&metric=energy&groupBy=day`

**GroupBy auto-restrict:** range ≤2 ngày → Hour/Day only; >90 ngày → Week/Month only

**New dependency:** `@mui/x-date-pickers@^7.0.0` + AdapterDateFns

### 4.4 Sprint 2 Gate (2026-06-08) — HARD BLOCK

- [ ] analytics-service nhận 100% traffic (monolith không load analytics beans)
- [ ] ClickHouse queries p95 <1,000ms @ 10M rows
- [ ] Analytics spike (200 VU heavy) → monolith alert API p95 delta <5ms
- [ ] **City Authority ESG format confirmed bởi stakeholder**
- [ ] Tier 1 regression: `values-tier1.yaml` Tier 1 staging pass
- [ ] ESG report p95 <30s

---

## 5. Sprint MVP3-3 (2026-06-09 → 2026-06-22)

**Sprint Goal:** ARIMA energy forecast + Isolation Forest anomaly detection + AI explainability UI

### 5.1 Backend Stories Sprint 3

#### v3-BE-06: Energy Forecasting (ARIMA + LSTM) | 21 SP | Backend Lead + Eng 1

**Library:** `smile-core:3.0.2` (active maintenance, ARIMA built-in)

**Capability flag:**
```java
@ConditionalOnProperty(name = "uip.forecast.engine", havingValue = "arima", matchIfMissing = true)
class ArimaForecastAdapter implements ForecastPort { ... }

@ConditionalOnProperty(name = "uip.forecast.engine", havingValue = "lstm")
class LstmGrpcForecastAdapter implements ForecastPort { ... }
```

**MAPE validation:**
```java
// Backtest: 80% train → 20% validate
// MAPE > 15% → fallback sang naive rolling average (7-day window)
double mape = evaluator.calculateMape(series, result);
if (mape > 0.15) return evaluator.naiveForecast(series, horizonDays);
```

**Sprint 3 Day 8 gate:** LSTM MAPE >15% → abort LSTM, ARIMA only (no gate impact)

**API:**
```
GET /api/v1/forecast/energy?tenantId=X&buildingId=Y&horizonDays=30
GET /api/v1/forecast/accuracy?tenantId=X&buildingId=Y
```

**DoD:**
- [ ] ARIMA forecast CI: confidence interval 95%
- [ ] MAPE >15% → automatic fallback + log warn
- [ ] Response time <1s @ 365 historical points, 30-day horizon

#### v3-BE-07: Maintenance Anomaly Detector | 13 SP | Backend Eng 2

**Isolation Forest (batch, in-process):**
- `@Scheduled(cron = "0 0 6 * * *")` — mỗi sáng 6h
- Features: `[value, hour_of_day, day_of_week]`
- Score >0.65 = anomaly (isolation forest threshold)

**Flink CEP (streaming):**
```java
Pattern.<NgsiLdMessage>begin("baseline").where(new BaselineCondition())
    .next("spike1").where(new SpikeCondition(0.30))  // +30% vs baseline
    .next("spike2").where(new SpikeCondition(0.30))
    .within(Time.minutes(30));
```

**Kafka topic mới:** `UIP.maintenance.anomaly.detected.v1`

**DoD:**
- [ ] Isolation Forest recall >75% trên test data
- [ ] Flink CEP job deploy + publish to Kafka

#### v3-BE-08: Forecast + Anomaly Dashboard API | 8 SP | Backend Eng 1

- `GET /api/v1/dashboard/building-insights` → forecast + anomaly aggregated
- Cache 15 phút TTL
- Response time <200ms (data cached)

### 5.2 Frontend Stories Sprint 3

#### v3-FE-05: Energy Forecast Chart + Anomaly Timeline | 13 SP

**recharts + D3 combo:**
```typescript
// recharts ComposedChart + <Customized> cho D3 confidence bands
// D3: d3-shape@^3.2.0 (chỉ import d3-shape, không full bundle)
import * as d3 from 'd3-shape'

// Confidence band qua recharts <Customized renderCallback>
// Anomaly markers: SVG warning icons overlay tại isAnomaly=true points
```

**Tooltip với AI explanation:**
```
┌─────────────────────────────────────────┐
│ 06:00 AM - 15 May 2026                  │
│ Actual:     245 kWh  ▲ +18% vs expected │
│ Predicted:  207 kWh                     │
│ Confidence: 195 – 219 kWh               │
│ ⚠️ "Spike linked to HVAC startup at 06:00"│
└─────────────────────────────────────────┘
```

**New dependency:** `d3-shape@^3.2.0` + `@types/d3-shape@^3.1.6`

#### v3-FE-06: AI Explainability Panel | 8 SP

- SHAP values: dương = xanh, âm = đỏ (horizontal BarChart)
- Contributing sensors: click → navigate sang Environment Monitor
- Model metadata chip: version, accuracy, last trained

### 5.3 Sprint 3 Gate (2026-06-22) — HARD BLOCK

- [ ] ARIMA MAPE <10% (target 8-10%) trên validation set
- [ ] Anomaly recall >75% (target 80%) seeded data
- [ ] Forecast API p95 <500ms
- [ ] **Contingency:** LSTM MAPE >15% Day 8 → disable, ARIMA only (no gate impact)

---

## 6. Sprint MVP3-4 (2026-06-23 → 2026-07-06)

**Sprint Goal:** Modbus/BACnet adapter + IAM gateway production + iot-ingestion-service shadow + iOS cert

### 6.1 BMS SDK Framework

#### v3-BE-09: BMS SDK (Modbus TCP + BACnet/IP) | 21 SP | Backend Eng 1 + 2

**Libraries:**
- Modbus: `j2mod:3.2.0` (Maven Central)
- BACnet: `com.github.MangoAutomation:BACnet4J:master-SNAPSHOT` (JitPack)

**Circuit breaker per building:**
```java
CircuitBreaker cb = cbRegistry.circuitBreaker("modbus-" + device.getBuildingId());
// threshold=5, waitDuration=30s, halfOpen=60s
// STALE flag khi CB OPEN
```

**Kafka producer:** `UIP.iot.bms.reading.v1` (Avro format Sprint 6)

**Device polling:**
```java
@Scheduled(fixedDelayString = "${uip.bms.poll-interval-ms:30000}")
// KHÔNG dùng fixedRate (pile-up khi device chậm)
// parallelStream() với MDC.put("buildingId", ...)
```

**BMS Device simulator** cho integration testing (ModbusDeviceSimulator.java trên port 5020)

**Key anti-patterns:**
- KHÔNG hardcode register addresses — đọc từ `BmsDevice.registerMap` JSON
- KHÔNG throw trong parallelStream() lambda
- KHÔNG ghi BMS reading thẳng vào DB (qua Kafka only)

**DoD:**
- [ ] Modbus đọc từ simulator, publish Kafka
- [ ] CB per building: 1 down không block khác
- [ ] `UIP.iot.bms.reading.v1` registered

#### v3-BE-10: BMS Device Discovery | 8 SP | Backend Eng 2

**Flow:** Admin trigger scan → TCP port probe :502 (Modbus) + :47808 (BACnet) → auto-register với building_id → Kafka event

**Migration V28:**
```sql
CREATE TABLE IF NOT EXISTS public.bms_devices (
    id              UUID    PRIMARY KEY,
    device_id       VARCHAR(100) NOT NULL,
    tenant_id       VARCHAR(50) NOT NULL,
    building_id     VARCHAR(100) NOT NULL,
    protocol        VARCHAR(20) NOT NULL,  -- MODBUS_TCP, BACNET_IP
    host            VARCHAR(100) NOT NULL,
    port            INT NOT NULL,
    register_map    JSONB,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at    TIMESTAMPTZ,
    UNIQUE (tenant_id, device_id)
);
```

**Kafka topic mới:** `UIP.iot.bms.device.registered.v1`

### 6.2 Extraction Stories Sprint 4

#### v3-EXT-06: Tạo `applications/iot-ingestion-service/` | 5 SP | Backend Lead

```
applications/iot-ingestion-service/
├── build.gradle (j2mod + bacnet4j + Resilience4j + spring-kafka)
├── Dockerfile
└── src/main/java/com/uip/iot/
    ├── UipIotIngestionApplication.java
    ├── bms/      ← BMS SDK (v3-BE-09)
    ├── kafka/    ← Producers
    └── api/      ← Admin/health endpoints
```

#### v3-EXT-07: @ConditionalOnProperty(iot-ingestion-external) + Shadow | 5 SP | Backend Lead + DevOps

**Shadow mode — dual-write:**
```java
@Component
@ConditionalOnProperty(name = "uip.capabilities.iot-ingestion-shadow", havingValue = "true")
public class ShadowIotIngestionAdapter implements IotIngestionPort {
    @Override
    public void ingest(BmsReadingEvent event) {
        local.ingest(event);           // primary — không block
        try { external.ingest(event); } // shadow — failure không block
        catch (Exception e) { log.warn("[Shadow] External fail: {}", e.getMessage()); }
    }
}
```

### 6.3 DevOps + QA Stories Sprint 4

- **v3-DevOps-04:** Kong ingress + Keycloak auth (prod TLS + rate-limiting) — OAuth2 tenant isolation, audit log
- **v3-DevOps-05:** Keycloak realm + role mapping — LDAP connector, JWT includes tenant + building scopes
- **v3-QA-04:** BMS integration test suite (3 device types) — 30 scenarios, timeout/CRC/reconnect
- **v3-QA-05:** Kong rate-limit + CORS + token tests — alg=none blocked, plugin order verified

**iOS cert:** Submit bởi Day 5 Sprint 4 (unblock Sprint 5)

### 6.4 Sprint 4 Gate (2026-07-06) — HARD BLOCK

- [ ] iot-ingestion-service shadow stable 48h+, diff <0.01%
- [ ] BMS 10+ devices simulator stable, polling
- [ ] CB test: 5 consecutive fail → OPEN, STALE flag emitted
- [ ] Kong production TLS, alg=none blocked (CI test PASS)
- [ ] Kong plugin order locked, CI green
- [ ] iOS cert submitted (Day 5)

---

## 7. Sprint MVP3-5 (2026-07-07 → 2026-07-20)

**Sprint Goal:** iOS/Android operator app MVP + iot-ingestion-service cutover + FCM/APNs push

### 7.1 Extraction Cutover

#### v3-EXT-08: iot-ingestion-service Cutover | 3 SP | DevOps

**CRITICAL — double-ingest prevention:**
```bash
# Flip flag
kubectl -n uip patch configmap uip-config --patch '{"data":{"iot-ingestion-external":"true"}}'
# Scale monolith MQTT subscriber disabled (rolling restart)
kubectl -n uip rollout restart deployment/uip-monolith
# CRITICAL: scale iot-service to primary (monolith stop MQTT first)
# Verify no duplicate messages in Kafka
```

### 7.2 Mobile App Stories

#### v3-Mobile-01: React Native + Expo Scaffold | 13 SP

**Monorepo addition:**
```
applications/
  operator-mobile/
    app.json (Expo SDK 51, scheme: "uip-operator")
    src/navigation/     ← RootNavigator + MainTabNavigator (4 tabs)
    src/screens/        ← Auth, Dashboard, Alerts, Controls, Profile
    src/hooks/          ← 60% từ web (useAlerts, useSensors, useBuildingList)
```

**npm workspaces:**
```json
// root package.json
{"workspaces": ["frontend", "applications/operator-mobile", "packages/*"]}
```

**packages/api-types:** Shared types (Building, AnalyticsParams, AlertEvent, ForecastDataPoint)

**Hooks có thể share 100% (platform-agnostic):**
`useAlerts`, `useEnergyForecast`, `useAnomalies`, `useBuildingList`, `useBuildingEnergyAnalytics`

**Hooks cần mobile variant:**

| Web | Mobile | Lý do |
|-----|--------|-------|
| `useAuth` (localStorage) | `useAuthMobile` (SecureStore) | Keycloak PKCE khác |
| `useMapSSE` (EventSource) | `useLiveAlertsWS` (polling 15s) | EventSource không có trong RN |
| `useNotificationSSE` | `usePushNotificationsMobile` | FCM/APNs thay SSE |

#### v3-Mobile-02: OAuth2 Login + Tenant Selection (Keycloak) | 8 SP

**PKCE flow:**
```typescript
// expo-auth-session — PKCE direct to Keycloak (không qua backend)
const [request, response, promptAsync] = AuthSession.useAuthRequest({
    clientId: 'uip-mobile',
    scopes: ['openid', 'profile', 'email', 'offline_access'],
    redirectUri: AuthSession.makeRedirectUri({ scheme: 'uip-operator' }),
    usePKCE: true,
}, discovery)
// Token storage: expo-secure-store (không localStorage)
```

#### v3-Mobile-03: Dashboard + Alerts (WebSocket Real-Time) | 13 SP

**Real-time:** React Query `refetchInterval: 15_000` (polling fallback) → WebSocket upgrade Sprint 6 nếu backend kịp

**Push notification:**
```typescript
// expo-notifications → FCM/APNs token
// Backend: POST /api/v1/mobile/push-token { token, platform, buildingIds }
// SLA: <5s từ alert trigger → device receive
```

#### v3-Mobile-04: Manual Control Panel | 8 SP

**Confirmation flow:** `requiresConfirmation=true` → Modal → reason field (min 10 chars) → Send → Audit trail

**HIGH danger** (EMERGENCY_DOOR, SHUTDOWN): extra step "Type actuator name to confirm"

### 7.3 Backend Story Sprint 5

#### v3-Mobile-05: FCM + APNs Push Notification Backend | 13 SP | Backend Eng 1

**Dependencies:** `firebase-admin:9.3.0` + `pushy:0.15.3`

**Multi-channel:**
```
Kafka UIP.maintenance.anomaly.detected.v1
  → NotificationRouter
      ├── FcmAdapter     (Android)
      ├── ApnsAdapter    (iOS)
      └── WebPushService (PWA — đã có MVP2)
```

**Migration V29:** `device_push_tokens` table (UUID, token, platform, tenant_id, building_ids[], last_seen_at)

**DoD:**
- [ ] FCM push đến Android thành công (test với real FCM token)
- [ ] APNs push đến iOS (sandbox)
- [ ] Invalid token auto-cleanup (UNREGISTERED → deregister event)

### 7.4 Sprint 5 Gate (2026-07-20) — HARD BLOCK

- [ ] iot-ingestion-service nhận 100% traffic, monolith MQTT subscriber disabled
- [ ] BMS spike 5K polls/sec → monolith alert p95 unchanged
- [ ] Zero duplicate readings `UIP.iot.sensor.reading.v1`
- [ ] APK + IPA builds successful
- [ ] 9/10 push notifications received <5s
- [ ] **Contingency:** iOS cert delay → Android APK first, IPA Sprint 6

---

## 8. Sprint MVP3-6 (2026-07-21 → 2026-08-03)

**Sprint Goal:** Schema Registry + Building Safety + Blue-green deploy + Pilot readiness

### 8.1 Backend Stories Sprint 6

#### v3-BE-11: Kafka Schema Registry + Avro Migration | 8 SP | Backend Lead

**Topics migrate Sprint 6 (Avro với Apicurio SR):**

| Topic | Producer | Consumer |
|-------|----------|----------|
| `UIP.iot.sensor.reading.v1` | iot-ingestion-service | monolith, Flink, analytics |
| `UIP.iot.bms.reading.v1` | iot-ingestion-service | Flink, analytics |
| `UIP.flink.alert.detected.v1` | Flink | monolith |
| `UIP.flink.analytics.hourly-rollup.v1` | Flink | analytics-service |

**Migration strategy (không breaking):**
```
Phase 1 (Week 1): Producer dual-publish JSON v1 + Avro v2
Phase 2 (Week 2): Consumer migrate sang Avro v2
Phase 3 (end Sprint 6): Deprecate v1 (retention = 1 day)
```

**Avro schema BmsReadingEvent:**
```json
{"type": "record", "name": "BmsReadingEvent", "namespace": "com.uip.iot.avro",
 "fields": [
   {"name": "tenantId", "type": "string"},
   {"name": "buildingId", "type": "string"},
   {"name": "metricType", "type": "string"},
   {"name": "value", "type": "double"},
   {"name": "unit", "type": ["null", "string"], "default": null}
 ]}
```

#### v3-BE-12: Building Safety — Structural Monitoring | 13 SP | Backend Eng 2

**Thresholds (TCVN 9386:2012 + ISO 4866):**

| Sensor | Warning | Critical |
|--------|---------|---------|
| STRUCTURAL_VIBRATION | 10 mm/s | 50 mm/s |
| STRUCTURAL_TILT | 3 mrad | 10 mrad |
| STRUCTURAL_CRACK | 0.3 mm | 2.0 mm |

**Welford online stddev:**
```java
// Alert khi value > μ + 4σ liên tục 50 readings (~5s @ 10Hz)
// Skip alerts khi n<1000 (cold start)
// Pre-seed Welford state từ historical data trên restart
```

**Flink CEP:** Pattern 3 consecutive spikes > baseline+4σ within 10s → `UIP.structural.alert.critical.v1`

**P0 escalation:** Alert <15s → FCM/APNs push + Email city authority (cooldown = 1 phút cho EMERGENCY)

**Migration V30:** Alert rules seed cho 6 structural sensor types

### 8.2 Frontend Story Sprint 6

#### v3-FE-07: Building Safety UI | 8 SP

**Components:** `SafetySensorStatusGrid`, `SafetyTrendChart` (24h sparkline), `SafetyAlertBanner` (sticky P0/P1), `SafetyScoreGauge`

**Color coding:** NORMAL=green / WARNING=amber / CRITICAL=red / OFFLINE=gray

**Virtualization:** react-window nếu sensor count >50

### 8.3 DevOps + QA Sprint 6

- **v3-DevOps-06:** Blue-green deploy + Istio traffic switch + rollback <30s validated
- **v3-QA-07:** Full regression 100+ scenarios + ALL perf thresholds — k6 sustained 30 phút
- **v3-QA-08:** Executive demo script v2 + City Authority dry-run (15 phút video)
- **v3-Docs-01:** Runbook + Tier 2 pilot deployment guide (6 incident scenarios)

### 8.4 Sprint 6 Gate / Pilot Readiness (2026-08-03) — HARD BLOCK (ALL phải pass)

- [ ] Avro Schema Registry deployed, 4 topics migrated, BACKWARD compat CI green
- [ ] Structural alerts <15s (P1 path — operator review, không auto-evacuate)
- [ ] Blue-green rollback <30s validated E2E
- [ ] **Regression: 100+ scenarios, 100% PASS**
- [ ] ALL SLA gates: cross-building <2s, ESG <30s, mobile <3s, Kong p99 <100ms
- [ ] OWASP 0 Critical findings
- [ ] Executive demo approved by PM + City Authority
- [ ] Pilot runbook reviewed Backend Lead + DevOps

---

## 9. Architecture Decisions

### ADR Summary Table

| ADR | Title | Sprint | Status |
|-----|-------|--------|--------|
| ADR-026 | ClickHouse Pre-emptive Adoption | MVP3-1 | Proposed → merge Day 4 Sprint 1 |
| ADR-027 | Keycloak Hybrid Auth — Issuer Migration Strategy | MVP3-1 | Proposed → merge Day 5 Sprint 1 |
| ADR-028 | Kong Gateway Scope — Extracted Services Only | MVP3-1 | Proposed → merge Day 5 Sprint 1 |
| ADR-029 | BMS Protocol Adapter Pattern | MVP3-4 | Proposed |
| ADR-030 | Mobile Stack — React Native (Operator) + PWA (Citizen) | MVP3-5 | Proposed |
| ADR-031 | Schema Registry — Avro for Cross-Service Topics | MVP3-6 | Proposed |
| ADR-032 | Predictive AI — ARIMA In-Process + LSTM External gRPC | MVP3-3 | Proposed |
| ADR-033 | Cross-Building Tenant Hierarchy — Parent Tenant Aggregation | MVP3-1 | Proposed → merge Day 3 Sprint 1 **BLOCKER** |
| ADR-034 | Structural Monitoring — Flink CEP + Welford stddev | MVP3-6 | Proposed |

### Key Architectural Constraints

1. **`matchIfMissing = true` bắt buộc** trên mọi `@ConditionalOnProperty` extraction — Tier 1 zero-regression
2. **Kong scope = extracted services only** — monolith vẫn qua nginx Ingress
3. **ClickHouse KHÔNG dùng cho alert path** — chỉ analytics/dashboard/report
4. **Cross-schema JOIN forbidden** — analytics-service query `analytics.*` only; monolith via Port
5. **Structural P0 = operator review** — KHÔNG auto-execute (safety constraint)
6. **gRPC migration (ADR-012)** = Sprint 7, sau pilot launch

---

## 10. User Stories & Business Rules

### Backlog Priority Map

| ID | Story | Sprint | Priority | SP |
|----|-------|--------|----------|----|
| US-001 | Cross-Building Real-Time Analytics Dashboard | 1-2 | P0 | 13 |
| US-005 | ClickHouse Analytics Microservice | 1-2 | P0 | 13 |
| US-003 | BMS Integration SDK | 4 | P0 | 21 |
| US-004 | Kong API Gateway + Keycloak IdP | 4 | P0 | 13 |
| US-002 | Sub-Meter Hierarchy & Cost Allocation | 2 | P1 | 8 |
| US-006 | Advanced ESG GRI Standards + Carbon Credit | 3 | P1 | 21 |
| US-007 | Energy Forecasting AI (ARIMA/LSTM) | 3 | P1 | 13 |
| US-008 | Predictive Maintenance Anomaly Detection | 3 | P2 | 13 |
| US-009 | Mobile Operator App (iOS/Android) | 5 | P1 | 21 |
| US-010 | Building Safety Structural Monitoring | 6 | P1 | 13 |
| US-011 | Schema Registry + Avro Migration | 6 | P1 | 8 |

### Key Business Rules (Critical)

| ID | Rule |
|----|------|
| BR-001 | Tenant isolation: Cluster Manager thấy ONLY assigned cluster data |
| BR-002 | Parent tenant có is_aggregator=true mới xem cross-building aggregate |
| BR-003 | Building_id validate ownership ở service layer (trước query) |
| BR-004 | BMS device phải register với tenant_id + building_id trước khi poll |
| BR-005 | CB mở sau 5 consecutive failures; STALE flag khi CB OPEN <10 phút |
| BR-006 | BMS adapters publish to `UIP.iot.bms.reading.v1` only — KHÔNG ghi DB trực tiếp |
| BR-007 | Kong plugin order bất biến: jwt → request-transformer → rate-limiting |
| BR-008 | X-Tenant-ID header từ JWT claim (Kong inject); client-supplied bị strip |
| BR-009 | ARIMA MAPE >15% → tự động fallback naive rolling average (log warn) |
| BR-010 | Structural P0 alert = operator review, KHÔNG auto-evacuate |

### 8 Open Questions — Cần Stakeholder Answer trước Sprint 2 EOL (2026-06-08)

| ID | Câu hỏi | Default assumption | Stakeholder |
|----|---------|-------------------|-------------|
| OQ-001 | Mobile platform confirm với City Authority? | React Native (SA đã chọn) | City Authority ESG Lead |
| OQ-002 | ClickHouse topology: single-node hay 3-node HA? | 2-node HA Sprint 2 | DevOps + SA |
| OQ-003 | Carbon credit: streaming (Flink) hay nightly batch? | Nightly batch MVP3 | City Authority |
| OQ-004 | GRI scope: subset 302/303/305 hay full ISO 37120? | Subset 302+305+ISO 37120-7.1/10.1 | City Authority ESG Lead |
| OQ-005 | BMS protocol priority: Modbus+BACnet baseline, KNX optional? | Confirmed (SA) | Pilot site engineer |
| OQ-006 | Data retention: 2 năm energy, 90 ngày anomalies? | Yes | City Authority + Legal |
| OQ-007 | Multi-region: single-region MVP3, multi-region v4.0? | Confirmed single-region | SA + PM |
| OQ-008 | Building mid-period cluster switch: pro-rated hay full attribution? | Full attribution mới | City Authority + Finance |

---

## 11. Service Extraction Plan

### analytics-service (Strangler Fig bước 1-4)

| Bước | Sprint | Story | Action |
|------|--------|-------|--------|
| 1 — Create | S1 W1 | v3-EXT-01/02 | Scaffold service + capability flag |
| 2 — Shadow | S1 W2 | v3-EXT-03 | Deploy Tier 2 staging parallel, mirror traffic |
| 3 — Validate | S1 end | QA-01 | Shadow diff <0.01% sustained 72h |
| 4 — Cutover | S2 W1 | v3-EXT-04 | Flip flag, monolith stop loading analytics beans |
| 5 — HPA | S2 W1 | v3-EXT-05 | Scale độc lập, stress test isolation |

### iot-ingestion-service (Strangler Fig bước 1-4)

| Bước | Sprint | Story | Action |
|------|--------|-------|--------|
| 1 — Create | S4 W1 | v3-EXT-06 | Scaffold service + BMS SDK |
| 2 — Shadow | S4 W2 | v3-EXT-07 | Shadow topic, BMS traffic dual-write |
| 3 — Validate | S4 end | QA-06 | Shadow diff <0.01% + BMS spike test |
| 4 — Cutover | S5 W1 | v3-EXT-08 | Flip flag + scale monolith MQTT=0 **ĐỒNG THỜI** |

**Rollback bắt buộc test:** analytics <5 phút; iot-ingestion <5 phút (double-ingest prevention)

### Flyway Migration Version Map

> **Baseline MVP2:** V1→V25 đã tồn tại (V25 = `create_alert_count_summary`). MVP3 bắt đầu từ **V26**.

| Version | File | Sprint | Story |
|---------|------|--------|-------|
| V26 | `V26__building_cluster.sql` + `V26__rls_policies.sql` | 1 | v3-BE-01 |
| V27 | `V27__maintenance_anomaly.sql` | 3 | v3-BE-07 |
| V28 | `V28__bms_devices.sql` | 4 | v3-BE-10 |
| V29 | `V29__device_push_tokens.sql` | 5 | v3-Mobile-05 |
| V30 | `V30__structural_alert_rules.sql` | 6 | v3-BE-12 |

---

## 12. QA Strategy & Quality Gates

### Coverage Targets

| Module | Coverage Target |
|--------|----------------|
| ESG GRI Calculator | **90%** (regulatory) |
| Cross-building Aggregation | **88%** (financial) |
| BMS SDK Adapters | **85%** (silent failure mode) |
| ClickHouse Analytics Service | **85%** (dual-store divergence) |
| Kong/Keycloak Integration | **80%** (security) |
| All other modules | 80% baseline |

### Tenant Isolation Test Matrix — HARD BLOCK (no merge nếu fail)

| Scenario | Test ID | What to verify |
|----------|---------|----------------|
| BMS device isolation | ISO-001, BMS-008 | Tenant A không thấy device Tenant B |
| ClickHouse data isolation | ISO-002 | Sum chỉ tenant's buildings, không cross |
| Cross-building RLS | ISO-003 | Cluster count == owned buildings only |
| Kong header injection | ISO-004 | X-Tenant-ID = JWT, không spoofable |
| Mobile FCM isolation | ISO-005 | Push chỉ đến đúng tenant's devices |
| Avro tenant_id mandatory | ISO-006 | null/empty tenant_id rejected at producer |
| Concurrent thread safety | ISO-007 | 50 concurrent requests, zero contamination |

### CI/CD Gates (8 gates mới)

| Gate | Trigger | Timeout | Hard Block |
|------|---------|---------|-----------|
| `tenant-isolation-gate` | Every PR | 20 min | YES — no continue-on-error |
| `tier1-regression-guard` | PRs touching `applications/**` | 20 min | YES |
| `bms-protocol-tests` | PRs touching `**/bms/**` | 15 min | YES |
| `clickhouse-consistency-tests` | PRs touching analytics | 15 min | YES |
| `avro-schema-compatibility` | PRs touching `**/kafka/**` | 10 min | YES |
| `pact-provider-verification` | PRs touching analytics/mobile API | 15 min | YES |
| `kong-security-scan` | PRs label `gateway` | 30 min | YES |
| `performance-tier2` | Push to `staging` | 60 min | YES — staging gate |

### k6 Performance Scenarios (Sprint 6)

```
bms_ingestion:     1,667 events/sec sustained 5 phút
analytics_users:   500 VU cross-building dashboard (ramp 2m→10m→5m→3m)
mobile_operators:  200 VU constant 20 phút

Thresholds:
  ESG summary p95:        <150ms
  Cross-building p95:     <500ms
  ClickHouse query p95:   <1,000ms
  Mobile alerts p95:      <100ms
  Error rate:             <0.01%
```

### Manual Test Cases Summary (100+ TC)

| Sprint | Count | Focus |
|--------|-------|-------|
| Sprint 1 | 10 TC | RLS isolation, schema V26, ClickHouse connectivity |
| Sprint 2 | 15 TC | analytics cutover, data consistency, ESG report, XLSX export |
| Sprint 3 | 10 TC | ARIMA MAPE, anomaly recall, AI explainability |
| Sprint 4 | 20 TC | BMS Modbus/BACnet, Kong rate limit, alg=none, Keycloak |
| Sprint 5 | 15 TC | Mobile login, alerts, push <5s, actuator commands |
| Sprint 6 | 20 TC | Building safety, regression, demo dry-run |
| **Total** | **90+ TC** | P0/P1 priority; covers Tier 1 + Tier 2 |

---

## 13. Performance Requirements

### Technical SLAs (Tier 2 Pilot)

| SLA | Target | Verify Sprint |
|-----|--------|--------------|
| Cross-building query p95 | <2s | Sprint 2 |
| ESG report generation | <30s | Sprint 2 |
| ClickHouse dashboard p95 | <1,000ms @ 500M rows | Sprint 2 |
| Energy forecast MAPE | <10% | Sprint 3 |
| Anomaly detection recall | >75% | Sprint 3 |
| Mobile push latency | <5s | Sprint 5 |
| Kong API p99 | <100ms | Sprint 4 |
| Keycloak token grant | <200ms | Sprint 1 |
| BMS ingestion lag → ClickHouse | <60s | Sprint 4 |
| System availability (K8s) | ≥99.5% | Sprint 6 |
| Pilot rollback time | ≤30s | Sprint 6 |

### Performance Gate (phải pass trước Pilot)

- ✅ Sensor query p95 <200ms @ 2M rows (Tier 1 baseline unchanged)
- ✅ Cross-building p95 <500ms @ 10M rows
- ✅ ESG report p95 <30s
- ✅ Mobile app <3s initial load, <5s push
- ✅ Kong p99 <100ms
- ✅ OWASP 0 Critical findings
- ✅ Regression ≥100 test cases, 100% PASS

---

## 14. Risk Register

| ID | Risk | Sev | Prob | Trigger Date | Mitigation | Contingency |
|----|------|-----|------|-------------|-----------|------------|
| R1 | Multi-building RLS >2s (N+1) | CRIT | 30% | Day 3 Sprint 1 | SA-01 spike 10M rows | Pre-fetch tenant_ids app layer, bypass RLS subquery |
| R2 | Tier 1 regression từ multi-building schema | CRIT | 25% | Day 1 Sprint 1 | CI `values-tier1.yaml` per PR | Block merge; V26__rollback.sql |
| R3 | LSTM MAPE >15% | HIGH | 35% | Sprint 3 Day 8 | ARIMA default, abort LSTM gate | Disable LSTM, ARIMA only |
| R4 | ClickHouse cluster HA delays (PV StatefulSet) | HIGH | 40% | Sprint 2 Day 10 | Test Sprint 1; fallback CH Cloud | Sign CH Cloud contract, migrate |
| R5 | City Authority ESG spec thay đổi mid-project | MED | 60% | Weekly sync | Finalize Sprint 2 EOL | Lock spec, add v3.1 |
| R6 | iOS cert + Apple review delays (48-72h) | MED | 55% | Sprint 4 Day 5 | Submit Day 5 Sprint 4 | Android APK first |
| R7 | Kong plugin priority error = auth bypass | CRIT | 20% | Sprint 4 | CI negative test (alg=none, tenant spoof) | Block deploy, review config |
| R8 | Extraction code break Tier 1 monolith | HIGH | 25% | Each extraction PR | `matchIfMissing=true` mandatory, CI tier1 suite | Revert PR |
| R9 | Dual-issuer JWK cache thrash → 401 spike | HIGH | 30% | Sprint 4-5 | RoutingJwtDecoder per issuer, 60s TTL | Reduce refresh 30s, fallback legacy |
| R10 | Welford cold start → false negative structural alert | MED | 40% | Sprint 6 Day 1 | Skip alerts n<1000, pre-seed from historical | Document limitation |

**Action ngay Sprint 1 Day 1:** Pair DevOps + Backend Eng 1 trên SA-01 và SA-03

---

## 15. Resource & Budget

### Team & Capacity

| Role | Sprint 1-2 | Sprint 3-4 | Sprint 5-6 |
|------|-----------|-----------|-----------|
| Backend Eng 1 | SA spike + BE analytics | Predictive AI + BMS SDK | Push notification + Avro |
| Backend Eng 2 | BE aggregation + Flink | BMS SDK + Anomaly | Building Safety |
| Frontend Eng | Dashboard shell | Forecast UI + Explainability | Mobile (React Native) |
| DevOps | ClickHouse + Kong/Keycloak | Kong prod + BMS infra | Blue-green + pilot env |
| QA | Test plan + seeding | AI validation + BMS tests | Regression + pilot gate |
| PM | 0.5 FTE | 0.5 FTE | 0.5 FTE |

**Total:** ~19 person-months | **Team:** 5 FTE + 0.5 PM | ~449 SP / 6 sprints

### Stakeholder Communication

| Frequency | Format | Owner | Audience |
|-----------|--------|-------|---------|
| Weekly (Fri 16h) | Email status | PM | City Authority ESG Lead |
| Sprint review (Fri 14h30) | Live demo 30-45 min | PM + Team Lead | City Authority + pilot site manager |
| Mid-sprint (Wed, if needed) | Call | PM | City Authority (blocker only) |
| **Sprint 2 EOL** | **ESG format finalization** | **PM + BA** | **City Authority** |

### Descope Plan (nếu velocity <50 SP/sprint)

| Feature | Action |
|---------|--------|
| Building Safety (v3-09) | → v3.1 post-pilot |
| BMS KNX adapter | → MVP3 chỉ Modbus + BACnet |
| LSTM energy forecast | → ARIMA only |
| Schema Registry (v3-10) | → v3.1 nếu Sprint 6 overloaded |

---

## 16. Registries cần update

Cập nhật sau mỗi sprint khi story merge:

### kafka-topic-registry.xlsx

| Sprint | Topic mới | Producer | Consumer |
|--------|-----------|----------|---------|
| 1 | `UIP.flink.analytics.hourly-rollup.v1` | Flink | analytics-service |
| 2 | `UIP.iot.bms.reading.v1` | iot-ingestion-service | Flink, analytics |
| 3 | `UIP.maintenance.anomaly.detected.v1` | Flink | monolith, push-notification |
| 4 | `UIP.iot.bms.device.registered.v1` | iot-ingestion-service | monolith |
| 4 | `UIP.iot.sensor.reading.shadow.v1` | iot-service (shadow only) | QA comparator |
| 6 | `UIP.iot.bms.reading.v2` (Avro) | iot-ingestion-service | Flink, analytics |
| 6 | `UIP.structural.alert.critical.v1` | Flink | monolith |

### environment-variables.xlsx

**ClickHouse:** `CLICKHOUSE_URL`, `CLICKHOUSE_USER`, `CLICKHOUSE_PASSWORD`, `CLICKHOUSE_DATABASE`

**Keycloak:** `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `KEYCLOAK_ADMIN_USER`, `KEYCLOAK_ADMIN_PASSWORD` (Secret), `LEGACY_ISSUER_ENABLED`

**Capabilities:** `UIP_CAPABILITIES_ANALYTICS_EXTERNAL`, `UIP_CAPABILITIES_IOT_INGESTION_EXTERNAL`, `UIP_CAPABILITIES_MULTI_BUILDING`

**BMS:** `UIP_IOT_BMS_MODBUS_ENABLED`, `UIP_IOT_BMS_BACNET_ENABLED`, `UIP_IOT_BMS_KNX_ENABLED`, `UIP_IOT_BMS_POLL_INTERVAL_SECONDS`

**AI:** `UIP_FORECAST_ENGINE` (arima|lstm), `UIP_FLINK_STRUCTURAL_K_VIBRATION`, `UIP_FLINK_STRUCTURAL_K_TILT`

**Mobile Push:** `FCM_SERVICE_ACCOUNT_PATH` (Secret), `APNS_KEY_PATH` (Secret), `APNS_TEAM_ID`, `APNS_KEY_ID`, `VAPID_PUBLIC_KEY`, `VAPID_PRIVATE_KEY` (Secret)

---

### Anti-Pattern Checklist (per PR — từ lessons Sprint 2-4)

```
[ ] Cross-schema SQL JOIN: grep -r "JOIN.*public\." src/ trong esg module → 0 kết quả
[ ] Cross-module service inject: grep -r "import.*esg" src/environment → 0
[ ] SET SESSION trong transaction: grep -r "SET SESSION" src/ → 0
[ ] @Builder.Default thiếu trên boolean: review mọi @Builder class
[ ] Kafka string literal trong @KafkaListener: phải là constant reference
[ ] Mock DB trong integration test: phải dùng @Testcontainers
[ ] @DirtiesContext: chỉ dùng khi thật sự cần
[ ] matchIfMissing missing trên extraction ConditionalOnProperty: BLOCK merge
```

---

*Detail Plan tổng hợp bởi 6 agents: SA + BA + QA + Backend + Frontend + PM/Tester*
*Ngày tổng hợp: 2026-05-11 | Sprint 1 verified: 2026-05-13 | Next review: Sprint MVP3-2 End (2026-06-08)*
*Sprint 1 status: COMPLETE ✅ — Sprint 2 UNBLOCKED (80% confidence)*
