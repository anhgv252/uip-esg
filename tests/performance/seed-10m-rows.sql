-- Sprint MVP3-1 Performance Seeding Script
-- Tạo ~10M ESG readings cho load testing
-- Runtime estimate: ~8 phút trên SSD
-- Chỉ dùng cho test environment, KHÔNG production

-- =============================================================================
-- 1. Seed buildings (5 buildings, 2 tenants)
-- =============================================================================
INSERT INTO public.buildings (building_code, building_name, tenant_id, cluster_id, floor_count, total_area_m2)
VALUES
    ('PERF-BLD-001', 'Perf Building Alpha-1', 'hcm', 'cluster-hcm-central', 30, 25000.0),
    ('PERF-BLD-002', 'Perf Building Alpha-2', 'hcm', 'cluster-hcm-central', 25, 20000.0),
    ('PERF-BLD-003', 'Perf Building Alpha-3', 'hcm', 'cluster-hcm-central', 20, 18000.0),
    ('PERF-BLD-004', 'Perf Building Beta-1',  'default', 'cluster-default', 15, 12000.0),
    ('PERF-BLD-005', 'Perf Building Beta-2',  'default', 'cluster-default', 10,  8000.0)
ON CONFLICT (tenant_id, building_code) DO NOTHING;

-- =============================================================================
-- 2. Seed 10M ESG readings via server-side generate_series
-- 5 buildings × 100 sensors × 20,000 readings = 10M rows
-- Interval: 4 minutes, from 2026-02-01 to ~2026-04-07 (~19,440 intervals/sensor)
-- Approximate using 20,000 readings per sensor for ~10M total
-- =============================================================================
DO $$
DECLARE
    start_ts TIMESTAMPTZ := '2026-02-01 00:00:00+00';
    v_count  BIGINT;
BEGIN
    RAISE NOTICE 'Starting 10M row seeding at %', NOW();
    RAISE NOTICE 'Estimated runtime: 5-10 minutes on SSD';

    INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, building_id, tenant_id)
    SELECT
        'SENSOR-' || building_code || '-' || LPAD(sensor_num::TEXT, 3, '0'),
        CASE (sensor_num % 3)
            WHEN 0 THEN 'energy'
            WHEN 1 THEN 'water'
            ELSE 'co2'
        END,
        start_ts + (row_num * INTERVAL '4 minutes'),
        -- Realistic values with some variance
        CASE
            WHEN sensor_num % 3 = 0 THEN  -- energy: 50-150 kWh
                50.0 + (building_multiplier * 20) + (RANDOM() * 50)
            WHEN sensor_num % 3 = 1 THEN  -- water: 10-50 m3
                10.0 + (building_multiplier * 5)  + (RANDOM() * 20)
            ELSE                           -- co2: 100-500 kg
                100.0 + (building_multiplier * 50) + (RANDOM() * 100)
        END,
        CASE (sensor_num % 3)
            WHEN 0 THEN 'kWh'
            WHEN 1 THEN 'm3'
            ELSE 'kg'
        END,
        building_code,
        tenant_id
    FROM (
        VALUES
            ('PERF-BLD-001', 'hcm',     1),
            ('PERF-BLD-002', 'hcm',     2),
            ('PERF-BLD-003', 'hcm',     3),
            ('PERF-BLD-004', 'default', 4),
            ('PERF-BLD-005', 'default', 5)
    ) AS b(building_code, tenant_id, building_multiplier),
    generate_series(1, 100) AS sensor_num,
    generate_series(0, 19999) AS row_num;

    GET DIAGNOSTICS v_count = ROW_COUNT;
    RAISE NOTICE 'Seeded % rows at %', v_count, NOW();
END $$;

-- =============================================================================
-- 3. Verify row counts
-- =============================================================================
SELECT
    tenant_id,
    building_id,
    metric_type,
    COUNT(*)        AS row_count,
    MIN(timestamp)  AS earliest,
    MAX(timestamp)  AS latest
FROM esg.clean_metrics
WHERE building_id LIKE 'PERF-BLD-%'
GROUP BY tenant_id, building_id, metric_type
ORDER BY tenant_id, building_id, metric_type;

-- Total count
SELECT COUNT(*) AS total_perf_rows
FROM esg.clean_metrics
WHERE building_id LIKE 'PERF-BLD-%';

-- =============================================================================
-- Cleanup (run when done with performance testing)
-- =============================================================================
-- DELETE FROM esg.clean_metrics WHERE building_id LIKE 'PERF-BLD-%';
-- DELETE FROM public.buildings WHERE building_code LIKE 'PERF-BLD-%';
