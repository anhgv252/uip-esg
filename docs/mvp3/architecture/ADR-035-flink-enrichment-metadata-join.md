# ADR-035: Flink Enrichment — Building Metadata Join Pattern

**Date:** 2026-05-15
**Status:** Proposed
**Deciders:** SA, Backend Lead
**Sprint:** MVP3-2

---

## Status

Proposed — SA spike output for v3-BE-04 implementation

## Context

v3-BE-04 (Sprint 2) requires enriching Flink sensor event streams with building metadata (building_name, district, category) before writing to ClickHouse. This enables the Analytics Dashboard to display human-readable building names and filter by district/category without additional joins at query time.

Current EsgDualSinkJob writes raw sensor events with `tenant_id` and `building_id` (extracted from deviceId pattern) but lacks `building_name`, `district`, and `category` fields. The ClickHouse `esg_readings` table schema also lacks these enrichment columns.

The building metadata lives in PostgreSQL `public.buildings` table, managed by the monolith's BuildingClusterService. The Flink job needs to JOIN this metadata at stream processing time.

---

## Decision

Use **Flink AsyncDataStream with JDBC lookup** for building metadata enrichment.

### Architecture

```
Kafka ngsi_ld_esg
  → EsgDualSinkJob
      → extractBuildingId (existing)
      → AsyncDataStream.unorderedWait(
            buildingMetadataLookup,
            timeout = 5s,
            capacity = 100
        )
      → Enriched event (adds building_name, district, category)
      ├── TimescaleDB Sink (unchanged)
      └── ClickHouse Sink (enriched schema)
```

### Why AsyncDataStream over alternatives

| Pattern | Latency | Complexity | Checkpoint Safe |
|---------|---------|------------|-----------------|
| **AsyncDataStream + JDBC** (CHOSEN) | ~5-20ms p99 with HikariCP pool | Medium | YES — Flink manages state |
| JDBC side-input (batch reload) | ~0ms after load | High — cache invalidation | YES but stale data risk |
| Broadcast Process Function | ~0ms | High — need separate Kafka source | YES — but overkill for ~100 buildings |
| Kafka Streams GlobalKTable | N/A — not available in Flink | N/A | N/A |

AsyncDataStream is the right choice because:
1. Building count is small (~5-100 buildings) → JDBC lookup is fast
2. HikariCP connection pool (min=2, max=5) handles concurrent lookups efficiently
3. Checkpoint-safe: Flink manages async operator state
4. Simpler than broadcast pattern for small lookup tables
5. `unorderedWait` — doesn't block the main stream, preserves throughput

### ClickHouse Schema Update

Add enrichment columns to `analytics.esg_readings`:

```sql
ALTER TABLE analytics.esg_readings
    ADD COLUMN IF NOT EXISTS building_name LowCardinality(String) DEFAULT '',
    ADD COLUMN IF NOT EXISTS district      LowCardinality(String) DEFAULT '',
    ADD COLUMN IF NOT EXISTS category      LowCardinality(String) DEFAULT '';
```

Update ORDER BY to include district for common filter-by-district queries:
```sql
-- New table (ReplacingMergeTree, TD-01):
ORDER BY (tenant_id, building_id, metric_type, recorded_at, district)
```

### HikariCP Configuration for Flink Lookup

```java
HikariConfig config = new HikariConfig();
config.setJdbcUrl(DB_URL);
config.setMaximumPoolSize(5);       // small pool — lookup only
config.setMinimumIdle(2);
config.setConnectionTimeout(3_000);
config.setIdleTimeout(60_000);
config.setMaxLifetime(300_000);
```

### Error Handling

- Metadata lookup timeout (>5s): emit event with empty enrichment fields (graceful degradation)
- Metadata not found (new building): emit with `building_name = building_id` (fallback to code)
- JDBC connection failure: Flink checkpoint will retry on restart

---

## Alternatives Considered

### Alternative 1: Enrich at query time (ClickHouse JOIN)
**Rejected.** ClickHouse `esg_readings` is in `analytics` database; building metadata is in PostgreSQL `public.buildings`. Cross-database JOIN is not possible. Would require duplicating building metadata into ClickHouse (another sync mechanism) or accepting query-time latency.

### Alternative 2: Pre-load all buildings into Flink state (Broadcast)
**Rejected.** Requires a second Kafka topic for building change events. Adds infrastructure complexity for a table that changes rarely (1-2 updates per week). Async JDBC is simpler and more maintainable.

### Alternative 3: Enrich in analytics-service (application layer)
**Rejected.** Would require analytics-service to maintain a building metadata cache and JOIN at query time. This adds latency to every ClickHouse query and duplicates caching logic. Better to enrich once at ingestion time.

---

## Consequences

### Positive
- ClickHouse queries can filter by district/category without any JOIN
- Building names directly available in dashboard — no extra API calls
- Graceful degradation: missing metadata doesn't block ingestion
- Throughput impact <100ms p99 (async, non-blocking)

### Negative / Risks
- R3: JDBC metadata join latency — mitigated by AsyncDataStream + HikariCP pool
- Building metadata changes require re-processing for historical data (acceptable — only affects display name)
- Additional PostgreSQL connections from Flink job (5 connections from lookup pool)

### Rollback
If enrichment causes issues, remove the AsyncDataStream operator from the pipeline. Events will be written without enrichment columns (empty strings as defaults). ClickHouse queries continue to work.

---

## Implementation

**Story:** v3-BE-04 (Sprint 2, 8 SP)
**Dependencies:** TD-01 (ReplacingMergeTree), TD-03 (Checkpoint E2E verified)
**Owner:** Backend Eng 2
**Files:**
- `flink-jobs/src/main/java/com/uip/flink/esg/BuildingMetadataAsyncFunction.java` (NEW)
- `flink-jobs/src/main/java/com/uip/flink/esg/EsgDualSinkJob.java` (MODIFY — add enrichment step)
- `flink-jobs/src/main/java/com/uip/flink/esg/ClickHouseSink.java` (MODIFY — enriched schema)
- `infra/clickhouse/schema/V001__create_analytics_schema.sql` (MODIFY — add columns)
