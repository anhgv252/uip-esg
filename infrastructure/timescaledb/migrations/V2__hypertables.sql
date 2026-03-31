-- V2__hypertables.sql
-- Convert time-series tables to TimescaleDB hypertables
-- Create indexes and continuous aggregates

-- ─── Hypertables ─────────────────────────────────────────────────────────────
SELECT create_hypertable(
    'environment.sensor_readings',
    'timestamp',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

SELECT create_hypertable(
    'esg.clean_metrics',
    'timestamp',
    chunk_time_interval => INTERVAL '7 days',
    if_not_exists => TRUE
);

SELECT create_hypertable(
    'traffic.traffic_counts',
    'timestamp',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

-- ─── Hypertable Compression ───────────────────────────────────────────────────
ALTER TABLE environment.sensor_readings
    SET (timescaledb.compress,
         timescaledb.compress_segmentby = 'sensor_id',
         timescaledb.compress_orderby   = 'timestamp DESC');

SELECT add_compression_policy('environment.sensor_readings',
    compress_after => INTERVAL '7 days',
    if_not_exists => TRUE);

ALTER TABLE esg.clean_metrics
    SET (timescaledb.compress,
         timescaledb.compress_segmentby = 'source_id, metric_type',
         timescaledb.compress_orderby   = 'timestamp DESC');

SELECT add_compression_policy('esg.clean_metrics',
    compress_after => INTERVAL '30 days',
    if_not_exists => TRUE);

-- ─── Indexes ──────────────────────────────────────────────────────────────────
CREATE INDEX IF NOT EXISTS idx_sensor_readings_sensor_ts
    ON environment.sensor_readings (sensor_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_sensor_readings_aqi
    ON environment.sensor_readings (timestamp DESC, aqi)
    WHERE aqi IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_clean_metrics_type_ts
    ON esg.clean_metrics (metric_type, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_traffic_counts_intersection_ts
    ON traffic.traffic_counts (intersection_id, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_alert_events_status
    ON alerts.alert_events (status, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_events_sensor
    ON alerts.alert_events (sensor_id, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_error_records_status
    ON error_mgmt.error_records (status, source_module, occurred_at DESC);

-- ─── Continuous Aggregate: 5-min AQI average (for Flink window materialisation)
CREATE MATERIALIZED VIEW IF NOT EXISTS environment.aqi_5min_avg
    WITH (timescaledb.continuous) AS
SELECT
    sensor_id,
    time_bucket('5 minutes', timestamp) AS bucket,
    avg(aqi)  AS avg_aqi,
    max(aqi)  AS max_aqi,
    avg(pm25) AS avg_pm25,
    avg(pm10) AS avg_pm10,
    count(*)  AS reading_count
FROM environment.sensor_readings
GROUP BY sensor_id, bucket
WITH NO DATA;

SELECT add_continuous_aggregate_policy('environment.aqi_5min_avg',
    start_offset => INTERVAL '1 hour',
    end_offset   => INTERVAL '1 minute',
    schedule_interval => INTERVAL '1 minute',
    if_not_exists => TRUE);

-- ─── Continuous Aggregate: hourly ESG metrics ────────────────────────────────
CREATE MATERIALIZED VIEW IF NOT EXISTS esg.metrics_hourly
    WITH (timescaledb.continuous) AS
SELECT
    source_id,
    metric_type,
    building_id,
    district_code,
    time_bucket('1 hour', timestamp) AS bucket,
    sum(value)  AS total_value,
    avg(value)  AS avg_value,
    count(*)    AS sample_count
FROM esg.clean_metrics
GROUP BY source_id, metric_type, building_id, district_code, bucket
WITH NO DATA;

SELECT add_continuous_aggregate_policy('esg.metrics_hourly',
    start_offset => INTERVAL '2 hours',
    end_offset   => INTERVAL '1 hour',
    schedule_interval => INTERVAL '1 hour',
    if_not_exists => TRUE);

-- ─── Retention Policies ───────────────────────────────────────────────────────
-- Keep raw sensor readings for 90 days
SELECT add_retention_policy('environment.sensor_readings',
    drop_after => INTERVAL '90 days',
    if_not_exists => TRUE);

-- Keep traffic counts for 60 days
SELECT add_retention_policy('traffic.traffic_counts',
    drop_after => INTERVAL '60 days',
    if_not_exists => TRUE);

-- ─── Seed Data: default alert rules ──────────────────────────────────────────
INSERT INTO alerts.alert_rules (rule_name, module, measure_type, operator, threshold, severity, cooldown_minutes)
VALUES
    ('AQI Warning',   'ENVIRONMENT', 'aqi', '>',  150, 'WARNING',   10),
    ('AQI Critical',  'ENVIRONMENT', 'aqi', '>',  200, 'CRITICAL',  5),
    ('AQI Emergency', 'ENVIRONMENT', 'aqi', '>',  300, 'EMERGENCY', 0),
    ('PM2.5 Warning', 'ENVIRONMENT', 'pm25', '>', 55,  'WARNING',   10),
    ('PM2.5 Critical','ENVIRONMENT', 'pm25', '>', 150, 'CRITICAL',  5)
ON CONFLICT DO NOTHING;

-- ─── Seed Data: system users ──────────────────────────────────────────────────
-- Passwords set by application at startup via Flyway Java migration or seed script
-- Hashes below correspond to 'Admin@1234', 'Operator@1234', 'Citizen@1234'
-- IMPORTANT: Change in production via env-var driven init
INSERT INTO public.app_users (username, email, password_hash, role) VALUES
    ('admin',    'admin@uip.city',    '$placeholder_admin_hash',    'ROLE_ADMIN'),
    ('operator', 'operator@uip.city', '$placeholder_operator_hash', 'ROLE_OPERATOR'),
    ('citizen1', 'citizen1@uip.city', '$placeholder_citizen_hash',  'ROLE_CITIZEN')
ON CONFLICT (username) DO NOTHING;
