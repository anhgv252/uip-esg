# ADR-015: Caching & Read-Heavy Performance Strategy — ESG API

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect, Product Owner
**Scope**: MVP2-2 trở đi — áp dụng cho `esg-service-api`

---

## Context

`esg-service-api` phục vụ hai loại workload có đặc điểm khác nhau:

**Dashboard queries** — tần suất cao, độ trễ thấp:
- ESG Overview refresh định kỳ (30–60s) cho tất cả user đang online.
- Nhiều user của cùng một tenant đọc cùng scope/time range → query trùng lặp cao.
- Data source: `esg_aggregate_metrics` với `period = '15m'` hoặc `'1h'`.
- Freshness yêu cầu: dữ liệu trễ 1–2 phút so với thực tế là chấp nhận được.

**Report queries** — tần suất thấp, data range lớn:
- ESG Monthly/Quarterly Report: scan hàng tuần đến hàng tháng dữ liệu.
- Thực hiện theo yêu cầu (on-demand), không phải định kỳ.
- Data source: `esg_aggregate_metrics` với `period = '1d'` hoặc `'1m'`.
- Freshness yêu cầu: có thể chấp nhận trễ vài phút đến vài giờ cho data cũ.

### Constraint từ kiến trúc hiện tại

`esg_aggregate_metrics` đã lưu data pre-aggregated ở 4 granularity (15m, 1h, 1d, 1m) — Flink đã làm việc aggregation. TimescaleDB Continuous Aggregates sẽ là **tầng thứ ba** trên dữ liệu đã aggregate, chỉ có ý nghĩa nếu report cần rollup qua nhiều scope (ví dụ: tổng kWh toàn tập đoàn qua 12 tháng). Với dashboard đọc dữ liệu của một scope cụ thể, Continuous Aggregates không mang lại lợi ích đáng kể.

### Vấn đề cần giải quyết

1. Giảm DB load cho các dashboard query trùng lặp giữa các user trong cùng tenant.
2. Tăng tốc report query khi data range lớn và cần cross-scope rollup.
3. Đảm bảo tenant isolation vẫn được giữ đúng trong mọi lớp cache.

---

## Decision

### Chiến lược chính: Application-Level Cache (Redis) cho dashboard queries

Redis cache ở tầng `esg-service-api`, cache API response theo cache key phân cấp:

```
cache key: esg:{tenant_id}:{scope_level}:{scope_id}:{period}:{from}:{to}
```

TTL theo loại query:
- Dashboard (period = 15m, 1h): TTL 60 giây — đủ fresh cho near-realtime display.
- Site Detail drill-down: TTL 120 giây.
- Report (period = 1d, 1m): TTL 5 phút — data ít biến động.

Cache invalidation: TTL-based (không cần event-driven invalidation ở MVP scale). Flink ghi batch vào TimescaleDB theo window, không ghi liên tục từng record.

**Tenant isolation trong cache**: `tenant_id` là thành phần đầu tiên trong cache key — không thể có cache hit cross-tenant. Không cần thêm cơ chế isolation bổ sung.

```java
// Cache key construction — tenant_id đứng đầu, không có cách nhầm lẫn
String cacheKey = String.format("esg:%s:%s:%s:%s:%s:%s",
    tenantId, scopeLevel, scopeId, period, from, to);
```

### Chiến lược bổ sung: TimescaleDB Continuous Aggregates cho report rollups

Áp dụng cho report queries cần rollup qua nhiều scope hoặc time range dài (tháng/quý/năm). Continuous Aggregates tính sẵn weekly và monthly summaries, giảm scan range khi generate report.

```sql
-- Weekly rollup từ daily buckets — chỉ có ý nghĩa cho report, không cho dashboard
CREATE MATERIALIZED VIEW esg_weekly_summary
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('7 days', bucket_start) AS week_start,
    tenant_id,
    scope_level,
    scope_id,
    metric_type,
    SUM(value)  AS total,
    AVG(value)  AS avg_val,
    MAX(value)  AS peak
FROM esg_aggregate_metrics
WHERE period = '1d'
GROUP BY 1, 2, 3, 4, 5;

-- Refresh policy: tính lại mỗi ngày, lookback 3 ngày để xử lý late data
SELECT add_continuous_aggregate_policy('esg_weekly_summary',
    start_offset => INTERVAL '3 days',
    end_offset   => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 day');
```

**RLS và Continuous Aggregates**: RLS không tự động apply trên materialized view. Bắt buộc dùng Wrapper View với `security_invoker`:

```sql
-- Wrapper View: RLS evaluate với quyền của caller, không phải view owner
CREATE VIEW esg_weekly_summary_secure
    WITH (security_invoker = true)
AS SELECT * FROM esg_weekly_summary;

-- Query luôn đi qua wrapper view, không đi thẳng vào materialized view
```

API chỉ expose `esg_weekly_summary_secure`, không expose `esg_weekly_summary` trực tiếp. Cần enforce bằng DB permission (revoke SELECT trên materialized view cho API user).

### Phân chia theo use case

| Query type | Primary strategy | Secondary |
|------------|-----------------|-----------|
| Dashboard overview (15m/1h) | Redis cache TTL 60s | — |
| Site detail drill-down (1h) | Redis cache TTL 120s | — |
| ESG Monthly Report | Continuous Aggregate (weekly_summary) | Redis cache TTL 5m |
| ESG Quarterly/Annual Report | Continuous Aggregate (monthly_summary) | Redis cache TTL 5m |
| Data Quality stats | Redis cache TTL 60s | — |

---

## Consequences

### Tích cực

- **Redis giải quyết ngay bài toán dashboard**: không cần thay đổi DB schema, không có RLS caveat.
- **Tenant isolation tự nhiên**: cache key bắt đầu bằng `tenant_id`, không cần logic isolation bổ sung.
- **Continuous Aggregates phù hợp với report workload**: data range lớn, refresh không cần realtime, pre-computation có ý nghĩa.
- **Không over-engineer**: dashboard không cần Continuous Aggregates vì data đã ở granularity phù hợp trong `esg_aggregate_metrics`.

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Cache miss storm khi Redis restart | Medium | TTL đủ nhỏ (60s) để warm lại nhanh; không cần persistent Redis storage |
| Continuous Aggregate stale khi có late data từ Flink | Low | `start_offset = 3 days` trong refresh policy bù cho Flink late data window |
| Developer query thẳng vào materialized view bỏ qua RLS | Critical | Revoke SELECT trên materialized view cho API DB user; chỉ grant trên wrapper view |
| Cache key collision nếu format không nhất quán | Medium | Đặt cache key format vào shared constant, không dùng string concatenation ad-hoc |

### Không chọn

| Giải pháp thay thế | Lý do loại |
|--------------------|------------|
| Continuous Aggregates cho dashboard | Không cần: `esg_aggregate_metrics` đã ở granularity phù hợp; thêm layer không có ý nghĩa |
| HTTP-level caching (ETag, Cache-Control) | Không khả thi cho tenant-specific data với RLS; phức tạp hóa API layer |
| In-memory cache trong `esg-service-api` (Caffeine) | Không share được giữa nhiều instance của API service khi scale-out |
| Không cache, query thẳng TimescaleDB | Acceptable ở T1; không đủ cho T2+ với nhiều concurrent user |

---

## Out of Scope

- Redis cluster setup và failover configuration: thuộc infrastructure runbook.
- Cache warming strategy khi deploy mới: có thể accept cold start ở MVP scale.
- Continuous Aggregates cho scope `group` (multi-site rollup): thuộc ADR-016 hoặc T4 prep.

---

## Implementation Checklist

### MVP2-2
- [ ] Deploy Redis instance (single node đủ cho MVP2)
- [ ] Implement cache layer trong `esg-service-api`: Spring Cache abstraction hoặc custom `CacheService`
- [ ] Chuẩn hoá `CacheKeyBuilder` — cache key format là shared constant, không inline string
- [ ] Test: verify cache key bao gồm `tenant_id`; verify không có cross-tenant cache hit

### MVP3 / T3 (khi report workload tăng)
- [ ] Tạo Continuous Aggregates: `esg_weekly_summary`, `esg_monthly_summary`
- [ ] Tạo Wrapper Views tương ứng với `security_invoker = true`
- [ ] Revoke SELECT trên materialized views cho API DB user; chỉ grant trên wrapper views
- [ ] Integration test: verify RLS hoạt động đúng qua wrapper view với nhiều tenant
- [ ] Benchmark: so sánh report query time trước và sau Continuous Aggregates

---

## Related

- ADR-010: Multi-Tenant Isolation Strategy (RLS + SET LOCAL)
- ADR-014: Telemetry Enrichment Pattern
- [uip-esg-architecture.md — Section 3.5, 3.7](../uip-esg-architecture.md)
- [SRS ESG — FR-ESG-008, FR-ESG-011](../SRS___Module_ESG.md)
