-- V31: Fix demo building UUID for consistent energy forecast testing
--
-- Problem: V26 uses gen_random_uuid() so Demo Building 1 gets a different UUID
-- each environment rebuild, breaking the demo-seed-data.sql + NaiveForecast flow.
--
-- Fix: Ensure Demo Building 1 exists with a fixed UUID '65c06d23-3cf3-4490-96a6-ac8ff2a17f2c'
-- by replacing any auto-generated row. Safe because public.buildings.id has no FK dependents.

DO $$
DECLARE
    fixed_uuid  UUID    := '65c06d23-3cf3-4490-96a6-ac8ff2a17f2c';
    target_code VARCHAR := 'BLD-DEFAULT-001';
    target_tenant VARCHAR := 'default';
BEGIN
    -- If the fixed UUID is already present, nothing to do
    IF NOT EXISTS (SELECT 1 FROM public.buildings WHERE id = fixed_uuid) THEN
        -- Remove the auto-generated row for Demo Building 1 if present
        DELETE FROM public.buildings
        WHERE  building_code = target_code
          AND  tenant_id     = target_tenant;

        -- Insert with the fixed UUID so demo seed data is stable across rebuilds
        INSERT INTO public.buildings
            (id, building_code, building_name, tenant_id, cluster_id, floor_count, total_area_m2, is_active)
        VALUES
            (fixed_uuid, target_code, 'Demo Building 1', target_tenant, 'cluster-default', 10, 12000.0, TRUE);

        RAISE NOTICE 'Demo Building 1 re-inserted with fixed UUID %', fixed_uuid;
    ELSE
        RAISE NOTICE 'Demo Building 1 already has fixed UUID %, skipping', fixed_uuid;
    END IF;
END $$;
