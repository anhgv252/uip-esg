-- V23: Seed cors.allowed-origins per tenant for BT-14b dynamic CORS
-- Values are JSON arrays or CSV of allowed origins loaded by DynamicCorsConfigurationSource.
-- Add entries here when onboarding new tenant partner origins.

INSERT INTO public.tenant_config (tenant_id, config_key, config_value, updated_by, updated_at)
VALUES
    ('hcm',     'cors.allowed-origins', '["https://city.hcm.gov.vn","https://uip.hcm.gov.vn"]', 'system', NOW()),
    ('default', 'cors.allowed-origins', '["http://localhost:3000","http://localhost:5173"]',       'system', NOW())
ON CONFLICT (tenant_id, config_key) DO UPDATE
    SET config_value = EXCLUDED.config_value,
        updated_by   = EXCLUDED.updated_by,
        updated_at   = EXCLUDED.updated_at;
