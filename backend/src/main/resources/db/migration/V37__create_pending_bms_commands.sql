-- M4-COR-03: Pending BMS commands — 2-step confirmation flow
-- BR-010: No BMS command executes without explicit operator approval.
-- Commands are created in PENDING state and require OPERATOR/ADMIN role to approve.

CREATE TABLE pending_bms_commands (
    id              BIGSERIAL PRIMARY KEY,
    building_id     VARCHAR(50)  NOT NULL,
    tenant_id       VARCHAR(50)  NOT NULL,
    command_type    VARCHAR(50)  NOT NULL,
    target_device   VARCHAR(200) NOT NULL,
    target_value    VARCHAR(200) NOT NULL,
    status          VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    requested_by    VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ,
    resolved_by     VARCHAR(100)
);

CREATE INDEX idx_bms_commands_status
    ON pending_bms_commands (status);

CREATE INDEX idx_bms_commands_building
    ON pending_bms_commands (building_id, status);

CREATE INDEX idx_bms_commands_tenant_status
    ON pending_bms_commands (tenant_id, status);

COMMENT ON TABLE  pending_bms_commands           IS 'M4-COR-03: BMS auto-command proposals awaiting operator approval (BR-010)';
COMMENT ON COLUMN pending_bms_commands.status    IS 'PENDING | APPROVED | REJECTED | EXPIRED | EXECUTED';
COMMENT ON COLUMN pending_bms_commands.expires_at IS 'created_at + 30s: approval window deadline';
