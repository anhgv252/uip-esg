# Sprint 3 Readiness Spike — Cache + Telemetry + Partner Theme
**Date:** 2026-05-03
**Author:** Solution Architect
**Status:** Accepted
**Target Sprint:** MVP2-3 (26 May – 06 Jun 2026)

---

## 1. Executive Summary

Sprint 3 tập trung vào 4 trụ cột: **Redis Caching**, **Kafka Telemetry**, **Capability Flags**, và **Partner Theme**. Sau khi phân tích codebase hiện tại, tôi kết luận:

**Sprint 3 KHÔNG cần spike document riêng** như Sprint 2 (multi-tenancy). Lý do:
- ADR-015 (Caching) đã quyết định rõ: Redis + Spring Cache + explicit tenantId key
- ADR-014 (Telemetry) đã quyết định rõ: Flink validator + error topic
- ADR-011 (Capability Flags) chỉ là YAML wiring, không có design ambiguity
- ADR-019 (Partner Theme) chỉ cần createPartnerTheme() factory — đơn giản

**Tuy nhiên, có 1 P0 risk cần xử lý đầu sprint:** BT-22b — EsgService refactor thêm tenantId vào ~10 methods. Đây là blocker cho tất cả cache tasks.

---

## 2. Sprint 3 Complexity Assessment

| Task | Complexity | Risk | Needs Spike? |
|------|-----------|------|-------------|
| **BT-22b: EsgService tenantId refactor** | **HIGH** | **P0 blocker** | No — nhưng cần thực hiện TRƯỚC mọi task khác |
| MVP2-21: Redis CacheConfig | LOW | LOW | No — Spring Cache standard |
| MVP2-22: CacheKeyBuilder + @Cacheable | MEDIUM | MEDIUM | No — ADR-015 đã define format |
| MVP2-23: Cache isolation tests | MEDIUM | MEDIUM | No — standard integration test |
| MVP2-24: Flink TenantIdValidator | LOW | LOW | No — simple filter pattern |
| MVP2-25: Kafka topic registry update | TRIVIAL | NONE | No |
| MVP2-26: Capability flags YAML | LOW | LOW | No — @ConfigurationProperties |
| MVP2-27: Helm values per tier | LOW | LOW | No — YAML only |
| MVP2-02: Kafka SASL + TLS | MEDIUM | MEDIUM | No — nhưng cần Docker infra |
| MVP2-15: Jaeger tracing | LOW | LOW | No — OpenTelemetry starter |
| MVP2-17: Coverage gate | LOW | LOW | No — JaCoCo đã trong build.gradle |
| FE-07: Partner theme factory | MEDIUM | LOW | No — MUI createTheme + tenant config |
| FE-08: useScope hook | TRIVIAL | NONE | No — 1 file, 3 dòng |
| FE-19: API tenant params | LOW | LOW | No |
| FE-20: Wire useScope vào pages | TRIVIAL | NONE | No |
| FE-29: Theme contrast utility | TRIVIAL | NONE | No |

---

## 3. P0 Blocker Analysis: BT-22b EsgService Refactor

### Hiện trạng (sau Sprint 2)

```
EsgService.java — 8 public methods, 0 có tenantId parameter
EsgMetricRepository.java — 4 JPQL queries, 0 filter theo tenantId
EsgController.java — 7 endpoints, 0 extract tenantId
```

**Vấn đề:** RLS (Sprint 2) tự động filter theo `current_setting('app.tenant_id')`, NHƯNG:
1. `@Cacheable` key KHÔNG tự động include tenantId → **cache cross-tenant leak**
2. Queries trong EsgMetricRepository dùng JPQL trên entity, RLS hoạt động ở SQL level, nhưng cache key phải explicit
3. EsgReport entity cũng có tenantId (Sprint 2 thêm) nhưng queries chưa dùng

### Refactor plan — BT-22b

**Scope:** 10 methods trong EsgService + 6 endpoints trong EsgController + 4 repository queries

```java
// BEFORE (hiện tại)
public EsgSummaryDto getSummary(String periodType, int year, int quarter)

// AFTER (Sprint 3)
public EsgSummaryDto getSummary(String tenantId, String periodType, int year, int quarter)
```

**Controller change:**
```java
// EsgController — extract tenantId từ TenantContext
@GetMapping("/summary")
public ResponseEntity<EsgSummaryDto> summary(...) {
    String tenantId = TenantContext.get();
    return ResponseEntity.ok(esgService.getSummary(tenantId, periodType, year, quarter));
}
```

**Repository change:**
```java
// BEFORE
@Query("SELECT SUM(e.value) FROM EsgMetric e WHERE e.metricType = :type AND e.id.timestamp BETWEEN :from AND :to")

// AFTER
@Query("SELECT SUM(e.value) FROM EsgMetric e WHERE e.tenantId = :tenantId AND e.metricType = :type AND e.id.timestamp BETWEEN :from AND :to")
```

**Execution order (blocker chain):**
```
BT-22b: EsgService refactor tenantId ← P0, thực hiện NGAY ngày đầu sprint
  │
  ├── MVP2-21: CacheConfig (không block bởi BT-22b, nhưng cache key cần tenantId)
  ├── MVP2-22: CacheKeyBuilder + @Cacheable (BLOCKED by BT-22b)
  ├── MVP2-23: Cache isolation tests (BLOCKED by MVP2-22)
  └── FE-07: Partner theme (independent, không block)
```

---

## 4. Cache Architecture (ADR-015 Implementation)

### Cache Key Design

```
Format: {cache-name}:{tenant_id}:{params}

Ví dụ:
  esg-dashboard:hcm:BUILDING:B1:MONTHLY:2026-01-01:2026-02-01
  esg-dashboard:default:CITY::QUARTERLY:2026-01-01:2026-04-01
  esg-report:hcm::2026:Q1
  esg-trend:hcm:SENSOR-001:2026-04-01:2026-05-01
```

**CacheKeyBuilder.java:**
```java
@Component
public class CacheKeyBuilder {
    public String dashboardKey(String tenantId, String scopeLevel, String scopeId,
                               String period, Instant from, Instant to) {
        return String.format("esg-dashboard:%s:%s:%s:%s:%s:%s",
            tenantId, scopeLevel, scopeId != null ? scopeId : "",
            period, from, to);
    }

    public String reportKey(String tenantId, int year, int quarter) {
        return String.format("esg-report:%s:%d:Q%d", tenantId, year, quarter);
    }

    public String trendKey(String tenantId, String sensorId, Instant from, Instant to) {
        return String.format("esg-trend:%s:%s:%s:%s", tenantId, sensorId, from, to);
    }
}
```

### Cache Config

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> configs = Map.of(
            "esg-dashboard", configWithTtl(Duration.ofSeconds(60)),
            "esg-report",    configWithTtl(Duration.ofMinutes(5)),
            "esg-trend",     configWithTtl(Duration.ofSeconds(30))
        );
        return RedisCacheManager.builder(factory)
            .cacheDefaults(configWithTtl(Duration.ofMinutes(1)))
            .withInitialCacheConfigurations(configs)
            .build();
    }

    private RedisCacheConfiguration configWithTtl(Duration ttl) {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(ttl)
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}
```

### Cache Eviction Strategy

```
Kafka Event → Consumer → @CacheEvict trên affected cache

UIP.esg.telemetry.v1 → new sensor data arrives
  → evict: esg-dashboard:{tenantId}:*
  → evict: esg-trend:{tenantId}:{sensorId}:*

UIP.esg.report.generated.v1 → report complete
  → evict: esg-report:{tenantId}:{year}:Q{quarter}
```

### Security: Cache Tenant Isolation

**RISK: Cross-tenant cache leak** — Nếu cache key thiếu tenantId, tenant A có thể đọc cached data của tenant B.

**Mitigation:**
1. CacheKeyBuilder **bắt buộc** tenantId parameter (không null, không optional)
2. Unit test verify: key format luôn chứa tenantId
3. Integration test (MVP2-23): tenant A cache → tenant B query → miss (không hit A's cache)
4. CacheEvict chỉ evict keys của đúng tenant

---

## 5. Telemetry Enrichment (ADR-014 Implementation)

### EsgFlinkJob → EsgCleansingJob

```
Current: EsgFlinkJob.java (read from Kafka → aggregate → write to TimescaleDB)
After:   EsgCleansingJob.java (same + TenantIdValidator step)
```

```
Sensor Message Flow:
  Kafka: UIP.iot.sensor.reading.v1
    → EsgCleansingJob
      → Step 1: TenantIdValidator (filter)
         ├─ tenant_id present → pass through
         └─ tenant_id missing → route to UIP.esg.telemetry.error.v1
      → Step 2: Data cleansing (existing logic)
      → Step 3: Aggregate + write TimescaleDB
```

### Error Topic Format

```json
{
  "errorCode": "MISSING_TENANT_ID",
  "originalTopic": "UIP.iot.sensor.reading.v1",
  "originalMessage": { ... },
  "detectedAt": "2026-05-26T10:30:00Z",
  "sensorId": "SENSOR-HCM-001"
}
```

### Flink Metrics

```
tenant_id_missing_count  — Counter, số message thiếu tenant_id
tenant_id_valid_count    — Counter, số message pass validation
error_routed_count       — Counter, số message routed sang error topic
```

---

## 6. Partner Theme (ADR-019 Partial Implementation)

### Frontend Architecture

```
TenantConfigContext.config.branding
  → { primaryColor: "#2E7D32", partnerName: "Green City", logoUrl: "..." }
    → createPartnerTheme(branding)
      → MUI ThemeProvider
        → All components use theme
```

### Implementation Steps

1. **Tách baseTheme.ts** từ theme/index.ts hiện tại
2. **Tạo createPartnerTheme(config?)** factory function
3. **Wire vào ThemedApp** — dùng `useTenantConfig().config?.branding`
4. **Theme contrast check** — WCAG AA validation cho partner colors
5. **Partner logo** — thay sidebar text khi `logoUrl` present

### File Changes

```
frontend/src/theme/
├── index.ts                  ← sửa: export createPartnerTheme()
├── baseTheme.ts              ← mới: tách base config
├── contrastCheck.ts          ← mới: meetsWcagAA()
└── partnerThemes/
    ├── default.theme.ts      ← mới: theme hiện tại
    ├── energy-optimizer.theme.ts  ← mới: green primary (#2E7D32)
    └── citizen-first.theme.ts     ← mới: orange primary (#E65100)
```

---

## 7. Sprint 3 Execution Order

### Phase 1 — P0 Blockers (Ngày 1-2)

```
BT-22b: EsgService tenantId refactor          [3 SP] ← P0, block mọi cache task
BT-22a: CacheKeyBuilder explicit param         [1 SP] ← song song BT-22b
```

### Phase 2 — Backend Core (Ngày 3-6)

```
MVP2-21: Redis CacheConfig                     [3 SP]
MVP2-22: @Cacheable ESG Queries                [5 SP] ← BLOCKED by BT-22b
BT-21a: Cache warmup service                   [2 SP]
MVP2-24: Flink TenantIdValidator               [3 SP] ← independent
BT-24a: EsgFlinkJob → EsgCleansingJob rename   [2 SP]
BT-25a: Error topic consumer                   [2 SP]
```

### Phase 3 — DevOps + Config (Ngày 4-7)

```
MVP2-26: Capability flags YAML                 [2 SP] ← independent
MVP2-27: Helm values per tier                  [3 SP] ← independent
MVP2-02: Kafka SASL + TLS                      [5 SP] ← cần Docker infra
MVP2-15: Jaeger distributed tracing            [5 SP] ← independent
MVP2-17: Coverage gate JaCoCo                  [2 SP] ← JaCoCo đã có
```

### Phase 4 — Frontend (Ngày 5-8)

```
FE-07: Partner theme factory                   [5 SP] ← independent
FE-08: useScope hook                           [2 SP] ← independent
FE-19: API tenant params                       [2 SP] ← sau BT-22b
FE-20: Wire useScope vào pages                 [1 SP]
FE-29: Theme contrast utility                  [1 SP]
```

### Phase 5 — Testing + Verification (Ngày 9-10)

```
MVP2-23: Cache tenant isolation tests          [3 SP]
E2E tests cho Sprint 3 features
Smoke test: cache performance, Kafka SASL, tracing
```

---

## 8. Risk Assessment

| ID | Risk | Severity | Likelihood | Mitigation |
|----|------|----------|-----------|-----------|
| R-301 | Cache key thiếu tenantId → cross-tenant data leak | **HIGH** | Medium | CacheKeyBuilder bắt buộc tenantId + integration test |
| R-302 | EsgService refactor breaking existing API contracts | **HIGH** | Medium | tenantId là parameter mới, backward compat cho default tenant |
| R-303 | Kafka SASL config break existing consumers | **MEDIUM** | Medium | Test với Docker Compose trước, SASL_PLAINTEXT cho dev |
| R-304 | Flink EsgFlinkJob rename breaking deployment | **LOW** | Low | Rename là trong code, Kafka topic không đổi |
| R-305 | Partner theme với dark color → low contrast | **LOW** | High | WCAG AA check utility (FE-29) |

---

## 9. ADR Cross-Reference

| ADR | Sprint 3 Tasks | Implementation Status |
|-----|---------------|----------------------|
| ADR-015 (Caching) | MVP2-21, MVP2-22, MVP2-23, BT-22a, BT-21a | Chưa implement |
| ADR-014 (Telemetry) | MVP2-24, MVP2-25, BT-24a, BT-25a | Chưa implement |
| ADR-011 (Capability Flags) | MVP2-26, MVP2-27 | Chưa implement |
| ADR-019 (Partner Theme) | FE-07, FE-29 (partial) | Chưa implement |
| ADR-022 (Cache Warming) | BT-21a | Chưa implement |
| ADR-023 (RLS Migration) | N/A — đã complete Sprint 2 (V16) | ✅ Done |

---

## 10. Team Handoff Summary

### Backend Engineer
- **Ngày 1-2:** BT-22b EsgService refactor — thêm tenantId vào 10 methods. Đây là P0 blocker.
- **Ngày 3-6:** Cache config (MVP2-21/22), Flink validator (MVP2-24)
- **Ngày 9-10:** Integration tests (MVP2-23)
- **Files sẽ sửa:** EsgService.java, EsgController.java, EsgMetricRepository.java, EsgReportGenerator.java

### Frontend Engineer
- **Ngày 5-8:** Partner theme factory (FE-07), useScope hook (FE-08)
- **Files sẽ tạo:** theme/baseTheme.ts, theme/contrastCheck.ts, hooks/useScope.ts, 3 partner theme files
- **Files sẽ sửa:** theme/index.ts, App.tsx (ThemedApp component), EsgPage.tsx, AlertsPage.tsx

### DevOps
- **Ngày 4-7:** Kafka SASL (MVP2-02), Jaeger (MVP2-15), Helm values (MVP2-27)
- **Files sẽ tạo:** infra/jaeger/docker-compose.jaeger.yml, infra/helm/values/values-tier{1,2,3}.yaml

### QA
- **Ngày 9-10:** Cache isolation tests, coverage gate verification, smoke test

---

## 11. Sprint 3 DoD Verification Checklist

- [ ] `GET /api/v1/esg/summary` lần 2 → response <5ms (cache hit)
- [ ] Redis key format: `esg-dashboard:{tenantId}:...` — verify trong Redis CLI
- [ ] Tenant A dashboard cache → Tenant B query → miss (không leak)
- [ ] Flink message thiếu tenant_id → error topic `UIP.esg.telemetry.error.v1`
- [ ] Kafka anonymous connection → rejected (SASL)
- [ ] `createPartnerTheme({primaryColor: "#2E7D32"})` → green sidebar
- [ ] `useScope("esg:write")` → operator: true, citizen: false
- [ ] JaCoCo coverage ≥80% trên critical paths
- [ ] Jaeger UI: trace ID trong error response match
- [ ] All existing E2E tests still pass (regression)

---

**Phiên bản tài liệu:** 1.0
**Người chuẩn bị:** Solution Architect
**Reviewer:** Project Manager
