# ADR-011: Monorepo Architecture & Module Extraction Strategy

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect
**Scope**: T1→T4 — monorepo áp dụng ngay; extraction theo trigger từ T2+
**Supersedes**: ADR-018 (Single Codebase, Configuration-Driven Tier Delivery) — nội dung hợp nhất vào đây

---

## Context

UIP có hai vấn đề liên quan chặt chẽ đến nhau mà không thể giải quyết độc lập:

**Vấn đề 1 — Module extraction:** MVP1 là monolith, nhưng khi scale lên T2/T3, một số module cần resource và lifecycle riêng. Cần biết tách module nào, khi nào, theo thứ tự nào.

**Vấn đề 2 — Code management:** UIP phục vụ 4 tier với infrastructure khác nhau. Nếu quản lý bằng branch riêng per tier:

```
Hậu quả:
  ├── Bug fix → backport thủ công sang 4 branch
  ├── Feature T3 không tự lan xuống T1
  ├── CI chạy 4 lần cho cùng logic
  └── 3 tháng sau: 4 branch diverge hoàn toàn, merge nightmare
```

Hai vấn đề này có cùng một giải pháp: **monorepo với capability flags**.

### Trạng thái hiện tại (T1 — Monolith)

```
Spring Boot Monolith (1 JVM)
├── iot-module           ← MQTT ingestion, device registry
├── environment-module   ← AQI, water, noise monitoring
├── esg-module           ← ESG aggregation, reporting
├── traffic-module       ← Traffic flow, incidents
├── alert-module         ← Alert rules, event processing
├── citizen-module       ← Complaints, bills, notifications
├── ai-workflow-module   ← Camunda + Claude AI
└── admin-module         ← Config, user management
```

Giao tiếp nội bộ: Port Interface (same JVM). Kafka dùng cho IoT stream và async events.

---

## Decision

### Nguyên tắc cốt lõi: "Monorepo, Same Version Tag"

> "Single Codebase" **không** có nghĩa là "1 Docker Image mãi mãi".  
> Nó có nghĩa là **1 git repository** — tất cả artifact đều build từ cùng 1 commit tag.

Hai giai đoạn tự nhiên theo thời gian:

```
Giai đoạn A (T1/T2 initial — monolith thuần):
  git tag v2.x.y → 1 build → uip-monolith:v2.x.y
                                 ├── values-tier1.yaml → T1 customer
                                 └── values-tier2.yaml → T2 customer

Giai đoạn B (T2+ sau extraction — nhiều images, cùng tag):
  git tag v2.x.y → 2 builds từ cùng commit:
                     ├── uip-monolith:v2.x.y    (iot-module DISABLED)
                     └── uip-iot-service:v2.x.y (mới, cùng source repo)
```

**Invariant bất biến:** Mọi Docker image trên production luôn mang **cùng version tag**. Không bao giờ monolith `v2.1.0` + iot-service `v2.0.5`.

---

### Phần A: Monorepo Structure

Khi module được extract, nó có Dockerfile riêng trong **cùng repo**:

```
uip-platform/  ← 1 git repository duy nhất
│
├── modules/
│   ├── iot-module/             ← code dùng chung bởi monolith VÀ iot-service
│   ├── environment-module/
│   ├── esg-module/
│   ├── alert-module/           ← dùng chung bởi monolith VÀ alert-service (T3)
│   ├── citizen-module/
│   ├── ai-workflow-module/
│   └── admin-module/
│
├── shared-libraries/
│   ├── eventbus/
│   ├── common/
│   └── sensor-schema/
│
├── applications/
│   ├── monolith/
│   │   ├── src/                ← Spring Boot app ghép tất cả modules
│   │   └── Dockerfile          ← build → uip-monolith:vX.Y.Z
│   │
│   ├── iot-ingestion-service/  ← tạo mới khi trigger T2 (Bước 1)
│   │   ├── src/                ← thin wrapper: chỉ import iot-module
│   │   └── Dockerfile          ← build → uip-iot-service:vX.Y.Z
│   │
│   ├── alert-service/          ← tạo mới khi trigger T2/T3 (Bước 2)
│   │   ├── src/
│   │   └── Dockerfile
│   │
│   └── analytics-service/      ← tạo mới khi ClickHouse adopt (Bước 3)
│       ├── src/
│       └── Dockerfile
│
└── infra/
    └── helm/
        ├── monolith/Chart.yaml
        ├── iot-ingestion-service/Chart.yaml
        └── values/
            ├── values-tier1.yaml
            ├── values-tier2.yaml
            └── values-tier3.yaml
```

---

### Phần B: Capability Flags — Hai Nhóm

Thay vì tier enum, dùng **capability flags** — mỗi flag là một khả năng độc lập. Có hai nhóm:

**Nhóm 1 — Feature flags** (bật tính năng mới cho tier cao hơn):
```yaml
uip:
  capabilities:
    multi-tenancy:  false   # T2+: RLS + TenantContext (ADR-010)
    clickhouse:     false   # T3+: ClickHouse analytics (ADR-012)
    kong-gateway:   false   # T3+: API Gateway
    keycloak:       false   # T3+: External IdP
    edge-computing: false   # T3+: Edge Flink jobs (ADR-013)
    multi-region:   false   # T4: Warm DR + Active-Active (ADR-017)
    ai-city-brain:  false   # T4: Cross-domain ML
```

**Nhóm 2 — Extraction flags** (tắt module đã được externalize khỏi monolith):
```yaml
uip:
  capabilities:
    iot-ingestion-external:  false  # true = iot-service đang chạy riêng
    alert-external:          false  # true = alert-service đang chạy riêng
    analytics-external:      false  # true = analytics-service đang chạy riêng
    ai-workflow-external:    false  # true = ai-workflow-service đang chạy riêng
    citizen-external:        false  # true = citizen-service đang chạy riêng
```

**Tier profiles tổng hợp:**
```yaml
# application-tier1.yml — baseline, không bật gì
# (dùng default false cho tất cả)

# application-tier2.yml
uip.capabilities:
  multi-tenancy:          true
  iot-ingestion-external: true   # sau khi iot-service stable

# application-tier3.yml
uip.capabilities:
  multi-tenancy:          true
  clickhouse:             true
  kong-gateway:           true
  keycloak:               true
  iot-ingestion-external: true
  alert-external:         true
  analytics-external:     true

# application-tier4.yml
uip.capabilities:
  multi-tenancy:         true
  clickhouse:            true
  kong-gateway:          true
  keycloak:              true
  edge-computing:        true
  multi-region:          true
  ai-city-brain:         true
  iot-ingestion-external: true
  alert-external:         true
  ai-workflow-external:   true
  citizen-external:       true
```

**Tại sao capability thay vì tier enum?**
Tier là marketing concept; capability là kỹ thuật. Một T2 customer có thể muốn thử `clickhouse` trước khi tăng hạng → bật 1 flag, không cần đổi tier. Easier to test: mock một capability thay vì mock cả tier.

---

### Phần C: Spring `@ConditionalOnProperty` — Swap Implementation

Port Interface + Conditional Bean = swap implementation per capability, không đổi business logic:

**Feature flag — chọn implementation:**
```java
// AnalyticsPort.java — trong shared-lib, không đổi
public interface AnalyticsPort {
    EsgAggregateResult queryAggregate(EsgAggregateQuery query);
}

@Component
@ConditionalOnProperty(name = "uip.capabilities.clickhouse",
                       havingValue = "false", matchIfMissing = true)
class TimescaleAnalyticsAdapter implements AnalyticsPort { ... }  // T1/T2 default

@Component
@ConditionalOnProperty(name = "uip.capabilities.clickhouse", havingValue = "true")
class ClickHouseAnalyticsAdapter implements AnalyticsPort { ... } // T3+
```

**Extraction flag — tắt module đã externalize:**
```java
@Configuration
@ConditionalOnProperty(
    name           = "uip.capabilities.iot-ingestion-external",
    havingValue    = "false",
    matchIfMissing = true        // default: monolith tự xử lý IoT (T1 safe)
)
public class IotIngestionAutoConfiguration {
    @Bean public MqttBridgeListener mqttBridgeListener() { ... }
    @Bean public SensorReadingIngestor ingestor() { ... }
}
```

Khi `iot-ingestion-external=true`: `IotIngestionAutoConfiguration` không load, không có `MqttBridgeListener` bean. Monolith tiếp tục consume Kafka `UIP.iot.sensor.reading.v1` — topic đó giờ được produce bởi iot-service bên ngoài.

---

### Phần D: Thứ tự Extraction và Trigger

Tách module **chỉ khi có trigger đo được**. Không tách vì "microservices là best practice".

| Thứ tự | Module | Trigger chính xác | Tier | Giao tiếp sau tách |
|--------|--------|------------------|------|-------------------|
| 1 | iot-ingestion | IoT throughput >50K events/sec sustained 30 phút | T2 | Kafka producer (unchanged topic) |
| 2 | alert | Alert processing p95 >5s, hoặc SLA breach sau iot tách | T2/T3 | Kafka consumer + Redis pub/sub |
| 3 | analytics | ClickHouse adopted (ADR-012 trigger) | T3 | REST API (read-only) |
| 4 | ai-workflow | >100 concurrent BPMN instances, hoặc Claude latency ảnh hưởng monolith SLA | T3 | Kafka (workflow events) |
| 5 | citizen | Concurrent users >5K, hoặc PII compliance isolation | T3 | REST API + Kafka |
| 6 | notification | Đi kèm citizen (stateless, dễ nhất) | T3 | Kafka consumer (fire-and-forget) |

**Giữ trong monolith đến T3 (không trigger):** `environment-module`, `esg-module`, `traffic-module`, `admin-module`.

**Trigger không hợp lệ:** "muốn tech mới", "microservices là best practice", "sợ monolith". Trigger phải là số đo được.

---

### Phần E: Extraction Procedure — Strangler Fig

Mỗi lần tách một module, tuân thủ đúng 5 bước:

```
Bước 1 — Chuẩn bị:
  Verify module communicate qua Port Interface, không direct Service inject
  Nếu chưa có Port Interface → refactor trước, commit riêng

Bước 2 — Tạo service trong cùng monorepo:
  Tạo applications/{service-name}/ với Dockerfile riêng
  Service chỉ import module code tương ứng — thin wrapper

Bước 3 — Deploy song song (shadow mode):
  Bật monolith VÀ service mới cùng lúc
  iot-ingestion-external=false → cả hai đều chạy nhưng service chưa nhận traffic
  Validate: Kafka output từ service mới == output từ monolith

Bước 4 — Cutover:
  Set iot-ingestion-external=true trong values file
  Monolith module tự disable; service mới nhận 100% traffic
  Monitor 1 sprint

Bước 5 — Cleanup (optional):
  Sau khi stable, có thể remove code khỏi monolith (giảm compile time)
  Hoặc giữ lại trong monolith nhưng disabled — cả hai đều OK
```

**Quy tắc không được phá vỡ:**
- Kafka topic naming **không đổi** khi extract — consumer không care ai produce
- Contract test viết **trước** khi extract, không phải sau
- Release tag `vX.Y.Z` apply cho **tất cả images** cùng lúc — không version skew
- Không bao giờ tạo repo riêng cho extracted service — phải trong monorepo

---

### Phần F: Git Branching — Trunk-Based Development

```
main (production-ready at all times)
  │
  ├── feature/mvp2-07-tenant-rls     ← short-lived, merge trong 1-2 ngày
  ├── feature/adr-012-clickhouse     ← short-lived
  └── hotfix/alert-npe-fix           ← merge same day

Release tags:
  v2.0.0 → T1 customer (values-tier1.yaml)
  v2.0.0 → T2 customer (values-tier2.yaml)
  v2.1.0 → tất cả tiers active cùng lúc
```

**Quy tắc bắt buộc:**
1. **Không có long-lived tier branch** — chỉ `main` tồn tại lâu dài
2. Feature branch tồn tại tối đa 2 ngày — tránh integration conflict
3. Mọi feature phải pass T1 baseline test trước khi merge vào `main`
4. CI chạy test suite với profile T1 (default) + T2 (multi-tenancy) cho mọi PR
5. Bug fix 1 lần trong `main` → tất cả tier nhận khi release

---

### Phần G: Helm Values — Infrastructure per Tier

Code giống nhau, infrastructure scale khác qua Helm values:

```yaml
# values-tier1.yaml
app:
  replicas: 1
  springProfile: tier1
kafka:
  replicas: 1
  partitions: 4
timescaledb:
  replicas: 1
clickhouse:
  enabled: false

# values-tier2.yaml (sau khi iot-service tách)
monolith:
  replicas: 2
  springProfile: tier2
kafka:
  replicas: 3
  partitions: 12
iot-ingestion-service:
  enabled: true       # deploy iot-service cùng với monolith
  replicas: 3

# values-tier3.yaml
monolith:
  replicas: 3
  springProfile: tier3
kafka:
  replicas: 3
  partitions: 24
iot-ingestion-service:
  enabled: true
  replicas: 5
alert-service:
  enabled: true
  replicas: 3
analytics-service:
  enabled: true
clickhouse:
  enabled: true
  replicas: 3
kong:
  enabled: true
```

---

### Phần H: Database Migration — Flyway trên mọi tier

Flyway chạy tất cả migration trên tất cả tier. Migration chỉ tạo structure — capability flag quyết định structure đó có được dùng không:

```sql
-- V14: thêm tenant_id — chạy T1, T2, T3, T4
-- T1: tenant_id='default', RLS không active
-- T2: tenant_id=real value, RLS active (enabled bởi capability flag riêng)
ALTER TABLE environment.sensors ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- V20: cursor cho ClickHouse sync — chạy T1, T2, T3
-- T1/T2: bảng tồn tại nhưng không được query (clickhouse=false)
CREATE TABLE IF NOT EXISTS esg.clickhouse_sync_cursor (
    tenant_id     TEXT,
    last_synced_at TIMESTAMP
);
```

**Quy tắc:** Migration tạo structure → chạy mọi nơi. Capability flag quyết định structure được dùng → tầng code. Không có conditional migration (`IF tier = 'T3'`).

---

### Tóm tắt: Vòng đời từ T1 đến T3

```
T1 (Docker Compose):
  1 image: uip-monolith:v2.x.y
  All modules in 1 JVM, no extraction flags set

T2 (K8s, sau khi iot trigger):
  2 images từ cùng tag:
    uip-monolith:v2.x.y        (iot-ingestion-external=true)
    uip-iot-service:v2.x.y     (thin wrapper, MQTT → Kafka)
  Multi-tenancy enabled, RLS active

T3 (K8s prod, ClickHouse adopted):
  4 images từ cùng tag:
    uip-monolith:v2.x.y        (iot-ext=true, alert-ext=true, analytics-ext=true)
    uip-iot-service:v2.x.y
    uip-alert-service:v2.x.y
    uip-analytics-service:v2.x.y
  Kong + Keycloak enabled

Xuyên suốt: 1 git repo, 1 main branch, 1 version tag cho tất cả images.
```

---

## Consequences

### Tích cực

- **Zero backporting**: bug fix ở `main` → tất cả tier nhận ngay khi release
- **Extraction an toàn**: capability flag disable-in-monolith + strangler fig = rollback bất kỳ lúc nào
- **Thứ tự tách rõ ràng**: team biết khi nào tách gì, không bị bất ngờ
- **Port Interface sẵn sàng**: swap sang gRPC/external khi tách không đổi business logic
- **1 CI pipeline**: test T1 baseline + T2 multi-tenancy là đủ cho hầu hết features
- **Onboarding đơn giản**: developer mới chỉ cần hiểu 1 repo, 1 branch strategy

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Monolith JAR chứa T3 code không dùng (larger binary) | Low | ~5-10%; T3 beans không load ở T1; acceptable |
| Version skew khi deploy nhiều images | Medium | CI enforce: tất cả images trong 1 release phải cùng tag |
| Developer quên `@ConditionalOnProperty` cho extracted module | Medium | Code review checklist; integration test với flag=false phát hiện ngay |
| Trigger xuất hiện đột ngột không kịp chuẩn bị | Medium | Monitor metrics; contract test viết sẵn trước khi trigger |
| Capability flag combination explosion (T4: 12 flags) | Low | Flags độc lập nhau; test T1 baseline + T2 multi-tenancy đủ cho hầu hết |

### Không chọn

| Phương án | Lý do loại |
|-----------|------------|
| Long-lived tier branches | Backporting hell; divergence inevitable |
| Separate repo per extracted service | Bug fix phải apply nhiều repos; version sync thủ công |
| Tách tất cả module cùng lúc (big bang) | Rủi ro cao; không rollback được |
| Runtime tier detection (`if tier == "T3"`) | Magic string coupling; tier leaks vào business logic |
| Tách ngay khi chưa có trigger | Over-engineering; tăng ops không có lợi ích đo được |

---

## Implementation Checklist

### MVP2-1 (immediate — chuẩn bị nền)
- [ ] Tạo `CapabilityProperties` class với `@ConfigurationProperties("uip.capabilities")`
- [ ] Tạo `application-tier1.yml`, `application-tier2.yml` với tất cả flags = false / true
- [ ] Refactor RLS/TenantContext beans: `@ConditionalOnProperty("uip.capabilities.multi-tenancy")`
- [ ] CI: thêm test run với profile `tier2` bên cạnh default
- [ ] Tạo cấu trúc `applications/monolith/` và `infra/helm/values/` theo monorepo layout

### Trước khi tách bất kỳ module nào
- [ ] Verify module communicate qua Port Interface, không direct inject
- [ ] Viết consumer-driven contract test cho tất cả cross-module interactions
- [ ] Setup distributed tracing (Jaeger/Zipkin) — bắt buộc khi có >1 service

### Khi iot-ingestion trigger (T2)
- [ ] Tạo `applications/iot-ingestion-service/` trong monorepo (không tạo repo mới)
- [ ] Thêm `IotIngestionAutoConfiguration` với `@ConditionalOnProperty(iot-ingestion-external=false, matchIfMissing=true)`
- [ ] Deploy song song, validate Kafka output identical
- [ ] Set `iot-ingestion-external=true` trong values-tier2.yaml
- [ ] CI: enforce cùng tag cho monolith + iot-service images

---

## Related

- ADR-010: Multi-Tenant Strategy (`multi-tenancy` capability flag)
- ADR-012: ClickHouse Adoption Trigger (`clickhouse` capability flag)
- ADR-013: Edge Computing (`edge-computing` capability flag)
- ADR-014: Telemetry Enrichment Pattern (iot-service cần inject tenant_id)
- ADR-017: Multi-Region (`multi-region` capability flag)
- ~~ADR-018~~: Superseded — nội dung đã hợp nhất vào ADR-011 này
