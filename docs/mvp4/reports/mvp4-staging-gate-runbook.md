# MVP4 Staging Gate Runbook — G1, G2, G5 Execution Procedures

| Field | Value |
|---|---|
| **Audience** | DevOps + QA |
| **Purpose** | Step-by-step procedures to execute the 3 staging-dependent quality gates (G1 AI cost, G2 false-positive, G5 JMeter 1000 VU) |
| **Prerequisite** | MVP4 code deployed to staging per `docs/mvp4/project/task-devops.md`; both Flink jobs (`DistrictAggregationJob`, `IncidentCorrelationJob`) running |

> Gates G6 (app stores), G9 (OWASP — run separately), G10 (30-day pilot uptime) are covered in `sprint6-mvp4-gate-review.md` §1.

---

## G1 — AI cost < $1/day @ 10K sensors

**Goal:** prove the Flink batching + routing + caching + budget stack reduces AI calls from ~600K/min (per-reading) to ~50/min (per-district-window).

### Procedure

1. **Deploy Flink job** (DevOps):
   ```bash
   cd flink-jobs && mvn package -Pflink-jar
   # POST target/uip-flink-jobs-*.jar to Flink REST API (same flow as VibrationAnomalyJob)
   ```
   Verify job RUNNING in Flink UI with source `ngsi_ld_environment` + sink `ai.district.aggregations`.

2. **Configure Claude API key** on staging backend:
   - `CLAUDE_API_KEY` env var set (real key, test billing account).
   - Confirm `AiCostMetrics` (`ai_cost_usd_total`) appears in Prometheus.

3. **Simulate 10K sensors** (perf harness, 60 min):
   ```bash
   # Use the perf injector pointed at ngsi_ld_environment with 10K sensor IDs
   # across 10 districts, AQI readings every 60s
   ./scripts/sprint2-load-test.sh   # adapt sensor count
   ```
   Target: 10K sensors × 1 reading/min × 60 min = 600K readings injected.

4. **Measure** (24h cumulative or extrapolate from 1h):
   - Grafana `docs/mvp4/grafana/ai-cost-dashboard.json` → panel "Cost today (USD)".
   - Cross-check: `ai_batched_events_consumed_total` counter should be ~10/min (10 districts × 1 window), NOT 10K/min.
   - Verify `ai_cache_hits_total` / (`hits`+`misses`) ≥ 50%.

5. **Pass criterion:** cumulative `ai_cost_usd_total` < $1.00 over the window (or extrapolated 24h < $1).
   **Expected:** ~$0.60/day (83x reduction). If > $1, check batching job is actually emitting (step 1) and cache hit rate (step 4).

### Common failures
- `ai.district.aggregations` empty → Flink job not running or wrong source topic.
- Cost still ~$50/day → consumer not calling `AiInferenceService` (check `ai_batched_events_consumed_total` = 0).
- Hit rate < 50% → cache TTL misconfigured or district AQI not bucketing (check `AqiRangeBucket`).

---

## G2 — False positive < 5% on 30-day data

**Goal:** confirm multi-sensor correlation produces incidents only when ≥3 distinct types fire together, with <5% false-positive rate over real pilot data.

### Procedure

1. **Deploy correlation Flink job** (DevOps):
   ```bash
   cd flink-jobs && mvn package -Pflink-jar
   # deploy IncidentCorrelationJob
   ```
   Verify source `UIP.flink.alert.detected.v1` + sink `correlated.incidents`.

2. **Run 30-day pilot** (Aug 2026 onwards) with real sensor data across ≥5 buildings.

3. **Measure** at day 30:
   ```sql
   -- Total incidents created
   SELECT COUNT(*) FROM correlated_incidents WHERE detected_at > now() - interval '30 days';
   -- Incidents later marked false-positive by operator feedback
   SELECT COUNT(*) FROM correlated_incidents ci
     JOIN alert_events ae ON ae.building_id = ci.building_id
     WHERE ae.feedback_correct = false AND ae.feedback_at > ci.detected_at;
   ```

4. **Pass criterion:** false-positive rate = (feedback_correct=false incidents) / (total incidents) < 5%.
   **Boundary already verified:** 2-sensor score 0.556 < 0.6 threshold → no false correlation (see `sprint4-correlation-test-results.md`).

### Common failures
- Single-sensor-type floods producing incidents → distinct-type check in `evaluateWindow` not enforced (verify `IncidentCorrelationJobTest#evaluateWindow_repeatedSingleType_empty`).
- Score inflation → check `COR_MIN_SCORE` env (default 0.6); do not lower without SA sign-off.

---

## G5 — JMeter 1000 VU PASS

**Goal:** p95 < 500ms, error < 1%, throughput > 500 RPS sustained over 5-minute hold.

### Procedure

1. **Plan exists:** `backend/src/test/resources/jmeter/uip-1000vu-plan.jmx` (1000 VU, 60s ramp, 300s hold).

2. **Run on staging:**
   ```bash
   jmeter -n -t backend/src/test/resources/jmeter/uip-1000vu-plan.jmx \
          -l results.jtl -e -o report/
   ```

3. **Read HTML report** (`report/index.html`):
   - Statistics → p95 latency (target < 500ms).
   - Error % (target < 1%).
   - Throughput (target > 500 RPS).

4. **Compare vs S2 baseline** (documented in `sprint2-pact-contracts.md` / perf records).

### Common failures
- p95 > 500ms → check Tomcat thread pool / HikariCP sizing (see `feedback_mvp2_demo_and_perf_lessons.md`); verify read-heavy endpoints cached.
- Error spike → check rate limiter (`X-RateLimit-Remaining`), JWT validation hot path.

---

## G4 note — Pact provider test re-enable

`BackendProviderPactTest` is `@Disabled` because no `provider: backend` pact files exist (consumer tests only generate backend-as-consumer pacts). To re-enable:

1. Frontend + mobile apps must publish `provider: backend` contracts to a Pact broker.
2. Set `pactbroker.url` in CI (no broker config exists today — `grep PACT_BROKER backend/build.gradle` returns empty).
3. Remove `@Disabled` from `backend/src/test/java/com/uip/backend/contract/BackendProviderPactTest.java`.

Until then, G4 passes on the strength of 42 `@Tag("contract")` REST Assured tests + consumer-side Pact tests; provider verification is a CI hardening task (not a code defect).

---

## Summary checklist for DevOps/QA

- [ ] G1: deploy `DistrictAggregationJob` + inject 10K sensors + read Grafana cost
- [ ] G2: deploy `IncidentCorrelationJob` + 30-day pilot + measure FP rate
- [ ] G5: run JMeter plan on staging + verify p95/error/throughput
- [ ] G6: submit apps per `docs/mvp4/project/{ios,android}-*-submission.md`
- [ ] G9: OWASP scan result (separate — see `sprint6-mvp4-gate-review.md` G9)
- [ ] G10: 30-day Prometheus uptime ≥ 99.5%

After all 10 gates PASS → QA signs Task #26 → PM declares MVP4 DONE (Task #27).
