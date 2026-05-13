-- ClickHouse OLAP schema cho analytics-service
-- Sprint MVP3-1 | ADR-026: ClickHouse Pre-emptive Adoption
-- Single-node POC; Sprint 2 upgrade sang 2-node HA (v3-DevOps-03)

CREATE DATABASE IF NOT EXISTS analytics;

-- =============================================================================
-- Sensor reading hourly rollup (Flink dual-sink target)
-- =============================================================================
CREATE TABLE IF NOT EXISTS analytics.sensor_reading_hourly (
    tenant_id        UInt32          COMMENT 'Tenant ID hashed (MurmurHash3)',
    building_id      UInt32          COMMENT 'Building ID hashed',
    sensor_id        UInt64          COMMENT 'Sensor ID hashed',
    metric_type      LowCardinality(String) COMMENT 'energy | water | co2 | air_quality',
    ts_hour          DateTime        CODEC(DoubleDelta, ZSTD(3)),
    avg_value        Float64         CODEC(Gorilla, ZSTD(3)),
    sum_value        Float64         CODEC(Gorilla, ZSTD(3)),
    sample_count     UInt32          DEFAULT 0
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(ts_hour)
ORDER BY (tenant_id, building_id, sensor_id, metric_type, ts_hour)
TTL ts_hour + INTERVAL 2 YEAR DELETE
SETTINGS index_granularity = 8192;

-- =============================================================================
-- Cross-building rollup (materialized view từ sensor_reading_hourly)
-- =============================================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.building_hourly_rollup_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(ts_hour)
ORDER BY (tenant_id, building_id, metric_type, ts_hour)
AS SELECT
    tenant_id,
    building_id,
    metric_type,
    ts_hour,
    sum(sum_value)   AS sum_value,
    sum(sample_count) AS sample_count
FROM analytics.sensor_reading_hourly
GROUP BY tenant_id, building_id, metric_type, ts_hour;

-- =============================================================================
-- ESG readings (native string tenant_id + building_id cho analytics-service)
-- analytics-service ghi trực tiếp vào table này qua JDBC
-- =============================================================================
CREATE TABLE IF NOT EXISTS analytics.esg_readings (
    tenant_id    String                  COMMENT 'Tenant identifier (plain string)',
    building_id  String                  COMMENT 'Building code hoặc ID',
    source_id    String                  DEFAULT ''  COMMENT 'Sensor device ID',
    metric_type  LowCardinality(String)  COMMENT 'energy | water | co2',
    value        Float64,
    unit         LowCardinality(String)  DEFAULT '',
    recorded_at  DateTime                CODEC(DoubleDelta, ZSTD(3)),
    ingested_at  DateTime                DEFAULT now()
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(recorded_at)
ORDER BY (tenant_id, building_id, source_id, metric_type, recorded_at)
TTL recorded_at + INTERVAL 2 YEAR DELETE
SETTINGS index_granularity = 8192;

-- =============================================================================
-- Tenant data isolation view (query qua view này trong production)
-- =============================================================================
CREATE VIEW IF NOT EXISTS analytics.esg_readings_v AS
SELECT * FROM analytics.esg_readings
-- Note: tenant filtering xử lý ở application layer (analytics-service nhận tenantId từ JWT)
;
