# ADR-020: Non-HTTP Tenant ID Propagation

**Status:** Proposed
**Date:** 2026-04-29
**Deciders:** Tech Lead, Solution Architect
**Scope:** MVP2 — Kafka consumers, Flink jobs, @Async methods
**Changelog:**
- 2026-04-29: Bản gốc — Proposed

---

## Context

ADR-010 định nghĩa `TenantContext` (ThreadLocal) được set từ JWT `tenant_id` claim tại HTTP request filter. Pattern này hoạt động đúng trong request-response lifecycle:

```
HTTP Request → JwtTenantFilter.set(TenantContext) → Controller → Service → Repository → TenantContext.clear()
```

Nhưng UIP có ba execution path chạy **ngoài HTTP request context**, nơi JWT không tồn tại và `TenantContext` là null:

| Path | Ví dụ | Vấn đề |
|------|--------|---------|
| **Kafka consumer** | `@KafkaListener` xử lý `esg.telemetry.v1` | Spring Kafka listener pool thread không mang theo HTTP session |
| **Flink job** | `esg-cleansing-job` đọc từ Kafka | Flink task slot là JVM thread riêng, không liên quan Spring context |
| **@Async method** | `NotificationService.sendAsync()` | Spring `@Async` dùng `TaskExecutor` thread mới, ThreadLocal bị mất |

Khi `TenantContext` null, RLS policy (ADR-010, T2) sẽ block mọi query — đúng về mặt bảo mật nhưng gây runtime crash nếu không xử lý tường minh. Cần quyết định nguồn `tenant_id` cho ba non-HTTP path này.

### Constraint từ ADR-014

ADR-014 đã quyết định `tenant_id` là **required field** trong message schema `ngsi_ld_telemetry` (được inject tại Redpanda Connect pipeline). Điều này có nghĩa: mọi Kafka message đi qua hệ thống **đã có sẵn** `tenant_id` trong payload.

---

## Decision

### Chiến lược chung: Extract `tenant_id` từ message body

**Mọi Kafka message trong UIP phải chứa field `tenant_id` ở top level.** Consumer/processor tự extract và set `TenantContext` thủ công trước khi thực hiện business logic.

```
Kafka Message → extract tenant_id from body → TenantContext.set(tenantId) → business logic → TenantContext.clear()
```

Pattern này áp dụng đồng nhất cho cả 3 non-HTTP path:

### 1. Kafka Consumer (Backend `@KafkaListener`)

```java
@KafkaListener(topics = "UIP.esg.telemetry.v1")
public void onTelemetry(String payload) {
    TelemetryEvent event = deserialize(payload);
    try {
        TenantContext.set(event.getTenantId());  // extract từ message body
        // ... business logic, repository calls ...
    } finally {
        TenantContext.clear();
    }
}
```

### 2. Flink Job

Flink job đọc `tenant_id` trực tiếp từ message payload (không cần TenantContext — Flink không chạy trong Spring):

```java
// Trong esg-cleansing-job ProcessFunction
public void processElement(TelemetryEvent event, Context ctx, Collector<CleanMetric> out) {
    if (event.getTenantId() == null) {
        ctx.output(errorTag, event);  // route sang error stream
        return;
    }
    // ... cleansing logic, giữ tenant_id cho output message ...
}
```

TenantIdValidator (story MVP2-24) kiểm tra `tenant_id` not null tại đầu Flink pipeline. Message thiếu field bị route sang error topic.

### 3. @Async Methods

Spring `@Async` tạo thread mới, ThreadLocal không tự propagate. Dùng `TenantContextTaskDecorator` (BT-CC-03) wrap executor:

```java
public class TenantContextTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        String tenantId = TenantContext.get();  // capture parent thread
        return () -> {
            try {
                TenantContext.set(tenantId);
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
```

Register trong `AsyncConfig`:

```java
@Bean
public TaskExecutor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new TenantContextTaskDecorator());
    // ... pool size config ...
    return executor;
}
```

---

## Options đã đánh giá

### Option 1: Message body field `tenant_id` (chọn)

Mỗi Kafka message chứa `tenant_id` ở top-level trong JSON payload. Consumer extract và set TenantContext.

| Tiêu chí | Đánh giá |
|----------|----------|
| Consistency với ADR-014 | Tuyệt đối — ADR-014 đã require field này |
| Debug | Cao — `tenant_id` visible trực tiếp trong payload |
| Schema enforcement | JSON schema validation kiểm tra được |
| Overhead | Không — field đã có sẵn, chỉ cần đọc |

### Option 2: Kafka message header `tenant_id`

Đặt `tenant_id` vào Kafka record header thay vì body. Tách metadata khỏi business data.

| Tiêu chí | Đánh giá |
|----------|----------|
| Separation of concerns | Tốt — metadata không lẫn vào business payload |
| Debug | Kém — header không hiển thị trong nhiều Kafka UI tools |
| Cross-system compat | Rủi ro — Flink Kafka connector cần config riêng để đọc header |
| Consistency với ADR-014 | Thấp — ADR-014 định nghĩa tenant_id trong body |

### Option 3: Topic naming convention `UIP.{tenantId}.telemetry.v1`

Mỗi tenant có Kafka topic riêng. Consumer phân biệt tenant qua topic name.

| Tiêu chí | Đánh giá |
|----------|----------|
| Physical isolation | Tốt — tenant data tách biệt tại topic level |
| Scalability | Kém — 100 tenant = 100 topic, Flink job cần subscribe động |
| Flink complexity | Cao — cần dynamic topic subscription hoặc multiple source |
| Operations | Tốn — topic management, monitoring, retention policy per topic |
| Consistency với ADR-010 tier model | Không cần — T2 dùng RLS, không cần physical topic isolation |

---

## Consequences

### Tích cực

- **Nhất quán với ADR-014**: `tenant_id` đã là required field trong message schema, không cần thêm field mới.
- **Đơn giản cho cả 3 path**: cùng một pattern extract-set-clear, không cần logic khác nhau cho từng execution context.
- **Debug dễ**: kiểm tra message payload trực tiếp thấy `tenant_id`, không cần inspect header hay topic name.
- **Flink không cần Spring context**: job chỉ đọc field từ POJO, không phụ thuộc ThreadLocal.

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Existing message thiếu `tenant_id` → breaking change | High | TenantIdValidator route sang error topic, không drop; backfill pipeline existings |
| Developer quên `TenantContext.set()` trong consumer mới | Medium | Abstract base class `TenantAwareKafkaListener` wrap try/finally; code review checklist |
| `@Async` chạy khi TenantContext null (scheduled job) | Low | TaskDecorator handle null gracefully — set tenant_id = null, RLS block query, log warning |
| Flink message schema thay đổi field name | Low | Avro/JSON schema registry enforce field name; breaking change cần version bump |

### Không chọn

| Giải pháp thay thế | Lý do loại |
|--------------------|------------|
| Kafka header `tenant_id` | Không dễ debug, Flink connector cần custom config, không consistent với ADR-014 |
| Topic naming convention | Không scalable, tăng Flink complexity, không cần thiết khi RLS đã handle isolation |
| Propagate ThreadLocal qua Kafka (Spring Cloud Stream) | Over-engineering, coupling Spring context vào Kafka layer |
| Hardcode tenant_id per consumer | Không hoạt động trong multi-tenant deployment |

---

## Implementation Checklist

### MVP2-1 — Kafka message schema

- [ ] Xác nhận `tenant_id` là required field trong tất cả Kafka message schemas (telemetry, alerts, notifications)
- [ ] Abstract `TenantAwareKafkaListener` base class: auto extract `tenant_id`, set TenantContext, try/finally clear
- [ ] TenantIdValidator trong Flink: check null, route error

### MVP2-2 — @Async propagation

- [ ] Implement `TenantContextTaskDecorator` (BT-CC-03)
- [ ] Register trong `AsyncConfig` — áp dụng cho mọi `@Async` method
- [ ] Unit test: verify TenantContext propagated đúng sang async thread

### MVP2-2 — Error handling

- [ ] Error topic cho message thiếu `tenant_id`: `UIP.errors.tenant_missing.v1`
- [ ] Dead Letter Queue consumer: log warning, alert ops team
- [ ] Integration test: gửi message không có `tenant_id` → verify RLS block, verify error topic nhận được

---

## Anti-Pattern Checklist

- [x] Không có cross-module direct dependency — consumer tự extract, không gọi module khác
- [x] Không có business logic trong Flink — chỉ validation tenant_id not null
- [x] Message thiếu tenant_id không bị drop — route sang error topic (DLQ)
- [x] Không có PII trong error log — chỉ log tenant_id (business identifier)

---

## Related

- ADR-010: Multi-Tenant Isolation Strategy — TenantContext ThreadLocal, RLS, SET LOCAL
- ADR-014: Telemetry Enrichment Pattern — inject tenant_id tại Redpanda Connect pipeline
- ADR-011: Monorepo Module Extraction — capability flags cho multi-tenant features
- [MVP2-24: TenantIdValidator](../project/mvp2-detail-plan.md)
- [BT-CC-03: TenantContextTaskDecorator](../project/mvp2-detail-plan.md)
