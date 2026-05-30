-- V10: AI Workflow Designer — workflow definitions
-- Sprint 6: BPMN visual editor backend
CREATE SCHEMA IF NOT EXISTS ai_workflow;

CREATE TABLE ai_workflow.workflow_definitions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               VARCHAR(50) NOT NULL,
    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    bpmn_xml                TEXT NOT NULL,
    version                 INT NOT NULL DEFAULT 1,
    is_active               BOOLEAN NOT NULL DEFAULT TRUE,
    camunda_deployment_id   VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (tenant_id, name, version)
);

CREATE INDEX idx_wf_defs_tenant_active ON ai_workflow.workflow_definitions (tenant_id, is_active);
CREATE INDEX idx_wf_defs_created_at ON ai_workflow.workflow_definitions (created_at DESC);

-- RLS for tenant isolation
ALTER TABLE ai_workflow.workflow_definitions ENABLE ROW LEVEL SECURITY;
CREATE POLICY workflow_tenant_isolation ON ai_workflow.workflow_definitions
    USING (tenant_id = current_setting('app.current_tenant', TRUE));
