-- =============================================================================
-- DEMO SEED DATA — UIP ESG POC Sprint 1
-- Purpose : Reset database to a live, presentable state for PO demo.
-- Run     : psql -h localhost -U uip -d uip_smartcity -f scripts/demo-seed-data.sql
--           or: docker exec -i uip-timescaledb psql -U uip -d uip_smartcity < scripts/demo-seed-data.sql
-- Safe    : All INSERT use ON CONFLICT DO NOTHING; UPDATE is idempotent.
-- =============================================================================

-- ─── 1. Set all sensors ONLINE (last_seen_at within 2 minutes) ───────────────
--    EnvironmentService.ONLINE_THRESHOLD_MINUTES = 5 → last_seen_at > NOW()-5min
UPDATE environment.sensors
SET    last_seen_at = NOW() - INTERVAL '2 minutes'
WHERE  sensor_id IN (
    'ENV-001','ENV-002','ENV-003','ENV-004',
    'ENV-005','ENV-006','ENV-007','ENV-008'
);

-- ─── 2. Insert recent sensor readings (last 2 hours, every 15 min) ───────────
--    8 sensors × 9 intervals = 72 rows
INSERT INTO environment.sensor_readings
    (sensor_id, timestamp, aqi, pm25, pm10, no2, o3, so2, co, temperature, humidity)
SELECT
    s.sensor_id,
    NOW() - (i.m || ' minutes')::INTERVAL,
    -- AQI: moderate → unhealthy range (70–180) with per-sensor variation
    ROUND((70 + (abs(hashtext(s.sensor_id)) % 110) + (i.m / 15 * 2))::numeric, 1),
    -- PM2.5: 15–80 µg/m³
    ROUND((15 + (abs(hashtext(s.sensor_id || 'pm25')) % 65) + (i.m / 15))::numeric, 2),
    -- PM10: 30–120 µg/m³
    ROUND((30 + (abs(hashtext(s.sensor_id || 'pm10')) % 90) + (i.m / 15 * 1.5))::numeric, 2),
    -- NO2: 20–90 µg/m³
    ROUND((20 + (abs(hashtext(s.sensor_id || 'no2')) % 70) + (i.m / 15 * 0.5))::numeric, 2),
    -- O3: 30–80 µg/m³
    ROUND((30 + (abs(hashtext(s.sensor_id || 'o3')) % 50))::numeric, 2),
    -- SO2: 5–30 µg/m³
    ROUND((5 + (abs(hashtext(s.sensor_id || 'so2')) % 25))::numeric, 2),
    -- CO: 0.3–1.5 mg/m³
    ROUND((0.3 + (abs(hashtext(s.sensor_id || 'co')) % 12) / 10.0)::numeric, 3),
    -- Temperature: 28–36°C
    ROUND((28 + (abs(hashtext(s.sensor_id || 'temp')) % 8))::numeric, 1),
    -- Humidity: 55–85%
    ROUND((55 + (abs(hashtext(s.sensor_id || 'hum')) % 30))::numeric, 1)
FROM
    (VALUES
        ('ENV-001'),('ENV-002'),('ENV-003'),('ENV-004'),
        ('ENV-005'),('ENV-006'),('ENV-007'),('ENV-008')
    ) AS s(sensor_id),
    (SELECT generate_series(0, 120, 15) AS m) AS i
ON CONFLICT DO NOTHING;

-- ─── 3. Insert OPEN/ESCALATED alerts for Acknowledge demo ────────────────────
--    Keep a mix of WARNING / CRITICAL / HIGH severity, all OPEN or ESCALATED
INSERT INTO alerts.alert_events
    (sensor_id, module, measure_type, value, threshold, severity, status, detected_at)
VALUES
    -- ENV-001: CRITICAL AQI spike — 45 minutes ago
    ('ENV-001', 'ENVIRONMENT', 'aqi',  215.0, 200.0, 'CRITICAL',  'OPEN',      NOW() - INTERVAL '45 minutes'),
    -- ENV-003: HIGH PM2.5 — 1 hour ago, escalated
    ('ENV-003', 'ENVIRONMENT', 'pm25', 130.0, 125.4, 'CRITICAL',  'ESCALATED', NOW() - INTERVAL '1 hour'),
    -- ENV-005: WARNING AQI — 20 minutes ago (fresh)
    ('ENV-005', 'ENVIRONMENT', 'aqi',  162.0, 150.0, 'WARNING',   'OPEN',      NOW() - INTERVAL '20 minutes'),
    -- ENV-007: WARNING NO2 — 2 hours ago
    ('ENV-007', 'ENVIRONMENT', 'no2',  108.0, 100.0, 'WARNING',   'OPEN',      NOW() - INTERVAL '2 hours'),
    -- ENV-002: CRITICAL AQI — 10 minutes ago (very fresh)
    ('ENV-002', 'ENVIRONMENT', 'aqi',  305.0, 300.0, 'EMERGENCY', 'OPEN',      NOW() - INTERVAL '10 minutes')
ON CONFLICT DO NOTHING;

-- ─── 4. Add fresh ESG metrics for dashboard charts ───────────────────────────
--    Extend existing data with today's readings so charts show current values
INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-ENERGY-' || b,
    'ENERGY',
    NOW() - INTERVAL '1 hour' * h,
    900.0 + b * 55 + h * 8,
    'kWh',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || b
FROM generate_series(1, 5) b, generate_series(0, 23) h
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-WATER-' || b,
    'WATER',
    NOW() - INTERVAL '1 hour' * h,
    180.0 + b * 22 + h * 3,
    'm3',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || b
FROM generate_series(1, 5) b, generate_series(0, 23) h
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-CARBON-' || b,
    'CARBON',
    NOW() - INTERVAL '1 hour' * h,
    ROUND((0.45 * (900.0 + b * 55 + h * 8) / 1000.0)::numeric, 4),
    'tCO2e',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || b
FROM generate_series(1, 5) b, generate_series(0, 23) h
ON CONFLICT DO NOTHING;

-- ─── 5. Summary ──────────────────────────────────────────────────────────────
SELECT 'Sensors ONLINE' AS check, count(*) AS count
FROM   environment.sensors
WHERE  last_seen_at > NOW() - INTERVAL '5 minutes';

SELECT 'Recent readings (last 2h)' AS check, count(*) AS count
FROM   environment.sensor_readings
WHERE  timestamp > NOW() - INTERVAL '2 hours';

SELECT 'OPEN/ESCALATED alerts' AS check, count(*) AS count
FROM   alerts.alert_events
WHERE  status IN ('OPEN','ESCALATED');

SELECT 'ESG metrics today' AS check, count(*) AS count
FROM   esg.clean_metrics
WHERE  timestamp > NOW() - INTERVAL '24 hours';
