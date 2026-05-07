CREATE TABLE public.push_subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
    tenant_id    VARCHAR(50) NOT NULL,
    platform     VARCHAR(20) NOT NULL DEFAULT 'web',
    endpoint     TEXT NOT NULL,
    p256dh       VARCHAR(255),
    auth_key     VARCHAR(255),
    device_token VARCHAR(500),
    user_agent   VARCHAR(500),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_push_sub_endpoint ON public.push_subscriptions(endpoint);
CREATE INDEX idx_push_sub_user_active ON public.push_subscriptions(user_id, active);
CREATE INDEX idx_push_sub_tenant ON public.push_subscriptions(tenant_id);

ALTER TABLE public.push_subscriptions ENABLE ROW LEVEL SECURITY;

CREATE POLICY push_sub_tenant_policy ON public.push_subscriptions
    USING (tenant_id = current_setting('app.tenant_id', true));

CREATE POLICY push_sub_user_policy ON public.push_subscriptions
    USING (user_id::text = current_setting('app.user_id', true));
