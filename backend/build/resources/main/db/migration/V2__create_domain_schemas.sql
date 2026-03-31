-- V2: Create domain schemas and tables for Sprint 2
-- environment, esg, alerts, traffic, error_mgmt

-- ─── Extensions ──────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ─── Domain Schemas ──────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS environment;
CREATE SCHEMA IF NOT EXISTS esg;
CREATE SCHEMA IF NOT EXISTS traffic;
CREATE SCHEMA IF NOT EXISTS alerts;
CREATE SCHEMA IF NOT EXISTS citizens;
CREATE SCHEMA IF NOT EXISTS error_mgmt;

-- ─── Sensor Registry ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS environment.sensors (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    sensor_id     VARCHAR(100)  NOT NULL UNIQUE,
    sensor_name   VARCHAR(255)  NOT NULL,
    sensor_type   VARCHAR(50),
    district_code VARCHAR(20),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    last_seen_at  TIMESTAMPTZ,
    installed_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- ─── Environment sensor_readings ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS environment.sensor_readings (
    id          BIGSERIAL,
    sensor_id   VARCHAR(100)     NOT NULL,
    timestamp   TIMESTAMPTZ      NOT NULL,
    aqi         DOUBLE PRECISION,
    pm25        DOUBLE PRECISION,
    pm10        DOUBLE PRECISION,
    o3          DOUBLE PRECISION,
    no2         DOUBLE PRECISION,
    so2         DOUBLE PRECISION,
    co          DOUBLE PRECISION,
    temperature DOUBLE PRECISION,
    humidity    DOUBLE PRECISION,
    raw_payload JSONB,
    PRIMARY KEY (id, timestamp)
);

CREATE INDEX IF NOT EXISTS idx_sensor_readings_sensor_timestamp
    ON environment.sensor_readings (sensor_id, timestamp DESC);

-- ─── ESG clean metrics ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS esg.clean_metrics (
    id            BIGSERIAL,
    source_id     VARCHAR(100)     NOT NULL,
    metric_type   VARCHAR(50)      NOT NULL,
    timestamp     TIMESTAMPTZ      NOT NULL,
    value         DOUBLE PRECISION NOT NULL,
    unit          VARCHAR(20),
    building_id   VARCHAR(100),
    district_code VARCHAR(20),
    raw_payload   JSONB,
    PRIMARY KEY (id, timestamp)
);

CREATE INDEX IF NOT EXISTS idx_clean_metrics_source_type_ts
    ON esg.clean_metrics (source_id, metric_type, timestamp DESC);

CREATE TABLE IF NOT EXISTS esg.reports (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    period_type  VARCHAR(20) NOT NULL,
    year         INT         NOT NULL,
    quarter      INT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    file_path    VARCHAR(500),
    generated_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── Alerts ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS alerts.alert_rules (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name        VARCHAR(100) NOT NULL,
    module           VARCHAR(30)  NOT NULL,
    measure_type     VARCHAR(50)  NOT NULL,
    operator         VARCHAR(10)  NOT NULL,
    threshold        DOUBLE PRECISION NOT NULL,
    severity         VARCHAR(20)  NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    cooldown_minutes INT          NOT NULL DEFAULT 10,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS alerts.alert_events (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id         UUID         REFERENCES alerts.alert_rules(id),
    sensor_id       VARCHAR(100) NOT NULL,
    module          VARCHAR(30)  NOT NULL,
    measure_type    VARCHAR(50)  NOT NULL,
    value           DOUBLE PRECISION NOT NULL,
    threshold       DOUBLE PRECISION NOT NULL,
    severity        VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    detected_at     TIMESTAMPTZ  NOT NULL,
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    note            TEXT
);

CREATE INDEX IF NOT EXISTS idx_alert_events_status_detected
    ON alerts.alert_events (status, detected_at DESC);

CREATE INDEX IF NOT EXISTS idx_alert_events_sensor
    ON alerts.alert_events (sensor_id, detected_at DESC);

-- ─── Traffic ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS traffic.traffic_counts (
    id               BIGSERIAL,
    intersection_id  VARCHAR(100)     NOT NULL,
    timestamp        TIMESTAMPTZ      NOT NULL,
    vehicle_count    INT              NOT NULL DEFAULT 0,
    avg_speed_kmh    DOUBLE PRECISION,
    congestion_level VARCHAR(20),
    raw_payload      JSONB,
    PRIMARY KEY (id, timestamp)
);

CREATE INDEX IF NOT EXISTS idx_traffic_counts_intersection_ts
    ON traffic.traffic_counts (intersection_id, timestamp DESC);

CREATE TABLE IF NOT EXISTS traffic.incidents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_type   VARCHAR(50) NOT NULL,
    intersection_id VARCHAR(100),
    description     TEXT,
    severity        VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ
);

-- ─── Error Management ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS error_mgmt.error_records (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    source_module VARCHAR(30) NOT NULL,
    kafka_topic   VARCHAR(200),
    kafka_offset  BIGINT,
    error_type    VARCHAR(100) NOT NULL,
    error_message TEXT,
    raw_payload   JSONB,
    status        VARCHAR(20) NOT NULL DEFAULT 'UNRESOLVED',
    resolved_by   UUID,
    resolved_at   TIMESTAMPTZ,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_error_records_module_status
    ON error_mgmt.error_records (source_module, status, occurred_at DESC);
