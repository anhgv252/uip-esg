# ADR-036: ClickHouse 2-Node HA — ReplicatedMergeTree + Keeper

**Date:** 2026-06-04
**Status:** Accepted
**Deciders:** SA, DevOps Lead
**Sprint:** MVP3-8

---

## Status

Accepted — implemented in S8-OPS01, deployed to dev environment.

## Context

Sprint 7 ran a single-node ClickHouse instance (`clickhouse-server`) for the `analytics` database. The Enterprise Architecture Assessment conducted in late Sprint 7 flagged this as **P0 risk**: a single ClickHouse node is an unacceptable SPOF for the platform's primary analytics query path used by the ESG Dashboard, Operations Center, and automated ESG reports.

Specific risks identified:
- Node restart during peak load causes full analytics outage
- OOM kill (ClickHouse is memory-intensive) has no failover path
- No data durability guarantee if disk fails on the single node
- City authority SLA requires 99.5% analytics availability

Sprint 8 task S8-OPS01 was assigned to resolve this risk by implementing a 2-node HA cluster with synchronous replication. The existing `infrastructure/docker-compose.yml` single-node setup must remain intact and functional for Tier 1 / local-dev environments.

During implementation, the SA code review (pre-deploy gate) identified one **C-1 Critical** issue in `keeper-config.xml`:
- Invalid `<tip>` block present in XML — not a recognized ClickHouse Keeper element, would cause keeper startup failure
- Missing `<server_id>` element in keeper config — required to identify which keeper node is "self"

Both were fixed before the HA compose was merged.

---

## Decision

Deploy ClickHouse in **2-node cluster mode** using **ClickHouse Keeper** (built-in Raft-based coordination) with **ReplicatedReplacingMergeTree** tables on cluster `uip_cluster`.

### Architecture

```
                ┌─────────────────────────────────┐
                │   docker-compose.ha.yml (HA)     │
                │                                  │
                │  ┌─────────────────────────────┐ │
                │  │   clickhouse-keeper          │ │
                │  │   (port 9181, raft 9234)     │ │
                │  │   Raft quorum: single node   │ │
                │  └────────────┬────────────────┘ │
                │               │ Keeper API        │
                │  ┌────────────▼──────────────┐   │
                │  │  clickhouse-01 (port 8123) │   │
                │  │  ReplicatedReplacingMerge  │   │
                │  │  Tree on uip_cluster       │◄──┤──── Analytics queries
                │  └────────────────────────────┘   │       (load-balanced)
                │  ┌────────────────────────────┐   │
                │  │  clickhouse-02 (port 8124) │   │
                │  │  Replica of clickhouse-01  │◄──┤────
                │  └────────────────────────────┘   │
                └─────────────────────────────────────┘

  docker-compose.yml (single-node, unchanged):
  ┌──────────────────────────┐
  │  clickhouse (port 8123)  │  MergeTree tables — no replication
  └──────────────────────────┘
```

### Key Configuration Choices

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Coordination | ClickHouse Keeper (built-in) | No ZooKeeper dependency; same process binary |
| Table engine | ReplicatedReplacingMergeTree | Deduplication + replication; matches existing ReplacingMergeTree semantics |
| Cluster name | `uip_cluster` | Consistent cluster macro `{cluster}` in config |
| Replication lag | <5s (verified in dev) | Async replication with Keeper sync |
| Keeper memory | ~128 MB per node on 2 GB host | Acceptable; Keeper is lightweight vs ZooKeeper |
| Shard count | 1 shard, 2 replicas | HA focus; horizontal sharding deferred to production scale-out |

### Table Engine Migration

```sql
-- HA environment (docker-compose.ha.yml): ReplicatedReplacingMergeTree
CREATE TABLE IF NOT EXISTS analytics.esg_readings ON CLUSTER uip_cluster
(
    tenant_id       String,
    building_id     String,
    metric_type     String,
    recorded_at     DateTime64(3, 'UTC'),
    value           Float64,
    unit            String,
    building_name   LowCardinality(String) DEFAULT '',
    district        LowCardinality(String) DEFAULT '',
    category        LowCardinality(String) DEFAULT ''
)
ENGINE = ReplicatedReplacingMergeTree(
    '/clickhouse/tables/{shard}/analytics/esg_readings',
    '{replica}'
)
PARTITION BY toYYYYMM(recorded_at)
ORDER BY (tenant_id, building_id, metric_type, recorded_at, district)
TTL recorded_at + INTERVAL 2 YEAR;

-- Single-node environment (docker-compose.yml): unchanged MergeTree
-- No migration required for Tier 1 local-dev.
```

### Failover Behavior

- **node-1 down**: queries auto-route to node-2 via cluster macro; Flink ClickHouse sink retries on connection failure; replication state preserved by Keeper
- **node-2 down**: node-1 continues as active; Keeper maintains quorum (single Keeper node in dev — acceptable for non-production)
- **Keeper down**: both ClickHouse nodes continue serving reads; writes proceed but replication pauses until Keeper recovers

### Backward Compatibility

The HA configuration lives exclusively in `infrastructure/docker-compose.ha.yml`. The original `infrastructure/docker-compose.yml` single-node setup is **not modified**. This preserves:
- Local developer environments (single node is sufficient)
- CI test runs (single node, faster startup)
- Tier 1 on-premise deployments

---

## Alternatives Considered

### Alternative 1: ZooKeeper-based ClickHouse Replication
**Rejected.** ZooKeeper adds a third service dependency, additional JVM memory overhead (~512 MB), and operational complexity. ClickHouse Keeper is built into the ClickHouse binary (no separate process for 2-node setups), performs comparably, and is the ClickHouse-recommended path for new deployments since v22.4.

### Alternative 2: ClickHouse Keeper on Each Data Node (Embedded)
**Rejected for dev.** Running Keeper embedded on each of the 2 data nodes would form a 2-node Raft quorum, which cannot achieve majority on single-node failure (2-node Raft requires both nodes for quorum). A separate Keeper service provides a 1+2 topology with proper fault isolation. Deferred to production: production will use 3-node Keeper ensemble.

### Alternative 3: Read Replica Only (No ReplicatedMergeTree)
**Rejected.** A read replica created via filesystem snapshot does not provide real-time replication or automatic failover. ReplicatedMergeTree is the native ClickHouse HA mechanism and the only approach that supports transparent failover via cluster macros.

---

## Consequences

### Positive
- Eliminates P0 single-node SPOF for analytics queries
- ReplicatedReplacingMergeTree provides block-level data durability across 2 nodes
- ClickHouse Keeper is operationally simpler than ZooKeeper (same binary, no JVM)
- Backward compatible: single-node dev flow is unaffected

### Negative / Risks
- 3× memory overhead vs single-node: keeper (~128 MB) + node-1 (~512 MB) + node-2 (~512 MB) on 2 GB host is tight; production nodes must have ≥4 GB RAM
- HA compose requires volume re-create on first deploy: if a pre-existing single-node ClickHouse volume exists, `docker compose -f docker-compose.ha.yml up` will fail on schema init; workaround: `docker volume rm clickhouse-01-data clickhouse-02-data` before first HA deploy
- `init.sql` manual apply: volume initialization via entrypoint scripts is unreliable with ReplicatedMergeTree — `ON CLUSTER` DDL must be run after both nodes are healthy

### Neutral
- Flink ClickHouse sink requires no changes: writes to node-1 which replicates to node-2 automatically
- Analytics-service query URL: point to node-1 (port 8123) as primary; node-2 (port 8124) as fallback in connection string

---

## Implementation Notes

**Story:** S8-OPS01
**Sprint:** MVP3-8
**Owner:** DevOps Lead
**Files:**
- `infrastructure/docker-compose.ha.yml` — HA compose: `clickhouse-keeper`, `clickhouse-01`, `clickhouse-02` containers
- `infrastructure/clickhouse/keeper-config.xml` — Keeper Raft config (fixed: removed `<tip>`, added `<server_id>`)
- `infrastructure/clickhouse/ha-init.sql` — Schema init with `ON CLUSTER uip_cluster` DDL
- `infrastructure/clickhouse/config-01.xml`, `config-02.xml` — Per-node macros `{shard}`, `{replica}`

**Verification:**
```bash
# Check replication status after startup
docker exec clickhouse-01 clickhouse-client \
  --query "SELECT * FROM system.replicas WHERE is_readonly = 0"

# Verify both nodes in cluster
docker exec clickhouse-01 clickhouse-client \
  --query "SELECT host_name, port FROM system.clusters WHERE cluster='uip_cluster'"
```
