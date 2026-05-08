-- =============================================================================
-- LARGE PERFORMANCE SEED — Sprint 6 Realistic Load Test
--
-- Purpose: Insert millions of records to stress-test query performance.
--          Without this data, ESG SUM queries return NULL instantly (no rows
--          match Q1-2026 range), making cold miss appear deceptively fast.
--
-- Targets:
--   esg.clean_metrics    : ~2.4M rows (30 buildings × 4 types × hourly × ~2yr)
--   alerts.alert_events  : ~600K rows (every 3 min, 2024-2026)
--   traffic.traffic_counts: ~700K rows (20 intersections × 30-min × 2yr)
--
-- After seed also adds missing composite index for ESG SUM query performance.
--
-- Run:
--   docker exec -i uip-timescaledb psql -U uip -d uip_smartcity \
--     < scripts/perf-seed-large.sql
-- Expected duration: 3-10 minutes depending on host.
-- =============================================================================

\timing on
\echo ''
\echo '============================================================'
\echo ' UIP — Large Performance Seed starting...'
\echo '============================================================'

-- ─── Pre-seed row counts ─────────────────────────────────────────────────────
\echo ''
\echo '--- Row counts BEFORE seed ---'
SELECT 'esg.clean_metrics' AS tbl, TO_CHAR(COUNT(*), 'FM9,999,999') AS rows FROM esg.clean_metrics
UNION ALL
SELECT 'alerts.alert_events',       TO_CHAR(COUNT(*), 'FM9,999,999') FROM alerts.alert_events
UNION ALL
SELECT 'traffic.traffic_counts',    TO_CHAR(COUNT(*), 'FM9,999,999') FROM traffic.traffic_counts
UNION ALL
SELECT 'environment.sensor_readings', TO_CHAR(COUNT(*), 'FM9,999,999') FROM environment.sensor_readings;

-- =============================================================================
-- 1. ESG clean_metrics — 30 buildings × 4 metric types × hourly 2024-04-30
--    Range covers:
--      • Historical baseline: 2024-01 → 2025-12
--      • Q1 2026 (cache-benchmark quarterly target): 2026-01-01 → 2026-03-31
--      • April 2026 (cache-benchmark monthly target): 2026-04-01 → 2026-04-30
-- =============================================================================

\echo ''
\echo '--- 1/3  Seeding esg.clean_metrics (ENERGY)...'
INSERT INTO esg.clean_metrics
    (source_id, metric_type, timestamp, value, unit, building_id, district_code, tenant_id)
SELECT
    'ESG-ENERGY-' || LPAD(b::text, 3, '0'),
    'ENERGY',
    ts,
    -- Realistic energy: 500–5500 kWh/h with building + time variation
    500.0 + (abs(hashtext('e' || b::text || to_char(ts, 'YYYYMMDDHH24'))) % 5001),
    'kWh',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b % 24) + 1)::text,
    'default'
FROM generate_series(1, 30) AS b,
     generate_series(
         '2024-01-01 00:00:00+00'::timestamptz,
         '2026-04-30 23:00:00+00'::timestamptz,
         '1 hour'::interval
     ) AS ts;

\echo '--- 1/3  Seeding esg.clean_metrics (WATER)...'
INSERT INTO esg.clean_metrics
    (source_id, metric_type, timestamp, value, unit, building_id, district_code, tenant_id)
SELECT
    'ESG-WATER-' || LPAD(b::text, 3, '0'),
    'WATER',
    ts,
    -- Realistic water: 50–250 m3/h
    50.0 + (abs(hashtext('w' || b::text || to_char(ts, 'YYYYMMDDHH24'))) % 201),
    'm3',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b % 24) + 1)::text,
    'default'
FROM generate_series(1, 30) AS b,
     generate_series(
         '2024-01-01 00:00:00+00'::timestamptz,
         '2026-04-30 23:00:00+00'::timestamptz,
         '1 hour'::interval
     ) AS ts;

\echo '--- 1/3  Seeding esg.clean_metrics (CARBON)...'
INSERT INTO esg.clean_metrics
    (source_id, metric_type, timestamp, value, unit, building_id, district_code, tenant_id)
SELECT
    'ESG-CARBON-' || LPAD(b::text, 3, '0'),
    'CARBON',
    ts,
    -- Realistic carbon: 0.2–2.5 tCO2e/h (0.45 kg/kWh × energy range / 1000)
    0.2 + (abs(hashtext('c' || b::text || to_char(ts, 'YYYYMMDDHH24'))) % 231) / 100.0,
    'tCO2e',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b % 24) + 1)::text,
    'default'
FROM generate_series(1, 30) AS b,
     generate_series(
         '2024-01-01 00:00:00+00'::timestamptz,
         '2026-04-30 23:00:00+00'::timestamptz,
         '1 hour'::interval
     ) AS ts;

\echo '--- 1/3  Seeding esg.clean_metrics (WASTE)...'
INSERT INTO esg.clean_metrics
    (source_id, metric_type, timestamp, value, unit, building_id, district_code, tenant_id)
SELECT
    'ESG-WASTE-' || LPAD(b::text, 3, '0'),
    'WASTE',
    ts,
    -- Realistic waste: 10–60 tons/h
    10.0 + (abs(hashtext('ws' || b::text || to_char(ts, 'YYYYMMDDHH24'))) % 51),
    'tons',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b % 24) + 1)::text,
    'default'
FROM generate_series(1, 30) AS b,
     generate_series(
         '2024-01-01 00:00:00+00'::timestamptz,
         '2026-04-30 23:00:00+00'::timestamptz,
         '1 hour'::interval
     ) AS ts;

-- =============================================================================
-- 2. alerts.alert_events — ~600K rows, every 3 minutes over 2 years
--    8 active sensor IDs (ENV-001..ENV-008), mixed severity/status
-- =============================================================================

\echo ''
\echo '--- 2/3  Seeding alerts.alert_events...'
INSERT INTO alerts.alert_events
    (sensor_id, module, measure_type, value, threshold, severity, status, detected_at, tenant_id)
SELECT
    'ENV-' || LPAD((1 + (abs(hashtext(ts::text)) % 8))::text, 3, '0'),
    'ENVIRONMENT',
    CASE (abs(hashtext('m' || ts::text)) % 4)
        WHEN 0 THEN 'aqi'
        WHEN 1 THEN 'pm25'
        WHEN 2 THEN 'pm10'
        ELSE 'no2'
    END,
    151.0 + (abs(hashtext('v' || ts::text)) % 149),
    150.0,
    CASE (abs(hashtext('sev' || ts::text)) % 3)
        WHEN 0 THEN 'WARNING'
        WHEN 1 THEN 'CRITICAL'
        ELSE 'EMERGENCY'
    END,
    CASE (abs(hashtext('st' || ts::text)) % 4)
        WHEN 0 THEN 'OPEN'
        WHEN 1 THEN 'OPEN'
        WHEN 2 THEN 'ACKNOWLEDGED'
        ELSE 'CLOSED'
    END,
    ts,
    'default'
FROM generate_series(
    '2024-01-01 00:00:00+00'::timestamptz,
    '2026-04-30 23:57:00+00'::timestamptz,
    '3 minutes'::interval
) AS ts;

-- =============================================================================
-- 3. traffic.traffic_counts — ~700K rows
--    20 intersections, 1 reading every 30 minutes over 2 years
-- =============================================================================

\echo ''
\echo '--- 3/3  Seeding traffic.traffic_counts...'
INSERT INTO traffic.traffic_counts
    (intersection_id, recorded_at, vehicle_count, vehicle_type, tenant_id)
SELECT
    'INT-HCM-' || LPAD(i::text, 3, '0'),
    ts,
    30 + (abs(hashtext('vc' || i::text || ts::text)) % 971),
    CASE (abs(hashtext('vt' || ts::text)) % 3)
        WHEN 0 THEN 'CAR'
        WHEN 1 THEN 'MOTORBIKE'
        ELSE 'TRUCK'
    END,
    'default'
FROM generate_series(1, 20) AS i,
     generate_series(
         '2024-01-01 00:00:00+00'::timestamptz,
         '2026-04-30 23:30:00+00'::timestamptz,
         '30 minutes'::interval
     ) AS ts;

-- =============================================================================
-- 4. Missing composite index for ESG SUM query
--    The SUM query: WHERE tenant_id = ? AND metric_type = ? AND timestamp BETWEEN ?
--    Existing indexes cover (tenant_id, ts) and (metric_type, ts) separately.
--    A composite (tenant_id, metric_type, timestamp) enables direct index range scan.
-- =============================================================================

\echo ''
\echo '--- Adding composite index idx_clean_metrics_tenant_type_ts...'
CREATE INDEX IF NOT EXISTS idx_clean_metrics_tenant_type_ts
    ON esg.clean_metrics (tenant_id, metric_type, "timestamp" DESC);

\echo '--- Adding index idx_alert_events_detected_at for unfiltered sort...'
CREATE INDEX IF NOT EXISTS idx_alert_events_detected_at
    ON alerts.alert_events (detected_at DESC);

-- =============================================================================
-- 5. VACUUM ANALYZE to update planner statistics
-- =============================================================================

\echo ''
\echo '--- ANALYZE tables (updating planner statistics)...'
ANALYZE esg.clean_metrics;
ANALYZE alerts.alert_events;
ANALYZE traffic.traffic_counts;

-- =============================================================================
-- Post-seed summary
-- =============================================================================

\echo ''
\echo '============================================================'
\echo ' Row counts AFTER seed'
\echo '============================================================'
SELECT 'esg.clean_metrics' AS tbl, TO_CHAR(COUNT(*), 'FM9,999,999') AS rows FROM esg.clean_metrics
UNION ALL
SELECT 'alerts.alert_events',         TO_CHAR(COUNT(*), 'FM9,999,999') FROM alerts.alert_events
UNION ALL
SELECT 'traffic.traffic_counts',      TO_CHAR(COUNT(*), 'FM9,999,999') FROM traffic.traffic_counts
UNION ALL
SELECT 'environment.sensor_readings', TO_CHAR(COUNT(*), 'FM9,999,999') FROM environment.sensor_readings;

\echo ''
\echo '--- Q1-2026 ESG row counts (cache-benchmark range) ---'
SELECT metric_type, TO_CHAR(COUNT(*), 'FM9,999,999') AS rows_q1_2026
FROM esg.clean_metrics
WHERE tenant_id = 'default'
  AND timestamp >= '2026-01-01'::timestamptz
  AND timestamp <  '2026-04-01'::timestamptz
GROUP BY metric_type
ORDER BY metric_type;

\echo ''
\echo '--- Indexes on esg.clean_metrics ---'
SELECT indexname, indexdef FROM pg_indexes
WHERE schemaname = 'esg' AND tablename = 'clean_metrics'
ORDER BY indexname;

\echo ''
\echo '============================================================'
\echo ' Seed complete. Flush Redis before running k6:'
\echo '   docker exec uip-redis redis-cli -a changeme_redis_password FLUSHDB'
\echo '============================================================'
