-- V3: Seed data — sensors, alert rules, error records for development/demo

-- ─── Seed sensors ────────────────────────────────────────────────────────────
INSERT INTO environment.sensors (sensor_id, sensor_name, sensor_type, district_code, latitude, longitude)
VALUES
    ('ENV-001', 'Bến Nghé AQI Station',    'AIR_QUALITY', 'D1',   10.7769, 106.7009),
    ('ENV-002', 'Tân Bình AQI Station',    'AIR_QUALITY', 'TB',   10.8011, 106.6526),
    ('ENV-003', 'Bình Thạnh AQI Station',  'AIR_QUALITY', 'BT',   10.8120, 106.7127),
    ('ENV-004', 'Gò Vấp AQI Station',      'AIR_QUALITY', 'GV',   10.8382, 106.6639),
    ('ENV-005', 'District 7 AQI Station',  'AIR_QUALITY', 'D7',   10.7347, 106.7218),
    ('ENV-006', 'Thủ Đức AQI Station',     'AIR_QUALITY', 'TD',   10.8580, 106.7619),
    ('ENV-007', 'Hóc Môn AQI Station',     'AIR_QUALITY', 'HM',   10.8913, 106.5946),
    ('ENV-008', 'Bình Chánh AQI Station',  'AIR_QUALITY', 'BCh',  10.6923, 106.5734)
ON CONFLICT (sensor_id) DO NOTHING;

-- ─── Seed alert rules ────────────────────────────────────────────────────────
INSERT INTO alerts.alert_rules (rule_name, module, measure_type, operator, threshold, severity, cooldown_minutes)
VALUES
    ('AQI WARNING',      'ENVIRONMENT', 'aqi',  '>',  150.0, 'WARNING',   10),
    ('AQI CRITICAL',     'ENVIRONMENT', 'aqi',  '>',  200.0, 'CRITICAL',  10),
    ('AQI EMERGENCY',    'ENVIRONMENT', 'aqi',  '>',  300.0, 'EMERGENCY', 5),
    ('PM2.5 WARNING',    'ENVIRONMENT', 'pm25', '>',  55.4,  'WARNING',   10),
    ('PM2.5 CRITICAL',   'ENVIRONMENT', 'pm25', '>',  125.4, 'CRITICAL',  10),
    ('PM10 WARNING',     'ENVIRONMENT', 'pm10', '>',  154.0, 'WARNING',   10),
    ('NO2 WARNING',      'ENVIRONMENT', 'no2',  '>',  100.0, 'WARNING',   15),
    ('O3 WARNING',       'ENVIRONMENT', 'o3',   '>',  70.0,  'WARNING',   15)
ON CONFLICT DO NOTHING;

-- ─── Seed demo alert events (last 24h) ───────────────────────────────────────
INSERT INTO alerts.alert_events (sensor_id, module, measure_type, value, threshold, severity, status, detected_at)
SELECT
    'ENV-00' || g,
    'ENVIRONMENT',
    'aqi',
    155.0 + g * 10,
    150.0,
    CASE WHEN g % 2 = 0 THEN 'CRITICAL' ELSE 'WARNING' END,
    CASE WHEN g % 3 = 0 THEN 'ACKNOWLEDGED' ELSE 'OPEN' END,
    NOW() - INTERVAL '1 hour' * g
FROM generate_series(1, 5) AS g;

-- ─── Seed demo ESG metrics ───────────────────────────────────────────────────
INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-ENERGY-' || b,
    'ENERGY',
    NOW() - INTERVAL '1 day' * d,
    1000.0 + b * 50 + d * 10,
    'kWh',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || b
FROM generate_series(1, 5) b, generate_series(0, 6) d;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-WATER-' || b,
    'WATER',
    NOW() - INTERVAL '1 day' * d,
    200.0 + b * 20 + d * 5,
    'm3',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || b
FROM generate_series(1, 5) b, generate_series(0, 6) d;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-CARBON-' || b,
    'CARBON',
    NOW() - INTERVAL '1 day' * d,
    0.45 * (1000.0 + b * 50 + d * 10) / 1000.0,
    'tCO2e',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || b
FROM generate_series(1, 5) b, generate_series(0, 6) d;

-- ─── Seed demo error records ──────────────────────────────────────────────────
INSERT INTO error_mgmt.error_records (source_module, kafka_topic, kafka_offset, error_type, error_message, status, occurred_at)
VALUES
    ('ENVIRONMENT', 'ngsi_ld_environment', 1001, 'PARSE_ERROR',      'Missing field: pm25',             'UNRESOLVED', NOW() - INTERVAL '2 hours'),
    ('ESG',         'ngsi_ld_esg',         2045, 'VALIDATION_ERROR', 'Value out of range: energy=-1',   'RESOLVED',   NOW() - INTERVAL '5 hours'),
    ('TRAFFIC',     'ngsi_ld_traffic',     305,  'PARSE_ERROR',      'Unknown congestion level: NONE',  'UNRESOLVED', NOW() - INTERVAL '1 hour'),
    ('ENVIRONMENT', 'ngsi_ld_environment', 1042, 'DB_WRITE_ERROR',   'Connection timeout',              'REINGESTED', NOW() - INTERVAL '3 hours'),
    ('ESG',         'ngsi_ld_esg',         2100, 'PARSE_ERROR',      'Invalid timestamp format',        'UNRESOLVED', NOW() - INTERVAL '30 minutes');
