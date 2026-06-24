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

-- allow custom session setting 'tenant_id' to be SET by non-default users.
-- ClickHouse 23.8+: arbitrary string settings are allowed by default; this
-- GRANT is belt-and-suspenders for environments with strict access management.
GRANT SHOW SETTINGS ON * TO analytics_policy;
