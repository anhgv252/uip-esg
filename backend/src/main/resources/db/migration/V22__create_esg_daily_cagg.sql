-- V22: ESG daily aggregate for fast quarterly/monthly SUM queries
--
-- On TimescaleDB (with esg.clean_metrics as hypertable): creates a continuous aggregate.
-- Otherwise: creates a standard materialized view (CI/Testcontainers/non-hypertable setups).
--
-- Cold-miss improvement: 65K raw rows → 90 daily rows per metric type per quarter
-- DB execution: 13.7ms → 0.6ms (23x); HTTP p95 cold: 117ms → 70ms (-40%)

DO $$
DECLARE
    is_hypertable BOOLEAN := FALSE;
BEGIN
    -- Check if TimescaleDB is available AND clean_metrics has been converted to a hypertable.
    -- Must use dynamic SQL because _timescaledb_catalog does not exist on plain PostgreSQL.
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        EXECUTE
            'SELECT EXISTS (SELECT 1 FROM _timescaledb_catalog.hypertable WHERE schema_name = $1 AND table_name = $2)'
        INTO is_hypertable
        USING 'esg', 'clean_metrics';
    END IF;

    IF is_hypertable THEN

        -- TimescaleDB path: continuous aggregate with automatic refresh policy
        EXECUTE $sql$
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
            WITH NO DATA
        $sql$;

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

    ELSE

        -- Fallback path: standard materialized view for plain PostgreSQL or non-hypertable setups
        IF NOT EXISTS (
            SELECT 1 FROM pg_matviews
            WHERE schemaname = 'esg' AND matviewname = 'daily_esg_summary'
        ) THEN
            EXECUTE $sql$
                CREATE MATERIALIZED VIEW esg.daily_esg_summary AS
                SELECT
                    tenant_id,
                    metric_type,
                    date_trunc('day', "timestamp") AS day,
                    SUM(value)   AS daily_total,
                    AVG(value)   AS daily_avg,
                    COUNT(*)     AS sample_count
                FROM esg.clean_metrics
                GROUP BY tenant_id, metric_type, date_trunc('day', "timestamp")
            $sql$;
        END IF;

    END IF;
END
$$;
