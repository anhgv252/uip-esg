# ADR-016: Data Lakehouse Strategy — Apache Iceberg on MinIO vs Snowflake

**Status**: Accepted
**Date**: 2026-04-28
**Deciders**: Tech Lead, Solution Architect
**Scope**: T4 — chỉ adopt khi historical data >2 năm hoặc ML training requirement

---

## Context

Ở T1→T3, TimescaleDB là đủ:
- Hot data (30 ngày): hypertable với chunk compression
- Analytics data (90 ngày): ClickHouse (khi adopted, ADR-012)
- ESG reports: MinIO lưu XLSX output (đã deploy)

Ở T4 (Smart Metropolis với 500+ buildings, 50K+ sensors, nhiều năm vận hành), xuất hiện các nhu cầu mà TimescaleDB và ClickHouse không đáp ứng tốt:

1. **Historical data >2 năm**: ClickHouse giữ toàn bộ data raw tốn storage rất lớn và expensive
2. **ML training**: Data scientist cần scan toàn bộ lịch sử (AQI, traffic, energy) để train ARIMA/LSTM — không thể chạy ML jobs trên production ClickHouse
3. **Cross-domain analytics**: Tương quan AQI ↔ traffic ↔ energy consumption ↔ weather qua 3 năm — cần query engine mạnh hơn ClickHouse
4. **Open data API**: Government yêu cầu expose anonymized city data cho researcher, startup, cơ quan — cần tầng data access riêng biệt
5. **Regulatory archival**: Một số regulatory framework yêu cầu lưu environmental data 7–10 năm

### Lựa chọn đang xem xét

1. **Apache Iceberg on MinIO** (open table format + S3-compatible storage đã có)
2. **Snowflake** (SaaS cloud data warehouse)

---

## Decision

### Chọn: Apache Iceberg on MinIO

**Không chọn Snowflake.** Lý do chi tiết ở phần "Không chọn" bên dưới.

### Kiến trúc Data Lakehouse

```
Hot Path (operational)
  TimescaleDB ← → ClickHouse (T3+)

Cold Path (historical / ML)
  Flink → Apache Iceberg (MinIO)
              │
              ├── Query Engine: Apache Trino (ad-hoc SQL)
              ├── ML Training: Spark hoặc DuckDB local
              └── Open Data API: Trino REST → API Gateway
```

### Tại sao Iceberg

**Apache Iceberg** là open table format — không phải một database, mà là một cách tổ chức Parquet files trên object storage với:
- Schema evolution: thêm/xóa column không rewrite data
- Time-travel: query data tại bất kỳ thời điểm nào trong quá khứ (useful cho audit)
- Partition evolution: thay đổi partition strategy không migrate data
- ACID semantics trên S3-compatible storage (MinIO)
- Compatible với Trino, Spark, Flink, DuckDB — không vendor lock-in

### Tại sao MinIO

MinIO đã là một phần của stack (lưu ESG XLSX reports). Dùng MinIO cho Iceberg storage:
- **Không thêm infrastructure mới** — chỉ thêm bucket và storage capacity
- S3-compatible: Iceberg API giống AWS S3 API — ecosystem tương thích hoàn toàn
- Có thể scale out MinIO cluster khi cần (distributed mode)
- Lưu tại on-premise: data sovereignty, không rời datacenter của thành phố

### Data Flow vào Lakehouse

```
Flink Archival Job (mới thêm ở T4):
  Source: Kafka (toàn bộ topics) + TimescaleDB change stream
  Transform: compress, anonymize PII (citizen data), add partition metadata
  Sink: Iceberg table on MinIO

Schedule: daily batch write (không cần realtime — cold path)
Retention: 7–10 năm (regulatory) với tiered storage (hot SSD → warm HDD → cold tape)
```

### Iceberg Schema Design

```sql
-- Partitioned theo year/month/domain để prune scan
CREATE TABLE uip_lakehouse.sensor_readings_archive (
    tenant_id     VARCHAR,
    domain        VARCHAR,   -- environment, traffic, energy, water
    sensor_id     VARCHAR,
    district      VARCHAR,
    metric_type   VARCHAR,
    timestamp     TIMESTAMP,
    value         DOUBLE,
    unit          VARCHAR
) PARTITIONED BY (year(timestamp), domain, district);

-- ESG aggregate archive (đã anonymized)
CREATE TABLE uip_lakehouse.esg_metrics_archive (
    tenant_id     VARCHAR,
    scope_level   VARCHAR,
    scope_id      VARCHAR,
    metric_type   VARCHAR,
    period        VARCHAR,
    bucket_start  TIMESTAMP,
    value         DOUBLE,
    unit          VARCHAR
) PARTITIONED BY (year(bucket_start), metric_type);
```

### Query Layer: Trino

Apache Trino (formerly PrestoSQL) là distributed SQL query engine:
- Query Iceberg trên MinIO với ANSI SQL
- Federation: query Iceberg + ClickHouse + TimescaleDB trong một query
- Rest API: ML team viết Python notebook gọi Trino
- Web UI: data analyst dùng Superset hoặc Metabase on top of Trino

```sql
-- Trino: cross-domain correlation query (3 năm dữ liệu)
SELECT
    date_trunc('month', timestamp) AS month,
    avg(case when domain='environment' and metric_type='aqi' then value end) AS avg_aqi,
    avg(case when domain='traffic' and metric_type='vehicle_count' then value end) AS avg_traffic,
    avg(case when domain='energy' and metric_type='consumption_kwh' then value end) AS avg_energy
FROM uip_lakehouse.sensor_readings_archive
WHERE district = 'hcmc.d7' AND year(timestamp) >= 2024
GROUP BY 1
ORDER BY 1;
```

### Deployment ở T4

```
MinIO Cluster: 4 nodes × 4TB HDD = 16TB usable (RF=2, erasure coding)
Trino: 1 coordinator + 3 workers (Docker Compose hoặc K8s)
Iceberg Catalog: REST catalog (simple, no Hive Metastore needed for T4 start)
```

---

## Consequences

### Tích cực

- **Không vendor lock-in**: Iceberg + MinIO là fully open-source, không phụ thuộc cloud vendor
- **Data sovereignty**: data ở on-premise, không rời datacenter — critical cho government data
- **Tái sử dụng MinIO**: không thêm infrastructure mới
- **Schema evolution**: có thể thêm metric mới mà không rewrite historical data
- **ML-ready**: Spark, DuckDB, Jupyter notebook đều đọc được Iceberg trực tiếp

### Tiêu cực / Risks

| Rủi ro | Mức độ | Mitigation |
|--------|--------|-----------|
| Trino cluster ops burden | Medium | Bắt đầu Docker Compose, chỉ chuyển K8s khi cần |
| Iceberg catalog management | Low | REST catalog đơn giản; không cần Hive Metastore ban đầu |
| MinIO storage scale | Medium | Add nodes khi cần; erasure coding tự xử lý |
| Flink archival job thêm resource | Low | Chạy offline (daily batch), không ảnh hưởng realtime jobs |
| Data quality: PII anonymization | High | Bắt buộc có data governance policy trước khi mở open data API |

### Không chọn

**Snowflake:**
| Tiêu chí | Snowflake | Iceberg/MinIO |
|----------|-----------|--------------|
| Vendor lock-in | ❌ Cao — phải dùng Snowflake SQL dialect | ✅ Open standard |
| Data sovereignty | ❌ Data rời datacenter (SaaS) | ✅ On-premise |
| Cost predictability | ❌ Per-credit billing, khó dự đoán khi nhiều concurrent queries | ✅ CapEx infrastructure, predictable |
| Government compliance | ❌ Khó đáp ứng data residency requirement | ✅ Tự control |
| Ecosystem | ✅ Managed, ít ops | ❌ Cần team ops |
| Initial setup | ✅ Nhanh hơn | ❌ Chậm hơn |

Ở Vietnam và cho government data, data residency là yêu cầu quan trọng. Snowflake lưu data tại AWS region Singapore là không đủ cho một số cơ quan nhà nước.

---

## Implementation Checklist

### T4 prep — khi trigger xuất hiện
- [ ] Provision MinIO distributed cluster (4 nodes, erasure coding)
- [ ] Setup Iceberg REST catalog
- [ ] Viết Flink archival job: Kafka → Iceberg daily batch
- [ ] Deploy Trino (1 coordinator + 2 workers đủ cho start)
- [ ] Test: Trino query 1 năm historical data, verify partition pruning hoạt động
- [ ] PII anonymization policy: xác định fields nào anonymize trước khi lưu Lakehouse
- [ ] Open data API: thiết kế access control cho researcher/government queries

---

## Related

- ADR-011: Module Extraction Order (analytics-service sẽ query Lakehouse)
- ADR-012: ClickHouse Adoption (ClickHouse = hot analytics, Iceberg = cold archive)
- [Demo & Roadmap 2026-04-25 — Section 4.3, New Domain Modules T4](../project/demo-and-roadmap-2026-04-25.md)
