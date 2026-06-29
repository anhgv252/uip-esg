-- M5-3 T01: Workflow drafts table for BPMN synthesis
-- Stores generated BPMN workflows pending operator review before deployment

CREATE TABLE IF NOT EXISTS ai.workflow_drafts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id VARCHAR(100) NOT NULL,
    intent VARCHAR(100) NOT NULL,
    bpmn_xml TEXT NOT NULL,
    confidence DOUBLE PRECISION,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_REVIEW',
    requested_by VARCHAR(100),
    approved_by VARCHAR(100),
    rejection_reason TEXT,
    extracted_entities JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    nl_parse_latency_ms BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_confidence CHECK (confidence >= 0.0 AND confidence <= 1.0),
    CONSTRAINT chk_status CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'SIMULATED', 'EXECUTED'))
);

CREATE INDEX idx_workflow_drafts_tenant_status ON ai.workflow_drafts(tenant_id, status);
CREATE INDEX idx_workflow_drafts_intent ON ai.workflow_drafts(intent, created_at DESC);
CREATE INDEX idx_workflow_drafts_created_at ON ai.workflow_drafts(created_at DESC);

COMMENT ON TABLE ai.workflow_drafts IS 'M5-3: AI-generated BPMN workflows awaiting operator approval';
COMMENT ON COLUMN ai.workflow_drafts.status IS 'Workflow lifecycle: PENDING_REVIEW → APPROVED/REJECTED → SIMULATED → EXECUTED';
COMMENT ON COLUMN ai.workflow_drafts.extracted_entities IS 'JSON map of NL entities (zone, threshold, etc.) used in BPMN generation';
COMMENT ON COLUMN ai.workflow_drafts.version IS 'Incremental version for each synthesis attempt (same intent can have multiple versions)';
