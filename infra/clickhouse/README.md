# ClickHouse POC — Sprint MVP3-1

Single-node ClickHouse cho analytics POC. Tier 2 only.

## Quick Start

```bash
cd infra/clickhouse
docker-compose -f docker-compose.poc.yml up -d

# Verify health
curl http://localhost:8123/ping
# Expected: Ok.
```

## Connection

| Property | Value |
|----------|-------|
| HTTP URL | `http://localhost:8123` |
| TCP Port | `9000` |
| Database | `analytics` |
| User | `uip_analytics` |
| Password | `uip_analytics_pwd` |

**JDBC URL:** `jdbc:clickhouse://localhost:9000/analytics`

## Verify Data

```sql
-- Check tables
SHOW TABLES IN analytics;

-- Sample query
SELECT count() FROM analytics.esg_readings;

-- Cross-building rollup test
SELECT tenant_id, building_id, metric_type, sum(sum_value)
FROM analytics.building_hourly_rollup_mv
GROUP BY tenant_id, building_id, metric_type;
```

## Notes

- **POC only** — single-node, không replicated
- Sprint 2 upgrade: 2-node HA (`v3-DevOps-03`)
- analytics-service kết nối qua `ClickHouseConfig.java` (JDBC)
- Schema init tự động từ `schema/V001__create_analytics_schema.sql`
