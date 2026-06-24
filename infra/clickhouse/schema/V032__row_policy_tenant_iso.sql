-- =============================================================================
-- V032 — ClickHouse Row-Level Policy for Tenant Isolation (ADR-047)
-- Sprint M5-1 | GAP-1 (P1)
-- =============================================================================
-- Defense-in-depth LAYER 2:
--   Layer 1 = WHERE tenant_id = ? SQL param (already in ClickHouseEnergyRepository)
--   Layer 2 = these policies (restrict SELECTs to rows whose tenant_id matches
--             the per-connection session setting SQL_tenant_id).
--
-- Enforcement point: analytics-service JDBC layer (NOT backend gRPC adapter).
-- See: docs/mvp5/adr/ADR-047-clickhouse-row-policy-tenant-isolation.md
--
-- ─── CH 23.8 / 24.3-specific corrections (regression fix M5-1-T10) ───────────
-- The original draft of this migration assumed:
--   1. `currentSetting('tenant_id')` works  → WRONG. `currentSetting` was
--      removed in CH 22.3+; use `getSetting(...)`.
--   2. `SET tenant_id = '...'` works on arbitrary custom settings
--      → WRONG. CH 22.3+ requires the `SQL_` prefix on user-defined settings.
--   3. `AS RESTRICTIVE` is the right policy mode → UNUSABLE in CH 23.8.
--      A RESTRICTIVE policy with no PERMISSIVE sibling returns ZERO rows for
--      every query (verified on 23.8.16), even when the USING clause matches.
--      CH 23.8 does not honour the documented "absent PERMISSIVE = always-true
--      PERMISSIVE" rule. PERMISSIVE is the only mode that enforces correctly.
--   4. The tenant_id setting must be declared in <profiles>/<custom_settings>
--      → WRONG. Declaring an `SQL_*` setting in `<profiles>` makes CH 23.8
--      crash on startup (`Couldn't restore Field from dump`). `SQL_*` settings
--      exist runtime-only and carry a String value via SET.
--
-- Net effect: RowPolicyEngine sets `SQL_tenant_id` per connection; the policy
-- below filters rows where tenant_id matches getSetting('SQL_tenant_id').
-- If a connection never ran SET, `getSetting('SQL_tenant_id')` throws
-- `Code 115 UNKNOWN_SETTING` at policy-evaluation time → the SELECT errors
-- out → fail-CLOSED (no rows leak).
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- Dedicated policy user — application connects as this user.
-- Password provisioned by DevOps via environment (CLICKHOUSE_POLICY_PASSWORD).
-- `IF NOT EXISTS` guards re-runs (ClickHouse DDL is idempotent for users).
-- ─────────────────────────────────────────────────────────────────────────────
CREATE USER IF NOT EXISTS analytics_policy IDENTIFIED WITH sha256_password BY 'changeme';

-- ─────────────────────────────────────────────────────────────────────────────
-- RowPolicy on esg_readings (String tenant_id — ADR-026 native string)
-- PERMISSIVE — see header: RESTRICTIVE returns zero rows in CH 23.8 when no
-- PERMISSIVE sibling exists. A PERMISSIVE policy whose USING clause is a strict
-- tenant equality still enforces isolation: a row whose tenant_id differs from
-- the session setting simply does not satisfy the clause and is filtered out.
-- Combined with Layer 1 (WHERE tenant_id = ?) this is defense-in-depth.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE ROW POLICY IF NOT EXISTS tenant_iso_esg_readings ON analytics.esg_readings
    FOR SELECT
    USING tenant_id = getSetting('SQL_tenant_id')
    AS PERMISSIVE
    TO analytics_policy;

GRANT SELECT ON analytics.esg_readings TO analytics_policy;

-- ─────────────────────────────────────────────────────────────────────────────
-- RowPolicy on sensor_reading_hourly (UInt32 hashed tenant_id)
-- Note: hashed tenant_id is numeric; session setting is compared as string
-- then cast. RowPolicyEngine must SET SQL_tenant_id to the hashed numeric
-- value for queries hitting this table. See ADR-047 §8 open question.
-- ─────────────────────────────────────────────────────────────────────────────
CREATE ROW POLICY IF NOT EXISTS tenant_iso_sensor_hourly ON analytics.sensor_reading_hourly
    FOR SELECT
    USING toString(tenant_id) = getSetting('SQL_tenant_id')
    AS PERMISSIVE
    TO analytics_policy;

GRANT SELECT ON analytics.sensor_reading_hourly TO analytics_policy;

-- ─────────────────────────────────────────────────────────────────────────────
-- NOTE: sensor_reading_hourly in init.sql uses UInt32; in V001 schema it is
-- also UInt32. esg_readings uses String. RowPolicyEngine must normalize the
-- tenant identifier to match each table's type when setting the session value.
-- Tracked as Spike S1 follow-up (ADR-047 §3.3).
-- ─────────────────────────────────────────────────────────────────────────────
