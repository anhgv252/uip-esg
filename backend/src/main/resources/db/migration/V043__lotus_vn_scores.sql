-- M5-4 T06: LOTUS VN Green Building Certification Score storage
-- Stores calculated scores for LOTUS VN certification (Certified/Silver/Gold/Platinum)
-- Score breakdown: Energy (EN:40), Water (WA:20), IEQ (20), Materials (MA:10), Site (ST:10)

CREATE TABLE IF NOT EXISTS esg.lotus_vn_scores (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    building_id         TEXT NOT NULL,
    tenant_id           TEXT NOT NULL,
    period              TEXT NOT NULL,  -- YYYY-MM format
    total_score         INT NOT NULL CHECK (total_score >= 0 AND total_score <= 100),
    certification_level TEXT NOT NULL CHECK (certification_level IN ('PLATINUM', 'GOLD', 'SILVER', 'CERTIFIED', 'NOT_CERTIFIED')),
    score_detail        JSONB NOT NULL,  -- full category breakdown and indicators
    calculated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    CONSTRAINT lotus_unique_building_period UNIQUE (building_id, tenant_id, period)
);

-- Index for queries by building and tenant
CREATE INDEX IF NOT EXISTS idx_lotus_building_tenant ON esg.lotus_vn_scores(building_id, tenant_id);

-- Index for queries by certification level
CREATE INDEX IF NOT EXISTS idx_lotus_level ON esg.lotus_vn_scores(certification_level);

-- Index for time-series queries
CREATE INDEX IF NOT EXISTS idx_lotus_period ON esg.lotus_vn_scores(period);

-- Partial index for high-performing buildings (Gold/Platinum)
CREATE INDEX IF NOT EXISTS idx_lotus_high_performers 
ON esg.lotus_vn_scores(building_id, certification_level) 
WHERE certification_level IN ('GOLD', 'PLATINUM');

-- GIN index for JSONB score_detail queries
CREATE INDEX IF NOT EXISTS idx_lotus_score_detail ON esg.lotus_vn_scores USING GIN(score_detail);

-- Comments
COMMENT ON TABLE esg.lotus_vn_scores IS 'LOTUS VN Green Building certification scores (M5-4 T06)';
COMMENT ON COLUMN esg.lotus_vn_scores.score_detail IS 'JSONB: {energyScore, waterScore, ieqScore, materialsScore, siteScore, indicators[]}';
COMMENT ON COLUMN esg.lotus_vn_scores.certification_level IS 'Platinum ≥75, Gold ≥60, Silver ≥50, Certified ≥40, NOT_CERTIFIED <40';
