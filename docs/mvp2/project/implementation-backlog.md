# MVP2 Implementation Backlog — ADR-driven

**Cập nhật**: 2026-04-28  
**Source**: ADR-010, ADR-011, ADR-014, ADR-015, ADR-019 (tất cả đã Accepted)

---

## Sprint MVP2-1 bổ sung (Tuần 1–2)

Các task dưới đây BỔ SUNG cho sprint plan hiện có (MVP2-01 đến MVP2-18).

### ADR-010: Multi-Tenancy Foundation

| ID | Task | File cần tạo/sửa | SP |
|----|------|------------------|----|
| MVP2-20 | V14 migration (đã có file) | `db/migration/V14__add_multi_tenant_columns.sql` | ✅ Done |
| MVP2-07a | Tenant entity + RLS policy | `common/domain/Tenant.java`, `V15__rls_policies.sql` | 8 |
| MVP2-07b | TenantContext ThreadLocal + filter repos | `common/security/TenantContext.java`, `common/filter/TenantContextFilter.java` | 5 |

**Chi tiết MVP2-07a — Tenant entity + RLS:**
```java
// common/domain/Tenant.java
@Entity @Table(schema = "tenants", name = "tenant")
public class Tenant {
    @Id private String tenantId;
    private String name;
    private String tier;        // T1 | T2 | T3 | T4
    private boolean active;
    private Instant createdAt;
}
```
```sql
-- V15__rls_policies.sql
ALTER TABLE environment.sensor_readings ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON environment.sensor_readings
    USING (tenant_id = current_setting('app.tenant_id', true));
-- Lặp lại cho: esg.clean_metrics, esg.alert_events, traffic.traffic_counts
```

**Chi tiết MVP2-07b — TenantContext:**
```java
// common/security/TenantContext.java
public final class TenantContext {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();
    public static void set(String tenantId) { TENANT.set(tenantId); }
    public static String get() { return TENANT.get(); }
    public static void clear() { TENANT.remove(); }
}

// common/filter/TenantContextFilter.java — extract từ JWT claim "tenant_id"
// Sau filter: SET LOCAL app.tenant_id = TenantContext.get()
// Dùng HikariCP connection listener hoặc Spring AOP @Around repository calls
```

---

## Sprint MVP2-2 bổ sung (Tuần 3–4)

### ADR-015: Redis Cache Layer cho ESG API

| ID | Task | File cần tạo/sửa | SP |
|----|------|------------------|----|
| MVP2-21 | Deploy Redis + Spring Cache config | `esg/config/CacheConfig.java`, `application.yml` | 3 |
| MVP2-22 | CacheKeyBuilder + cache tất cả ESG queries | `esg/common/CacheKeyBuilder.java`, `esg/service/EsgService.java` | 5 |
| MVP2-23 | Test: tenant isolation trong cache | `EsgServiceCacheTest.java` | 3 |

**Chi tiết MVP2-21 — CacheConfig:**
```java
// esg/config/CacheConfig.java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put("esg-dashboard",
            RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(60)));
        configs.put("esg-report",
            RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(5)));
        return RedisCacheManager.builder(factory)
            .withInitialCacheConfigurations(configs).build();
    }
}
```

**Chi tiết MVP2-22 — CacheKeyBuilder:**
```java
// esg/common/CacheKeyBuilder.java — KHÔNG inline string format ở caller
public final class CacheKeyBuilder {
    // Format: esg:{tenant_id}:{scope_level}:{scope_id}:{period}:{from}:{to}
    public static String dashboard(String tenantId, String scopeLevel,
                                   String scopeId, String period,
                                   LocalDate from, LocalDate to) {
        return String.format("esg:%s:%s:%s:%s:%s:%s",
            tenantId, scopeLevel, scopeId, period, from, to);
    }
}

// esg/service/EsgService.java — dùng @Cacheable với custom key
@Cacheable(cacheNames = "esg-dashboard",
           key = "T(com.uip.backend.esg.common.CacheKeyBuilder)" +
                 ".dashboard(#tenantId, #scopeLevel, #scopeId, #period, #from, #to)")
public EsgSummaryDto getDashboardSummary(...) { ... }
```

**application.yml bổ sung:**
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  cache:
    type: redis
```

---

### ADR-014: Telemetry Enrichment — tenant_id validation tại Flink

| ID | Task | File cần tạo/sửa | SP |
|----|------|------------------|----|
| MVP2-24 | tenant_id validation tại esg-cleansing-job | Flink job source (nếu có trong repo) | 3 |
| MVP2-25 | Cập nhật messaging contract | `docs/deployment/kafka-topic-registry.xlsx` | 1 |

**Rule**: `tenant_id` bắt buộc trong message `ngsi_ld_telemetry`. Nếu thiếu → route sang `UIP.esg.telemetry.error.v1` với error code `MISSING_TENANT_ID`.

---

### ADR-011: Capability Flags — wiring vào application.yml

| ID | Task | File cần tạo/sửa | SP |
|----|------|------------------|----|
| MVP2-26 | Thêm `uip.capabilities.*` block vào application.yml | `backend/src/main/resources/application.yml` | 2 |
| MVP2-27 | Helm values per tier (T1, T2, T3) | `infra/helm/values/values-tier1.yaml` ... | 3 |

**application.yml bổ sung:**
```yaml
uip:
  capabilities:
    # Feature flags — bật theo tier/deployment
    multi-tenancy: false         # bật từ T2
    clickhouse: false            # bật khi trigger ADR-012
    kong-gateway: false
    keycloak: false
    edge-computing: false
    multi-region: false

    # Extraction flags — bật khi tách module thành service riêng
    iot-ingestion-external: false
    alert-external: false
    analytics-external: false
```

---

### ADR-019: Partner Customization — Foundation

| ID | Task | File cần tạo/sửa | SP |
|----|------|------------------|----|
| MVP2-28 | Tạo `partner-extensions/` directory structure | `partner-extensions/README.md` + stub module | 2 |
| MVP2-29 | Tạo `infra/partner-profiles/` + template | `infra/partner-profiles/application-partner-default.yml` | 2 |
| MVP2-30 | Định nghĩa EsgReportExportPort extension point | `esg/extension/EsgReportExportPort.java` | 3 |
| MVP2-31 | Helm values template cho partner | `infra/helm/values/values-partner-template.yaml` | 1 |

**Chi tiết MVP2-30 — Extension Point:**
```java
// esg/extension/EsgReportExportPort.java
public interface EsgReportExportPort {
    String getFormatId();          // "csv", "excel", "iso-50001", "gri"
    byte[] export(EsgReportData data);
    default boolean isAvailable() { return true; }
}

// esg/service/EsgReportGenerator.java — inject List<EsgReportExportPort>
// thay vì hardcode format logic trong service
```

**Cấu trúc thư mục cần tạo:**
```
partner-extensions/
├── README.md
└── partner-energy-optimizer/        ← stub, implement ở T2
    └── pom.xml

infra/
├── partner-profiles/
│   └── application-partner-default.yml
└── helm/
    └── values/
        ├── values-tier1.yaml
        ├── values-tier2.yaml
        ├── values-tier3.yaml
        └── values-partner-template.yaml
```

---

## MVP3 Backlog (chưa implement)

Tasks này là **out of scope cho MVP2** — ghi lại để không bị quên.

| ADR | Task | Trigger |
|-----|------|---------|
| ADR-014 | Flink Broadcast State + Debezium CDC cho shared gateway | Khi có deployment dùng shared MQTT gateway |
| ADR-015 | Continuous Aggregates: `esg_weekly_summary`, `esg_monthly_summary` | Khi ESG report query >2 phút |
| ADR-015 | Wrapper Views với `security_invoker = true` | Cùng với Continuous Aggregates |
| ADR-012 | ClickHouse dual-write qua Flink | Khi ESG report >5 phút p95 hoặc >10K sensors |
| ADR-013 | EMQX Edge buffer deployment | Khi onboard site T2 với WAN constraint |
| ADR-019 | partner-energy-optimizer module code thực | Khi có đối tác yêu cầu ISO 50001 |

---

## Dependency Map — thứ tự implement

```
V14 migration (✅ Done)
    ↓
MVP2-07a: Tenant entity + RLS     ← cần có trước khi test tenant isolation
    ↓
MVP2-07b: TenantContext filter     ← cần có để set app.tenant_id tại DB layer
    ↓
MVP2-26: Capability flags          ← application.yml wiring
    ↓
MVP2-21: Redis + CacheConfig       ← infra dep: Redis phải up
    ↓
MVP2-22: CacheKeyBuilder + @Cacheable
    ↓
MVP2-23: Cache isolation tests
    ↓
MVP2-28 → MVP2-31: Partner foundation   ← không block release, làm cuối sprint
```

---

## Definition of Done — mỗi task

- [ ] Code compile, không có compiler warning
- [ ] Unit test: happy path + boundary (empty result, null tenant, wrong format)
- [ ] Integration test: verify với real DB nếu có SQL logic mới
- [ ] Không có cross-module direct dependency injection mới (check import)
- [ ] Không có `if (tenantId == "xxx")` trong business logic
- [ ] Cache test: verify `tenant_id` có trong cache key, không có cross-tenant hit
- [ ] PR review: SA checklist pass (xem ADR-011 Architecture Review Checklist)
