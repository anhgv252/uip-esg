-- M5-4 T10: ISO 37120:2018 City services and quality of life indicators storage
-- Stores annual reports with 15 urban sustainability indicators across 5 categories:
-- Energy (E1, E2), Environment (Env1, Env2, Env3), Transport (T1), Waste (W1), Governance (G1, G2)

CREATE TABLE IF NOT EXISTS esg.iso37120_reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    city_id         TEXT NOT NULL,
    tenant_id       TEXT NOT NULL,
    year            INT NOT NULL CHECK (year >= 2000 AND year <= 2100),
    report_detail   JSONB NOT NULL,  -- full indicator list with values, units, data sources
    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT iso37120_unique_city_year UNIQUE (city_id, tenant_id, year)
);

-- Index for queries by city and tenant
CREATE INDEX IF NOT EXISTS idx_iso37120_city_tenant ON esg.iso37120_reports(city_id, tenant_id);

-- Index for time-series queries
CREATE INDEX IF NOT EXISTS idx_iso37120_year ON esg.iso37120_reports(year DESC);

-- GIN index for JSONB report_detail queries (e.g., filter by indicator availability)
CREATE INDEX IF NOT EXISTS idx_iso37120_report_detail ON esg.iso37120_reports USING GIN(report_detail);

-- Partial index for recent reports (last 5 years)
CREATE INDEX IF NOT EXISTS idx_iso37120_recent 
ON esg.iso37120_reports(city_id, year) 
WHERE year >= EXTRACT(YEAR FROM NOW()) - 5;

-- Comments
COMMENT ON TABLE esg.iso37120_reports IS 'ISO 37120:2018 City services and quality of life indicators (M5-4 T10)';
COMMENT ON COLUMN esg.iso37120_reports.report_detail IS 'JSONB: {indicators: [{code, name, category, value, unit, dataSource, dataAvailable}]}';
COMMENT ON COLUMN esg.iso37120_reports.year IS 'Report year (annual aggregation)';
