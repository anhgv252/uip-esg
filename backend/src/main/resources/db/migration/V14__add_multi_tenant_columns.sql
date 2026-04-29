-- V14: Multi-tenancy foundation — tenant_id + LTREE location_path
-- ADR-010: Multi-Tenant Isolation Strategy (Accepted, 2026-04-28)
--
-- Rules (per ADR-010 §2):
--   - tenant_id: added to ALL domain tables
--   - location_path (LTREE): added ONLY to metadata/entity tables (sensors, buildings)
--     NOT added to time-series or event tables (sensor_readings, clean_metrics, etc.)
--     Time-series: use JOIN via sensor_id/device_id when location context needed.
--
-- T1 deployments: tenant_id = 'default', location_path = 'city.district.cluster.building'
-- T2+: RLS policies applied in MVP2-07a, TenantContext (SET LOCAL) in MVP2-07b

CREATE EXTENSION IF NOT EXISTS ltree;

-- =============================================================================
-- environment module
-- =============================================================================

-- sensors: metadata/entity table — has location_path
ALTER TABLE environment.sensors
    ADD COLUMN tenant_id      TEXT  NOT NULL DEFAULT 'default',
    ADD COLUMN location_path  LTREE NOT NULL DEFAULT 'city.district.cluster.building';

-- sensor_readings: time-series table — tenant_id only, NO location_path
ALTER TABLE environment.sensor_readings
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- =============================================================================
-- esg module
-- =============================================================================

-- clean_metrics: time-series table — tenant_id only, NO location_path
ALTER TABLE esg.clean_metrics
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- reports: config/output table — tenant_id only
ALTER TABLE esg.reports
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- =============================================================================
-- alerts module
-- =============================================================================

-- alert_rules: config table — tenant_id only
ALTER TABLE alerts.alert_rules
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- alert_events: event log table — tenant_id only, NO location_path
-- Location context available via JOIN on environment.sensors or citizens.buildings
ALTER TABLE alerts.alert_events
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- =============================================================================
-- traffic module
-- =============================================================================

-- traffic_counts: time-series table — tenant_id only, NO location_path
ALTER TABLE traffic.traffic_counts
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- incidents: event log table — tenant_id only, NO location_path
ALTER TABLE traffic.incidents
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- =============================================================================
-- citizens module
-- =============================================================================

-- buildings: metadata/entity table — has location_path (hierarchy anchor)
ALTER TABLE citizens.buildings
    ADD COLUMN tenant_id      TEXT  NOT NULL DEFAULT 'default',
    ADD COLUMN location_path  LTREE NOT NULL DEFAULT 'city.district.cluster.building';

-- households: scoped to tenant, location derived from building FK
ALTER TABLE citizens.households
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- citizen_accounts: tenant-scoped identity
ALTER TABLE citizens.citizen_accounts
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- =============================================================================
-- error_mgmt module
-- =============================================================================

ALTER TABLE error_mgmt.error_records
    ADD COLUMN tenant_id TEXT NOT NULL DEFAULT 'default';

-- =============================================================================
-- Indexes
-- =============================================================================

-- Metadata tables: GIST for LTREE subtree queries + B-tree on tenant_id
CREATE INDEX idx_sensors_location   ON environment.sensors USING GIST (location_path);
CREATE INDEX idx_sensors_tenant     ON environment.sensors (tenant_id);
CREATE INDEX idx_buildings_location ON citizens.buildings  USING GIST (location_path);
CREATE INDEX idx_buildings_tenant   ON citizens.buildings  (tenant_id);

-- Time-series tables: composite B-tree on (tenant_id, timestamp DESC) — NO GIST
CREATE INDEX idx_sensor_readings_tenant_ts ON environment.sensor_readings (tenant_id, timestamp DESC);
CREATE INDEX idx_clean_metrics_tenant_ts   ON esg.clean_metrics           (tenant_id, timestamp DESC);
CREATE INDEX idx_traffic_counts_tenant_ts  ON traffic.traffic_counts      (tenant_id, timestamp DESC);

-- Event/operational tables: B-tree on tenant_id
CREATE INDEX idx_alert_events_tenant ON alerts.alert_events (tenant_id);
CREATE INDEX idx_incidents_tenant    ON traffic.incidents    (tenant_id);

-- Config tables: B-tree on tenant_id
CREATE INDEX idx_alert_rules_tenant       ON alerts.alert_rules        (tenant_id);
CREATE INDEX idx_citizen_accounts_tenant  ON citizens.citizen_accounts (tenant_id);
CREATE INDEX idx_households_tenant        ON citizens.households        (tenant_id);
CREATE INDEX idx_esg_reports_tenant       ON esg.reports               (tenant_id);
CREATE INDEX idx_error_records_tenant     ON error_mgmt.error_records  (tenant_id);

-- =============================================================================
-- Column comments
-- =============================================================================

COMMENT ON COLUMN environment.sensors.tenant_id         IS 'Tenant identifier. T1=default. T2+: enforced by RLS (MVP2-07a).';
COMMENT ON COLUMN environment.sensors.location_path      IS 'LTREE path: city.district.cluster.building[.floor.zone]. Metadata anchor for sensor location.';
COMMENT ON COLUMN environment.sensor_readings.tenant_id  IS 'Tenant identifier. Location context via JOIN on environment.sensors.location_path.';
COMMENT ON COLUMN citizens.buildings.location_path        IS 'LTREE path for this building in the city hierarchy. Anchor for all household/zone sub-paths.';
