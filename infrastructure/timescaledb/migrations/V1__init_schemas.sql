-- V1__init_schemas.sql
-- UIP Smart City — Schema initialisation
-- Creates 6 domain schemas with extension support

CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- for UUID generation

-- ─── Domain Schemas ────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS environment;
CREATE SCHEMA IF NOT EXISTS esg;
CREATE SCHEMA IF NOT EXISTS traffic;
CREATE SCHEMA IF NOT EXISTS alerts;
CREATE SCHEMA IF NOT EXISTS citizens;
CREATE SCHEMA IF NOT EXISTS error_mgmt;

-- ─── Users / Auth (in public schema, owned by application) ─────────────────
CREATE TABLE IF NOT EXISTS public.app_users (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    username     VARCHAR(50) NOT NULL UNIQUE,
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role         VARCHAR(30) NOT NULL DEFAULT 'ROLE_CITIZEN',
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── Sensor Registry ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS environment.sensors (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sensor_id     VARCHAR(100) NOT NULL UNIQUE,
    sensor_name   VARCHAR(255) NOT NULL,
    sensor_type   VARCHAR(50),   -- AIR_QUALITY, WATER, NOISE, etc.
    district_code VARCHAR(20),
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at  TIMESTAMPTZ,
    installed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── Environment readings ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS environment.sensor_readings (
    id           BIGSERIAL,
    sensor_id    VARCHAR(100)        NOT NULL,
    timestamp    TIMESTAMPTZ         NOT NULL,
    aqi          DOUBLE PRECISION,
    pm25         DOUBLE PRECISION,
    pm10         DOUBLE PRECISION,
    o3           DOUBLE PRECISION,
    no2          DOUBLE PRECISION,
    so2          DOUBLE PRECISION,
    co           DOUBLE PRECISION,
    temperature  DOUBLE PRECISION,
    humidity     DOUBLE PRECISION,
    raw_payload  JSONB,
    PRIMARY KEY (id, timestamp)
);

-- ─── ESG clean metrics ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS esg.clean_metrics (
    id           BIGSERIAL,
    source_id    VARCHAR(100)        NOT NULL,
    metric_type  VARCHAR(50)         NOT NULL,  -- ENERGY, WATER, CARBON, WASTE
    timestamp    TIMESTAMPTZ         NOT NULL,
    value        DOUBLE PRECISION    NOT NULL,
    unit         VARCHAR(20),
    building_id  VARCHAR(100),
    district_code VARCHAR(20),
    raw_payload  JSONB,
    PRIMARY KEY (id, timestamp)
);

CREATE TABLE IF NOT EXISTS esg.reports (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    period_type  VARCHAR(20) NOT NULL,  -- QUARTERLY, ANNUAL
    year         INT         NOT NULL,
    quarter      INT,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, GENERATING, DONE, FAILED
    file_path    VARCHAR(500),
    generated_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── Traffic ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS traffic.traffic_counts (
    id              BIGSERIAL,
    intersection_id VARCHAR(100)    NOT NULL,
    timestamp       TIMESTAMPTZ     NOT NULL,
    vehicle_count   INT             NOT NULL DEFAULT 0,
    avg_speed_kmh   DOUBLE PRECISION,
    congestion_level VARCHAR(20),   -- LOW, MEDIUM, HIGH, CRITICAL
    raw_payload     JSONB,
    PRIMARY KEY (id, timestamp)
);

CREATE TABLE IF NOT EXISTS traffic.incidents (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    incident_type   VARCHAR(50) NOT NULL,
    intersection_id VARCHAR(100),
    district_code   VARCHAR(20),
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    severity        VARCHAR(20) NOT NULL DEFAULT 'LOW',
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    occurred_at     TIMESTAMPTZ NOT NULL,
    resolved_at     TIMESTAMPTZ,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ─── Alerts ─────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS alerts.alert_events (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    sensor_id      VARCHAR(100) NOT NULL,
    module         VARCHAR(30)  NOT NULL,  -- ENVIRONMENT, TRAFFIC, ESG
    measure_type   VARCHAR(50)  NOT NULL,
    value          DOUBLE PRECISION NOT NULL,
    threshold      DOUBLE PRECISION NOT NULL,
    severity       VARCHAR(20)  NOT NULL,  -- WARNING, CRITICAL, EMERGENCY
    status         VARCHAR(20)  NOT NULL DEFAULT 'OPEN',  -- OPEN, ACKNOWLEDGED, ESCALATED, CLOSED
    detected_at    TIMESTAMPTZ  NOT NULL,
    acknowledged_by UUID,
    acknowledged_at TIMESTAMPTZ,
    note           TEXT,
    FOREIGN KEY (acknowledged_by) REFERENCES public.app_users(id)
);

CREATE TABLE IF NOT EXISTS alerts.alert_rules (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_name    VARCHAR(100) NOT NULL,
    module       VARCHAR(30)  NOT NULL,
    measure_type VARCHAR(50)  NOT NULL,
    operator     VARCHAR(10)  NOT NULL,  -- >, <, >=, <=, ==
    threshold    DOUBLE PRECISION NOT NULL,
    severity     VARCHAR(20)  NOT NULL,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    cooldown_minutes INT      NOT NULL DEFAULT 10,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Citizens ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS citizens.citizen_accounts (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID        NOT NULL UNIQUE REFERENCES public.app_users(id),
    full_name     VARCHAR(255) NOT NULL,
    phone_number  VARCHAR(20),
    id_card_number VARCHAR(20),
    district_code  VARCHAR(20),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS citizens.households (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id   UUID        NOT NULL REFERENCES citizens.citizen_accounts(id),
    building_id  VARCHAR(100) NOT NULL,
    floor        INT,
    unit_number  VARCHAR(20),
    is_primary   BOOLEAN     NOT NULL DEFAULT FALSE,
    verified_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS citizens.meters (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    citizen_id   UUID        NOT NULL REFERENCES citizens.citizen_accounts(id),
    meter_code   VARCHAR(100) NOT NULL UNIQUE,
    meter_type   VARCHAR(20) NOT NULL,  -- ELECTRICITY, WATER, GAS
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    linked_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS citizens.invoices (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    meter_id     UUID        NOT NULL REFERENCES citizens.meters(id),
    citizen_id   UUID        NOT NULL REFERENCES citizens.citizen_accounts(id),
    billing_year INT         NOT NULL,
    billing_month INT        NOT NULL,
    consumption  DOUBLE PRECISION NOT NULL,
    unit         VARCHAR(10) NOT NULL,  -- kWh, m3
    unit_price   NUMERIC(12,4) NOT NULL,
    total_amount NUMERIC(12,2) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    due_date     DATE,
    paid_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
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
    status        VARCHAR(20) NOT NULL DEFAULT 'UNRESOLVED',  -- UNRESOLVED, RESOLVED, REINGESTED
    resolved_by   UUID,
    resolved_at   TIMESTAMPTZ,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
