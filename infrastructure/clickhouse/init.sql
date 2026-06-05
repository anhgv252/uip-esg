-- ClickHouse schema init — chạy tự động khi container khởi động lần đầu
-- Database được tạo qua CLICKHOUSE_DB env var; chỉ cần tạo bảng.
-- Schema matches: infra/clickhouse/schema/V001__create_analytics_schema.sql

CREATE DATABASE IF NOT EXISTS analytics;

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
) ENGINE = ReplacingMergeTree(ingested_at)
PARTITION BY toYYYYMM(recorded_at)
ORDER BY (tenant_id, building_id, source_id, metric_type, recorded_at)
TTL recorded_at + toIntervalYear(2)
SETTINGS index_granularity = 8192;

-- ─────────────────────────────────────────────────────────────────────────────
-- Hourly rollup table for sensor readings (Sprint 8 — BUG-006 fix)
-- Pre-aggregated hourly metrics to reduce query load on raw sensor data
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS analytics.sensor_reading_hourly
(
    tenant_id       String,
    sensor_id       String,
    sensor_type     LowCardinality(String),
    hour_bucket     DateTime,
    avg_value       Float64,
    min_value       Float64,
    max_value       Float64,
    reading_count   UInt32,
    ingested_at     DateTime DEFAULT now()
) ENGINE = ReplacingMergeTree(ingested_at)
PARTITION BY toYYYYMM(hour_bucket)
ORDER BY (tenant_id, sensor_id, sensor_type, hour_bucket)
TTL hour_bucket + toIntervalMonth(6)
SETTINGS index_granularity = 8192;
