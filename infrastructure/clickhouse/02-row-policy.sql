-- =============================================================================
-- 02-row-policy.sql — ClickHouse Row-Level Policy provisioning (ADR-047)
-- =============================================================================
-- Mounted at /docker-entrypoint-initdb.d/ — runs AFTER init.sql (alphabetical
-- order: init.sql < 02-row-policy.sql). The base schema (esg_readings,
-- sensor_reading_hourly) must exist first.
--
-- This is the USER provisioning side. The policies themselves live in
-- infra/clickhouse/schema/V032__row_policy_tenant_iso.sql (applied by the
-- analytics-service Flyway/runner OR by mounting V032 here too — see runbook).
--
-- Provisioning only the user here so a fresh container can be connected to
-- as analytics_policy even before V032 is applied. V032 then GRANTs SELECT
-- and creates the RESTRICTIVE policies.
-- =============================================================================

-- analytics_policy — application connects as this user. RowPolicy (V032)
-- restricts every SELECT to rows matching currentSetting('tenant_id').
-- Password from CLICKHOUSE_POLICY_PASSWORD env (DevOps sets it).
CREATE USER IF NOT EXISTS analytics_policy
    IDENTIFIED WITH sha256_password BY 'changeme';

-- PREREQUISITE (regression fix M5-1-T10): the per-connection session setting
-- 'SQL_tenant_id' is SET by RowPolicyEngine before every query and consumed by
-- the V032 RowPolicy via `getSetting('SQL_tenant_id')`.
--   * The `SQL_` prefix is mandatory for user-defined settings in CH 23.8 / 24.3
--     (the old "arbitrary string settings allowed by default" was removed).
--   * The setting is runtime-only — do NOT declare it in <profiles>/<custom_settings>
--     or in <profiles>/<default>/<SQL_tenant_id>... ; doing so crashes CH 23.8
--     startup with `Couldn't restore Field from dump`. The setting materializes
--     the first time RowPolicyEngine issues SET SQL_tenant_id = '...' on a
--     connection.
--   * If a connection never runs SET, getSetting('SQL_tenant_id') throws
--     Code 115 UNKNOWN_SETTING → fail-CLOSED (no rows leak).
-- The earlier comment claiming "arbitrary string settings are allowed by
-- default" was wrong for CH 23.8.
GRANT SHOW SETTINGS ON * TO analytics_policy;
