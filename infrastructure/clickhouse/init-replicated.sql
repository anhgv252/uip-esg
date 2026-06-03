-- ClickHouse HA init — Replicated tables for 2-node cluster (ADR-036)
-- Runs automatically when HA containers start for the first time.
-- Uses ReplicatedReplacingMergeTree to auto-deduplicate rows.

CREATE DATABASE IF NOT EXISTS analytics;

-- Replicated version of analytics.esg_readings
-- ReplacingMergeTree uses `ingested_at` as version column for dedup.
-- ZooKeeper path: /clickhouse/tables/{shard}/esg_readings
-- Replica name from CLICKHOUSE_REPLICA_NAME env var (macros config).
CREATE TABLE IF NOT EXISTS analytics.esg_readings
(
    tenant_id    String,
    building_id  String,
    source_id    String DEFAULT '',
    metric_type  LowCardinality(String),
    value        Float64,
    unit         LowCardinality(String) DEFAULT '',
    recorded_at  DateTime CODEC(DoubleDelta, ZSTD(3)),
    ingested_at  DateTime DEFAULT now(),
    building_name String DEFAULT '',
    district     String DEFAULT ''
)
ENGINE = ReplicatedReplacingMergeTree(
    '/clickhouse/tables/{shard}/analytics/esg_readings',
    '{replica}',
    ingested_at
)
PARTITION BY toYYYYMM(recorded_at)
ORDER BY (tenant_id, building_id, source_id, metric_type, recorded_at)
TTL recorded_at + toIntervalYear(2)
SETTINGS index_granularity = 8192;
