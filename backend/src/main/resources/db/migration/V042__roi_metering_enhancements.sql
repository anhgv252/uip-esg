-- M5-3 T06: ROI Dashboard — Add building_id and token tracking to metering_events
-- Enhancement to support ROI cost breakdown per building

ALTER TABLE billing.metering_events
    ADD COLUMN building_id      VARCHAR(50),
    ADD COLUMN sensor_id        VARCHAR(100),
    ADD COLUMN alert_id         UUID,
    ADD COLUMN token_count      INTEGER DEFAULT 0;

-- Index for per-building ROI queries
CREATE INDEX idx_metering_building_month 
    ON billing.metering_events (building_id, DATE_TRUNC('month', recorded_at));

-- Index for tenant+building queries
CREATE INDEX idx_metering_tenant_building_month 
    ON billing.metering_events (tenant_id, building_id, DATE_TRUNC('month', recorded_at));

COMMENT ON COLUMN billing.metering_events.building_id IS 'Building identifier for per-building ROI calculation';
COMMENT ON COLUMN billing.metering_events.sensor_id IS 'Sensor ID for SENSOR_READING events';
COMMENT ON COLUMN billing.metering_events.alert_id IS 'Alert ID for ALERT_GENERATED events';
COMMENT ON COLUMN billing.metering_events.token_count IS 'AI token count for AI_INFERENCE events';
