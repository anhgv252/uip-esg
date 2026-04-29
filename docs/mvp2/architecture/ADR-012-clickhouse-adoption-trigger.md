# ADR-012: ClickHouse Adoption Trigger Criteria

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect
**Scope**: T3 — ClickHouse chỉ được adopt khi trigger rõ ràng

---

## Context

MVP1 dùng TimescaleDB làm cả operational DB lẫn analytics DB. Điều này đủ cho T1/T2 vì:
- ESG report (<10 phút SLA) đạt được với TimescaleDB Continuous Aggregates
- Dashboard queries (<200ms p95) đạt được với index đúng + Redis cache (ADR-015)
- Team chưa cần maintain thêm infrastructure

ClickHouse là columnar OLAP database với throughput aggregation cao hơn TimescaleDB nhiều lần ở scale lớn. Tuy nhiên, adopting ClickHouse sớm mang lại:
- Thêm 1 database technology cần maintain
- Dual-write complexity (Flink phải write sang 2 target)
- Team phải học SQL dialect mới
- Ops overhead: ClickHouse cluster, schema management, backup strategy riêng

**Vấn đề cốt lõi:** TimescaleDB đủ cho T1/T2, nhưng ở T3 với 50K+ sensors và cross-domain analytics, TimescaleDB sẽ có giới hạn. Cần định nghĩa chính xác khi nào và làm gì.

---

## Decision

### Không adopt ClickHouse cho đến khi có trigger rõ ràng

TimescaleDB là **default choice** cho mọi analytics workload cho đến khi bị breach. Lý do:
- TimescaleDB đã trong stack, team đã quen
- Hypertable + Continuous Aggregates đủ cho T1/T2 scale
- Redis cache layer (ADR-015) giải quyết dashboard read load
- ClickHouse phức tạp hơn không tương xứng với lợi ích ở quy mô nhỏ

### Trigger để adopt ClickHouse

Chỉ adopt khi **ít nhất một** trigger sau được đo thực tế, không phải ước tính:

| # | Trigger | Threshold | Đo ở đâu |
|---|---------|-----------|---------|
| T1 | ESG report generation time | > 5 phút p95, sau khi đã optimize TimescaleDB query và index | CI performance test |
| T2 | District dashboard query (cross-site aggregate) | > 3 giây p95, sau khi đã apply Continuous Aggregates và Redis cache | Grafana dashboard |
| T3 | Số sensor active | > 10,000 sensors đang ghi data liên tục | TimescaleDB stats |
| T4 | Retention requirement analytics | > 90 ngày cần query với sub-2s | Business requirement |
| T5 | Cross-domain analytics (AQI + Traffic + ESG correlation) | Cần realtime join >3 domains ở district scale | Feature requirement |

**Khi trigger T1 hoặc T2 xuất hiện → chạy optimization checklist trước, chỉ adopt ClickHouse nếu vẫn không đạt.**

### Optimization checklist trước khi adopt ClickHouse

```sql
-- 1. Verify Continuous Aggregates đã được tạo và đang refresh
SELECT view_name, materialization_hypertable_name
FROM timescaledb_information.continuous_aggregates;

-- 2. Kiểm tra refresh policy đang chạy đúng
SELECT job_id, application_name, next_start, last_run_status
FROM timescaledb_information.job_stats
WHERE application_name LIKE '%Refresh%';

-- 3. EXPLAIN ANALYZE để xác nhận query đang dùng Continuous Aggregate
EXPLAIN ANALYZE
SELECT time_bucket('1 hour', bucket_start), sum(value)
FROM esg.clean_metrics
WHERE tenant_id = $1 AND timestamp > now() - interval '30 days'
GROUP BY 1;
-- Phải thấy "Custom Scan (ChunkAppend)" trên materialized view, không phải base table

-- 4. Kiểm tra chunk exclusion đang hoạt động
SELECT chunk_name, range_start, range_end
FROM timescaledb_information.chunks
WHERE hypertable_name = 'sensor_readings'
ORDER BY range_start DESC LIMIT 10;
```

Nếu sau optimization checklist vẫn breach trigger → proceed với ClickHouse adoption.

---

### Kiến trúc khi adopt ClickHouse

**Dual-write pattern qua Flink** — không migrate data cũ, bắt đầu ghi mới vào ClickHouse:

```
Kafka (ngsi_ld_telemetry)
    └─→ esg-aggregation-job (Flink)
            ├──→ TimescaleDB (hot operational, 30 days)  ← giữ nguyên
            └──→ ClickHouse (analytics, full retention)  ← thêm mới
```

**Phân chia responsibility:**

| Workload | Database | Lý do |
|---------|---------|-------|
| Operational queries (<30 days) | TimescaleDB | ACID, PostGIS, đã có |
| Real-time alerts, circuit breaker | TimescaleDB | Low-latency write path |
| Long-range analytics (>30 days) | ClickHouse | Columnar scan performance |
| ESG quarterly report | ClickHouse | Large aggregation, no ACID need |
| District dashboard (cross-site aggregate) | ClickHouse | Fan-out GROUP BY per-sensor |
| Citizen billing queries | TimescaleDB | ACID + join với PostgreSQL tables |

**Module ownership:**
- `analytics-service` sẽ own ClickHouse khi nó được tách theo ADR-011
- Trước khi tách: ESG module và analytics module query ClickHouse trực tiếp từ monolith
- **Không cross-module query**: ESG module chỉ query ClickHouse cho ESG data, không query traffic data

### ClickHouse Schema Design (khi adopt)

```sql
-- Denormalized table — mỗi row tự chứa đủ ngữ cảnh analytics
CREATE TABLE esg.clean_metrics_ch (
    tenant_id       LowCardinality(String),
    site_id         String,
    building_id     String,
    district        String,
    metric_type     LowCardinality(String),  -- energy, water, carbon
    period          LowCardinality(String),  -- 15m, 1h, 1d, 1m
    bucket_start    DateTime,
    value           Float64,
    unit            LowCardinality(String)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(bucket_start)
ORDER BY (tenant_id, site_id, metric_type, period, bucket_start);
```

**Lý do denormalize trong ClickHouse (khác TimescaleDB):**
- ClickHouse không có joins nhanh — scan trên wide table nhanh hơn JOIN nhiều table
- `district`, `building_id` được copy từ metadata tại write time
- Không cần normalize vì ClickHouse là append-only, không update

### Deployment cho T3

```
T3 deployment (10–30 nodes):
├── TimescaleDB cluster (1 primary + 2 replicas)
├── ClickHouse cluster (3 nodes, ReplicatedMergeTree)
│     ReplicationFactor = 2 (an toàn cho analytics)
└── Flink (thêm ClickHouse sink vào aggregation job)
```

---

## Consequences

### Tích cực

- **Không over-engineer T1/T2**: giữ stack đơn giản cho đến khi thực sự cần
- **Trigger đo được**: không phải cảm tính — có số liệu cụ thể trước khi quyết định
- **Phân chia rõ**: TimescaleDB = operational, ClickHouse = analytics
- **Dual-write qua Flink**: không cần migrate data cũ, low-risk adoption

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Dual-write tăng Flink job complexity | Medium | Thêm 1 sink vào job hiện có; test parallel với TimescaleDB baseline |
| ClickHouse schema drift khỏi TimescaleDB | Medium | Analytics-service là single owner của ClickHouse schema |
| Flink job lỗi → ClickHouse bị lag | Low | TimescaleDB vẫn là source of truth; ClickHouse là optional read path |
| Team chưa quen ClickHouse SQL dialect | Low | Chỉ adopt khi có engineer đã làm quen |

### Không chọn

| Phương án | Lý do loại |
|-----------|------------|
| Adopt ClickHouse ngay từ MVP2 | Không có trigger; overkill; tăng ops burden không cần thiết |
| Migrate hoàn toàn từ TimescaleDB sang ClickHouse | ClickHouse thiếu ACID, PostGIS; vẫn cần TimescaleDB cho operational |
| Apache Druid thay vì ClickHouse | ClickHouse SQL-compatible và dễ onboard hơn; Druid cần Zookeeper thêm complexity |
| BigQuery / Snowflake (SaaS) | Vendor lock-in; data residency concern (dữ liệu nhạy cảm đô thị); cost unpredictable |

---

## Implementation Checklist

### Khi trigger xuất hiện
- [ ] Chạy optimization checklist TimescaleDB (xem phần Decision)
- [ ] Benchmark: query time trước và sau TimescaleDB optimization
- [ ] Nếu vẫn breach → present kết quả benchmark để approve ClickHouse adoption

### Khi adopt ClickHouse
- [ ] Provision ClickHouse cluster (3 nodes RF=2 cho T3)
- [ ] Thêm ClickHouse sink vào `esg-aggregation-job` Flink
- [ ] Tạo ClickHouse schema theo pattern denormalized
- [ ] Validate: dual-write không ảnh hưởng Flink job throughput
- [ ] Chuyển ESG report generation query sang ClickHouse; validate kết quả == TimescaleDB
- [ ] Benchmark: ESG report time sau khi dùng ClickHouse (phải < 5 phút)

---

## Related

- ADR-011: Module Extraction Order (analytics-service sẽ own ClickHouse)
- ADR-015: Caching & Read-Heavy Performance (Redis cache là first line of defense trước ClickHouse)
- [Demo & Roadmap 2026-04-25 — Section 4.1, Table T3](../project/demo-and-roadmap-2026-04-25.md)
