-- V19: Seed demo tenant admin user for Sprint 2 multi-tenancy demo
-- Creates `tadmin` with ROLE_TENANT_ADMIN linked to the HCMC tenant.
-- Password: admin_Dev#2026!  (BCrypt cost=12, same hash as `admin`)
--   → $2a$12$JS7kx3KnMjNWQjx1bJpCWOxqUfUsSu.r6Vy6FLhP3SzB6w/3v6i9K

INSERT INTO public.app_users (username, email, password_hash, role, tenant_id, tenant_path)
VALUES (
    'tadmin',
    'tadmin@hcm.uip.local',
    '$2a$12$JS7kx3KnMjNWQjx1bJpCWOxqUfUsSu.r6Vy6FLhP3SzB6w/3v6i9K',
    'ROLE_TENANT_ADMIN',
    'hcm',
    'city.hcm'
)
ON CONFLICT (username) DO NOTHING;

-- Grant tenant admin scopes for HCMC tenant admin
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, unnest(ARRAY[
    'tenant:admin',
    'environment:read',
    'esg:read',
    'alert:read',
    'traffic:read',
    'citizen:read',
    'citizen:admin'
])
FROM public.app_users WHERE username = 'tadmin'
ON CONFLICT DO NOTHING;

-- Enable tenant_management feature flag for HCMC tenant
-- so the "Tenant Admin" nav item appears in the frontend AppShell.
UPDATE public.tenants
SET config_json = COALESCE(config_json, '{}') ||
    '{"tenant_management":{"enabled":true},"esg-module":{"enabled":true},"environment-module":{"enabled":true}}'::jsonb
WHERE tenant_id = 'hcm';
