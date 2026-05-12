# UIP Extracted Services (Tier 2)

This directory contains microservices extracted from the monolith for **Tier 2 deployments** (Building Cluster, 5–20 buildings).

**Tier 1 customers** (single building) continue using `../backend/` monolith only.

## Services

| Service | Status | Story | Sprint |
|---------|--------|-------|--------|
| `analytics-service/` | `../analytics-service/` (root, pending move) | v3-EXT-01 | MVP3-1 |
| `iot-ingestion-service/` | Planned | v3-EXT-06 | MVP3-4 |

## Deployment

```yaml
# values-tier2.yaml — enables extracted services
uip:
  capabilities:
    analytics-external: "true"        # analytics-service handles OLAP queries
    iot-ingestion-external: "true"    # iot-ingestion-service handles BMS (Sprint 5)
```

`matchIfMissing = true` on all `@ConditionalOnProperty` annotations ensures Tier 1 monolith is unchanged when flags are not set.

## analytics-service Current Location

Currently at `../analytics-service/` (root level). Full migration to `applications/analytics-service/` pending v3-EXT-01 completion (after docker-compose and CI path updates).
