-- M4-COR-06: Operator feedback fields on alert_events
-- Allows operators to flag whether AI prediction was correct.

ALTER TABLE alerts.alert_events
    ADD COLUMN IF NOT EXISTS feedback_correct  BOOLEAN,
    ADD COLUMN IF NOT EXISTS feedback_comment  TEXT,
    ADD COLUMN IF NOT EXISTS feedback_by       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS feedback_at       TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_alert_events_feedback_correct
    ON alerts.alert_events (feedback_correct)
    WHERE feedback_correct IS NOT NULL;
