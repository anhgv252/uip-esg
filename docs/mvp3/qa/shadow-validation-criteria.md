# analytics-service Shadow Validation Criteria

**Sprint:** MVP3-1 (shadow deploy) → MVP3-2 (cutover gate)  
**Purpose:** Validate analytics-service (ClickHouse) produces identical results vs monolith (TimescaleDB) before cutover  
**Status:** ACTIVE

---

## Shadow Mode Architecture

```
HTTP Request to analytics endpoint
  ├── Primary: monolith → TimescaleDB adapter → response (used)
  └── Shadow: analytics-service → ClickHouse adapter → response (compared, discarded)

Diff logged to: analytics-shadow-diff.log
Prometheus metric: uip_analytics_shadow_diff_ratio
```

---

## Validation Thresholds

| Metric | Threshold | Action if Exceeded | Hard Block |
|--------|-----------|-------------------|-----------|
| Row count diff (per query) | < 0.01% | Alert + investigate | YES (cutover blocked) |
| Value sum diff (kWh total) | < 0.01% relative | Alert + investigate | YES |
| Latency p95 ratio (svc/mono) | < 1.5x | Monitor, no block | NO |
| Error rate analytics-service | < 0.1% | Alert | YES |
| Sustained validation duration | ≥ 72h | Must complete before cutover | YES |

---

## Comparison Script

`tests/shadow/compare-outputs.sh`:

```bash
#!/bin/bash
# Compare monolith vs analytics-service for same query
# Usage: ./compare-outputs.sh <tenant_id> <from_date> <to_date>

TENANT_ID=${1:-hcm}
FROM=${2:-"2026-04-01T00:00:00Z"}
TO=${3:-"2026-04-30T23:59:59Z"}

MONO_URL="http://localhost:8080"
ANALYTICS_URL="http://localhost:8082"

# Query both endpoints
MONO_RESPONSE=$(curl -s -X POST "${MONO_URL}/api/v1/esg/summary" \
  -H "X-Tenant-ID: ${TENANT_ID}" \
  -H "Content-Type: application/json" \
  -d "{\"from\":\"${FROM}\",\"to\":\"${TO}\"}")

ANALYTICS_RESPONSE=$(curl -s -X POST "${ANALYTICS_URL}/cross-building-aggregate" \
  -H "X-Tenant-ID: ${TENANT_ID}" \
  -H "Content-Type: application/json" \
  -d "{\"from\":\"${FROM}\",\"to\":\"${TO}\"}")

echo "Monolith response:"
echo "$MONO_RESPONSE" | python3 -m json.tool

echo ""
echo "Analytics-service response:"
echo "$ANALYTICS_RESPONSE" | python3 -m json.tool

# Diff check (requires jq)
MONO_SUM=$(echo "$MONO_RESPONSE" | jq -r '.totalEnergy // 0')
ANALYTICS_SUM=$(echo "$ANALYTICS_RESPONSE" | jq -r '[.[] | .totalValue] | add // 0')

if [ "$(echo "$MONO_SUM $ANALYTICS_SUM" | awk '{diff=($1-$2)/$1*100; if(diff<0) diff=-diff; print (diff < 0.01) ? "PASS" : "FAIL"}')" = "PASS" ]; then
  echo "✓ Shadow diff PASS: Mono=$MONO_SUM Analytics=$ANALYTICS_SUM (diff < 0.01%)"
  exit 0
else
  echo "✗ Shadow diff FAIL: Mono=$MONO_SUM Analytics=$ANALYTICS_SUM (diff >= 0.01%)"
  exit 1
fi
```

---

## Sign-off Requirements

Before setting `analytics-external: "true"` in `values-tier2.yaml`:

1. **QA sign-off:** Shadow diff <0.01% for 72h sustained → QA Engineer sign `sprint1-gate-checklist.md`
2. **Backend Lead sign-off:** analytics-service `/actuator/health` = UP, ClickHouse has ≥1000 rows
3. **PM sign-off:** No P0/P1 open bugs related to analytics

### Monitoring Dashboard

Grafana panel: `uip_analytics_shadow_diff_ratio` gauge  
Alert threshold: > 0.0001 (= 0.01%) → Slack #uip-alerts

---

## Cutover Procedure (Sprint 2)

When shadow diff < 0.01% sustained 72h:

```yaml
# values-tier2.yaml — ONLY change needed
uip:
  capabilities:
    analytics-external: "true"
  analytics-service:
    url: "http://analytics-service.uip.svc.cluster.local:8082"
```

Rollback (< 5 min):
```bash
kubectl -n uip patch configmap uip-config \
  --patch '{"data":{"UIP_CAPABILITIES_ANALYTICS_EXTERNAL":"false"}}'
kubectl -n uip rollout restart deployment/uip-monolith
```
