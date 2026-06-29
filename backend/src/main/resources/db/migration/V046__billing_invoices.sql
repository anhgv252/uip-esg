-- M5-4 T02: Invoice Auto-Generation — Invoices Table
-- Generated monthly from billing.monthly_usage aggregates

CREATE TABLE IF NOT EXISTS billing.invoices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         VARCHAR(100) NOT NULL,
    invoice_number    VARCHAR(50) NOT NULL UNIQUE,       -- Format: INV-2026-06-{tenantId}-001
    billing_period    VARCHAR(7) NOT NULL,               -- YYYY-MM
    subtotal_vnd      BIGINT NOT NULL,
    tax_vnd           BIGINT DEFAULT 0,                  -- 10% VAT for Vietnam
    total_vnd         BIGINT NOT NULL,
    status            VARCHAR(50) NOT NULL DEFAULT 'GENERATED',  -- GENERATED, SENT, PAID, DISPUTED
    line_items        JSONB,                             -- Breakdown per building (array of {buildingId, baseFee, aiOverage, total})
    pdf_path          VARCHAR(500),                      -- Path to generated PDF (stub for now)
    generated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    due_date          DATE,
    paid_at           TIMESTAMPTZ,
    notes             TEXT
);

CREATE INDEX idx_invoices_tenant_period 
    ON billing.invoices(tenant_id, billing_period DESC);

CREATE INDEX idx_invoices_status 
    ON billing.invoices(status, generated_at DESC);

CREATE INDEX idx_invoices_number 
    ON billing.invoices(invoice_number);

COMMENT ON TABLE billing.invoices IS 'M5-4 T02: Monthly invoices auto-generated from monthly_usage aggregates';
COMMENT ON COLUMN billing.invoices.invoice_number IS 'Unique invoice identifier: INV-YYYY-MM-{tenantId}-{seq}';
COMMENT ON COLUMN billing.invoices.status IS 'Invoice lifecycle: GENERATED → SENT → PAID (or DISPUTED)';
COMMENT ON COLUMN billing.invoices.line_items IS 'JSON array: [{buildingId, baseFee, aiOverage, totalCost, sensors, aiTokens}]';
COMMENT ON COLUMN billing.invoices.pdf_path IS 'Path to PDF in MinIO/S3 (stub: returns HTML for now)';
