# OpenTelemetry Distributed Tracing Runbook — UIP Smart City

## Sprint
MVP5 Sprint M5-3

## Story
M5-3 T10 — Observability OTel: Trace 25 Bounded Contexts + Grafana Tempo

## Owner
DevOps

## Status
✅ **DONE** — 2026-06-29

---

## 1. Overview

Distributed tracing for the UIP Smart City system via OpenTelemetry (OTel) + Grafana Tempo.

**Architecture:**
```
┌──────────────┐   OTLP/gRPC   ┌────────────────┐   OTLP/gRPC   ┌──────────────┐
│ uip-backend  │──────────────▶│ otel-collector │──────────────▶│ tempo        │
│ (OTel agent) │   :4317       │ (tail sample)  │   :4317       │ (trace store)│
└──────────────┘               └────────────────┘               └──────────────┘
                                                                       │
                                                                       │ HTTP :3200
                                                                       ▼
                                                                 ┌────────────┐
                                                                 │  Grafana   │
                                                                 │  Explorer  │
                                                                 └────────────┘
```

**Sampling Strategy:**
- **10%** of normal traffic (probabilistic)
- **100%** of error traces (status=ERROR)
- **100%** of slow traces (>500ms end-to-end)

**Retention:** 48h (dev/POC — increase to 7d+ for production)

---

## 2. Components

### 2.1 OpenTelemetry Collector (`uip-otel-collector`)
- **Image:** `otel/opentelemetry-collector-contrib:0.100.0`
- **Config:** `infrastructure/otel/otel-collector-config.yml`
- **Ports:**
  - `4317`: OTLP gRPC receiver
  - `4318`: OTLP HTTP receiver
- **Function:**
  - Receives traces from all Spring Boot services
  - Tail sampling: 10% normal + 100% errors + 100% slow (>500ms)
  - Batch processing (5s timeout, 1024 traces/batch)
  - Exports to Tempo via OTLP gRPC

### 2.2 Grafana Tempo (`uip-tempo`)
- **Image:** `grafana/tempo:2.4.0`
- **Config:** `infrastructure/tempo/tempo.yaml`
- **Ports:**
  - `3200`: HTTP API + UI
  - `4319→4317`: OTLP gRPC receiver (direct access)
- **Storage:**
  - Backend: local (vParquet4 encoding)
  - Path: `/var/tempo/blocks` (volume: `uip-tempo-data`)
  - WAL: `/var/tempo/wal`
- **Retention:** 48h (compaction window: 1h)
- **Metrics Generator:** Generates RED metrics (Rate, Errors, Duration) + service graphs

### 2.3 OpenTelemetry Java Agent (Spring Boot services)
- **Artifact:** `opentelemetry-javaagent.jar` (v2.3.0)
- **Location:** `infrastructure/agents/opentelemetry-javaagent.jar`
- **Download:** `make otel-agent`
- **Injection:** Volume mount into `/opt/otel/opentelemetry-javaagent.jar`
- **Configuration:** `JAVA_TOOL_OPTIONS` env var in `docker-compose.ha.yml`

---

## 3. Setup

### 3.1 Download OTel Java Agent

```bash
cd infrastructure
make otel-agent
```

This downloads `opentelemetry-javaagent.jar` (v2.3.0) to `infrastructure/agents/`.

### 3.2 Enable Volume Mount in `docker-compose.ha.yml`

Uncomment the agent volume mount in the `backend` service:

```yaml
backend:
  volumes:
    - vault-secrets:/run/secrets:ro
    - ./agents/opentelemetry-javaagent.jar:/opt/otel/opentelemetry-javaagent.jar:ro  # ← uncomment this
```

### 3.3 Start HA Stack with OTel

```bash
make up-ha
```

This starts:
- Tempo (trace storage)
- OTel Collector (tail sampling + batch processing)
- Backend with OTel Java agent auto-instrumentation

### 3.4 Verify Tempo Health

```bash
make tempo-check
```

Expected output:
```
=== Tempo Health Check ===
✅ Tempo ready

Trace search: http://localhost:3200/api/search?tags={service.name="uip-backend"}&limit=10
Grafana explore: http://localhost:3000/explore?orgId=1&left={...}
```

### 3.5 Generate Traffic (Trigger Traces)

```bash
# Trigger backend API calls to generate traces
curl http://localhost:8080/api/v1/buildings
curl http://localhost:8080/api/v1/sensors
curl http://localhost:8080/api/v1/esg/metrics
```

Wait ~10s for OTel collector to flush batch to Tempo.

### 3.6 View Traces in Grafana

1. Open Grafana: http://localhost:3000
2. Navigate to **Explore** (compass icon, left sidebar)
3. Select **Tempo** datasource (dropdown, top left)
4. Query traces:
   - **Search by service:** `{service.name="uip-backend"}`
   - **Search by status:** `{status=error}`
   - **Search by duration:** `{duration>500ms}`
5. Click a trace to view the flame graph

**Dashboard:**
- Pre-provisioned dashboard: **OTel Traces Overview — UIP Smart City**
- Path: http://localhost:3000/d/grafana-traces-overview
- Panels:
  - Service map (node graph)
  - Recent error traces (table)
  - Request rate by service (timeseries)
  - Trace search (table)

---

## 4. Configuration

### 4.1 Sampling Rate (Production Tuning)

**Current:** 10% probabilistic sampling

**To increase sampling rate** (e.g., 25% for production):

Edit `infrastructure/otel/otel-collector-config.yml`:

```yaml
processors:
  tail_sampling:
    policies:
      - name: sample-10-percent
        type: probabilistic
        probabilistic:
          sampling_percentage: 25  # ← increase here
```

Restart OTel collector:
```bash
docker compose -f docker-compose.ha.yml restart otel-collector
```

### 4.2 Retention Period

**Current:** 48h (dev/POC)

**To increase retention** (e.g., 7d for production):

Edit `infrastructure/tempo/tempo.yaml`:

```yaml
compactor:
  compaction:
    block_retention: 168h  # 7 days (168h)
```

Restart Tempo:
```bash
docker compose -f docker-compose.ha.yml restart tempo
```

### 4.3 Grafana Datasource Provisioning

**File:** `infrastructure/monitoring/grafana-datasource-tempo.yml`

**To enable automatic datasource provisioning:**
1. Mount this file into Grafana container:
   ```yaml
   grafana:
     volumes:
       - ./monitoring/grafana-datasource-tempo.yml:/etc/grafana/provisioning/datasources/tempo.yml:ro
   ```
2. Restart Grafana

**To enable dashboard provisioning:**
1. Mount the dashboard JSON:
   ```yaml
   grafana:
     volumes:
       - ./monitoring/otel-traces-dashboard.json:/etc/grafana/provisioning/dashboards/otel-traces.json:ro
   ```
2. Create a dashboard provider config at `/etc/grafana/provisioning/dashboards/default.yml`:
   ```yaml
   apiVersion: 1
   providers:
     - name: 'default'
       folder: ''
       type: file
       disableDeletion: false
       updateIntervalSeconds: 10
       options:
         path: /etc/grafana/provisioning/dashboards
   ```

---

## 5. Troubleshooting

### 5.1 No Traces in Tempo

**Symptoms:**
- Tempo `/ready` endpoint OK
- Grafana Explore shows "No traces found"

**Diagnostic Steps:**

1. **Check backend logs for OTel agent initialization:**
   ```bash
   docker logs uip-backend 2>&1 | grep -i otel
   ```
   
   Expected output:
   ```
   [otel.javaagent] OpenTelemetry Javaagent 2.3.0
   [otel.javaagent] Instrumentation enabled: spring-webmvc-6.0, spring-boot-autoconfigure, jdbc, ...
   ```

2. **Check OTel collector logs for received spans:**
   ```bash
   docker logs uip-otel-collector --tail=50
   ```
   
   Expected:
   ```
   2026-06-29T10:15:30.123Z info    TracesExporter    {"kind": "exporter", "name": "otlp", "spans_sent": 42}
   ```

3. **Verify Tempo received traces:**
   ```bash
   curl -s http://localhost:3200/api/search?limit=10 | jq .
   ```
   
   Should return JSON with trace IDs. If empty array `[]`, no traces ingested.

**Common Causes:**

- **OTel agent JAR not mounted:**
  - Symptom: Backend logs show no `[otel.javaagent]` lines
  - Fix: Uncomment volume mount in `docker-compose.ha.yml`, restart backend

- **OTel collector can't reach Tempo:**
  - Symptom: OTel collector logs show `connection refused` or `context deadline exceeded`
  - Fix: Check `docker network inspect uip-network` — ensure Tempo is on the same network
  - Fix: Verify Tempo container is running: `docker ps | grep tempo`

- **Backend can't reach OTel collector:**
  - Symptom: Backend logs show `Failed to export spans` or `OTLP exporter timeout`
  - Fix: Check `OTEL_EXPORTER_OTLP_ENDPOINT` in backend environment (should be `http://uip-otel-collector:4317`)
  - Fix: Verify collector is running: `docker ps | grep otel-collector`

- **Sampling dropped all traces:**
  - Symptom: OTel collector logs show `spans_received: 100, spans_sampled: 0`
  - Fix: Temporarily increase sampling rate to 100% for testing:
    ```yaml
    # otel-collector-config.yml
    sampling_percentage: 100  # debug only
    ```
  - Fix: Or trigger an error trace (4xx/5xx) which is always sampled

### 5.2 Tempo Out of Disk Space

**Symptoms:**
- Tempo container logs: `no space left on device`
- Trace ingestion stops

**Diagnostic:**
```bash
docker exec uip-tempo df -h /var/tempo
```

**Fixes:**

1. **Reduce retention period** (e.g., 24h instead of 48h):
   ```yaml
   # tempo.yaml
   compactor:
     compaction:
       block_retention: 24h
   ```

2. **Manually trigger compaction:**
   ```bash
   docker exec uip-tempo wget -qO- http://localhost:3200/api/compact
   ```

3. **Prune old blocks:**
   ```bash
   docker exec uip-tempo find /var/tempo/blocks -type f -mtime +2 -delete
   ```

### 5.3 High Memory Usage (OTel Collector)

**Symptoms:**
- OTel collector container OOM-killed
- Memory usage >512MB (limit: 512MB in compose)

**Diagnostic:**
```bash
docker stats uip-otel-collector
```

**Fixes:**

1. **Reduce batch size:**
   ```yaml
   # otel-collector-config.yml
   processors:
     batch:
       send_batch_size: 512   # down from 1024
       send_batch_max_size: 1024  # down from 2048
   ```

2. **Reduce tail_sampling buffer:**
   ```yaml
   tail_sampling:
     num_traces: 25000  # down from 50000
   ```

3. **Increase memory limit:**
   ```yaml
   # docker-compose.ha.yml
   otel-collector:
     deploy:
       resources:
         limits:
           memory: 1g  # up from 512m
   ```

### 5.4 Missing x-trace-id in Response Headers

**Expected:** Backend should return `x-trace-id: <trace_id>` header in all responses.

**Diagnostic:**
```bash
curl -I http://localhost:8080/api/v1/buildings
```

Look for:
```
x-trace-id: 1234567890abcdef1234567890abcdef
```

**Fix (if missing):**
1. Check backend Spring Boot config for OTel propagation:
   ```yaml
   # application.yml
   management:
     tracing:
       propagation:
         type: W3C  # or B3
   ```

2. Verify OTel agent injected the propagation interceptor:
   ```bash
   docker logs uip-backend 2>&1 | grep -i "TracingFilter"
   ```

### 5.5 Tempo API Returns 500 Error

**Symptoms:**
- `curl http://localhost:3200/api/search` returns `500 Internal Server Error`
- Grafana Explore shows "Error loading data"

**Diagnostic:**
```bash
docker logs uip-tempo --tail=100
```

Look for:
- `failed to create block: context deadline exceeded`
- `failed to read wal: corrupt entry`
- `failed to open parquet file`

**Fixes:**

1. **Corrupted WAL — recreate:**
   ```bash
   docker compose -f docker-compose.ha.yml stop tempo
   docker volume rm uip-tempo-data  # WARNING: deletes all traces
   docker compose -f docker-compose.ha.yml up -d tempo
   ```

2. **Increase query timeout:**
   ```yaml
   # tempo.yaml
   query_frontend:
     search:
       max_duration: 72h  # up from 48h
   ```

---

## 6. Reference

### 6.1 File Locations

| File | Purpose |
|------|---------|
| `infrastructure/otel/otel-collector-config.yml` | OTel collector pipeline config |
| `infrastructure/tempo/tempo.yaml` | Tempo trace storage config |
| `infrastructure/monitoring/grafana-datasource-tempo.yml` | Grafana datasource provisioning |
| `infrastructure/monitoring/otel-traces-dashboard.json` | Pre-built Grafana dashboard |
| `infrastructure/docker-compose.ha.yml` | Service definitions (otel-collector, tempo, backend+agent) |
| `infrastructure/Makefile` | Operational commands (otel-agent, tempo-check) |
| `infrastructure/agents/opentelemetry-javaagent.jar` | OTel Java agent (download via `make otel-agent`) |

### 6.2 Makefile Targets

| Target | Description |
|--------|-------------|
| `make otel-agent` | Download OTel Java agent JAR to ./agents/ |
| `make tempo-check` | Verify Tempo health + trace search endpoint |
| `make up-ha` | Start HA stack (includes OTel + Tempo) |
| `make logs svc=otel-collector` | Tail OTel collector logs |
| `make logs svc=tempo` | Tail Tempo logs |
| `make logs svc=backend` | Tail backend logs (check OTel agent init) |

### 6.3 Docker Commands

```bash
# Check OTel collector health
docker exec uip-otel-collector wget -qO- http://localhost:13133/

# Check Tempo health
docker exec uip-tempo wget -qO- http://localhost:3200/ready

# Inspect Tempo storage
docker exec uip-tempo ls -lh /var/tempo/blocks

# View OTel collector config (runtime)
docker exec uip-otel-collector cat /etc/otel-collector-config.yml

# View Tempo config (runtime)
docker exec uip-tempo cat /etc/tempo.yaml
```

### 6.4 Tempo API Endpoints

| Endpoint | Description |
|----------|-------------|
| `http://localhost:3200/ready` | Health check (returns 200 if ready) |
| `http://localhost:3200/api/search?limit=10` | Search traces (returns last 10 traces) |
| `http://localhost:3200/api/search?tags={service.name="uip-backend"}` | Search traces by service |
| `http://localhost:3200/api/traces/<trace_id>` | Get specific trace by ID |
| `http://localhost:3200/api/compact` | Manually trigger compaction |
| `http://localhost:3200/metrics` | Prometheus metrics |

### 6.5 TraceQL Query Examples

**Search by service:**
```
{service.name="uip-backend"}
```

**Search by HTTP status:**
```
{span.http.status_code >= 400}
```

**Search by duration:**
```
{duration > 500ms}
```

**Search by span name:**
```
{name="GET /api/v1/buildings"}
```

**Complex query (errors AND slow):**
```
{status=error && duration > 1s}
```

**Rate by service (metrics):**
```
{ span.http.status_code >= 200 } | rate() by (resource.service.name)
```

---

## 7. Known Limitations (OPEN)

1. **OTel Java agent volume mount required**
   - Current backend Docker image does NOT include the agent
   - Workaround: Volume mount from host (`./agents/opentelemetry-javaagent.jar`)
   - Production: Bake agent into backend image via Dockerfile COPY

2. **Grafana datasource NOT auto-provisioned**
   - Manual import required: upload `grafana-datasource-tempo.yml` via Grafana UI
   - OR: Mount as volume into Grafana container (see §4.3)

3. **Dashboard NOT auto-provisioned**
   - Manual import required: upload `otel-traces-dashboard.json` via Grafana UI
   - OR: Configure dashboard provider (see §4.3)

4. **Single-node Tempo (no HA)**
   - Dev/POC only — production should use Tempo in microservices mode or Grafana Cloud

5. **48h retention**
   - May be insufficient for production debugging
   - Recommendation: 7d minimum for production

---

## 8. Next Steps (Post-POC)

1. **Instrument analytics-service, forecast-service**
   - Add `JAVA_TOOL_OPTIONS` for OTel agent
   - Volume mount agent JAR

2. **Add trace-to-logs correlation**
   - Configure Loki datasource in Grafana
   - Enable `tracesToLogsV2` in Tempo datasource (already in config)

3. **Add service map to Prometheus**
   - Enable Tempo metrics generator (already in config)
   - Configure `serviceMap` datasource in Grafana (already in config)

4. **Production Tempo deployment**
   - Migrate to microservices mode (distributor, ingester, compactor, querier)
   - S3-compatible storage (MinIO or AWS S3)
   - 7d retention minimum

5. **Bake OTel agent into backend image**
   - Add to Dockerfile:
     ```dockerfile
     FROM eclipse-temurin:21-jre-alpine
     COPY --from=builder /build/libs/app.jar /app.jar
     ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.3.0/opentelemetry-javaagent.jar /opt/otel/opentelemetry-javaagent.jar
     ```
   - Remove volume mount requirement

---

## 9. Sign-off

**DevOps Engineer:** ✅ Infrastructure deployed and verified  
**Solution Architect:** _(pending review)_  
**Date:** 2026-06-29

---

## Appendix: ADR Reference

**ADR-049: NL Model Residency + Trace Lineage**
- Distributed tracing for AI inference pipeline
- Trace lineage for compliance (data residency requirements)
- OpenTelemetry as standard instrumentation

**ADR-048 §6: Vault Secret Injection**
- OTel collector reads `DEPLOYMENT_ENV` from environment
- Backend OTel agent config does NOT contain secrets (only endpoints)
