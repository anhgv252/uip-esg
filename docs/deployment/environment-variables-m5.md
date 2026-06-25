# Environment Variables — MVP5 (ADR-047 RowPolicy)

> Append-only registry for env vars introduced in MVP5. Mirrors the format of
> `docs/deployment/environment-variables.xlsx` (which covers MVP1–MVP4). Update
> BOTH files when promoting an MVP5 var to the master registry.

## analytics-service — ClickHouse RowPolicy (ADR-047)

| Env var | Module | Required prod | Default | Secret / ConfigMap | Notes |
|---|---|---|---|---|---|
| `CLICKHOUSE_POLICY_USER` | analytics-service | yes | `analytics_policy` | ConfigMap | User provisioned by `infrastructure/clickhouse/02-row-policy.sql`. V032 GRANTs SELECT to this user. |
| `CLICKHOUSE_POLICY_PASSWORD` | analytics-service | yes | `changeme` (dev only) | **Secret** | sha256_password. Override in every non-dev env. Rotate per security policy. |
| `CLICKHOUSE_DB` | analytics-service | yes | `analytics` | ConfigMap | Database name. RowPolicy lives on `analytics.esg_readings` + `analytics.sensor_reading_hourly`. |

## Why these are separate from existing CLICKHOUSE_USER

The existing `CLICKHOUSE_USER=default` is the **admin/bootstrap** user (creates
schema, applies V032). The new `CLICKHOUSE_POLICY_USER=analytics_policy` is the
**runtime** user the application connects as in production — it has NO direct
table access, only RowPolicy-restricted SELECT. This separation is the
defense-in-depth boundary: even if app credentials leak, the RowPolicy still
restricts every query to one tenant.

## Compose wiring

`infrastructure/docker-compose.yml` mounts:
```
./clickhouse/init.sql                                            → 01-init.sql
./clickhouse/02-row-policy.sql                                   → 02-row-policy.sql
../infra/clickhouse/schema/V032__row_policy_tenant_iso.sql       → 03-v032-row-policy.sql
```
Scripts run alphabetically: schema → user → policy+GRANT.

## Rotation

`CLICKHOUSE_POLICY_PASSWORD` rotation:
1. Generate new hash: `echo -n 'NEWPASS' | sha256sum`
2. `ALTER USER analytics_policy IDENTIFIED WITH sha256_password BY 'NEWPASS'`
3. Update Secret + rolling restart analytics-service pods

See `docs/mvp5/deployment/m5-1-row-policy-smoke-test.md` for the full verification flow.
