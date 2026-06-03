-- B2-3: Structural monitoring — alert rules + building_id correlation column
-- Per TCVN 9386:2012 + ISO 4866 thresholds

-- 1. Add building_id to alert_events for building-level safety score filtering
--    Populated by StructuralAlertConsumer (B2-5) when structural alerts arrive.
ALTER TABLE alerts.alert_events
    ADD COLUMN IF NOT EXISTS building_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_alert_events_building_module
    ON alerts.alert_events (building_id, module)
    WHERE building_id IS NOT NULL;

-- RLS: building_id follows same tenant_isolation policy as alert_events (inherited from parent policy)

-- 2. Seed structural alert rules (WARNING + CRITICAL per sensor type)
INSERT INTO alerts.alert_rules (rule_name, module, measure_type, operator, threshold, severity, cooldown_minutes, tenant_id)
VALUES
    ('Structural Vibration Warning',  'STRUCTURAL', 'VIBRATION', '>',  10.0, 'WARNING',  1, 'default'),
    ('Structural Vibration Critical', 'STRUCTURAL', 'VIBRATION', '>',  50.0, 'CRITICAL', 1, 'default'),
    ('Structural Tilt Warning',       'STRUCTURAL', 'TILT',      '>',   3.0, 'WARNING',  1, 'default'),
    ('Structural Tilt Critical',      'STRUCTURAL', 'TILT',      '>',  10.0, 'CRITICAL', 1, 'default'),
    ('Structural Crack Warning',      'STRUCTURAL', 'CRACK',     '>',   0.3, 'WARNING',  1, 'default'),
    ('Structural Crack Critical',     'STRUCTURAL', 'CRACK',     '>',   2.0, 'CRITICAL', 1, 'default')
ON CONFLICT DO NOTHING;
