-- M5-4 T09: Audit Log Lite — Immutable Append-Only Audit Trail
-- Critical events: billing, LOTUS certification, ESG report generation

CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE IF NOT EXISTS audit.events (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     VARCHAR(100) NOT NULL,
    event_type    VARCHAR(100) NOT NULL,          -- BILLING_INVOICE_GENERATED, LOTUS_CERT_CALCULATED, ESG_REPORT_GENERATED
    actor_id      VARCHAR(100),                   -- User ID or 'SYSTEM' for automated events
    entity_id     VARCHAR(200),                   -- Invoice ID, Building ID, Report ID, etc.
    entity_type   VARCHAR(100),                   -- INVOICE, BUILDING, REPORT, CERTIFICATION
    metadata      JSONB,                          -- Event-specific context (e.g., invoice amount, LOTUS score)
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Performance indexes
CREATE INDEX idx_audit_tenant_type 
    ON audit.events(tenant_id, event_type, occurred_at DESC);

CREATE INDEX idx_audit_entity 
    ON audit.events(entity_type, entity_id, occurred_at DESC);

CREATE INDEX idx_audit_occurred 
    ON audit.events(occurred_at DESC);

-- Enable Row Level Security (immutable: no updates/deletes allowed)
ALTER TABLE audit.events ENABLE ROW LEVEL SECURITY;

-- Block all DELETE operations
CREATE POLICY audit_no_delete ON audit.events 
    FOR DELETE USING (false);

-- Block all UPDATE operations
CREATE POLICY audit_no_update ON audit.events 
    FOR UPDATE USING (false);

-- Allow INSERT for all authenticated users (application role)
CREATE POLICY audit_insert_only ON audit.events 
    FOR INSERT WITH CHECK (true);

COMMENT ON SCHEMA audit IS 'M5-4 T09: Immutable audit trail for critical business events';
COMMENT ON TABLE audit.events IS 'Append-only audit log (no updates/deletes permitted by RLS policies)';
COMMENT ON COLUMN audit.events.event_type IS 'Event classification: BILLING_INVOICE_GENERATED, LOTUS_CERT_CALCULATED, ESG_REPORT_GENERATED, etc.';
COMMENT ON COLUMN audit.events.actor_id IS 'Who triggered the event: user UUID or SYSTEM for automated processes';
COMMENT ON COLUMN audit.events.metadata IS 'Event-specific JSON payload (e.g., {invoiceAmount: 10000000, buildings: 5})';
