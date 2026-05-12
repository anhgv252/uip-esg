-- V26: Multi-Building Cluster support cho MVP3
-- Sprint MVP3-1 | Story: v3-BE-01
-- Tạo: public.buildings table + RLS + index on esg.clean_metrics

-- =============================================================================
-- 1. Thêm is_aggregator flag vào public.tenants nếu chưa có
-- =============================================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'tenants'
                   AND column_name = 'is_aggregator') THEN
        ALTER TABLE public.tenants ADD COLUMN is_aggregator BOOLEAN NOT NULL DEFAULT FALSE;
    END IF;
END $$;

COMMENT ON COLUMN public.tenants.is_aggregator
    IS 'TRUE = Cluster Manager tenant; được phép query cross-building aggregate. BR-002.';

-- =============================================================================
-- 2. Tạo public.buildings table
-- =============================================================================
CREATE TABLE IF NOT EXISTS public.buildings (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    building_code   VARCHAR(50)     NOT NULL,
    building_name   VARCHAR(255)    NOT NULL,
    tenant_id       VARCHAR(50)     NOT NULL REFERENCES public.tenants(tenant_id),
    cluster_id      VARCHAR(50),
    floor_count     INT             NOT NULL DEFAULT 1,
    total_area_m2   DOUBLE PRECISION,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_building_tenant_code UNIQUE (tenant_id, building_code)
);

CREATE INDEX IF NOT EXISTS idx_buildings_tenant
    ON public.buildings(tenant_id);
CREATE INDEX IF NOT EXISTS idx_buildings_cluster
    ON public.buildings(cluster_id)
    WHERE cluster_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_buildings_active_tenant
    ON public.buildings(tenant_id, is_active)
    WHERE is_active = TRUE;

CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS buildings_updated_at ON public.buildings;
CREATE TRIGGER buildings_updated_at
    BEFORE UPDATE ON public.buildings
    FOR EACH ROW EXECUTE FUNCTION public.update_updated_at_column();

COMMENT ON TABLE public.buildings
    IS 'MVP3 Multi-building management table. Tenant-isolated via RLS. Distinct from citizens.buildings (citizen portal).';
COMMENT ON COLUMN public.buildings.cluster_id
    IS 'Optional cluster grouping. Aggregator tenant với is_aggregator=true mới được query cross-cluster.';

-- =============================================================================
-- 3. Enable RLS on public.buildings
-- =============================================================================
ALTER TABLE public.buildings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.buildings FORCE ROW LEVEL SECURITY;

-- Policy: tenant thấy buildings của mình; empty string = admin/service account bypass
DROP POLICY IF EXISTS buildings_tenant_isolation ON public.buildings;
CREATE POLICY buildings_tenant_isolation ON public.buildings
    USING (
        tenant_id = current_setting('app.tenant_id', true)
        OR current_setting('app.tenant_id', true) = ''
        OR current_setting('app.tenant_id', true) IS NULL
    );

-- =============================================================================
-- 4. Index hỗ trợ cross-building query trên esg.clean_metrics
-- =============================================================================
-- esg.clean_metrics đã có building_id VARCHAR(100) từ V2
-- Thêm composite index cho cross-building aggregation
CREATE INDEX IF NOT EXISTS idx_clean_metrics_building_tenant_ts
    ON esg.clean_metrics (building_id, tenant_id, timestamp DESC)
    WHERE building_id IS NOT NULL;

-- =============================================================================
-- 5. Seed demo buildings (cho testing Sprint 1)
-- =============================================================================
INSERT INTO public.buildings (building_code, building_name, tenant_id, cluster_id, floor_count, total_area_m2)
VALUES
    ('BLD-HCM-001', 'Landmark 81 - Tower A', 'hcm', 'cluster-hcm-central', 81, 92000.0),
    ('BLD-HCM-002', 'Saigon Centre - South Tower', 'hcm', 'cluster-hcm-central', 25, 45000.0),
    ('BLD-DEFAULT-001', 'Demo Building 1', 'default', 'cluster-default', 10, 12000.0)
ON CONFLICT (tenant_id, building_code) DO NOTHING;

-- Update hcm tenant to be aggregator (cluster manager)
UPDATE public.tenants SET is_aggregator = TRUE WHERE tenant_id = 'hcm';
