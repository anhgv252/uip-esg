-- M5-2: DB-backed BPMN template storage for NL→BPMN feature
-- Allows operators/admins to customise templates without redeployment.
-- Classpath templates are used when no DB override exists for an intent.

CREATE SCHEMA IF NOT EXISTS ai;

CREATE TABLE IF NOT EXISTS ai.nl_bpmn_templates (
    id           BIGSERIAL    PRIMARY KEY,
    intent       VARCHAR(100) NOT NULL UNIQUE,   -- e.g. 'flood_response'
    display_name VARCHAR(200) NOT NULL,
    description  TEXT,
    bpmn_xml     TEXT         NOT NULL,
    version      INTEGER      NOT NULL DEFAULT 1,
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_by   VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE SCHEMA IF NOT EXISTS ai;

CREATE INDEX idx_nl_bpmn_templates_intent_active
    ON ai.nl_bpmn_templates (intent, is_active);

COMMENT ON TABLE ai.nl_bpmn_templates IS
    'Operator-customisable BPMN templates for NL→BPMN intent grounding. '
    'Overrides classpath templates when is_active=true for an intent.';
COMMENT ON COLUMN ai.nl_bpmn_templates.intent IS
    'Workflow intent key — must match one of the 10 MVP4 intents';
COMMENT ON COLUMN ai.nl_bpmn_templates.version IS
    'Monotonically increasing version; increment on each update for audit trail';
COMMENT ON COLUMN ai.nl_bpmn_templates.bpmn_xml IS
    'Full BPMN 2.0 XML with {{placeholder}} syntax for entity substitution';
