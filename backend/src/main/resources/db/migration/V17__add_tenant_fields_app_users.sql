-- V16: Add tenant fields to app_users + create scopes table
-- Extends JWT claims for multi-tenancy: tenant_id, tenant_path, scopes per user.

ALTER TABLE public.app_users
    ADD COLUMN IF NOT EXISTS tenant_id TEXT NOT NULL DEFAULT 'default',
    ADD COLUMN IF NOT EXISTS tenant_path TEXT DEFAULT 'city.default';

CREATE TABLE IF NOT EXISTS public.app_user_scopes (
    user_id UUID NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
    scope   VARCHAR(100) NOT NULL,
    PRIMARY KEY (user_id, scope)
);

-- Backfill tenant_id
UPDATE public.app_users SET tenant_id = 'default' WHERE tenant_id IS NULL;

-- Admin gets all scopes
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, unnest(ARRAY[
    'environment:read','environment:write',
    'esg:read','esg:write',
    'alert:read','alert:ack',
    'traffic:read','traffic:write',
    'sensor:read','sensor:write',
    'citizen:read','citizen:admin',
    'workflow:read','workflow:write',
    'tenant:admin'
])
FROM public.app_users WHERE role = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- Operator gets operational scopes
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, unnest(ARRAY[
    'environment:read','environment:write',
    'esg:read',
    'alert:read','alert:ack',
    'traffic:read','traffic:write',
    'sensor:read','sensor:write',
    'workflow:read'
])
FROM public.app_users WHERE role = 'ROLE_OPERATOR'
ON CONFLICT DO NOTHING;

-- Citizen gets read-only scopes
INSERT INTO public.app_user_scopes (user_id, scope)
SELECT id, unnest(ARRAY[
    'environment:read',
    'esg:read',
    'alert:read',
    'traffic:read'
])
FROM public.app_users WHERE role = 'ROLE_CITIZEN'
ON CONFLICT DO NOTHING;

CREATE INDEX IF NOT EXISTS idx_app_user_scopes_user ON public.app_user_scopes (user_id);
