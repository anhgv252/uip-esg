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
    ingested_at  DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(recorded_at)
ORDER BY (tenant_id, building_id, source_id, metric_type, recorded_at)
SETTINGS index_granularity = 8192;
