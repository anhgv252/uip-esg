-- V20: Tenant config KV table + invite tokens table
-- Sprint 4 BE-4.6/4.9: TenantAdminService, InviteService

-- tenant_config: key-value store per tenant for feature flags and settings
CREATE TABLE IF NOT EXISTS public.tenant_config (
    config_key   VARCHAR(100) NOT NULL,
    tenant_id    VARCHAR(50)  NOT NULL,
    config_value TEXT         NOT NULL,
    updated_by   VARCHAR(100),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, config_key)
);

CREATE INDEX IF NOT EXISTS idx_tenant_config_tenant ON public.tenant_config (tenant_id);

-- invite_tokens: invitation tokens for tenant user onboarding
CREATE TABLE IF NOT EXISTS public.invite_tokens (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token       UUID        NOT NULL UNIQUE,
    tenant_id   VARCHAR(50) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(30) NOT NULL DEFAULT 'ROLE_CITIZEN',
    invited_by  VARCHAR(100),
    used_at     TIMESTAMPTZ,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_invite_tokens_token ON public.invite_tokens (token);
CREATE INDEX IF NOT EXISTS idx_invite_tokens_email ON public.invite_tokens (email);
