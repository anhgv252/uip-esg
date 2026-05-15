# analytics-service Cutover Runbook

**Service:** analytics-service (ClickHouse OLAP queries)
**Story:** v3-EXT-04
**DRI:** Product Owner
**Sprint:** MVP3-2

---

## Pre-Cutover Checklist

Verify ALL items are GREEN before proceeding:

- [ ] analytics-service healthy: `curl http://analytics-service:8081/actuator/health` → 200
- [ ] ClickHouse has data: `SELECT count() FROM analytics.esg_readings` ≥ 1,000 rows
- [ ] Shadow 72h diff <0.01% (check daily audit log)
- [ ] Monolith config: `UIP_ANALYTICS_SERVICE_URL` set correctly
- [ ] ReplacingMergeTree deployed (TD-01 DONE)
- [ ] Cutover runbook reviewed by CTO

---

## Cutover Procedure

### Step 1: Announce maintenance window

```
Slack #uip-deploy: "Starting analytics-service cutover — estimated 5 min. Rollback <5 min if needed."
```

### Step 2: Pre-cutover snapshot

```bash
# Record current state
kubectl -n uip get configmap uip-config -o yaml > /tmp/uip-config-pre-cutover.yaml

# Verify shadow diff
curl -s http://analytics-service:8081/actuator/health
```

### Step 3: Flip feature flag

```bash
# Tier 2 staging
kubectl -n uip patch configmap uip-config \
  --patch '{"data":{"analytics-external":"true"}}'

# Trigger rolling restart
kubectl -n uip rollout restart deployment/uip-monolith
kubectl -n uip rollout status deployment/uip-monolith --timeout=300s
```

### Step 4: Post-cutover validation (5 min)

```bash
# Verify monolith delegates to analytics-service
curl -s http://monolith:8080/actuator/beans | grep -i analytics
# Should NOT show TimescaleDbAnalyticsAdapter beans loaded

# Verify analytics-service receives traffic
curl -s -H "Authorization: Bearer $TOKEN" \
  http://analytics-service:8081/api/v1/analytics/energy-aggregate \
  -X POST -H "Content-Type: application/json" \
  -d '{"tenantId":"alpha","buildingIds":[],"fromEpoch":1716163200,"toEpoch":1716768000}'

# Verify ClickHouse query returns data
curl -s "http://clickhouse:8123/?query=SELECT+count()+FROM+analytics.esg_readings"
```

### Step 5: Monitor for 4 hours

- Watch Grafana: analytics-service request rate, error rate, latency
- Watch monolith: alert API p95 unchanged
- Watch ClickHouse: query performance

---

## Rollback Procedure (<5 minutes)

```bash
# Flip flag back
kubectl -n uip patch configmap uip-config \
  --patch '{"data":{"analytics-external":"false"}}'

# Rolling restart
kubectl -n uip rollout restart deployment/uip-monolith
kubectl -n uip rollout status deployment/uip-monolith --timeout=300s

# Verify monolith loads TimescaleDbAnalyticsAdapter
curl -s http://monolith:8080/actuator/beans | grep TimescaleDbAnalyticsAdapter
```

### Rollback Triggers

- analytics-service error rate >1%
- ClickHouse query timeout >5s sustained
- Monolith alert API p95 degrades >10ms
- Any P0 bug discovered

---

## Schedule

| Environment | Window | Date |
|-------------|--------|------|
| DEV | Any time | Mon 2026-05-26 09:00 SGT |
| UAT | Business hours | Mon 2026-05-26 10:00 SGT |
| PROD-SHADOW | Low traffic (3-7am) | Tue 2026-05-27 03:00 SGT |

---

## Sign-Off

| Role | Name | Date |
|------|------|------|
| Product Owner | TBD | 2026-05-23 |
| Tech Lead / CTO | TBD | 2026-05-23 |
| DevOps | TBD | 2026-05-26 |
