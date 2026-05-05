# UIP ESG POC — On-Call Playbook

**Version:** 1.0  
**Last updated:** 2026-06-20  
**On-call rotation:** PagerDuty schedule `uip-prod-oncall`  
**Escalation:** Backend Lead → CTO within severity timelines

---

## Severity Matrix

| Severity | Definition | Response Time | MTTR Target | Examples |
|----------|-----------|---------------|-------------|---------|
| **P0** | Production down, data loss risk, security breach | Page ngay, 24/7 | < 15 phút | API hoàn toàn down, DB corruption, JWT secret leaked |
| **P1** | Partial degradation, SLA at risk (< 99.5% uptime) | Within 1 giờ | < 2 giờ | Circuit breaker OPEN, Kafka lag > 10k, sensor ingest drop > 50% |
| **P2** | Non-critical, workaround exists | Next business day | < 24 giờ | Report generation slow, non-prod alert, dashboard widget glitch |

---

## Alert Scenarios

---

### Alert 1: `HighP95Latency`

**Grafana alert:** `uip-backend.p95_latency_ms > 200` sustained 5 phút

#### Nguyên nhân phổ biến

| Khả năng | Dấu hiệu | Hành động |
|----------|---------|-----------|
| Database slow query | `pg_stat_activity` có query > 2s | EXPLAIN ANALYZE, add index |
| Redis miss / cache cold | Cache hit rate < 80% | Kiểm tra Redis connection, trigger warmup |
| Claude API timeout | Circuit breaker `claudeApiService` HALF_OPEN/OPEN | Xem Alert 3 |
| JVM GC pressure | Heap > 85%, GC time > 5% | `jmap -histo`, xem memory leak |

#### Hành động từng bước

```bash
# 1. Xác định endpoint chậm nhất
curl http://localhost:8080/actuator/metrics/http.server.requests | \
  jq '.measurements[] | select(.statistic=="MAX")'

# 2. Kiểm tra DB slow queries
psql $DATABASE_URL -c "
  SELECT query, mean_exec_time, calls
  FROM pg_stat_statements
  WHERE mean_exec_time > 500
  ORDER BY mean_exec_time DESC
  LIMIT 10;"

# 3. Kiểm tra Redis
redis-cli -u $REDIS_URL PING
redis-cli -u $REDIS_URL INFO stats | grep keyspace_hits

# 4. Nếu tất cả bình thường — scale up
kubectl scale deployment uip-backend-blue --replicas=5 -n uip-prod
```

**Escalation:** P1 nếu > 200ms sau 10 phút. P0 nếu timeout hoàn toàn.

---

### Alert 2: `KafkaConsumerLag`

**Grafana alert:** `kafka_consumer_group_lag{group="uip-esg-consumer-group"} > 10000` sustained 5 phút

#### Hành động từng bước

```bash
# 1. Kiểm tra consumer lag chi tiết
kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group uip-esg-consumer-group

# 2. Kiểm tra consumer alive
kubectl get pods -n uip-prod -l app=uip-backend | grep -v Running

# 3. Kiểm tra throughput Kafka topic
kubectl exec -n kafka kafka-0 -- \
  kafka-topics.sh --bootstrap-server localhost:9092 \
  --describe --topic uip.esg.telemetry.v1

# 4. Xem lỗi trong consumer logs
kubectl logs -n uip-prod -l app=uip-backend \
  --since=10m | grep -E "ERROR|WARN.*kafka"

# 5a. Nếu consumer pod bị crash-loop → restart
kubectl rollout restart deployment/uip-backend -n uip-prod

# 5b. Nếu message volume spike thật → tăng partition
# Xem runbook.md Section 3 — Scale Kafka Partitions

# 5c. Nếu poison message (DLQ tăng) → inspect DLQ
kubectl exec -n kafka kafka-0 -- \
  kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic uip.esg.telemetry.error.v1 \
  --from-beginning --max-messages 5
```

**Severity:** P1 nếu lag > 10k. P0 nếu lag > 100k hoặc consumer group lost (offset reset risk).

---

### Alert 3: `CircuitBreakerOpen` — Claude API Down

**Grafana alert:** `resilience4j_circuitbreaker_state{name="claudeApiService"} == 2` (OPEN)

#### Ý nghĩa fallback behavior

Khi `claudeApiService` OPEN:
- **AI Workflow:** Trả fallback response từ `RuleBasedFallbackDecisionService`
- **ESG Report:** Skip AI summary, dùng template-based summary
- **Alert Detection:** Rule-based logic tiếp tục hoạt động bình thường
- **User impact:** Hiển thị banner "AI features temporarily limited"

#### Hành động từng bước

```bash
# 1. Kiểm tra Claude API status
curl https://status.anthropic.com/api/v2/status.json | jq '.status.indicator'
# Nếu "major" hoặc "critical" → đây là upstream issue, KHÔNG cần action thêm

# 2. Kiểm tra circuit breaker state
curl http://localhost:8080/actuator/circuitbreakers | \
  jq '.circuitBreakers.claudeApiService'

# 3. Kiểm tra API key valid
kubectl exec -n uip-prod deploy/uip-backend -- env | grep CLAUDE_API_KEY | wc -c
# Nếu key expired → rotate (xem runbook.md Section 5)

# 4. Nếu CB stuck OPEN sau khi Anthropic recover
# CB tự recover sau WAIT_DURATION (default 60s) với HALF_OPEN test
# Force reset nếu cần (chỉ dùng khi chắc API đã recover):
curl -X POST http://localhost:8080/actuator/circuitbreakers/claudeApiService/reset

# 5. Verify workflows dùng fallback
kubectl logs -n uip-prod -l app=uip-backend \
  --since=10m | grep "RuleBasedFallback"
```

**Severity:** P2 (fallback hoạt động). Lên P1 nếu fallback cũng fail hoặc > 4 giờ.

---

### Alert 4: `SensorIngestRateDrop`

**Grafana alert:** `rate(uip_sensor_ingest_total[5m]) < 0.5 * rate(uip_sensor_ingest_total[1h])` — drop > 50%

#### Check path: EMQX → Kafka → Flink → DB

```bash
# 1. Kiểm tra EMQX broker
kubectl exec -n emqx emqx-0 -- emqx_ctl status
kubectl exec -n emqx emqx-0 -- emqx_ctl broker stats | grep messages

# 2. Kiểm tra Kafka input rate
kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group flink-esg-cleansing-group

# 3. Kiểm tra Flink job status
kubectl exec -n flink flink-jobmanager-0 -- \
  flink list -r 2>/dev/null || \
  curl http://flink-jobmanager:8081/jobs | jq '.jobs[] | select(.status == "RUNNING")'

# 4. Nếu Flink job FAILED → restart
kubectl exec -n flink flink-jobmanager-0 -- \
  flink run -d /opt/flink/usrlib/esg-cleansing-job.jar

# 5. Kiểm tra sensor connectivity (EMQX connected clients)
kubectl exec -n emqx emqx-0 -- emqx_ctl clients list | wc -l
# Nếu count giảm > 50% so với baseline → network issue phía sensor

# 6. Kiểm tra DLQ rate
kubectl exec -n kafka kafka-0 -- \
  kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group flink-error-consumer-group
```

**Root causes phổ biến:**
- EMQX broker restart (clients tự reconnect sau ~30s)
- Flink job OOM → restart loop → check `flink-taskmanager` logs
- Network partition giữa IoT sensors và EMQX
- Kafka broker rebalance (transient, < 2 phút)

**Severity:** P1. P0 nếu ingest = 0 kéo dài > 15 phút.

---

### Alert 5: `HighErrorRate`

**Grafana alert:** `rate(http_server_requests_total{status=~"5.."}[5m]) / rate(http_server_requests_total[5m]) > 0.05` — error rate > 5%

#### Hành động từng bước

```bash
# 1. Xác định endpoint có lỗi nhiều nhất
curl http://localhost:8080/actuator/metrics/http.server.requests | \
  jq '[.measurements[] | select(.statistic=="COUNT")] | sort_by(.value) | reverse | .[0:5]'

# 2. Xem recent errors
kubectl logs -n uip-prod -l app=uip-backend \
  --since=5m | grep -E '"level":"ERROR"' | \
  jq -r '"\(.timestamp) \(.logger) \(.message)"' | tail -20

# 3. Kiểm tra database connection pool
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
# Nếu active = max (default 10) → pool exhausted

# 4. Pool exhaustion → tăng tạm thời
kubectl set env deployment/uip-backend \
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20 \
  -n uip-prod

# 5. Kiểm tra 500 vs 503
# 503 nhiều → load balancer issue hoặc pod không ready
# 500 nhiều → application bug, xem stack trace

# 6. Nếu NullPointerException trong tenant context
kubectl logs -n uip-prod -l app=uip-backend \
  --since=5m | grep "NullPointerException" | grep -i tenant
# → Có thể TenantContext không được set đúng (xem ADR-010)
```

**Severity:** P1 nếu 5–20%. P0 nếu > 20% hoặc specific P0 endpoint (auth, ESG report) fail hoàn toàn.

---

## On-Call Rotation Template

### PagerDuty Schedule: `uip-prod-oncall`

```
Primary: Backend/DevOps Engineer
  - Week rotation (Mon 09:00 → Mon 09:00 ICT)
  - Receives all P0 pages immediately
  - Receives P1 pages after 15 phút auto-escalation

Secondary / Escalation:
  - Backend Lead
  - Receives: P0 + P1 unacknowledged after 15 phút

Final Escalation:
  - CTO
  - Receives: P0 unacknowledged after 30 phút
```

### Handoff checklist

Khi hết ca, on-call engineer phải:
- [ ] Document incidents trong incident log (Confluence: `UIP > Ops > Incidents`)
- [ ] Note open P1/P2 issues cho ca tiếp theo
- [ ] Verify không có alert đang mute không có lý do
- [ ] Xác nhận với người nhận ca về context hiện tại

---

## Quick Reference

| Tình huống | Lệnh nhanh |
|-----------|-----------|
| Xem pod logs | `kubectl logs -n uip-prod -l app=uip-backend --since=10m` |
| Scale up | `kubectl scale deployment uip-backend-blue --replicas=5 -n uip-prod` |
| Restart app | `kubectl rollout restart deployment/uip-backend -n uip-prod` |
| Xem circuit breakers | `curl http://localhost:8080/actuator/circuitbreakers` |
| Kafka consumer lag | `kubectl exec -n kafka kafka-0 -- kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group uip-esg-consumer-group` |
| DB connections | `psql $DB -c "SELECT count(*), state FROM pg_stat_activity GROUP BY state;"` |
| Redis ping | `redis-cli -u $REDIS_URL PING` |

---

## Incident Log Template

```markdown
## Incident <DATE>-<SEQ>

**Severity:** P0 / P1 / P2
**Start:** HH:MM ICT
**Resolved:** HH:MM ICT
**Duration:** X phút

**Symptoms:** <mô tả ngắn>

**Root cause:** <nguyên nhân gốc rễ>

**Timeline:**
- HH:MM — Alert fired
- HH:MM — On-call acknowledged
- HH:MM — Root cause identified
- HH:MM — Fix applied
- HH:MM — Resolved + monitoring 15 phút

**Action items:**
- [ ] <preventive action> — @owner — <due date>
```
