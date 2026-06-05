-- =============================================================================
-- SEED ESG MULTI-QUARTER DATA — Sprint 9 S9-DATA-SEED
-- Purpose: Seed ESG metrics for Q1-Q4 2026 to enable multi-quarter analytics
-- Run    : psql -h localhost -U uip -d uip_smartcity -f scripts/seed-esg-multi-quarter.sql
--          or: docker exec -i uip-timescaledb psql -U uip -d uip_smartcity < scripts/seed-esg-multi-quarter.sql
-- Safe   : All INSERTs use ON CONFLICT DO NOTHING (idempotent, safe to re-run)
-- =============================================================================

-- ─── Tenant/Building Configuration ──────────────────────────────────────────
-- 4 demo tenants: tenant-001, tenant-002, tenant-003, tenant-004
-- 5 buildings per tenant: BLDG-001 to BLDG-005
-- 4 quarters: Q1 (Jan), Q2 (Apr), Q3 (Jul), Q4 (Oct) 2026
-- Trend: slight improvement Q1→Q4 (simulating ESG progress)

-- ─── Q1 2026: Baseline ESG Metrics (January) ────────────────────────────────
-- Energy: 1000-1200 kWh per building
-- Water: 200-250 m³ per building
-- Carbon: 0.45 kg CO2e per kWh (Vietnam grid factor)

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-ENERGY-Q1-' || b,
    'ENERGY',
    '2026-01-31 00:00:00+00'::TIMESTAMPTZ,
    1000.0 + b * 40,
    'kWh',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-WATER-Q1-' || b,
    'WATER',
    '2026-01-31 00:00:00+00'::TIMESTAMPTZ,
    200.0 + b * 10,
    'm3',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-CARBON-Q1-' || b,
    'CARBON',
    '2026-01-31 00:00:00+00'::TIMESTAMPTZ,
    ROUND((0.45 * (1000.0 + b * 40) / 1000.0)::numeric, 4),
    'tCO2e',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

-- ─── Q2 2026: Slight Improvement (April) ────────────────────────────────────
-- Energy: 5% reduction
-- Water: 3% reduction
-- Carbon: proportional to energy

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-ENERGY-Q2-' || b,
    'ENERGY',
    '2026-04-30 00:00:00+00'::TIMESTAMPTZ,
    ROUND(((1000.0 + b * 40) * 0.95)::numeric, 2),
    'kWh',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-WATER-Q2-' || b,
    'WATER',
    '2026-04-30 00:00:00+00'::TIMESTAMPTZ,
    ROUND(((200.0 + b * 10) * 0.97)::numeric, 2),
    'm3',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-CARBON-Q2-' || b,
    'CARBON',
    '2026-04-30 00:00:00+00'::TIMESTAMPTZ,
    ROUND((0.45 * (1000.0 + b * 40) * 0.95 / 1000.0)::numeric, 4),
    'tCO2e',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

-- ─── Q3 2026: Further Improvement (July) ────────────────────────────────────
-- Energy: 10% reduction from Q1
-- Water: 7% reduction from Q1
-- Carbon: proportional to energy

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-ENERGY-Q3-' || b,
    'ENERGY',
    '2026-07-31 00:00:00+00'::TIMESTAMPTZ,
    ROUND(((1000.0 + b * 40) * 0.90)::numeric, 2),
    'kWh',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-WATER-Q3-' || b,
    'WATER',
    '2026-07-31 00:00:00+00'::TIMESTAMPTZ,
    ROUND(((200.0 + b * 10) * 0.93)::numeric, 2),
    'm3',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-CARBON-Q3-' || b,
    'CARBON',
    '2026-07-31 00:00:00+00'::TIMESTAMPTZ,
    ROUND((0.45 * (1000.0 + b * 40) * 0.90 / 1000.0)::numeric, 4),
    'tCO2e',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

-- ─── Q4 2026: Best Performance (October) ────────────────────────────────────
-- Energy: 15% reduction from Q1
-- Water: 10% reduction from Q1
-- Carbon: proportional to energy

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-ENERGY-Q4-' || b,
    'ENERGY',
    '2026-10-31 00:00:00+00'::TIMESTAMPTZ,
    ROUND(((1000.0 + b * 40) * 0.85)::numeric, 2),
    'kWh',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-WATER-Q4-' || b,
    'WATER',
    '2026-10-31 00:00:00+00'::TIMESTAMPTZ,
    ROUND(((200.0 + b * 10) * 0.90)::numeric, 2),
    'm3',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, district_code)
SELECT
    'ESG-CARBON-Q4-' || b,
    'CARBON',
    '2026-10-31 00:00:00+00'::TIMESTAMPTZ,
    ROUND((0.45 * (1000.0 + b * 40) * 0.85 / 1000.0)::numeric, 4),
    'tCO2e',
    'BLDG-' || LPAD(b::text, 3, '0'),
    'D' || ((b - 1) / 5 + 1)::text
FROM generate_series(1, 20) b
ON CONFLICT DO NOTHING;

-- ─── Summary ─────────────────────────────────────────────────────────────────
SELECT 'Q1 2026 (Jan) ESG metrics' AS period, count(*) AS count
FROM   esg.clean_metrics
WHERE  timestamp = '2026-01-31 00:00:00+00';

SELECT 'Q2 2026 (Apr) ESG metrics' AS period, count(*) AS count
FROM   esg.clean_metrics
WHERE  timestamp = '2026-04-30 00:00:00+00';

SELECT 'Q3 2026 (Jul) ESG metrics' AS period, count(*) AS count
FROM   esg.clean_metrics
WHERE  timestamp = '2026-07-31 00:00:00+00';

SELECT 'Q4 2026 (Oct) ESG metrics' AS period, count(*) AS count
FROM   esg.clean_metrics
WHERE  timestamp = '2026-10-31 00:00:00+00';

-- Expected: 60 metrics per quarter (20 buildings × 3 metric types)
-- Total: 240 metrics across 4 quarters
