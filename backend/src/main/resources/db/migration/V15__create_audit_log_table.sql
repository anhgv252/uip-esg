-- V15: AuditLog table for tracking user actions across the platform
CREATE TABLE IF NOT EXISTS public.audit_log (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    actor       VARCHAR(255) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    resource_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(255),
    tenant_id   VARCHAR(100),
    timestamp   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    details     JSONB
);

CREATE INDEX IF NOT EXISTS idx_audit_log_resource ON public.audit_log (resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant   ON public.audit_log (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_actor    ON public.audit_log (actor);
CREATE INDEX IF NOT EXISTS idx_audit_log_timestamp ON public.audit_log (timestamp DESC);
