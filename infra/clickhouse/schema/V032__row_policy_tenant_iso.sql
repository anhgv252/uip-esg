-- =============================================================================
-- V032 — ClickHouse Row-Level Policy for Tenant Isolation (ADR-047)
-- Sprint M5-1 | GAP-1 (P1)
-- =============================================================================
-- Defense-in-depth LAYER 2:
--   Layer 1 = WHERE tenant_id = ? SQL param (already in ClickHouseEnergyRepository)
--   Layer 2 = these RESTRICTIVE policies (cannot be bypassed by a query missing WHERE)
--
-- Enforcement point: analytics-service JDBC layer (NOT backend gRPC adapter).
-- See: docs/mvp5/adr/ADR-047-clickhouse-row-policy-tenant-isolation.md
--
-- Session setting 'tenant_id' is set per-connection by RowPolicyEngine before
-- each query (SET tenant_id = '<value>') and reset in a finally block.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- Dedicated policy user — application connects as this user.
-- Password provisioned by DevOps via environment (CLICKHOUSE_POLICY_PASSWORD).
-- `IF NOT EXISTS` guards re-runs (ClickHouse DDL is idempotent for users).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE USER IF NOT EXISTS analytics_policy IDENTIFIED WITH sha256_password BY 'changeme';

-- ─────────────────────────────────────────────────────────────────────────────
-- RowPolicy on esg_readings (String tenant_id — ADR-026 native string)
-- RESTRICTIVE: combined with AND against any other filter — query cannot
-- read rows whose tenant_id differs from the session setting.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE ROW POLICY IF NOT EXISTS tenant_iso_esg_readings ON analytics.esg_readings
    FOR SELECT
    USING tenant_id = currentSetting('tenant_id')
    AS RESTRICTIVE
    TO analytics_policy;

GRANT SELECT ON analytics.esg_readings TO analytics_policy;

-- ─────────────────────────────────────────────────────────────────────────────
-- RowPolicy on sensor_reading_hourly (UInt32 hashed tenant_id)
-- Note: hashed tenant_id is numeric; session setting is compared as string
-- then cast. RowPolicyEngine must SET tenant_id to the hashed numeric value
-- for queries hitting this table. See ADR-047 §8 open question.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE ROW POLICY IF NOT EXISTS tenant_iso_sensor_hourly ON analytics.sensor_reading_hourly
    FOR SELECT
    USING toString(tenant_id) = currentSetting('tenant_id')
    AS RESTRICTIVE
    TO analytics_policy;

GRANT SELECT ON analytics.sensor_reading_hourly TO analytics_policy;

-- ─────────────────────────────────────────────────────────────────────────────
-- NOTE: sensor_reading_hourly in init.sql uses UInt32; in V001 schema it is
-- also UInt32. esg_readings uses String. RowPolicyEngine must normalize the
-- tenant identifier to match each table's type when setting the session value.
-- Tracked as Spike S1 follow-up (ADR-047 §3.3).
-- ─────────────────────────────────────────────────────────────────────────────
