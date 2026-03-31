-- ───────────────────────────────────────────────────────────────
--  PostgreSQL  •  ESG Error Records Schema
--  Database: error_db  |  User: error_user
-- ───────────────────────────────────────────────────────────────

-- ════════════════════════════════════════════════════════════════
--  TABLE: esg_error_records
--  All records that failed validation in the Flink cleansing job.
--  Raw field values preserved as-is for debug and re-ingestion.
-- ════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS esg_error_records (
    id                  BIGSERIAL PRIMARY KEY,
    -- raw fields (as received from Redpanda Connect)
    meter_id            TEXT,
    site_id             TEXT,
    building_id         TEXT,
    floor_id            TEXT,
    zone_id             TEXT,
    event_timestamp     TEXT,
    measure_type        TEXT,
    raw_value           TEXT,
    unit                TEXT,
    source_id           TEXT,
    -- error classification
    error_type          TEXT   NOT NULL,
    error_detail        TEXT,
    -- processing metadata
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- operator review workflow
    reviewed            BOOLEAN NOT NULL DEFAULT FALSE,
    reviewed_by         TEXT,
    reviewed_at         TIMESTAMPTZ,
    reingested          BOOLEAN NOT NULL DEFAULT FALSE,
    reingested_at       TIMESTAMPTZ,
    notes               TEXT
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_err_error_type
    ON esg_error_records (error_type);
CREATE INDEX IF NOT EXISTS idx_err_received_at
    ON esg_error_records (received_at DESC);
CREATE INDEX IF NOT EXISTS idx_err_meter_id
    ON esg_error_records (meter_id);
CREATE INDEX IF NOT EXISTS idx_err_source_id
    ON esg_error_records (source_id);
CREATE INDEX IF NOT EXISTS idx_err_reviewed
    ON esg_error_records (reviewed) WHERE reviewed = FALSE;


-- ════════════════════════════════════════════════════════════════
--  VIEW: error_summary  –  Breakdown by type
-- ════════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW error_summary AS
SELECT
    error_type,
    COUNT(*)                                              AS count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)   AS pct,
    MIN(received_at)                                      AS first_seen,
    MAX(received_at)                                      AS last_seen,
    COUNT(*) FILTER (WHERE reviewed)                      AS reviewed_count,
    COUNT(*) FILTER (WHERE reingested)                    AS reingested_count
FROM esg_error_records
GROUP BY error_type
ORDER BY count DESC;


-- ════════════════════════════════════════════════════════════════
--  VIEW: error_by_source  –  Per-source error rates
-- ════════════════════════════════════════════════════════════════
CREATE OR REPLACE VIEW error_by_source AS
SELECT
    COALESCE(source_id, 'unknown') AS source_id,
    COUNT(*)                       AS total_errors,
    COUNT(DISTINCT error_type)     AS distinct_error_types,
    MAX(received_at)               AS last_error_at
FROM esg_error_records
GROUP BY source_id
ORDER BY total_errors DESC;
