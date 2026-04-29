# ADR-022: Cache Warming Strategy After Batch Write

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect
**Scope**: MVP2-6 — áp dụng cho `esg-service-api` cache layer

---

## Context

ADR-015 đã quyết định dùng Redis cache cho ESG dashboard queries với TTL 60 giây. Flink aggregation job ghi batch data vào TimescaleDB mỗi 1 phút. Ở MVP2-6, Kafka event sẽ trigger cache eviction khi Flink hoàn thành ghi batch.

### Vấn đề

Giữa thời điểm cache bị evict (khi Kafka event đến) và lần query kế tiếp populate cache, tồn tại một **stale window** mà request có thể nhận data cũ hoặc gây **cache miss storm**:

1. **Stale data window**: Sau khi Flink ghi batch mới vào TimescaleDB, cache vẫn chứa data cũ cho đến khi eviction event xử lý xong. Nếu eviction chậm, user thấy data cũ.
2. **Thundering herd problem**: Khi cache evict, nhiều user query đồng thời -> tất cả miss cache -> tất cả query thẳng TimescaleDB -> DB spike. Với 100 concurrent users, 100 query giống nhau đánh vào DB cùng lúc.

### Constraint từ kiến trúc hiện tại

- ADR-015: Redis cache key format `esg:{tenant_id}:{scope_level}:{scope_id}:{period}:{from}:{to}`, TTL 60s.
- Cache eviction được trigger bởi Kafka event khi Flink hoàn thành batch write.
- ESG dashboard không yêu cầu real-time: data trễ 1-2 phút là chấp nhận được.
- `CacheKeyBuilder` dùng explicit `tenantId` param, không dùng `TenantContext` ThreadLocal (xem ADR-010).
- Pattern cache hiện có: Spring `@Cacheable` / `@CacheEvict` (xem `TriggerConfigCacheService`).

---

## Decision

### Chiến lược: Accept TTL window với `@Cacheable(sync=true)`

Không triển khai cache warming ở MVP2. Thay vào đó, dùng `@Cacheable(sync=true)` để ngăn thundering herd: khi cache miss, chỉ 1 request query DB và populate cache, các request khác chờ kết quả từ request đó.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class EsgDashboardCacheService {

    static final String CACHE_NAME = "esg-dashboard";

    private final EsgDashboardRepository dashboardRepo;

    @Cacheable(
        value = CACHE_NAME,
        key = "T(com.uip.backend.esg.cache.CacheKeyBuilder)"
            + ".buildKey(#tenantId, #scopeLevel, #scopeId, #period, #from, #to)",
        sync = true                    // <-- 1 thread populate, cac thread khac doi
    )
    public EsgDashboardResponse getDashboard(
            String tenantId,
            String scopeLevel,
            String scopeId,
            String period,
            String from,
            String to) {
        log.debug("[CACHE] MISS esg-dashboard for tenant={} scope={}:{}",
            tenantId, scopeLevel, scopeId);
        return dashboardRepo.queryDashboard(tenantId, scopeLevel, scopeId, period, from, to);
    }

    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAll() {
        log.info("[CACHE] Evicted all esg-dashboard cache entries");
    }
}
```

```java
// CacheKeyBuilder -- explicit tenantId param, khong ThreadLocal
public final class CacheKeyBuilder {

    private CacheKeyBuilder() {}

    public static String buildKey(
            String tenantId,
            String scopeLevel,
            String scopeId,
            String period,
            String from,
            String to) {
        return String.format("esg:%s:%s:%s:%s:%s:%s",
            tenantId, scopeLevel, scopeId, period, from, to);
    }
}
```

### Tại sao KHONG warming ở MVP2

| Yếu tố | Phân tích |
|--------|-----------|
| Freshness requirement | ESG dashboard chấp nhận trễ 1-2 phút; TTL 60s đã đủ |
| Complexity | Warming service cần biết chính xác key nào cần warm -> phải parse Flink batch metadata -> phức tạp |
| Scale | MVP2 có ít concurrent users; thundering herd risk thấp, `sync=true` đủ handle |
| Cost/benefit | Thêm EsgCacheWarmupService tăng complexity đáng kể nhưng lợi ích không rõ ràng ở MVP scale |

### Cách `@Cacheable(sync=true)` hoạt động

```
Timeline sau khi Kafka eviction event den:

T=0s   Cache evict -> cache empty
T=0.1s Request A den -> cache miss -> query DB -> populate cache
T=0.1s Request B den -> cache miss -> BLOCK, doi Request A
T=0.1s Request C den -> cache miss -> BLOCK, doi Request A
T=0.2s Request A hoan thanh -> cache populated
T=0.2s Request B, C nhan ket qua tu cache (khong query DB)
T=0.3s Request D den -> cache HIT -> tra ket qua ngay
...
T=60s  TTL expire -> quay lai nhu tren
```

**Ket qua**: Chi 1 DB query cho moi cache key miss, khong phai N queries.

---

## Options considered

### Option 1: Accept TTL window, sync=true (CHON)

**Mo ta**: Evict cache khi Kafka event den. Request tiep theo miss -> query DB -> populate cache. `@Cacheable(sync=true)` dam bao chi 1 thread populate.

**Uu diem**:
- Don gian: khong can them service moi, chi them `sync = true` vao annotation.
- Nhat quan voi pattern hien co (`TriggerConfigCacheService`).
- TTL 60s du ngan: staleness window chap nhan duoc.
- Spring native: khong can dependency ngoai.

**Nhuoc diem**:
- Worst case: 60s stale data sau khi Flink ghi moi.
- Request dau tien sau evict co latency cao hon (phai query DB).

### Option 2: Eager warming

**Mo ta**: Sau khi Flink ghi xong, Kafka event trigger warmup: query DB -> populate cache ngay truoc khi user request den.

```java
// Chi draft, KHONG trien khai o MVP2
@Service
public class EsgCacheWarmupService {

    private final EsgDashboardRepository dashboardRepo;
    private final CacheManager cacheManager;

    @KafkaListener(topics = "esg-batch-write-complete")
    public void onBatchWriteComplete(BatchWriteEvent event) {
        // Parse metadata tu Flink batch -> xac dinh key can warm
        // Query DB -> put vao cache
        // Complex: can biet chinh xac tenant/scope/period/time range
    }
}
```

**Uu diem**:
- Khong co stale window: cache da co data truoc khi user request.
- Khong co miss latency cho user.

**Nhuoc diem**:
- Complex: phai parse Flink batch metadata de biet key nao can warm.
- Over-warming risk: warm nhieu key khong can thiet.
- Tight coupling: cache layer phai hieu Flink batch structure.
- Khong co gia tri o MVP2 scale.

### Option 3: Refresh-ahead

**Mo ta**: Background thread refresh cache truoc khi TTL expire, user luon nhan data tu cache.

**Uu diem**:
- User khong bao gio thay cache miss.
- Latency nhat quan.

**Nhuoc diem**:
- Can Spring Cache extension hoac custom scheduler.
- Refresh nhieu key khong can thiet (tenant khong co user online).
- Phuc tap nhat trong 3 options.
- Overkill cho dashboard khong yeu cau real-time.

### So sanh

| Tieu chi | Option 1: sync=true | Option 2: Eager warming | Option 3: Refresh-ahead |
|----------|--------------------|-----------------------|----------------------|
| Complexity | Thap | Trung binh | Cao |
| Stale window | Toi da 60s | Khong co | Khong co |
| Thundering herd | Khong (sync=true) | Khong | Khong |
| Dependency | Spring native | Them service + Kafka listener | Custom scheduler |
| Phu hop MVP2 | **Co** | Khong | Khong |

---

## Consequences

### Tich cuc

- **Don gian**: Chi them 1 thuoc tinh `sync = true` vao `@Cacheable`. Khong can service moi, khong can scheduler, khong can Kafka listener them.
- **Nhat quan**: Cung pattern voi `TriggerConfigCacheService` da co san.
- **Giai quyet duoc thundering herd**: 1 thread populate, cac thread khac doi. Khong co DB spike.
- **De nang cap**: Neu can warming o T2/T3, them `EsgCacheWarmupService` khong anh huong code hien tai.

### Tieucuc / Risks

| Rui ro | Muc do | Mitigation |
|--------|--------|-----------|
| Worst case 60s stale data sau Flink ghi moi | Low | ESG dashboard khong yeu cau real-time; 60s la acceptable |
| `@Cacheable(sync=true)` chi hoat dong voi Spring 5.3+ | Low | Dung Spring Boot 3.x (Spring 6.x Framework) -> da ho tro |
| Request dau tien sau evict co latency cao (query DB) | Medium | Dashboard query tren TimescaleDB da optimize (< 100ms); khong anh huong UX |
| Neu scale len T2/T3 voi nhieu tenant, sync=true khong du | Medium | Luc do them Eager warming (Option 2); trien khai tang progressive |

### Khong chon

| Giai phap thay the | Ly do loai |
|--------------------|------------|
| Khong dung sync=true, chap nhan thundering herd | DB spike voi nhieu concurrent users; khong chap nhan duoc o bat ky tier nao |
| Warm toan bo cache sau moi batch write | Over-warming: nhieu tenant/scope khong co user online -> phi DB query vo ich |
| Dung Stale-While-Revalidate | Spring Cache khong ho tro native; can custom implementation |

---

## Out of Scope

- Chi tiet Flink batch write event schema: thuoc ADR-014 va Flink job documentation.
- Redis cluster configuration va failover: thuoc infrastructure runbook.
- Cache warming cho report queries (period = 1d, 1m): TTL 5 phut du dai, staleness khong anh huong report UX.

---

## Implementation Checklist

### MVP2-6 (immediate)
- [ ] Them `sync = true` vao `@Cacheable` trong `EsgDashboardCacheService`
- [ ] Implement `CacheKeyBuilder` voi explicit `tenantId` param (khong ThreadLocal)
- [ ] Implement `@CacheEvict` listener cho Kafka eviction event
- [ ] Unit test: verify chi 1 thread query DB khi nhieu thread cung miss cache
- [ ] Integration test: verify thundering herd khong xay ra (JMeter voi 50 concurrent requests)
- [ ] Verify cache key bao gom `tenantId`; khong co cross-tenant cache hit

### T2/T3 (khi scale len nhieu tenant)
- [ ] Danh gia xem `sync=true` con du khong (measure DB load, cache hit rate)
- [ ] Neu can: trien khai `EsgCacheWarmupService` (Option 2) cho top tenants
- [ ] Consider refresh-ahead cho tenants voi traffic cao va SLA nghiem ngat

---

## Related

- ADR-010: Multi-Tenant Isolation Strategy (RLS + SET LOCAL, tenant_id trong cache key)
- ADR-014: Telemetry Enrichment Pattern (Flink batch write event)
- ADR-015: Caching & Read-Heavy Performance Strategy (Redis cache, TTL, cache key format)
- [uip-esg-architecture.md — Section 3.5, 3.7](../uip-esg-architecture.md)
- [SRS ESG — FR-ESG-008, FR-ESG-011](../SRS___Module_ESG.md)
