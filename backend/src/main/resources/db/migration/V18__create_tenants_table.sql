-- V18: Tenant registry table
CREATE TABLE IF NOT EXISTS public.tenants (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   VARCHAR(50) NOT NULL UNIQUE,
    tenant_name VARCHAR(255) NOT NULL,
    tier        VARCHAR(10) NOT NULL DEFAULT 'T1',
    location_path TEXT,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    config_json JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Seed default tenant
INSERT INTO public.tenants (tenant_id, tenant_name, tier, location_path)
VALUES ('default', 'Default Tenant (T1)', 'T1', 'city.default')
ON CONFLICT (tenant_id) DO NOTHING;

-- HCMC demo tenant
INSERT INTO public.tenants (tenant_id, tenant_name, tier, location_path)
VALUES ('hcm', 'Ho Chi Minh City', 'T2', 'city.hcm')
ON CONFLICT (tenant_id) DO NOTHING;
