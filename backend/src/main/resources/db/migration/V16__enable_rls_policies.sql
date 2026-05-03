-- V16: Enable RLS policies for multi-tenant isolation
-- ADR-010, ADR-023: Zero-downtime RLS migration
-- Prerequisite: V14 (tenant_id columns) + V17 (backfill) must be complete

-- =============================================================================
-- Verify backfill complete — fail fast if any table still has NULL tenant_id
-- =============================================================================
DO $$
DECLARE
    null_count INT;
BEGIN
    -- Environment module
    SELECT COUNT(*) INTO null_count FROM environment.sensors WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: environment.sensors has % NULL tenant_id rows', null_count;
    END IF;

    SELECT COUNT(*) INTO null_count FROM environment.sensor_readings WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: environment.sensor_readings has % NULL tenant_id rows', null_count;
    END IF;

    -- ESG module
    SELECT COUNT(*) INTO null_count FROM esg.clean_metrics WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: esg.clean_metrics has % NULL tenant_id rows', null_count;
    END IF;

    SELECT COUNT(*) INTO null_count FROM esg.reports WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: esg.reports has % NULL tenant_id rows', null_count;
    END IF;

    -- Alerts module
    SELECT COUNT(*) INTO null_count FROM alerts.alert_rules WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: alerts.alert_rules has % NULL tenant_id rows', null_count;
    END IF;

    SELECT COUNT(*) INTO null_count FROM alerts.alert_events WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: alerts.alert_events has % NULL tenant_id rows', null_count;
    END IF;

    -- Traffic module
    SELECT COUNT(*) INTO null_count FROM traffic.traffic_counts WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: traffic.traffic_counts has % NULL tenant_id rows', null_count;
    END IF;

    SELECT COUNT(*) INTO null_count FROM traffic.traffic_incidents WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: traffic.traffic_incidents has % NULL tenant_id rows', null_count;
    END IF;

    -- Citizens module
    SELECT COUNT(*) INTO null_count FROM citizens.buildings WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: citizens.buildings has % NULL tenant_id rows', null_count;
    END IF;

    SELECT COUNT(*) INTO null_count FROM citizens.households WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: citizens.households has % NULL tenant_id rows', null_count;
    END IF;

    SELECT COUNT(*) INTO null_count FROM citizens.citizen_accounts WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: citizens.citizen_accounts has % NULL tenant_id rows', null_count;
    END IF;

    -- Error management
    SELECT COUNT(*) INTO null_count FROM error_mgmt.error_records WHERE tenant_id IS NULL;
    IF null_count > 0 THEN
        RAISE EXCEPTION 'Backfill incomplete: error_mgmt.error_records has % NULL tenant_id rows', null_count;
    END IF;
END $$;

-- =============================================================================
-- Enable RLS on all domain tables
-- =============================================================================

-- Environment module
ALTER TABLE environment.sensors ENABLE ROW LEVEL SECURITY;
ALTER TABLE environment.sensors FORCE ROW LEVEL SECURITY;
-- sensor_readings is a TimescaleDB hypertable; RLS on compressed hypertables requires a DO block
DO $$ BEGIN
    ALTER TABLE environment.sensor_readings ENABLE ROW LEVEL SECURITY;
EXCEPTION WHEN feature_not_supported THEN
    RAISE NOTICE 'RLS not supported on compressed hypertable environment.sensor_readings — skipping';
END; $$;
DO $$ BEGIN
    ALTER TABLE environment.sensor_readings FORCE ROW LEVEL SECURITY;
EXCEPTION WHEN feature_not_supported THEN
    RAISE NOTICE 'FORCE RLS not supported on compressed hypertable environment.sensor_readings — skipping';
END; $$;

-- ESG module
-- clean_metrics is a TimescaleDB hypertable; RLS on compressed hypertables requires a DO block
DO $$ BEGIN
    ALTER TABLE esg.clean_metrics ENABLE ROW LEVEL SECURITY;
EXCEPTION WHEN feature_not_supported THEN
    RAISE NOTICE 'RLS not supported on compressed hypertable esg.clean_metrics — skipping';
END; $$;
DO $$ BEGIN
    ALTER TABLE esg.clean_metrics FORCE ROW LEVEL SECURITY;
EXCEPTION WHEN feature_not_supported THEN
    RAISE NOTICE 'FORCE RLS not supported on compressed hypertable esg.clean_metrics — skipping';
END; $$;
ALTER TABLE esg.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE esg.reports FORCE ROW LEVEL SECURITY;

-- Alerts module
ALTER TABLE alerts.alert_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts.alert_events FORCE ROW LEVEL SECURITY;

-- Traffic module
ALTER TABLE traffic.traffic_counts ENABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_counts FORCE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_incidents ENABLE ROW LEVEL SECURITY;
ALTER TABLE traffic.traffic_incidents FORCE ROW LEVEL SECURITY;

-- Citizens module
ALTER TABLE citizens.buildings ENABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.buildings FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.households ENABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.households FORCE ROW LEVEL SECURITY;
ALTER TABLE citizens.citizen_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE citizens.citizen_accounts FORCE ROW LEVEL SECURITY;

-- Error management
ALTER TABLE error_mgmt.error_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE error_mgmt.error_records FORCE ROW LEVEL SECURITY;

-- =============================================================================
-- Create RLS policies
-- USING (tenant_id = current_setting('app.tenant_id', true))
--   missing_ok=true returns NULL when GUC not set -> policy evaluates FALSE -> 0 rows
-- =============================================================================

-- Environment
CREATE POLICY tenant_isolation ON environment.sensors
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON environment.sensor_readings
    USING (tenant_id = current_setting('app.tenant_id', true));

-- ESG
CREATE POLICY tenant_isolation ON esg.clean_metrics
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON esg.reports
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Alerts
CREATE POLICY tenant_isolation ON alerts.alert_rules
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON alerts.alert_events
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Traffic
CREATE POLICY tenant_isolation ON traffic.traffic_counts
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON traffic.traffic_incidents
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Citizens
CREATE POLICY tenant_isolation ON citizens.buildings
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON citizens.households
    USING (tenant_id = current_setting('app.tenant_id', true));
CREATE POLICY tenant_isolation ON citizens.citizen_accounts
    USING (tenant_id = current_setting('app.tenant_id', true));

-- Error management
CREATE POLICY tenant_isolation ON error_mgmt.error_records
    USING (tenant_id = current_setting('app.tenant_id', true));
