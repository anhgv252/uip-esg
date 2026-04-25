-- T-DEBT-05: Audit trail for TriggerConfig changes
-- Tracks who changed what and when for rollback and compliance

CREATE TABLE workflow.trigger_config_audit (
    id            BIGSERIAL    PRIMARY KEY,
    config_id     BIGINT       NOT NULL,
    scenario_key  VARCHAR(100),
    action        VARCHAR(20)  NOT NULL,     -- CREATE | UPDATE | DISABLE
    changed_by    VARCHAR(100) NOT NULL,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    snapshot      JSONB        NOT NULL      -- config state after the change
);

CREATE INDEX idx_tca_config_id   ON workflow.trigger_config_audit (config_id);
CREATE INDEX idx_tca_changed_at  ON workflow.trigger_config_audit (changed_at DESC);
