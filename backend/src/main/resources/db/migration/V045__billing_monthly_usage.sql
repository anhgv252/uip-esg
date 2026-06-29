-- M5-4 T01: Billing Aggregation Job — Monthly Usage Summary Table
-- Materialized roll-up of metering_events for fast billing dashboard queries

CREATE TABLE IF NOT EXISTS billing.monthly_usage (
    id                         BIGSERIAL PRIMARY KEY,
    tenant_id                  VARCHAR(100) NOT NULL,
    building_id                VARCHAR(100),
    billing_month              VARCHAR(7) NOT NULL,           -- YYYY-MM format
    total_sensor_readings      BIGINT DEFAULT 0,
    total_ai_inferences        BIGINT DEFAULT 0,
    total_ai_tokens            BIGINT DEFAULT 0,
    total_alerts               BIGINT DEFAULT 0,
    total_workflow_executions  BIGINT DEFAULT 0,
    base_fee_vnd               BIGINT DEFAULT 2000000,        -- 2M VND/building/month
    ai_overage_vnd             BIGINT DEFAULT 0,              -- 50 VND per 1K tokens over 100K baseline
    total_cost_vnd             BIGINT DEFAULT 0,
    aggregated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(tenant_id, building_id, billing_month)
);

CREATE INDEX idx_monthly_usage_tenant_month 
    ON billing.monthly_usage(tenant_id, billing_month DESC);

CREATE INDEX idx_monthly_usage_building 
    ON billing.monthly_usage(building_id, billing_month DESC) 
    WHERE building_id IS NOT NULL;

COMMENT ON TABLE billing.monthly_usage IS 'M5-4 T01: Daily aggregated billing summary per tenant/building';
COMMENT ON COLUMN billing.monthly_usage.billing_month IS 'Format: YYYY-MM (e.g., 2026-06)';
COMMENT ON COLUMN billing.monthly_usage.base_fee_vnd IS 'Base subscription fee: 2M VND/building/month';
COMMENT ON COLUMN billing.monthly_usage.ai_overage_vnd IS 'AI token overage cost: 50 VND per 1K tokens above 100K baseline';
COMMENT ON COLUMN billing.monthly_usage.total_cost_vnd IS 'Total cost = base_fee_vnd + ai_overage_vnd';
