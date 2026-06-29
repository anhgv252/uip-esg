-- M5-2 T07: Billing Skeleton — Tenant Metering Ledger
-- Tracks AI cost consumption events for tenant billing

CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.metering_events (
    id                  BIGSERIAL    PRIMARY KEY,
    tenant_id           VARCHAR(50)  NOT NULL,
    event_id            VARCHAR(100) NOT NULL UNIQUE,
    event_type          VARCHAR(50)  NOT NULL,
    workflow_run_id     UUID,
    metadata            JSONB,
    cost_usd_cents      INTEGER      NOT NULL DEFAULT 0,
    recorded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_metering_tenant_recorded 
    ON billing.metering_events (tenant_id, recorded_at DESC);

CREATE INDEX idx_metering_event_id 
    ON billing.metering_events (event_id);

CREATE INDEX idx_metering_type_recorded 
    ON billing.metering_events (event_type, recorded_at DESC);

-- Aggregated monthly usage view
CREATE OR REPLACE VIEW billing.monthly_usage AS
SELECT 
    tenant_id,
    DATE_TRUNC('month', recorded_at) AS month,
    event_type,
    COUNT(*) AS event_count,
    SUM(cost_usd_cents) AS total_cost_usd_cents,
    MIN(recorded_at) AS first_event_at,
    MAX(recorded_at) AS last_event_at
FROM billing.metering_events
GROUP BY tenant_id, DATE_TRUNC('month', recorded_at), event_type;

COMMENT ON SCHEMA billing IS 'M5-2 T07: Tenant billing and metering ledger';
COMMENT ON TABLE  billing.metering_events IS 'AI cost consumption events for tenant billing';
COMMENT ON COLUMN billing.metering_events.event_id IS 'Kafka message ID for idempotency (Redis dedup key)';
COMMENT ON COLUMN billing.metering_events.event_type IS 'AI_PREDICTION | AI_TRAINING | AI_INFERENCE | WORKFLOW_RUN | API_CALL';
COMMENT ON COLUMN billing.metering_events.cost_usd_cents IS 'Cost in USD cents (e.g., 150 = $1.50)';
COMMENT ON COLUMN billing.metering_events.metadata IS 'Additional event context (model name, tokens used, etc.)';
COMMENT ON VIEW   billing.monthly_usage IS 'Aggregated monthly usage by tenant and event type';
