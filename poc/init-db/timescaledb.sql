-- ────────────────────────────────────────────────────────────────────
--  TimescaleDB  •  UIP ESG Unified Schema
--  Database: esg_db  |  User: esg_user
--
--  Single database, two schemas:
--    esg          – time-series data (clean metrics + aggregates)
--    error_mgmt   – error records + operator review workflow
--
--  Key design decisions:
--
--  1. event_ts TIMESTAMPTZ – partitions hypertable on SENSOR event time,
--     not ingestion time. Time-range queries by sensor time use index scans.
--
--  2. Idempotent upsert (at-least-once safe):
--     esg.clean_metrics        → UNIQUE (meter_id, event_ts, measure_type)
--     esg.aggregate_metrics    → UNIQUE (meter_id, window_start, measure_type)
--     error_mgmt.error_records → PRIMARY KEY (dedup_key) where dedup_key is
--       an MD5 hash computed by Flink over the logical error identity.
--     Flink JDBC upsert mode (INSERT … ON CONFLICT DO UPDATE) uses these
--     constraints, making checkpoint replay safe without duplicate rows.
--
--  3. Operator field isolation – the Flink JDBC sink DDL does NOT include
--     the operator workflow columns (reviewed, reviewed_by, reviewed_at,
--     reingested, reingested_at, notes, id). PostgreSQL's ON CONFLICT DO
--     UPDATE only touches columns listed in the Flink DDL, so operator
--     actions survive Flink replays without any application-level locking.
--
-- NOTE: postgres-errors.sql is no longer used. Error records now live in
--       the error_mgmt schema of this database.
-- ────────────────────────────────────────────────────────────────────

CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE SCHEMA IF NOT EXISTS esg;
CREATE SCHEMA IF NOT EXISTS error_mgmt;


-- ════════════════════════════════════════════════════════════════════
--  esg.clean_metrics
--  Valid telemetry records written by the Flink cleansing job.
--  quality_flag = 'OK' for every row in this table.
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS esg.clean_metrics (
    meter_id        VARCHAR(100)     NOT NULL,
    site_id         VARCHAR(100),
    building_id     VARCHAR(100),
    floor_id        VARCHAR(100),
    zone_id         VARCHAR(100),
    event_ts        TIMESTAMPTZ      NOT NULL,  -- sensor event time (parsed ISO-8601)
    measure_type    VARCHAR(50)      NOT NULL,
    value           DOUBLE PRECISION NOT NULL,
    unit            VARCHAR(20)      NOT NULL,
    quality_flag    VARCHAR(20)      NOT NULL DEFAULT 'OK',
    source_id       VARCHAR(100),
    ingested_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    -- Natural business key drives Flink JDBC upsert:
    --   INSERT … ON CONFLICT (meter_id, event_ts, measure_type) DO UPDATE SET …
    CONSTRAINT uq_clean_metrics UNIQUE (meter_id, event_ts, measure_type)
);

-- Hypertable partitioned on event_ts (sensor time), NOT ingestion time.
-- Queries like "show me energy from 2am-3am by meter" now use index scans.
SELECT create_hypertable(
    'esg.clean_metrics',
    'event_ts',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists       => TRUE
);

CREATE INDEX IF NOT EXISTS idx_ecm_meter_event
    ON esg.clean_metrics (meter_id, event_ts DESC);
CREATE INDEX IF NOT EXISTS idx_ecm_site_event
    ON esg.clean_metrics (site_id, event_ts DESC);
CREATE INDEX IF NOT EXISTS idx_ecm_measure_event
    ON esg.clean_metrics (measure_type, event_ts DESC);
-- Secondary index on ingested_at for pipeline monitoring / lag detection
CREATE INDEX IF NOT EXISTS idx_ecm_ingested
    ON esg.clean_metrics (ingested_at DESC);


-- ════════════════════════════════════════════════════════════════════
--  esg.aggregate_metrics
--  1-minute tumbling event-time window aggregates, computed by Flink.
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS esg.aggregate_metrics (
    meter_id        VARCHAR(100)     NOT NULL,
    site_id         VARCHAR(100),
    measure_type    VARCHAR(50)      NOT NULL,
    unit            VARCHAR(20),
    window_start    TIMESTAMPTZ      NOT NULL,
    window_end      TIMESTAMPTZ      NOT NULL,
    total_value     DOUBLE PRECISION,
    avg_value       DOUBLE PRECISION,
    min_value       DOUBLE PRECISION,
    max_value       DOUBLE PRECISION,
    record_count    BIGINT,
    computed_at     TIMESTAMPTZ      NOT NULL DEFAULT NOW(),

    -- Upsert key: same window replayed → same key → update in place
    CONSTRAINT uq_aggregate_metrics UNIQUE (meter_id, window_start, measure_type)
);

SELECT create_hypertable(
    'esg.aggregate_metrics',
    'window_start',
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists       => TRUE
);

CREATE INDEX IF NOT EXISTS idx_eam_meter_window
    ON esg.aggregate_metrics (meter_id, window_start DESC);
CREATE INDEX IF NOT EXISTS idx_eam_site_window
    ON esg.aggregate_metrics (site_id, window_start DESC);


-- ════════════════════════════════════════════════════════════════════
--  esg.processing_summary  (view)
-- ════════════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW esg.processing_summary AS
SELECT
    'clean_metrics'          AS table_name,
    COUNT(*)                 AS total_records,
    MIN(event_ts)            AS first_event,
    MAX(event_ts)            AS last_event,
    COUNT(DISTINCT meter_id) AS unique_meters,
    COUNT(DISTINCT site_id)  AS unique_sites
FROM esg.clean_metrics
UNION ALL
SELECT
    'aggregate_metrics',
    COUNT(*),
    MIN(window_start),
    MAX(window_end),
    COUNT(DISTINCT meter_id),
    COUNT(DISTINCT site_id)
FROM esg.aggregate_metrics;


-- ════════════════════════════════════════════════════════════════════
--  error_mgmt.error_records
--  All records that failed Flink validation. Raw fields preserved
--  as-is to support debugging and re-ingestion.
--
--  Idempotency design:
--    dedup_key CHAR(32) PRIMARY KEY
--      → MD5 hash of (meter_id | event_timestamp | measure_type | error_type)
--      → computed by Flink before INSERT
--      → same logical error always produces the same hash
--      → Flink upsert: INSERT … ON CONFLICT (dedup_key) DO UPDATE SET …
--
--  Operator field protection (no trigger required):
--    The Flink JDBC sink DDL intentionally omits operator columns
--    (reviewed, reviewed_by, reviewed_at, reingested, reingested_at,
--    notes, id). PostgreSQL ON CONFLICT DO UPDATE only touches columns
--    present in the Flink INSERT statement, leaving operator fields intact.
--
--    id BIGSERIAL: also omitted from Flink DDL → PostgreSQL auto-assigns
--    a stable integer ID on first INSERT; subsequent upserts never change it.
-- ════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS error_mgmt.error_records (
    -- Flink-managed columns (included in Flink JDBC sink DDL)
    dedup_key       CHAR(32)     PRIMARY KEY,  -- MD5 idempotency key
    meter_id        TEXT,
    site_id         TEXT,
    building_id     TEXT,
    floor_id        TEXT,
    zone_id         TEXT,
    event_timestamp TEXT,                       -- raw string (may be null/malformed)
    measure_type    TEXT,
    raw_value       TEXT,
    unit            TEXT,
    source_id       TEXT,
    error_type      TEXT         NOT NULL,
    error_detail    TEXT,

    -- PostgreSQL-managed columns (NOT in Flink DDL → safe from upsert overwrite)
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),  -- timestamp of first occurrence
    id              BIGSERIAL    NOT NULL,                -- human-friendly sequential ID

    -- Operator review workflow (NOT in Flink DDL → survives checkpoint replay)
    reviewed        BOOLEAN      NOT NULL DEFAULT FALSE,
    reviewed_by     TEXT,
    reviewed_at     TIMESTAMPTZ,
    reingested      BOOLEAN      NOT NULL DEFAULT FALSE,
    reingested_at   TIMESTAMPTZ,
    notes           TEXT
);

-- Integer id lookup for API endpoints like /errors/{id}/review
CREATE UNIQUE INDEX IF NOT EXISTS idx_err_id
    ON error_mgmt.error_records (id);

CREATE INDEX IF NOT EXISTS idx_err_error_type
    ON error_mgmt.error_records (error_type);
CREATE INDEX IF NOT EXISTS idx_err_received_at
    ON error_mgmt.error_records (received_at DESC);
CREATE INDEX IF NOT EXISTS idx_err_meter_id
    ON error_mgmt.error_records (meter_id);
CREATE INDEX IF NOT EXISTS idx_err_source_id
    ON error_mgmt.error_records (source_id);
CREATE INDEX IF NOT EXISTS idx_err_unreviewed
    ON error_mgmt.error_records (received_at DESC) WHERE reviewed = FALSE;


-- ════════════════════════════════════════════════════════════════════
--  error_mgmt.error_summary  (view)
-- ════════════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW error_mgmt.error_summary AS
SELECT
    error_type,
    COUNT(*)                                              AS count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)   AS pct,
    MIN(received_at)                                      AS first_seen,
    MAX(received_at)                                      AS last_seen,
    COUNT(*) FILTER (WHERE reviewed)                      AS reviewed_count,
    COUNT(*) FILTER (WHERE reingested)                    AS reingested_count
FROM error_mgmt.error_records
GROUP BY error_type
ORDER BY count DESC;


-- ════════════════════════════════════════════════════════════════════
--  error_mgmt.error_by_source  (view)
-- ════════════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW error_mgmt.error_by_source AS
SELECT
    COALESCE(source_id, 'unknown') AS source_id,
    COUNT(*)                       AS total_errors,
    COUNT(DISTINCT error_type)     AS distinct_error_types,
    MAX(received_at)               AS last_error_at
FROM error_mgmt.error_records
GROUP BY source_id
ORDER BY total_errors DESC;
