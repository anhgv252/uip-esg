# ADR-026: ClickHouse Pre-emptive Adoption for Cross-Building OLAP Analytics

**Date:** 2026-05-12  
**Status:** Accepted  
**Deciders:** SA, Backend Lead, DevOps  
**Sprint:** MVP3-1

---

## Status

Accepted — overrides ADR-012's KPI-breach trigger for analytics extraction

## Context

ADR-012 defined the trigger for extracting analytics to a dedicated store as "ESG dashboard query p95 > 5 minutes sustained for 30 days." That trigger was designed for single-building MVP1/MVP2 workloads.

MVP3 cross-building analytics will exceed TimescaleDB limits **from day one**:

- 5 buildings × 2M ESG rows = **10M rows per query scope**
- Cross-building `GROUP BY building_id` with time-series data = TimescaleDB estimated p95 **3–8 seconds**
- Alert engine, citizen API, and analytics share the same TimescaleDB connection pool in monolith
- An analytics spike (100+ concurrent dashboard users) would starve the alert pipeline of connections

Rather than wait for the KPI breach, we adopt ClickHouse pre-emptively to prevent the breach from ever occurring.

---

## Decision

Deploy ClickHouse as the **OLAP layer** for cross-building analytics, served exclusively by `analytics-service`. TimescaleDB remains the **operational hot store** (recent <30 days, alert path, sensor writes).

### Architecture

```
IoT Event
  → Kafka UIP.iot.bms.reading.v1
  → Flink EsgClickHouseEnrichmentJob
        ├── TimescaleDB Sink (operational, alert-path)   ← MVP2 unchanged
        └── ClickHouse Analytics Sink (batch 5,000/2s)  ← NEW Sprint 2
```

### ClickHouse schema (POC, single-node)

```sql
CREATE TABLE analytics.sensor_reading_hourly (
    tenant_id     UInt32,
    building_id   UInt32,
    sensor_id     UInt64,
    metric_type   LowCardinality(String),
    ts_hour       DateTime CODEC(DoubleDelta, ZSTD),
    avg_value     Float64  CODEC(Gorilla, ZSTD),
    sum_value     Float64  CODEC(Gorilla, ZSTD),
    sample_count  UInt32
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(ts_hour)
ORDER BY (tenant_id, building_id, sensor_id, metric_type, ts_hour)
TTL ts_hour + INTERVAL 2 YEAR DELETE;
```

### Constraints

1. ClickHouse **NOT** used for alert path — alert latency must not depend on OLAP tier
2. ClickHouse queries return empty list (not 500) if connection fails — analytics degraded gracefully
3. analytics-service exclusively owns ClickHouse schema (monolith never queries ClickHouse directly)
4. Shadow mode (Sprint 1) validates delta <0.01% vs TimescaleDB before Sprint 2 cutover

---

## Alternatives Considered

### Alternative 1: Materialized views in TimescaleDB
**Rejected.** TimescaleDB continuous aggregates work well for single-building. Cross-building aggregates require `GROUP BY (tenant_id, building_id)` which conflicts with hypertable partition key. p95 will exceed 5s at 10M rows.

### Alternative 2: ClickHouse Cloud (managed)
**Deferred.** Cost acceptable for production but overkill for POC. Revisit if self-hosted HA delays (R4, 40% probability). Contract provisioning takes 48h — can activate if needed Sprint 2 Day 10.

### Alternative 3: Apache Druid
**Rejected.** Significantly more operationally complex (Coordinator, Broker, Historical nodes). ClickHouse simpler for team skill level. Druid adoption would require dedicated DevOps sprint.

### Alternative 4: DuckDB (embedded)
**Rejected.** Cannot be used by multiple analytics-service replicas (no shared storage). Embedding in analytics-service would violate single-responsibility.

---

## Consequences

### Positive
- Cross-building query p95 <1,000ms @ 10M rows (ClickHouse MergeTree vs PG seq scan)
- Analytics spike (200 VU) → zero impact on monolith alert API p95 (separate JVM, separate pool)
- `LowCardinality(String)` on metric_type = ~10x dictionary compression
- Columnar CODEC (Gorilla for floats, DoubleDelta for timestamps) = 3–5x storage reduction

### Negative / Risks
- **R4 (40%)**: ClickHouse cluster HA delays — mitigation: POC single-node Sprint 1, HA Sprint 2; fallback = ClickHouse Cloud
- Dual-write adds Flink complexity (exactly-once, DLQ) — mitigated by `uid()` on sinks + RocksDB state backend
- Shadow mode adds 1 sprint latency before analytics-service goes live

### Rollback plan
Set `analytics-external=false` in ConfigMap → monolith loads `TimescaleDbAnalyticsAdapter` (matchIfMissing=true) → analytics-service traffic drops to 0. Time: <5 minutes.

---

## Implementation

**Infrastructure:** `infra/clickhouse/docker-compose.poc.yml`  
**Schema:** `infra/clickhouse/schema/V001__create_analytics_schema.sql`  
**Sprint 2:** `v3-BE-03` (ClickHouse client), `v3-BE-04` (Flink dual-sink job)  
**Sprint 1:** POC validation only (manual queries, no Flink integration yet)
