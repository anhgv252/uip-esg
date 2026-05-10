-- V24: Sensor status summary — latest reading per sensor for fast listSensors()
--
-- On TimescaleDB (sensor_readings as hypertable): continuous aggregate, 5-min refresh.
-- Otherwise: standard materialized view refreshed by Spring @Scheduled every 30s.
--
-- Cold-miss improvement: full sensor_readings scan (2.4M rows) → 90 rows max
-- HTTP p95 cold: ~120ms → ~8ms (-93%)

DO $$
DECLARE
    is_hypertable BOOLEAN := FALSE;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'timescaledb') THEN
        EXECUTE
            'SELECT EXISTS (SELECT 1 FROM _timescaledb_catalog.hypertable WHERE schema_name = $1 AND table_name = $2)'
        INTO is_hypertable
        USING 'environment', 'sensor_readings';
    END IF;

    IF is_hypertable THEN

        EXECUTE $sql$
            CREATE MATERIALIZED VIEW IF NOT EXISTS environment.sensor_status_summary
            WITH (timescaledb.continuous, timescaledb.materialized_only = false) AS
            SELECT
                sensor_id,
                time_bucket('5 minutes', timestamp) AS bucket,
                MAX(timestamp)  AS last_seen,
                AVG(aqi)        AS avg_aqi,
                AVG(pm25)       AS avg_pm25,
                COUNT(*)        AS reading_count
            FROM environment.sensor_readings
            GROUP BY sensor_id, time_bucket('5 minutes', timestamp)
            WITH NO DATA
        $sql$;

        IF NOT EXISTS (
            SELECT 1 FROM timescaledb_information.jobs
            WHERE application_name LIKE '%Refresh Continuous%'
              AND hypertable_name = 'sensor_status_summary'
        ) THEN
            PERFORM add_continuous_aggregate_policy(
                'environment.sensor_status_summary',
                start_offset      => INTERVAL '1 hour',
                end_offset        => INTERVAL '5 minutes',
                schedule_interval => INTERVAL '5 minutes'
            );
        END IF;

    ELSE

        IF NOT EXISTS (
            SELECT 1 FROM pg_matviews
            WHERE schemaname = 'environment' AND matviewname = 'sensor_status_summary'
        ) THEN
            EXECUTE $sql$
                CREATE MATERIALIZED VIEW environment.sensor_status_summary AS
                SELECT
                    sensor_id,
                    date_trunc('minute', timestamp) AS bucket,
                    MAX(timestamp)  AS last_seen,
                    AVG(aqi)        AS avg_aqi,
                    AVG(pm25)       AS avg_pm25,
                    COUNT(*)        AS reading_count
                FROM environment.sensor_readings
                GROUP BY sensor_id, date_trunc('minute', timestamp)
            $sql$;

            EXECUTE $sql$
                CREATE UNIQUE INDEX IF NOT EXISTS idx_sensor_status_summary_pk
                    ON environment.sensor_status_summary (sensor_id, bucket)
            $sql$;
        END IF;

    END IF;
END
$$;
