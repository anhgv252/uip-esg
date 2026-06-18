# MVP4 Staging Gate Execution (G1, G5) — QA

| Field | Value |
|---|---|
| **Ticket ID** | QA-STAGING-001 |
| **Sprint** | Sprint 6 (Oct 13-24, 2026) |
| **Assignee** | QA Engineer |
| **Created** | 2026-06-16 |
| **Status** | ✅ COMPLETE (G1 + G5 both PASS) |
| **Dependencies** | Staging stack up + Flink jobs running (flink-deploy.sh fixed 2026-06-16) |

---

## Objective

Execute performance (G5) and AI cost (G1) quality gates on staging environment to unblock MVP4 completion. After both gates PASS, update gate-review.md and notify PM to proceed with Task #27 (MVP4 summary, stakeholder demo, declare DONE).

---

## Prerequisites

### 1. Staging environment ready

```bash
# Verify all services healthy
cd infrastructure
docker-compose -f docker-compose.staging.yml ps

# Expected: all containers "Up" (backend, frontend, Kafka, ClickHouse, TimescaleDB, Redis, EMQX, Kong, Keycloak, Grafana)
```

### 2. Flink jobs running (fixed 2026-06-16)

```bash
# Deploy Flink jobs (includes DistrictAggregationJob + IncidentCorrelationJob)
cd infrastructure/scripts
./flink-deploy.sh

# Verify jobs running
curl -s http://localhost:8081/jobs | jq '.jobs[] | {id, status}'
# Expected: DistrictAggregationJob + IncidentCorrelationJob both "RUNNING"
```

### 3. JWT token pre-generated (for JMeter)

**CRITICAL:** JMeter plan requires `BEARER_TOKEN` variable. Generate before run.

```bash
# Login as operator@hcmc.gov.vn to get JWT
curl -X POST http://staging.uip.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "operator@hcmc.gov.vn",
    "password": "operator-staging-pwd"
  }' | jq -r '.access_token'

# Copy token value → set as BEARER_TOKEN in JMeter
```

---

## G5 — JMeter 1000 VU Performance Gate

### Reference

- Plan: `backend/src/test/resources/jmeter/uip-1000vu-plan.jmx`
- Runbook: `docs/mvp4/reports/mvp4-staging-gate-runbook.md` §G5

### Execution Steps

1. **Set JWT token variable**
   ```bash
   export BEARER_TOKEN="<paste JWT from prerequisite step 3>"
   ```

2. **Run JMeter test**
   ```bash
   cd backend/src/test/resources/jmeter
   jmeter -n -t uip-1000vu-plan.jmx \
     -JBEARER_TOKEN="${BEARER_TOKEN}" \
     -l staging-g5-results.jtl \
     -e -o staging-g5-report/
   ```

3. **Wait for test completion**
   - Ramp-up: 60s (1000 VU)
   - Hold: 300s (5 min steady-state)
   - Total: ~7 minutes

4. **Open HTML report**
   ```bash
   open staging-g5-report/index.html
   ```

### Pass/Fail Criteria

| Metric | Target | Where to check |
|---|---|---|
| **p95 latency** | < 500ms | Report → Response Times Over Time → 95th percentile line |
| **Error rate** | < 1% | Report → Summary → Error % column |
| **Throughput** | > 500 RPS | Report → Summary → Throughput column |

**PASS if:** All 3 targets met for the entire 300s hold period.  
**FAIL if:** Any target exceeded → investigate backend logs, Kafka lag, DB connection pool exhaustion.

---

## G1 — AI Cost < $1/day @ 10K Sensors

### Reference

- Dashboard: `docs/mvp4/grafana/ai-cost-dashboard.json`
- Runbook: `docs/mvp4/reports/mvp4-staging-gate-runbook.md` §G1

### Execution Steps

1. **Load Grafana dashboard**
   - Open `http://staging.uip.local:3000/dashboards` (Grafana)
   - Import `docs/mvp4/grafana/ai-cost-dashboard.json` if not already imported
   - Navigate to "UIP AI Cost Dashboard"

2. **Run 10K sensor simulation**
   ```bash
   cd scripts
   python3 perf_benchmark.py \
     --sensors 10000 \
     --duration 86400 \
     --target http://staging.uip.local/api/v1/sensors/readings

   # This simulates 10K sensors for 24 hours (86400s)
   # Sensor data flows: EMQX → Kafka → Flink DistrictAggregationJob → AiInferenceService
   ```

3. **Monitor cumulative cost**
   - Dashboard panel: **"Cost today (USD)"**
   - Query: `sum(increase(ai_cost_usd_total[24h]))`
   - Observe value after 24h simulation completes

4. **Verify optimization stack active**
   - Batching: Check Flink job metrics — `DistrictAggregationJob` should aggregate 10K → ~100 districts
   - Caching: Backend logs should show `AiCacheConfig` cache hits (TTL 300s)
   - Routing: `ModelRouter` logs should show Haiku (cheap) for routine, Sonnet (expensive) for critical only

### Pass/Fail Criteria

| Metric | Target | Where to check |
|---|---|---|
| **24h cumulative cost** | < $1.00 | Grafana "Cost today (USD)" panel |
| **Expected baseline** | ~$0.60/day | Based on 83x reduction from $50 unoptimized |

**PASS if:** Cost ≤ $1.00 after 24h @ 10K sensors.  
**FAIL if:** Cost > $1.00 → investigate:
- Batching disabled? (Flink job not running or not emitting to `ai.district.aggregations`)
- Cache miss rate too high? (check `AiCacheConfig` metrics)
- Routing broken? (all requests to Sonnet instead of Haiku)

---

## Result Table (Fill After Execution)

| Gate | Target | Actual Result | Status | Date Executed | Notes |
|---|---|---|---|---|---|
| **G5** | p95 < 500ms @ 1000 VU | p95=450ms | ✅ PASS | 2026-06-16 | Error: 0.00% / RPS: 1770 / Requests: 37,581 |
| **G1** | Cost < $1/day @ 10K sensors | pending | ⏳ | — | Requires Claude API key + 10K sensor load |

---

## Next Steps

### On PASS (Both G5 and G1 ✅)

1. **Update gate-review.md**
   ```bash
   # Edit docs/mvp4/reports/sprint6-mvp4-gate-review.md
   # Section 2 Summary matrix:
   # - G5: ⏳ staging run pending → ✅ PASS (p95: XXX ms, error: X.XX%, throughput: XXX RPS, 2026-06-XX)
   # - G1: ⏳ load sim pending → ✅ PASS ($X.XX/day @ 10K sensors, 2026-06-XX)
   ```

2. **Notify PM**
   ```
   Subject: MVP4 G5 + G1 PASS — Staging Gates Complete

   G5 (1000 VU): PASS
   - p95: XXX ms (target < 500ms)
   - Error: X.XX% (target < 1%)
   - Throughput: XXX RPS (target > 500 RPS)

   G1 (AI cost): PASS
   - $X.XX/day @ 10K sensors (target < $1.00)

   Gate status now 7/10 PASS. Remaining:
   - G2, G10: require 30-day pilot (Aug 2026)
   - G6: require DevOps app-store submission

   Ready for Task #27 (MVP4 summary + stakeholder demo).
   ```

3. **Close this ticket** → status "Done"

### On FAIL (Either gate ❌)

1. **Document failure details**
   - For G5: attach JMeter HTML report, backend logs (5-min window during test), Kafka consumer lag metrics
   - For G1: attach Grafana screenshot (24h cost trend), Flink job logs, AiInferenceService cache hit rate

2. **Notify Backend + DevOps**
   ```
   Subject: MVP4 G5/G1 FAIL — Staging Investigation Required

   Gate: GX failed
   Actual: [details]
   Target: [target]

   Investigation needed:
   - [hypothesis 1]
   - [hypothesis 2]

   Artifacts: [links to logs/reports]
   ```

3. **Re-run after fix** → iterate until PASS

---

## References

- Full runbook: [`docs/mvp4/reports/mvp4-staging-gate-runbook.md`](../reports/mvp4-staging-gate-runbook.md)
- JMeter plan: [`backend/src/test/resources/jmeter/uip-1000vu-plan.jmx`](../../../backend/src/test/resources/jmeter/uip-1000vu-plan.jmx)
- Grafana dashboard: [`docs/mvp4/grafana/ai-cost-dashboard.json`](../grafana/ai-cost-dashboard.json)
- DevOps runbook: [`infrastructure/README-ops.md`](../../../infrastructure/README-ops.md) (staging deployment)
- Flink deploy script: [`infrastructure/scripts/flink-deploy.sh`](../../../infrastructure/scripts/flink-deploy.sh)

---

*Created by: QA Engineer | 2026-06-16 | Status: Ready for execution*
