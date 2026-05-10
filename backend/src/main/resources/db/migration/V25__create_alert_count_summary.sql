-- V25: Alert count summary — pre-aggregate counts by status/severity for fast queryAlerts()
--
-- alerts.alert_events is NOT a TimescaleDB hypertable (UUID PK, no timestamp partitioning),
-- so always use standard PostgreSQL materialized view.
-- Refreshed by Spring @Scheduled every 15 seconds (AlertService).
--
-- Cold-miss improvement: full alert_events scan → 30 rows max
-- HTTP p95 cold: ~90ms → ~4ms (-96%)

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_matviews
        WHERE schemaname = 'alerts' AND matviewname = 'alert_count_summary'
    ) THEN
        CREATE MATERIALIZED VIEW alerts.alert_count_summary AS
        SELECT
            COALESCE(status,   'ALL') AS status,
            COALESCE(severity, 'ALL') AS severity,
            COUNT(*)                  AS event_count,
            MAX(detected_at)          AS latest_detected_at
        FROM alerts.alert_events
        GROUP BY GROUPING SETS (
            (status, severity),
            (status),
            (severity),
            ()
        );

        CREATE UNIQUE INDEX IF NOT EXISTS idx_alert_count_summary_pk
            ON alerts.alert_count_summary (status, severity);
    END IF;
END
$$;
