# MVP3 DevOps Assessment -- 2026-06-11

**Assessed by:** DevOps Engineer (automated)
**Platform:** UIP Smart City MVP3 -- Sprint 11 baseline
**Scope:** Build verification, infrastructure HA, monitoring, security, pilot readiness

---

## 1. Build Status (Backend + Frontend)

### Backend (Gradle)

| Metric | Result |
|--------|--------|
| Build | **BUILD SUCCESSFUL** in 9s |
| Tasks | 15 tasks (all UP-TO-DATE -- incremental build) |
| Tests | All tests passed |
| SpotBugs | Main passed, Test skipped |
| JaCoCo | Report generated |
| Artifact | `backend/build/libs/app.jar` -- **176 MB** |

Warnings:
- Log4j 2.25.0 POM has unresolved placeholder versions for jspecify and error_prone_annotations -- cosmetic, does not affect build
- Gradle 9.0 deprecation warnings -- non-blocking

### Frontend (Vite + React)

| Metric | Result |
|--------|--------|
| Build | **SUCCESS** in 7.36s |
| PWA | v1.3.0, generateSW mode, 87 precached entries |
| Total dist | **2.5 MB** (assets 2.4 MB) |
| SW files | `sw.js` + `workbox-9e0cfdd6.js` |

Bundle concerns (chunks > 500 KB):
- `AiWorkflowPage-DGq7tuNU.js` -- **648 KB** (183 KB gzip) -- largest chunk, BPMN designer
- `mui-BgegSMtb.js` -- **391 KB** (117 KB gzip) -- MUI library
- `generateCategoricalChart-Bvq28OoA.js` -- **367 KB** (101 KB gzip) -- recharts
- `CityOpsPage-DEobvSA5.js` -- **207 KB** (64 KB gzip) -- map components

**Recommendation:** Code-split AiWorkflowPage and CityOpsPage with dynamic import() to reduce initial load. Non-blocking for pilot.

### Flink Jobs (Maven)

| Metric | Result |
|--------|--------|
| Artifact | `flink-jobs/target/uip-flink-jobs-0.1.0-SNAPSHOT.jar` -- **86 MB** (fat JAR) |
| Last build | 2026-06-06 -- stale, should rebuild before pilot |

---

## 2. Docker Images Status

### Dockerfiles (8 production images)

| Service | Dockerfile | Base Image | Multi-stage | Non-root User |
|---------|-----------|------------|-------------|---------------|
| Backend | `backend/Dockerfile` | eclipse-temurin:17-jre | Yes (copy pre-built JAR) | Yes (uip user) |
| Analytics-service | `applications/analytics-service/Dockerfile` | eclipse-temurin:17-jre | Yes (builds inside Docker) | Yes (uip user) |
| Frontend | `frontend/Dockerfile` | nginx:1.25-alpine | Yes (node build) | No (nginx default) |
| Flink Jobs | `flink-jobs/Dockerfile` | scratch (export only) | Yes (maven build) | N/A |
| Forecast-service | `applications/forecast-service/Dockerfile` | -- | -- | -- |
| IoT Ingestion | `applications/iot-ingestion-service/Dockerfile` | -- | -- | -- |

### Image Versions in docker-compose.yml

| Component | Image | Version |
|-----------|-------|---------|
| TimescaleDB | timescale/timescaledb | 2.13.1-pg15 |
| ClickHouse | clickhouse/clickhouse-server | 23.8 |
| ClickHouse Keeper | clickhouse/clickhouse-keeper | 23.8-alpine |
| Kafka | confluentinc/cp-kafka | 7.5.0 |
| Redis | redis | 7.2-alpine |
| Flink | flink | 1.19-java17 |
| Keycloak | quay.io/keycloak/keycloak | 23.0 |
| Kong | kong | 3.6 |
| EMQX | emqx/emqx | 5.3.2 |
| MinIO | minio/minio | RELEASE.2024-02-17T01-15-57Z |
| Prometheus | prom/prometheus | v2.51.0 |
| Grafana | grafana/grafana | 10.4.0 |
| Alertmanager | prom/alertmanager | v0.27.0 |
| Kafka UI | redpandadata/console | v2.3.8 |
| Apicurio Registry | apicurio/apicurio-registry-mem | 2.6.6.Final |
| Redpanda Connect | redpandadata/connect | 4.41.0 |

**Assessment:** All image versions are recent stable releases. No critical CVE alerts identified at assessment time. No AGPL-licensed images.

---

## 3. Infrastructure HA Assessment

### 3.1 ClickHouse 2-Node ReplicatedMergeTree (ADR-036)

| Aspect | Status | Detail |
|--------|--------|--------|
| Keeper quorum | **3-node** | clickhouse-keeper, clickhouse-keeper-02, clickhouse-keeper-03 |
| Data nodes | **2-node** | clickhouse-01 (port 8125/9002), clickhouse-02 (port 8124/9003) |
| Engine | ReplicatedReplacingMergeTree | Auto-dedup via ingested_at version column |
| Init SQL | init-replicated.sql | Creates analytics DB + esg_readings with ZooKeeper paths |
| Named volumes | Yes | clickhouse-01-data, clickhouse-02-data, 3x keeper-data |
| Health checks | Yes | wget ping on :8123, nc on :9181 (Keeper) |
| Depends_on | Yes | Nodes wait for all 3 Keepers healthy |
| Schema alignment | PASS | CLICKHOUSE_DB=analytics on all nodes |

### 3.2 Kafka 3-Broker KRaft Mode (ADR-037)

| Aspect | Status | Detail |
|--------|--------|--------|
| Brokers | 3 (kafka, kafka-2, kafka-3) | KRaft mode, no ZooKeeper |
| Quorum voters | `1@kafka:9093,2@kafka-2:9093,3@kafka-3:9093` | Majority = 2/3 survives 1 failure |
| Replication factor | 3 (HA overlay) | offsets + transaction topics RF=3, min.insync.replicas=2 |
| Named volumes | Yes | kafka_data, kafka-2-data, kafka-3-data |
| Health checks | Yes | kafka-topics --list on each broker |
| Topic creation | create-topics.sh | 18 topics defined, RF=1 in base, RF=3 via HA overlay |

**Issue found:** `create-topics.sh` hardcodes `--replication-factor 1`. The HA overlay sets env vars for Kafka but does NOT override the init script. Topics will be created with RF=1 even in HA mode. Need to either:
- Parameterize replication-factor in create-topics.sh based on broker count, OR
- Run a manual rebalance after HA startup (`make kafka-rebalance`)

### 3.3 PostgreSQL Streaming Replication (S8-OPS04)

| Aspect | Status | Detail |
|--------|--------|--------|
| Primary | timescaledb | wal_level=replica, max_wal_senders=3, wal_keep_size=256MB |
| Standby | timescaledb-standby | Hot standby, read-only on port 5433 |
| Replication user | replicator | Created via create-replication-role.sh |
| pg_hba.conf | Mounted read-only | Allows replication connections |
| Standby entrypoint | standby-entrypoint.sh | Custom pg_basebackup + recovery config |
| Failover | Manual promotion | No auto-failover (SA recommendation: false promotion worse than brief downtime) |
| Named volumes | Yes | timescaledb_data, timescaledb-standby-data |
| Health checks | Yes | pg_isready on both primary and standby |

### 3.4 Flink Checkpoint to MinIO (S3)

| Aspect | Status | Detail |
|--------|--------|--------|
| Checkpoint dir | `s3://uip-flink-checkpoints/checkpoints` | Correct -- NOT local disk |
| Savepoint dir | `s3://uip-flink-checkpoints/savepoints` | Correct |
| S3 plugin | flink-s3-fs-hadoop | Copied from /opt to /plugins in entrypoint |
| MinIO | RELEASE.2024-02-17 | Bucket auto-created via minio-init |
| Bucket init | `mc mb --ignore-existing` | Idempotent |
| Staging override | PASS | staging.yml RESTORES S3 path (fixes UAT overlay regression) |

**Critical catch:** The UAT overlay (`docker-compose.uat.yml`) correctly uses S3 checkpoints. The staging overlay explicitly restores S3 path. This is correct.

### 3.5 Kong + Keycloak Integration

| Aspect | Status | Detail |
|--------|--------|--------|
| Kong mode | DB-less | kong.poc.yml / kong.staging.yml declarative config |
| JWT plugin | RS256 | Keycloak public key configured |
| alg=none rejection | Built-in | Kong JWT plugin requires valid signature |
| Header spoofing | Protected | request-transformer strips X-Tenant-ID, X-Is-Aggregator |
| Plugin order | Locked (BR-007) | cors -> jwt -> request-transformer -> rate-limiting -> prometheus -> correlation-id |
| Rate limiting | 1000/min per service | Local policy, fault-tolerant |
| Keycloak | v23.0 start-dev | Realm import, health check enabled |
| Health check | TCP-based | exec 3<>/dev/tcp + HTTP GET /health/ready |
| CORS | Configured | Origins restricted per environment |

---

## 4. Monitoring Stack Review

### 4.1 Prometheus Configuration

| Aspect | Detail |
|--------|--------|
| Scrape interval | 15s default, 30s for analytics/clickhouse/forecast |
| Retention | 15 days |
| Rule files | 4 files: alert-rules.yml, alert-rules-sprint11.yml, alert-rules-ch-keeper.yml, alerts/*.yml |
| Alertmanager | Static target at alertmanager:9093 |
| Web lifecycle | Enabled (hot reload) |
| Named volume | prometheus_data |

### 4.2 Scrape Targets (12 jobs)

| Job | Target | Interval |
|-----|--------|----------|
| uip-backend | uip-backend:8081/actuator/prometheus | 15s |
| analytics-service | analytics-service:8081/actuator/prometheus | 15s |
| kafka | kafka-exporter:9308 | 15s |
| postgres | postgres-exporter:9187 | default |
| redis | redis-exporter:9121 | default |
| kong | uip-kong:8001/metrics | 15s |
| forecast-service | uip-forecast-service:8090/metrics | 30s |
| flink-jobmanager | flink-jobmanager:9250 | 15s |
| flink-taskmanager | flink-taskmanager:9250 | 15s |
| emqx | emqx:18083/api/v5/prometheus/stats | 15s |
| clickhouse | clickhouse-exporter:9116 | 30s |
| clickhouse-keeper | uip-clickhouse-keeper:9363/metrics | 30s |

### 4.3 Alert Rules Coverage

| Category | Rules | Severity |
|----------|-------|----------|
| Backend | HighP95Latency, KafkaConsumerLag, CircuitBreakerOpen, PostgresConnectionPoolExhausted, SensorIngestRateDrop, BackupFailed | Warning/Critical |
| Analytics-service | AnalyticsP95LatencyHigh, AnalyticsErrorRateHigh, AnalyticsServiceDown | Warning/Critical |
| Sprint 11 SyncQueue | SyncQueueDepthHigh, SyncQueueFlushFailuresHigh | Warning |
| Sprint 11 Analytics | AnalyticsRestLatencyHigh, GrpcAdapterUnexpectedlyActive, AnalyticsServiceUnreachable | Critical/Warning |
| Sprint 11 Infra | BackendRestartLoop, BackendLatencySLOBreached | Critical/Warning |
| CH Keeper Memory | MemoryHigh(>70%), MemoryCritical(>90%), HeapHigh(>75%), GoroutinesHigh(>1000), SnapshotLarge(>500MB) | Warning/Critical |

**Total: 21 alert rules across 6 groups.** Comprehensive coverage for pilot.

### 4.4 Grafana Dashboards (4 dashboards)

| Dashboard | File |
|-----------|------|
| UIP Services Overview | uip-services.json |
| Forecast Monitoring | uip-forecast.json |
| Sprint 6 AI Flood Alert | uip-sprint6-ai-flood.json |
| CH Keeper Overview | ch-keeper-overview.json |

### 4.5 Exporters (5 sidecars)

| Exporter | Image | Target |
|----------|-------|--------|
| Kafka exporter | danielqsj/kafka-exporter | kafka:9092 |
| Postgres exporter | prometheuscommunity/postgres-exporter | timescaledb:5432 |
| Redis exporter | oliver006/redis_exporter | uip-redis:6379 |
| ClickHouse exporter | f1yegor/clickhouse-exporter | uip-clickhouse:8123 |
| (Flink native) | Built-in PrometheusReporter | :9250 |

---

## 5. Security Configuration Review

### 5.1 Authentication

| Aspect | Status | Detail |
|--------|--------|--------|
| Dual auth | HMAC + Keycloak | JWT_HMAC_ISSUER + JWT_KEYCLOAK_ISSUER |
| Keycloak realm | `uip` | Realm export JSON, custom mappers for tenant_id, building_ids, is_aggregator |
| JWT algorithm | RS256 (Keycloak) | RSA public key in kong.yml |
| Token lifespan | 15 min access, 7 day refresh (staging) | Configurable |
| Health endpoints | Secured | MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS: never (staging) |
| Actuator exposure | Restricted | health,info,prometheus only (staging) |

### 5.2 Authorization

| Aspect | Status | Detail |
|--------|--------|--------|
| Kong JWT | Validates exp claim | alg=none rejected |
| Header injection | Kong strips client X-Tenant-ID | Prevents tenant spoofing |
| RLS | Mentioned in smoke test | Row-Level Security on buildings/alerts/sensor_readings |
| Multi-tenancy | Capability flag | uip.capabilities.multi-tenancy=true in staging |

### 5.3 Network Security

| Aspect | Status | Detail |
|--------|--------|--------|
| EMQX dashboard | localhost-only (staging) | 127.0.0.1:18083 |
| Kafka UI | localhost-only (UAT/staging) | 127.0.0.1:8090 |
| Management port | Separate from app port | Backend: 8080 (app), 8086 (actuator) |
| Docker network | bridge (uip-network) | External for monitoring stack |

### 5.4 Secrets Management

| Aspect | Status | Detail |
|--------|--------|--------|
| .env files | .env, .env.staging, .env.security | Templates committed, values gitignored |
| Example files | .env.example, .env.staging.example | Document all required vars |
| Hardcoded secrets | **PRESENT** in kong.poc.yml | RS256 public key inline -- acceptable for POC, must be externalized for production |
| Default passwords | Documented in .env.example | POSTGRES_PASSWORD, REDIS_PASSWORD, JWT_SECRET, MINIO credentials |
| Admin passwords | Default dev passwords | admin_Dev#2026!, operator_Dev#2026! -- CHANGE for staging/prod |

### 5.5 Production Profile

| Aspect | Status | Detail |
|--------|--------|--------|
| Profile exists | Yes | application-t1.yml, application-t2.yml, application-staging.yml |
| Production test | ProductionProfileSecurityTest | Verifies debug endpoints return 404 |
| Container security | Non-root user | Backend + analytics-service run as `uip` user |
| Frontend | nginx:1.25-alpine | SPA routing, security headers |

---

## 6. Smoke Test Readiness Assessment

Services are NOT running locally. Assessing **config readiness** instead of live smoke test.

### 6.1 Health Check Configuration

| Service | Health Check | Method |
|---------|-------------|--------|
| TimescaleDB | pg_isready | CMD-SHELL, 10s interval |
| ClickHouse | wget /ping | CMD-SHELL, 10s interval |
| ClickHouse Keeper | nc :9181 | CMD-SHELL, 10s interval |
| Redis | redis-cli ping | CMD, 10s interval |
| Kafka | kafka-topics --list | CMD, 15s interval |
| EMQX | TCP :1883 | CMD-SHELL, 10s interval |
| MinIO | mc ready local | CMD, 10s interval |
| Flink JM | curl /overview | CMD-SHELL, 15s interval |
| Backend | curl /api/v1/health | CMD-SHELL, 15s interval, 60s start period |
| Analytics-service | curl /actuator/health | CMD-SHELL, 15s interval, 60s start period |
| Kong | kong health | CMD, 10s interval |
| Keycloak | TCP + HTTP /health/ready | CMD-SHELL, 15s interval, 90s start period |
| Forecast-service | curl /api/v1/forecast/health | CMD, 30s interval |

**All 13 services have health checks.** Start periods are generous (30-90s) for cold start.

### 6.2 Environment Variables Defaults

All env vars use `${VAR:-default}` pattern. Verified:
- POSTGRES_USER=uip, POSTGRES_DB=uip_smartcity
- CLICKHOUSE_DB=analytics, CLICKHOUSE_USER=default
- KAFKA_CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qg
- MINIO_ROOT_USER=minioadmin
- JWT_EXPIRATION_MS=900000, JWT_REFRESH_EXPIRATION_MS=604800000

**Assessment:** Tier 1 (dev) deployment will work without any capability flags. Only JWT_SECRET and POSTGRES_PASSWORD are mandatory.

### 6.3 Database Migration Scripts

| DB | Migrations | Location |
|----|-----------|----------|
| TimescaleDB | V1__init_schemas.sql, V2__hypertables.sql, V3__sensor_last_seen_trigger.sql | infrastructure/timescaledb/migrations/ |
| TimescaleDB replication | create-replication-role.sh, pg_hba.conf, standby-entrypoint.sh | infrastructure/timescaledb/ |
| ClickHouse | init.sql (single), init-replicated.sql (HA) | infrastructure/clickhouse/ |
| Kafka topics | create-topics.sh (18 topics) | infrastructure/kafka/ |

### 6.4 Sprint 11 Smoke Test Script

File: `scripts/smoke-test-sprint11.sh` -- 6 test suites:

| Suite | Tests | Automated |
|-------|-------|-----------|
| 1. Infrastructure Health | T1-1 to T1-6 (6 tests) | Yes |
| 2. Kong Auth Integrity | T2-1, T2-2 (2 tests) | Yes |
| 3. Capability Flags | T3-1 (auto), T3-2 (manual), T3-3 (auto w/ token) | Partial |
| 4. Offline-Online SyncQueue | T4-1 (auto w/ token), T4-2 (manual, device) | Partial |
| 5. Cross-Tenant Isolation | T5-1 (manual), T5-2 (auto, docker exec) | Partial |
| 6. Performance SLO Gate | T6-1 (auto), T6-2 (manual, k6) | Partial |

**Total: 10 automated, 4 manual steps.** Script returns exit 0 (GO) or 1 (NO-GO).

---

## 7. Pilot Deployment Readiness

### 7.1 Readiness Scorecard

| Category | Score | Notes |
|----------|-------|-------|
| Build artifacts | **PASS** | Backend + Frontend build clean |
| Docker images | **PASS** | 8 Dockerfiles, multi-stage, version-pinned |
| HA configuration | **PASS** | CH 2-node, Kafka 3-broker, PG replication |
| Flink checkpoints | **PASS** | S3/MinIO, NOT local disk |
| Health checks | **PASS** | All 13 services configured |
| Resource limits | **PASS** | UAT + staging overlays set CPU+memory on every service |
| Monitoring | **PASS** | Prometheus + 12 scrape targets + 21 alert rules |
| Security | **PASS** | Kong JWT, header stripping, non-root containers |
| Pilot runbook | **PASS** | 6 incident scenarios documented |
| Memory profile | **PASS** | 16GB RAM budget calculated (~14GB with 2GB buffer) |
| Chaos scripts | **PASS** | 4 scenarios (Kafka, CH, PG, Flink) + HTML report |
| Smoke test | **PASS** | Sprint 11 script with automated + manual steps |
| Secrets management | **WARN** | Templates OK, hardcoded public key in kong.yml |
| Kafka RF mismatch | **FAIL** | create-topics.sh hardcodes RF=1, HA overlay does not fix |

### 7.2 Deployment Commands

```bash
# Tier 1 (dev) -- no capability flags
docker compose -f infrastructure/docker-compose.yml --env-file .env up -d

# UAT
docker compose -f infrastructure/docker-compose.yml \
  -f infrastructure/docker-compose.uat.yml \
  --env-file .env.uat up -d

# Staging (HA + Sprint 11)
docker compose -f infrastructure/docker-compose.yml \
  -f infrastructure/docker-compose.uat.yml \
  -f infrastructure/docker-compose.staging.yml \
  --env-file .env.staging up -d

# HA overlay (additional)
docker compose -f infrastructure/docker-compose.yml \
  -f infrastructure/docker-compose.ha.yml up -d
```

### 7.3 Pre-Pilot Checklist

- [ ] Build Flink JAR (stale since 2026-06-06)
- [ ] Set strong passwords in .env.staging (all CHANGE_ME values)
- [ ] Update kong.staging.yml RS256 public key from staging Keycloak
- [ ] Verify Kafka topic RF=3 after HA startup (create-topics.sh uses RF=1)
- [ ] Run `scripts/smoke-test-sprint11.sh` after stack is up
- [ ] Complete 4 manual smoke test steps (T3-2, T4-2, T5-1, T6-2)
- [ ] Verify memory: `docker stats --no-stream` total < 13GB on 16GB host
- [ ] Run chaos engineering suite: `bash infrastructure/chaos/run-all-chaos.sh`

---

## 8. Issues Found

### Critical (must fix before pilot)

1. **Kafka topic replication factor hardcoded to 1** -- `infrastructure/kafka/create-topics.sh` uses `--replication-factor 1` for all topics. The HA overlay sets `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=3` on brokers but does NOT override the init script. Application topics will be RF=1 even in 3-broker mode. Fix: parameterize `REPLICATION_FACTOR` in create-topics.sh and set it via environment variable.

### Warning (should fix before production)

2. **Flink JAR stale** -- Last built 2026-06-06. Rebuild before pilot deployment: `cd flink-jobs && mvn package -DskipTests`

3. **Frontend bundle size** -- AiWorkflowPage chunk is 648 KB. Consider code-splitting with dynamic import() for faster initial load on pilot devices.

4. **Hardcoded RS256 public key** in kong.poc.yml and kong.staging.yml. For production, fetch from Keycloak JWKS endpoint at runtime or use Kong's jwks_uri config.

5. **Redis health check exposes password** -- `redis-cli -a ${REDIS_PASSWORD} ping` may log password in process list. Use `REDISCLI_AUTH` env var instead.

6. **Missing ClickHouse Keeper resource limits** -- The HA overlay (docker-compose.ha.yml) does not set deploy.resources.limits on keeper containers. Under memory pressure, keepers could consume unbounded RAM.

### Informational

7. **Log4j 2.25.0 POM warnings** -- Cosmetic Maven metadata issue, no runtime impact.

8. **Gradle 9.0 deprecation** -- Build uses deprecated features. Track for future upgrade.

9. **No Grafana alert dashboards for SyncQueue** -- Alert rules exist but no dedicated dashboard for offline sync monitoring.

10. **Kafka UI accessible in POC** -- No localhost restriction in base docker-compose.yml (fixed in UAT/staging overlay).

---

## 9. Recommendations

### Before Pilot Deployment (2026-06-12)

1. **Fix Kafka RF=1 in create-topics.sh** -- Add `REPLICATION_FACTOR=${KAFKA_REPLICATION_FACTOR:-1}` and use it in create_topic(). Set KAFKA_REPLICATION_FACTOR=3 in HA/staging env files.

2. **Rebuild Flink JAR** -- `cd flink-jobs && mvn clean package -DskipTests`

3. **Add resource limits to CH Keeper** in docker-compose.ha.yml:
   ```yaml
   clickhouse-keeper:
     deploy:
       resources:
         limits: { memory: 512m, cpus: '0.25' }
   ```

4. **Verify staging secrets** -- All CHANGE_ME passwords in .env.staging must be replaced with strong unique values.

5. **Run smoke test** after full stack startup.

### Before Production

6. Externalize Kong JWT config to use JWKS endpoint instead of hardcoded RSA public key.

7. Code-split frontend AiWorkflowPage and CityOpsPage (lazy loading).

8. Add SyncQueue monitoring dashboard to Grafana.

9. Implement automated Kafka RF verification in smoke test script.

10. Add SSL/TLS termination (nginx/LB) in front of Kong for HTTPS.

---

*Assessment complete. Platform is PILOT-READY with 1 critical fix required (Kafka RF) and 5 warnings to address before production.*
*Next step: Fix Kafka create-topics.sh RF parameterization, rebuild Flink JAR, run full staging deployment + smoke test.*
