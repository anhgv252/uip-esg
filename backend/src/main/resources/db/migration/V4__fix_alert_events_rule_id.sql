-- V4: Fix schema gaps left by Docker init scripts vs JPA entities
-- Docker init scripts created tables without some columns that JPA entities expect.

-- 1. alerts.alert_events is missing rule_id
ALTER TABLE alerts.alert_events
    ADD COLUMN IF NOT EXISTS rule_id UUID REFERENCES alerts.alert_rules(id);

CREATE INDEX IF NOT EXISTS idx_alert_events_rule_id
    ON alerts.alert_events (rule_id);

-- 2. esg.reports is missing updated_at
ALTER TABLE esg.reports
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
