-- M4-COR-04: BMS feedback loop events
-- Records each stage of the BMS command feedback state machine:
--   COMMAND_SENT → COMMAND_ACKNOWLEDGED → ACTION_TAKEN → FEEDBACK_VERIFIED

CREATE TABLE bms_feedback_events (
    id                  BIGSERIAL    PRIMARY KEY,
    pending_command_id  BIGINT       NOT NULL REFERENCES pending_bms_commands(id),
    building_id         VARCHAR(50),
    stage               VARCHAR(40)  NOT NULL,
    notes               VARCHAR(500),
    success             BOOLEAN      NOT NULL DEFAULT true,
    recorded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_bms_feedback_command
    ON bms_feedback_events (pending_command_id, recorded_at);

COMMENT ON TABLE  bms_feedback_events              IS 'M4-COR-04: BMS command feedback loop stage events';
COMMENT ON COLUMN bms_feedback_events.stage        IS 'COMMAND_SENT | COMMAND_ACKNOWLEDGED | ACTION_TAKEN | FEEDBACK_VERIFIED';
COMMENT ON COLUMN bms_feedback_events.success      IS 'false indicates the stage failed (e.g. BACnet NAK, no sensor confirmation)';
