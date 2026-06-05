# ADR-037: Kafka 3-Broker KRaft — Quorum Replication

**Date:** 2026-06-04
**Status:** Accepted
**Deciders:** SA, DevOps Lead, Backend Lead
**Sprint:** MVP3-8

---

## Status

Accepted — implemented in S8-OPS02, deployed to dev environment.

## Context

Sprint 7 ran Kafka in single-broker KRaft mode (`kafka-1` only, no ZooKeeper). This architecture was sufficient for Sprint 7's throughput targets but introduced two key risks:

1. **Single-broker SPOF**: broker restart or OOM causes full message loss window for all producers (Flink, IoT ingestion service). In-flight messages not yet committed are lost; Flink consumers recover from checkpoint but producers lose the interval.
2. **No partition replication**: all UIP topics (`esg-sensor-events`, `ngsi_ld_esg`, `alert-events`) have `replication.factor=1`. A broker disk failure causes permanent data loss for any committed-but-not-consumed messages.

The Enterprise Architecture Assessment flagged Kafka replication as **P1 risk** (analytics pipeline data loss scenario). Sprint 8 task S8-OPS02 was assigned to scale to 3 brokers.

Sprint 7 already adopted **KRaft mode** (no ZooKeeper) — this decision is retained. KRaft simplifies the broker count increase: there is no ZooKeeper ensemble to maintain separately; the KRaft controller role is distributed across all 3 brokers.

During the S8-OPS02 implementation, the SA pre-deploy code review identified a **parsing error** in the partition reassignment script: the script iterated `job['id']` against the Kafka admin API response, which uses the key `partition_id` for reassignment results. The field name was corrected before the deploy runbook was finalized.

---

## Decision

Scale to a **3-broker KRaft cluster** (`kafka-1`, `kafka-2`, `kafka-3`) with **replication.factor=3** and **min.insync.replicas=2** on all UIP topics. Maintain KRaft-only operation — no ZooKeeper. Deploy 3-broker configuration via a dedicated HA compose file (`docker-compose.ha.yml`).

### Architecture

```
                ┌───────────────────────────────────────────┐
                │         KRaft Quorum (3 voters)           │
                │                                           │
                │  kafka-1  ←──── controller vote ────►  kafka-2  │
                │     │                                     │     │
                │     └──────── controller vote ──────► kafka-3  │
                │                                               │
                │  KAFKA_NODE_ID: 1          KAFKA_NODE_ID: 2    │
                │  controller.quorum.voters: 1@kafka-1:9093,     │
                │                            2@kafka-2:9093,     │
                │                            3@kafka-3:9093      │
                └───────────────────────────────────────────────┘

  Topic: esg-sensor-events
  ┌──────────────────────────────────────────────────┐
  │  Partition 0: Leader=kafka-1, Replicas=[1,2,3]   │
  │  Partition 1: Leader=kafka-2, Replicas=[2,3,1]   │
  │  Partition 2: Leader=kafka-3, Replicas=[3,1,2]   │
  │  replication.factor=3, min.insync.replicas=2      │
  └──────────────────────────────────────────────────┘
```

### KRaft Configuration per Broker

Each broker acts as both `broker` and `controller` (combined mode, appropriate for dev/staging 3-node):

```properties
# kafka-1
KAFKA_NODE_ID=1
KAFKA_PROCESS_ROLES=broker,controller
KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER
KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3
KAFKA_DEFAULT_REPLICATION_FACTOR=3
KAFKA_MIN_INSYNC_REPLICAS=2
```

Each of `kafka-2` and `kafka-3` uses the same template with their respective `KAFKA_NODE_ID` value.

### Topic Configuration

```bash
# Applied via init script on compose startup:
kafka-topics.sh --bootstrap-server kafka-1:9092 \
  --create --if-not-exists \
  --topic esg-sensor-events \
  --partitions 3 \
  --replication-factor 3 \
  --config min.insync.replicas=2

kafka-topics.sh --bootstrap-server kafka-1:9092 \
  --create --if-not-exists \
  --topic ngsi_ld_esg \
  --partitions 3 \
  --replication-factor 3 \
  --config min.insync.replicas=2

kafka-topics.sh --bootstrap-server kafka-1:9092 \
  --create --if-not-exists \
  --topic alert-events \
  --partitions 3 \
  --replication-factor 3 \
  --config min.insync.replicas=2
```

### Failover Behavior

| Scenario | Behavior |
|----------|----------|
| 1 broker down (e.g., kafka-2) | ISR drops to 2; producers continue with min.insync.replicas=2; controller election if kafka-2 was active controller (KRaft timeout ~10s) |
| 2 brokers down | ISR=1 < min.insync.replicas=2; producers receive `NotEnoughReplicas`; writes blocked until a broker recovers |
| Flink consumer during rebalance | Consumer restarts from last Kafka checkpoint; brief consumer lag (~seconds); no data loss |
| IoT ingestion service during rebalance | Retries with exponential backoff (existing producer retry config); messages buffered in producer |

### Rolling Migration Path

For environments upgrading from single-broker:
1. Add `kafka-2` and `kafka-3` to compose with the same `KAFKA_CLUSTER_ID` as `kafka-1`
2. Start `kafka-2` and `kafka-3` — they join the KRaft quorum automatically
3. Run partition reassignment to distribute existing partitions: `kafka-reassign-partitions.sh --reassignment-json-file reassign.json --execute`
4. Verify ISR=3 on all partitions: `kafka-topics.sh --describe --topic esg-sensor-events`
5. Update topic configs to set `min.insync.replicas=2`

---

## Alternatives Considered

### Alternative 1: ZooKeeper-based Kafka Cluster
**Rejected.** ZooKeeper was removed from Kafka's supported coordination path in Kafka 3.x (KIP-500 / KIP-833). Sprint 7 already adopted KRaft, which eliminates the ZooKeeper dependency and reduces the operational footprint. Reverting to ZooKeeper would add a 3–5 node ZooKeeper ensemble, additional memory, and a separate failure domain. KRaft is the strategic direction.

### Alternative 2: 2-Broker KRaft with replication.factor=2
**Rejected.** A 2-node KRaft cluster cannot achieve controller quorum majority on single-node failure (Raft requires N/2+1 votes; 2 nodes need both for quorum). With 3 brokers, 1 failure still leaves a functioning quorum of 2. Additionally, `min.insync.replicas=2` with `replication.factor=2` blocks writes on any single broker failure, providing no durability improvement in the failure scenario. 3 brokers is the minimum viable HA configuration for Kafka KRaft.

### Alternative 3: Redpanda as Kafka-compatible Replacement
**Rejected.** Redpanda is already used for connect (`redpanda-connect`) for data transformation. Using Redpanda as the primary broker was evaluated in Sprint 4 (ADR-024). The conclusion was to retain Kafka for the core event bus due to Flink's first-class Kafka connector support and operational familiarity of the team. This decision is unchanged.

---

## Consequences

### Positive
- Tolerates 1 broker failure: ISR=2 satisfies min.insync.replicas=2, producers continue uninterrupted
- Data durability: all messages replicated to 3 nodes before producer ack (acks=all implied by min.insync.replicas=2)
- KRaft quorum: 3-voter quorum is the minimum fault-tolerant Raft configuration
- No ZooKeeper dependency: operational simplicity maintained

### Negative / Risks
- 3× broker resource cost: each broker requires ~512 MB JVM heap + OS memory; total Kafka footprint increases from ~512 MB to ~1.5 GB
- Partition rebalancing causes brief consumer lag: Flink Kafka consumer group rebalances on broker join/leave; rebalance window ~10–30s; Flink restarts consumers from last Kafka offset checkpoint (no data loss, but brief processing gap)
- `KAFKA_CLUSTER_ID` must match across all 3 brokers: if kafka-2/3 volumes are created with a different cluster ID, they will fail to join the quorum — volume re-create required

### Neutral
- Flink Kafka consumer recovers from checkpoint on rebalance: this is existing behavior, not a new risk. The rebalance window is bounded by Flink's checkpoint interval (default 30s in dev)
- IoT ingestion service producer retry config (3 retries, 100ms backoff) already handles transient broker unavailability — no config change needed

---

## Implementation Notes

**Story:** S8-OPS02
**Sprint:** MVP3-8
**Owner:** DevOps Lead
**Files:**
- `infrastructure/docker-compose.ha.yml` — 3-broker Kafka section: `kafka-1`, `kafka-2`, `kafka-3`
- `infrastructure/kafka/init-topics.sh` — Topic creation with `replication.factor=3`, `min.insync.replicas=2`
- `infrastructure/scripts/kafka-rebalance.sh` — Partition reassignment script (fixed: `partition_id` field name)

**Verification:**
```bash
# Verify all 3 brokers in ISR for esg-sensor-events
docker exec kafka-1 kafka-topics.sh \
  --bootstrap-server kafka-1:9092 \
  --describe --topic esg-sensor-events

# Expected: ISR: 1,2,3  for all partitions

# Simulate broker-2 failure:
docker stop kafka-2
# Produce messages → should succeed (ISR=2 >= min.insync.replicas=2)
# Consume messages → Flink rebalances, restarts from checkpoint
docker start kafka-2
# ISR recovers to 3 after leader epoch increment
```
