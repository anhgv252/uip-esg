# Sprint 8 — Architecture Design: Infrastructure HA + Flink CI/CD

**SA:** UIP Solution Architect | **Date:** 2026-06-03
**Status:** DRAFT — subject to SA review Day 1-2
**Contributors:** SA agent (overlay pattern, ReplicatedReplacingMergeTree, PG manual promotion)

---

## Overlay Strategy — `docker-compose.ha.yml`

**SA khuyến nghị:** Thay vì sửa `docker-compose.yml`, tạo **overlay file** riêng. HA config chỉ active khi explicitly enable.

```bash
# Tier 1 (dev / single-node — không đổi):
docker compose up -d

# Tier 2 (pilot / HA — overlay):
docker compose -f docker-compose.yml -f docker-compose.ha.yml up -d
```

**Ưu điểm:**
- `docker-compose.yml` giữ nguyên → Tier 1 zero-regression
- HA chỉ opt-in qua overlay → không break dev environment
- Rõ ràng separation of concerns

---

## ADR-036: ClickHouse 2-node HA — ReplicatedMergeTree + Keeper

### Context
ClickHouse hiện chạy single-node (`clickhouse` trong docker-compose). Enterprise Assessment (4.73/5.0) flag single-node là P0 risk. Cần HA cho pilot production.

### Decision
Deploy ClickHouse cluster 2 nodes với **ClickHouse Keeper** (embedded, không ZooKeeper). Sử dụng **ReplicatedReplacingMergeTree** thay ReplicatedMergeTree — tự động deduplicate rows, tránh manual `FINAL` queries.

### Topology

```
┌─────────────────────────────────────────────┐
│            ClickHouse Keeper                 │
│         (port 9181, Raft quorum)             │
└──────────┬──────────────────┬───────────────┘
           │                  │
    ┌──────▼──────┐    ┌──────▼──────┐
    │  clickhouse-01 │    │  clickhouse-02 │
    │  (port 8123)  │    │  (port 8124)  │
    │  Replicated   │◄──►│  Replicated   │
    │  MergeTree    │    │  MergeTree    │
    └───────────────┘    └───────────────┘
```

### Docker Compose Addition

```yaml
services:
  clickhouse-keeper:
    image: clickhouse/clickhouse-keeper:23.8-alpine
    ports:
      - "9181:9181"
    volumes:
      - clickhouse-keeper-data:/keeper
      - ./clickhouse/keeper-config.xml:/etc/clickhouse-keeper/keeper_config.xml
    healthcheck:
      test: ["CMD", "clickhouse-keeper", "--probe", "9181"]
      interval: 10s
      timeout: 5s
      retries: 5

  clickhouse-01:
    image: clickhouse/clickhouse-server:23.8-alpine
    ports:
      - "8123:8123"
      - "9000:9000"
    volumes:
      - clickhouse-01-data:/var/lib/clickhouse
      - ./clickhouse/node-01-config.xml:/etc/clickhouse-server/config.xml
    depends_on:
      clickhouse-keeper:
        condition: service_healthy

  clickhouse-02:
    image: clickhouse/clickhouse-server:23.8-alpine
    ports:
      - "8124:8123"
      - "9001:9000"
    volumes:
      - clickhouse-02-data:/var/lib/clickhouse
      - ./clickhouse/node-02-config.xml:/etc/clickhouse-server/config.xml
    depends_on:
      clickhouse-keeper:
        condition: service_healthy
```

### ReplicatedMergeTree Schema Migration

```sql
-- Trên cluster (ON CLUSTER)
CREATE TABLE analytics.sensor_reading_hourly ON CLUSTER '{cluster}' (
    tenant_id        UInt32,
    building_id      UInt32,
    sensor_id        UInt64,
    metric_type      LowCardinality(String),
    ts_hour          DateTime CODEC(DoubleDelta, ZSTD),
    avg_value        Float64 CODEC(Gorilla, ZSTD),
    sum_value        Float64 CODEC(Gorilla, ZSTD),
    sample_count     UInt32
)
ENGINE = ReplicatedReplacingMergeTree(
    '/clickhouse/tables/{shard}/sensor_reading_hourly',
    '{replica}'
    -- ReplacingMergeTree auto-deduplicates by ORDER KEY, keeps latest version
)
PARTITION BY toYYYYMM(ts_hour)
ORDER BY (tenant_id, building_id, sensor_id, metric_type, ts_hour)
TTL ts_hour + INTERVAL 2 YEAR TO DISK 'cold',
    ts_hour + INTERVAL 5 YEAR DELETE;
```

### BACKWARD Compatibility

```yaml
# Tier 1 (single-node — no changes):
CLICKHOUSE_URL: jdbc:clickhouse://clickhouse:8123/analytics
CLICKHOUSE_CLUSTER_ENABLED: "false"

# Tier 2 (HA cluster):
CLICKHOUSE_URL: jdbc:clickhouse://clickhouse-01:8123,clickhouse-02:8123/analytics
CLICKHOUSE_CLUSTER_ENABLED: "true"
```

```java
@ConditionalOnProperty(name = "uip.clickhouse.cluster-enabled", havingValue = "false", matchIfMissing = true)
class SingleNodeClickHouseConfig { ... }

@ConditionalOnProperty(name = "uip.clickhouse.cluster-enabled", havingValue = "true")
class ClusterClickHouseConfig { ... }
```

### Data Migration Strategy

```sql
-- Step 1: Create Replicated table (empty)
CREATE TABLE analytics.sensor_reading_hourly_replicated ON CLUSTER '{cluster}' (...)

-- Step 2: Copy data from old table
INSERT INTO analytics.sensor_reading_hourly_replicated
SELECT * FROM analytics.sensor_reading_hourly

-- Step 3: Rename (atomic swap)
RENAME TABLE analytics.sensor_reading_hourly TO analytics.sensor_reading_hourly_old;
RENAME TABLE analytics.sensor_reading_hourly_replicated TO analytics.sensor_reading_hourly;

-- Step 4: Verify, then drop old
DROP TABLE analytics.sensor_reading_hourly_old;
```

### Failover Strategy
- Application uses both endpoints: `clickhouse-01:8123,clickhouse-02:8123`
- ClickHouse JDBC driver supports native load balancing (`clickhouse.jdbc.load_balancing_policy=ROUND_ROBIN`)
- Node down → driver auto-retries on next endpoint
- Application-level fallback: catch `ClickHouseException` → return cached/empty data

### Risk: Keeper single point of failure
- 1 Keeper = SPOF → **acceptable cho Tier 2 pilot** (Keeper rarely fails, ~100MB memory vs ~1GB ZooKeeper)
- Production: expand to 3 Keepers (documented as future enhancement)
- Mitigation: Keeper stateless restart rejoins automatically

### Data Migration — Pause-and-Migrate Strategy (SA recommendation)
```
Step 0: Flink savepoint (pause writes)
Step 1: Deploy keeper + clickhouse-02
Step 2: CREATE TABLE ... ReplicatedReplacingMergeTree ON CLUSTER
Step 3: INSERT INTO replicated SELECT * FROM old MergeTree table
Step 4: Atomic RENAME swap
Step 5: Resume Flink from savepoint
```
**Rationale:** Flink savepoint đảm bảo zero data gap trong khi ClickHouse table swap xảy ra (typically <30 giây cho vài million rows).

---

## ADR-037: Kafka 3-broker KRaft — Quorum Replication

### Context
Kafka hiện chạy 1 broker KRaft. Single broker = data loss risk. EA Assessment flag là P0. Cần replication cho message durability.

### Decision
Scale lên **3 brokers trong KRaft mode** (no ZooKeeper). Mỗi broker cũng là controller (co-located).

### Topology

```
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   kafka-1     │  │   kafka-2     │  │   kafka-3     │
│ broker+ctrl  │  │ broker+ctrl  │  │ broker+ctrl  │
│ :9092        │  │ :9093        │  │ :9094        │
│ KRaft voter  │  │ KRaft voter  │  │ KRaft voter  │
└──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │                 │                 │
       └────────┬────────┘────────┬────────┘
                │  KRaft Quorum   │
                │  (3 voters)     │
                └─────────────────┘
```

### Docker Compose Configuration

```yaml
services:
  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      CLUSTER_ID: "${KAFKA_CLUSTER_ID}"
    volumes:
      - kafka-1-data:/var/lib/kafka/data

  kafka-2:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9094:9092"
    environment:
      KAFKA_NODE_ID: 2
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
      CLUSTER_ID: "${KAFKA_CLUSTER_ID}"
    volumes:
      - kafka-2-data:/var/lib/kafka/data

  kafka-3:
    image: confluentinc/cp-kafka:7.5.0
    ports:
      - "9095:9092"
    environment:
      KAFKA_NODE_ID: 3
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-3:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka-1:9093,2@kafka-2:9093,3@kafka-3:9093
      CLUSTER_ID: "${KAFKA_CLUSTER_ID}"
    volumes:
      - kafka-3-data:/var/lib/kafka/data
```

### Migration Strategy (Zero Downtime)

```
Step 1: Generate CLUSTER_ID (uuidgen)
Step 2: Format storage on kafka-1 with new cluster ID
Step 3: Start kafka-1 with KRaft quorum voters for 3 nodes
Step 4: Start kafka-2, kafka-3 → quorum formed
Step 5: Reassign existing topic partitions to new brokers
Step 6: Verify replication factor = 3
Step 7: Update application config: bootstrap.servers=kafka-1:9092,kafka-2:9092,kafka-3:9092
```

### Topic Replication Update

```bash
# Generate reassignment plan
kafka-reassign-partitions --bootstrap-server kafka-1:9092 \
  --topics-to-move-json-file topics.json \
  --broker-list "1,2,3" \
  --generate

# Execute reassignment
kafka-reassign-partitions --bootstrap-server kafka-1:9092 \
  --reassignment-json-file reassignment.json \
  --execute

# Verify
kafka-reassign-partitions --bootstrap-server kafka-1:9092 \
  --reassignment-json-file reassignment.json \
  --verify
```

### BACKWARD Compatibility

```yaml
# Tier 1 (single broker — no changes initially):
KAFKA_BOOTSTRAP_SERVERS: kafka-1:9092

# Tier 2 (3 brokers):
KAFKA_BOOTSTRAP_SERVERS: kafka-1:9092,kafka-2:9092,kafka-3:9092
```

### Producer/Consumer Config Updates

```yaml
# Producer:
spring.kafka.producer.properties.acks: all  # Wait for all ISRs
spring.kafka.producer.properties.enable.idempotence: true
spring.kafka.producer.properties.retries: 3

# Consumer:
spring.kafka.consumer.properties.auto.offset.reset: earliest
spring.kafka.consumer.properties.session.timeout.ms: 30000
```

---

## ADR-038: Flink Job CI/CD — Automated Submission Pipeline

### Context
Flink jobs hiện submit manual via curl. EA Assessment flag là P1 automation gap. Cần CI/CD integration.

### Decision
Implement **Makefile targets** cho Flink job lifecycle + CI integration.

### Pipeline Design

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│  Build   │───►│  Upload  │───►│ Savepoint│───►│  Cancel  │───►│ Submit   │
│  JAR     │    │  to Flink│    │  Current │    │  Old Job │    │  New Job │
└──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
```

### Makefile Targets

```makefile
FLINK_URL ?= http://localhost:8081
FLINK_SAVEPOINT_DIR ?= file:///tmp/flink-savepoints
JAR_PATH ?= flink-jobs/build/libs/flink-jobs-*.jar

.PHONY: flink-list flink-submit flink-cancel flink-savepoint flink-deploy

flink-list:
	@echo "=== Running Flink Jobs ==="
	@curl -s $(FLINK_URL)/jobs/overview | jq '.jobs[] | {id, name, state}'

flink-savepoint:
	@echo "=== Taking savepoint ==="
	@JOB_ID=$$(curl -s $(FLINK_URL)/jobs/overview | jq -r '.jobs[] | select(.state=="RUNNING") | .id' | head -1); \
	if [ -n "$$JOB_ID" ]; then \
		curl -s -X PATCH "$(FLINK_URL)/jobs/$$JOB_ID?mode=cancel_with_savepoint" \
			-H "Content-Type: application/json" \
			-d '{"target-directory":"$(FLINK_SAVEPOINT_DIR)"}' | jq .; \
	fi

flink-cancel:
	@echo "=== Cancelling running jobs ==="
	@for JOB_ID in $$(curl -s $(FLINK_URL)/jobs/overview | jq -r '.jobs[] | select(.state=="RUNNING") | .id'); do \
		echo "Cancelling $$JOB_ID..."; \
		curl -s -X PATCH "$(FLINK_URL)/jobs/$$JOB1?mode=cancel"; \
	done

flink-submit:
	@echo "=== Submitting Flink job ==="
	@JAR_FILE=$$(ls $(JAR_PATH) | head -1); \
	JAR_NAME=$$(basename $$JAR_FILE); \
	JAR_ID=$$(curl -s -X POST "$(FLINK_URL)/jars/upload" \
		-H "Expect:" \
		-F "jarfile=@$$JAR_FILE" | jq -r '.filename'); \
	echo "Uploaded: $$JAR_ID"; \
	curl -s -X POST "$(FLINK_URL)/jars/$$(basename $$JAR_ID)/run" | jq .

flink-deploy: flink-savepoint flink-submit
	@echo "=== Deploy complete ==="
```

### CI Integration

```yaml
# .github/workflows/flink-deploy.yml (or equivalent)
- name: Build Flink Jobs
  run: ./gradlew :flink-jobs:build

- name: Deploy to Flink
  run: |
    make flink-deploy FLINK_URL=http://flink-jobmanager:8081
```

### Savepoint Recovery

```bash
# Submit with savepoint restore
curl -X POST "${FLINK_URL}/jars/${JAR_ID}/run" \
  -H "Content-Type: application/json" \
  -d '{"savepointPath":"/tmp/flink-savepoints/savepoint-xxx","allowNonRestoredState":true}'
```

### Error Handling
- Upload fails → CI fails with clear error
- Savepoint fails → log warning, proceed without savepoint
- Cancel fails (no running job) → skip, proceed to submit
- Submit fails → CI fails, Flink dashboard link in logs

---

## Mobile Architecture — Shared Hooks Strategy

### Hook Reuse Matrix

| Hook | Web (React) | Mobile (React Native) | Reuse Strategy |
|------|-------------|----------------------|----------------|
| `useAlerts` | ✅ React Query | ✅ Same | 100% reuse — platform-agnostic |
| `useBuildingList` | ✅ React Query | ✅ Same | 100% reuse |
| `useSensors` | ✅ React Query | ✅ Same | 100% reuse |
| `useAuth` | localStorage | SecureStore (expo-secure-store) | Mobile variant |
| `useNotificationSSE` | EventSource | Polling (15s) | Mobile variant (no EventSource in RN) |
| `useMapSSE` | EventSource | Polling (15s) | Mobile variant |

### Deep-link Routing (SA recommendation)

```
Scheme: uipmobile://
Routes:
  uipmobile://alerts/{alertId}     → AlertDetailScreen
  uipmobile://buildings/{id}/safety → BuildingSafetyScreen
  uipmobile://dashboard             → DashboardScreen
```

**iOS:** Universal Links cần Apple App Site Association file (deferred to production)
**Android:** App Links cần assetlinks.json (deferred to production)
**Pilot:** Custom URL scheme `uipmobile://` là đủ — không cần AASA/assetlinks cho demo

### Package Structure

```
packages/
  api-types/          ← Shared TypeScript types
    Building.ts
    Alert.ts
    SensorReading.ts
    ForecastDataPoint.ts

frontend/src/hooks/   ← Platform-agnostic hooks
  useAlerts.ts        ← 100% reuse in mobile
  useBuildingList.ts
  useSensors.ts

applications/operator-mobile/src/hooks/
  useAuthMobile.ts    ← Mobile-specific (SecureStore)
  usePolling.ts       ← Mobile-specific (replaces SSE)
```

### API Client

```typescript
// packages/api-client/src/index.ts
// Works for both web and mobile — uses fetch (available in both)
export const apiClient = {
  async get<T>(url: string, token: string): Promise<T> {
    const response = await fetch(`${API_BASE_URL}${url}`, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'X-Tenant-ID': getTenantId(),
        'Content-Type': 'application/json',
      },
    });
    if (!response.ok) throw new ApiError(response.status, await response.text());
    return response.json();
  },
};
```

---

## Dependency Graph

```
S8-C01 ──────────────────────────────────────────────────→ GATE-0 (Day 1)
S8-OPS01 (CH HA) ────→ S8-OPS03 (Flink CI/CD) ────────→ GATE-1 (Day 4)
S8-OPS02 (Kafka 3) ────────────────────────────────────→ GATE-1 (Day 4)
S8-M01 (Dashboard) ──→ S8-M02 (Alerts + Safety) ──────→ GATE-2 (Day 7)
S8-OPS01 + S8-OPS02 + S8-OPS05 ──→ S8-QA01 (Regression)→ GATE-3 (Day 9)
ALL ────────────────────────────────────────────────────→ FINAL GATE (Day 10)
```

---

*Document: Sprint 8 Architecture v1.0 | SA | 2026-06-03*
*ADR-036, ADR-037, ADR-038 drafts pending SA review Day 1-2*
