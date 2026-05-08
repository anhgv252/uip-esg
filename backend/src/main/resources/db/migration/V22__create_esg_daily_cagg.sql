-- V22: ESG daily continuous aggregate for fast quarterly/monthly SUM queries
--
-- Problem: getSummary() scans ~65K raw rows per metric_type per quarter
--          (30 buildings × 24h × 90 days = 64,835 rows → 4 queries = 260K rows/request)
--          Cold miss: ~96ms + planning overhead → p95 ~117ms under load
--
-- Solution: Pre-compute daily totals per (tenant_id, metric_type).
--           Quarterly SUM becomes SUM of 90 rows → <1ms execution (measured: 0.6ms).
--
-- Query served by this cagg:
--   SELECT SUM(daily_total) FROM esg.daily_esg_summary
--   WHERE tenant_id = 'default' AND metric_type = 'ENERGY'
--     AND day >= '2026-01-01' AND day < '2026-04-01'
--   → 90 rows → Execution: 0.6ms (vs 13.7ms raw scan)

CREATE MATERIALIZED VIEW IF NOT EXISTS esg.daily_esg_summary
WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
SELECT
    tenant_id,
    metric_type,
    time_bucket('1 day', "timestamp") AS day,
    SUM(value)   AS daily_total,
    AVG(value)   AS daily_avg,
    COUNT(*)     AS sample_count
FROM esg.clean_metrics
GROUP BY tenant_id, metric_type, time_bucket('1 day', "timestamp")
WITH NO DATA;

-- Add refresh policy only if it does not already exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM timescaledb_information.jobs
        WHERE application_name LIKE '%Refresh Continuous%'
          AND hypertable_name = 'daily_esg_summary'
    ) THEN
        PERFORM add_continuous_aggregate_policy(
            'esg.daily_esg_summary',
            start_offset      => INTERVAL '2 years',
            end_offset        => INTERVAL '1 hour',
            schedule_interval => INTERVAL '1 hour'
        );
    END IF;
END
$$;
