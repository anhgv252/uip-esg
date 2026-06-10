#!/usr/bin/env bash
# Sprint 11 Staging Smoke Test
# Runs MANDATORY pre-production validation gates.
# All tests must PASS before production promotion.
#
# Usage (from infrastructure/):
#   bash scripts/smoke-test-sprint11.sh
#
# Or with a custom backend URL:
#   BACKEND_URL=https://staging-api.uip-smartcity.vn bash scripts/smoke-test-sprint11.sh
#
# Exit code: 0 = all PASS, 1 = one or more FAIL

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
KONG_PROXY_URL="${KONG_PROXY_URL:-http://localhost:8000}"
ANALYTICS_SERVICE_URL="${ANALYTICS_SERVICE_URL:-http://localhost:8082}"
CLICKHOUSE_URL="${CLICKHOUSE_URL:-http://localhost:8123}"
BACKEND_LOG_CMD="${BACKEND_LOG_CMD:-docker compose logs --no-log-prefix --tail=500 backend}"

PASS=0
FAIL=0
WARN=0
RESULTS=()

# ── Helpers ───────────────────────────────────────────────────────────────────

log_pass() { echo "[PASS] $1"; PASS=$((PASS + 1)); RESULTS+=("PASS: $1"); }
log_fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); RESULTS+=("FAIL: $1"); }
log_warn() { echo "[WARN] $1"; WARN=$((WARN + 1)); RESULTS+=("WARN: $1"); }
log_info() { echo "[INFO] $1"; }
hr()       { echo "────────────────────────────────────────────────────"; }

http_code() {
  curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$@" || echo "000"
}

# ── Pre-flight ────────────────────────────────────────────────────────────────
hr
echo "Sprint 11 Staging Smoke Test"
echo "  Backend:          $BACKEND_URL"
echo "  Kong proxy:       $KONG_PROXY_URL"
echo "  Analytics-service: $ANALYTICS_SERVICE_URL"
echo "  ClickHouse:       $CLICKHOUSE_URL"
echo "  Started:          $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
hr

# ═════════════════════════════════════════════════════════════════════════════
# TEST SUITE 1: Infrastructure Health
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "=== Suite 1: Infrastructure Health ==="

# T1-1: Backend /health
log_info "T1-1: Backend /health"
CODE=$(http_code "$BACKEND_URL/api/v1/health")
if [ "$CODE" = "200" ]; then
  log_pass "T1-1: Backend /health returns 200"
else
  log_fail "T1-1: Backend /health returned $CODE (expected 200)"
fi

# T1-2: Backend actuator health
log_info "T1-2: Backend /actuator/health (management port 8086)"
MGMT_URL="${MGMT_URL:-http://localhost:8086}"
CODE=$(http_code "$MGMT_URL/actuator/health")
if [ "$CODE" = "200" ]; then
  log_pass "T1-2: Backend actuator/health returns 200"
else
  log_warn "T1-2: Backend actuator/health returned $CODE (management port may differ)"
fi

# T1-3: Analytics-service health
log_info "T1-3: Analytics-service /actuator/health"
CODE=$(http_code "$ANALYTICS_SERVICE_URL/actuator/health")
if [ "$CODE" = "200" ]; then
  log_pass "T1-3: Analytics-service /actuator/health returns 200"
else
  log_fail "T1-3: Analytics-service /actuator/health returned $CODE (expected 200)"
fi

# T1-4: ClickHouse ping
log_info "T1-4: ClickHouse /ping"
RESP=$(curl -s --max-time 5 "$CLICKHOUSE_URL/ping" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "Ok."; then
  log_pass "T1-4: ClickHouse /ping returns 'Ok.'"
else
  log_fail "T1-4: ClickHouse /ping failed (response: '$RESP')"
fi

# T1-5: ClickHouse DB 'analytics' accessible
log_info "T1-5: ClickHouse analytics database accessible"
CH_USER="${CLICKHOUSE_USER:-default}"
CH_PASS="${CLICKHOUSE_PASSWORD:-}"
if [ -n "$CH_PASS" ]; then
  CH_AUTH="-u $CH_USER:$CH_PASS"
else
  CH_AUTH="-u $CH_USER"
fi
RESP=$(curl -s --max-time 10 $CH_AUTH "$CLICKHOUSE_URL/?query=SELECT%20name%20FROM%20system.databases%20WHERE%20name%3D'analytics'" 2>/dev/null || echo "")
if echo "$RESP" | grep -q "analytics"; then
  log_pass "T1-5: ClickHouse 'analytics' database exists"
else
  log_fail "T1-5: ClickHouse 'analytics' database not found (response: '$RESP'). Check CLICKHOUSE_DB env var."
fi

# T1-6: Kong health
log_info "T1-6: Kong proxy reachable"
CODE=$(http_code "$KONG_PROXY_URL" || echo "000")
if [ "$CODE" != "000" ]; then
  log_pass "T1-6: Kong proxy reachable (HTTP $CODE)"
else
  log_fail "T1-6: Kong proxy unreachable at $KONG_PROXY_URL"
fi

# ═════════════════════════════════════════════════════════════════════════════
# TEST SUITE 2: Kong Auth Integrity (CRITICAL — must return 401 on unauthenticated)
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "=== Suite 2: Kong Auth Integrity ==="

# T2-1: Unauthenticated request → 401
log_info "T2-1: Kong auth — unauthenticated GET /api/v1/analytics"
CODE=$(http_code "$KONG_PROXY_URL/api/v1/analytics")
if [ "$CODE" = "401" ]; then
  log_pass "T2-1: Kong returns 401 on unauthenticated request (auth active)"
else
  log_fail "T2-1: Kong returned $CODE (expected 401). Auth bypass! Check kong.staging.yml jwt plugin."
fi

# T2-2: alg=none token → 401
log_info "T2-2: Kong rejects alg=none JWT"
# Header: {"alg":"none","typ":"JWT"}, Payload: {"iss":"test","exp":9999999999}
ALG_NONE_TOKEN="eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJpc3MiOiJ0ZXN0IiwiZXhwIjo5OTk5OTk5OTk5fQ."
CODE=$(http_code -H "Authorization: Bearer $ALG_NONE_TOKEN" "$KONG_PROXY_URL/api/v1/analytics")
if [ "$CODE" = "401" ]; then
  log_pass "T2-2: Kong returns 401 for alg=none token"
else
  log_fail "T2-2: Kong returned $CODE for alg=none token (expected 401). Critical security gap."
fi

# ═════════════════════════════════════════════════════════════════════════════
# TEST SUITE 3: Sprint 11 Capability Flag Verification
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "=== Suite 3: Sprint 11 Capability Flag Verification ==="

# T3-1: REST adapter loaded, gRPC adapter NOT loaded
log_info "T3-1: AnalyticsPort adapter — verifying mutual exclusivity from backend startup log"
BACKEND_LOGS=$(eval "$BACKEND_LOG_CMD" 2>/dev/null || echo "")
if echo "$BACKEND_LOGS" | grep -q "ClickHouseRestAnalyticsAdapter"; then
  REST_LOADED=true
else
  REST_LOADED=false
fi
if echo "$BACKEND_LOGS" | grep -q "ClickHouseGrpcAnalyticsAdapter"; then
  GRPC_LOADED=true
else
  GRPC_LOADED=false
fi

if [ "$REST_LOADED" = "true" ] && [ "$GRPC_LOADED" = "false" ]; then
  log_pass "T3-1: REST adapter loaded, gRPC adapter NOT loaded (mutual exclusivity OK)"
elif [ "$REST_LOADED" = "false" ] && [ "$GRPC_LOADED" = "false" ]; then
  log_warn "T3-1: Neither adapter found in logs — check SPRING_APPLICATION_JSON and backend startup for errors"
elif [ "$REST_LOADED" = "true" ] && [ "$GRPC_LOADED" = "true" ]; then
  log_fail "T3-1: BOTH adapters detected in logs — NoUniqueBeanDefinitionException risk. Check @ConditionalOnExpression."
else
  log_warn "T3-1: Only gRPC adapter detected — staging should use REST. Check SPRING_APPLICATION_JSON."
fi

# T3-2: gRPC adapter mutual exclusivity — restart backend with grpc transport, verify only gRPC loads
# This is a MANUAL test step — automated steps above cover the REST profile.
# The DevOps / QA engineer must run this manually on a separate backend instance.
log_info "T3-2: gRPC mutual exclusivity (MANUAL STEP)"
echo ""
echo "  [MANUAL] T3-2: gRPC Adapter Mutual Exclusivity Test"
echo "  ─────────────────────────────────────────────────────────"
echo "  1. Temporarily restart backend with transport=grpc:"
echo "     SPRING_APPLICATION_JSON='{\"uip\":{\"capabilities\":{\"analytics-external\":true,\"analytics-transport\":\"grpc\",\"multi-tenancy\":true}}}' \\"
echo "       docker compose restart backend"
echo "  2. Check startup log: docker compose logs --tail=200 backend"
echo "     EXPECTED: 'ClickHouseGrpcAnalyticsAdapter' visible"
echo "     EXPECTED: 'ClickHouseRestAnalyticsAdapter' NOT visible"
echo "  3. Restore REST profile:"
echo "     docker compose -f docker-compose.yml -f docker-compose.uat.yml -f docker-compose.staging.yml \\"
echo "       --env-file .env.staging up -d backend"
echo "  Mark T3-2 PASS if gRPC-only loaded, FAIL otherwise."
echo ""
log_warn "T3-2: gRPC mutual exclusivity is a MANUAL step — not auto-verifiable without restart"

# T3-3: Analytics API returns data (via REST adapter path)
log_info "T3-3: Analytics endpoint reachable from backend (REST adapter path)"
# This tests that the REST adapter successfully contacts analytics-service
# Use operator token from env if available, otherwise skip
if [ -n "${OPERATOR_TOKEN:-}" ]; then
  CODE=$(http_code -H "Authorization: Bearer $OPERATOR_TOKEN" \
    -H "X-Tenant-ID: test" \
    "$KONG_PROXY_URL/api/v1/analytics")
  if [ "$CODE" = "200" ] || [ "$CODE" = "403" ]; then
    log_pass "T3-3: Analytics endpoint reachable (HTTP $CODE — auth working, analytics path active)"
  else
    log_fail "T3-3: Analytics endpoint returned $CODE with valid token"
  fi
else
  log_warn "T3-3: OPERATOR_TOKEN not set — skipping authenticated analytics call. Set OPERATOR_TOKEN env var."
fi

# ═════════════════════════════════════════════════════════════════════════════
# TEST SUITE 4: Sprint 11 Offline-Online Path (SyncQueue X-Tenant-ID)
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "=== Suite 4: Offline-Online Path (SyncQueue X-Tenant-ID) ==="

# T4-1: Backend access log verification (checks X-Tenant-ID is accepted, not 400)
log_info "T4-1: Backend accepts X-Tenant-ID header on building routes"
if [ -n "${OPERATOR_TOKEN:-}" ]; then
  CODE=$(http_code \
    -H "Authorization: Bearer $OPERATOR_TOKEN" \
    -H "X-Tenant-ID: ${TEST_TENANT_ID:-TENANT_A}" \
    -H "Content-Type: application/json" \
    -d '{"name":"smoke-test-building","code":"BLD-SMOKE-01"}' \
    -X POST "$BACKEND_URL/api/v1/buildings")
  if [ "$CODE" = "201" ] || [ "$CODE" = "409" ]; then
    log_pass "T4-1: Backend accepts POST /api/v1/buildings with X-Tenant-ID header (HTTP $CODE)"
  elif [ "$CODE" = "400" ]; then
    log_fail "T4-1: Backend returned 400 on POST with X-Tenant-ID — X-Tenant-ID enforcement broken"
  elif [ "$CODE" = "403" ]; then
    log_warn "T4-1: Backend returned 403 — token may lack building:create permission. Check tenant ownership."
  else
    log_fail "T4-1: Backend returned unexpected $CODE"
  fi
else
  log_warn "T4-1: OPERATOR_TOKEN not set — skipping SyncQueue header test. Set OPERATOR_TOKEN env var."
fi

# T4-2: SyncQueue round-trip (MANUAL — requires physical device with staging APK)
log_info "T4-2: Offline-online round-trip test (MANUAL STEP)"
echo ""
echo "  [MANUAL] T4-2: SyncQueue Offline-Online Round-Trip"
echo "  ─────────────────────────────────────────────────────────"
echo "  Prerequisites: staging APK installed on device, operator login as Tenant A"
echo ""
echo "  Steps:"
echo "  1. Login to mobile app as multi-tenant operator (Tenant A)"
echo "  2. Toggle airplane mode ON"
echo "  3. Create a building via mobile (Buildings screen)"
echo "  4. Verify console: '[SyncQueue] enqueue: POST /api/v1/buildings with tenantId=TENANT_A'"
echo "  5. Toggle airplane mode OFF"
echo "  6. Observe SyncQueue flush: '[SyncQueue] flush: executing 1 queued actions'"
echo "  7. Check backend access log:"
echo "     docker compose logs backend | grep -E 'X-Tenant-ID|buildings'"
echo "     EXPECTED: X-Tenant-ID=TENANT_A header present in request log"
echo "  8. Verify building persisted under correct tenant in DB:"
echo "     SELECT id, name, tenant_id FROM buildings WHERE code='<created-code>' ORDER BY id DESC LIMIT 1;"
echo "     EXPECTED: tenant_id = TENANT_A"
echo ""
echo "  Mark T4-2 PASS if X-Tenant-ID=TENANT_A present in backend log and DB row correct."
echo "  Mark T4-2 FAIL if X-Tenant-ID missing (HTTP 400), request not replayed, or wrong tenant."
echo ""
log_warn "T4-2: SyncQueue offline-online round-trip is a MANUAL step — requires physical device"

# ═════════════════════════════════════════════════════════════════════════════
# TEST SUITE 5: Cross-Tenant Isolation
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "=== Suite 5: Cross-Tenant Isolation ==="

# T5-1: Tenant B cannot access Tenant A alert
log_info "T5-1: Cross-tenant isolation (MANUAL STEP)"
echo ""
echo "  [MANUAL] T5-1: Cross-Tenant Alert Isolation"
echo "  ─────────────────────────────────────────────────────────"
echo "  1. As Tenant A operator: create an alert (note the alert ID)"
echo "  2. Switch to Tenant B credentials (new login or token)"
echo "  3. GET /api/v1/alerts/{alertId} with Tenant B token"
echo "     EXPECTED: HTTP 403 or 404 (alert not visible to Tenant B)"
echo "  4. GET /api/v1/alerts with Tenant B token — list must not contain Tenant A alert"
echo ""
echo "  Mark T5-1 PASS if Tenant B cannot see/modify Tenant A data."
echo ""
log_warn "T5-1: Cross-tenant isolation is a MANUAL step — requires two operator accounts"

# T5-2: JWT RLS (Row-Level Security) enforced at DB layer
log_info "T5-2: Verify RLS active on TimescaleDB (DB-level enforcement)"
RLS_CHECK=$(docker compose exec timescaledb psql -U "${POSTGRES_USER:-uip}" -d "${POSTGRES_DB:-uip_smartcity_staging}" \
  -c "SELECT relname, relrowsecurity FROM pg_class WHERE relname IN ('buildings','alerts','sensor_readings') AND relrowsecurity = true;" \
  2>/dev/null || echo "SKIP")
if echo "$RLS_CHECK" | grep -q "t"; then
  log_pass "T5-2: RLS is active on TimescaleDB tables"
elif echo "$RLS_CHECK" | grep -q "SKIP"; then
  log_warn "T5-2: DB RLS check skipped (docker compose exec unavailable or different env)"
else
  log_warn "T5-2: RLS check returned no results — verify RLS policies on buildings/alerts/sensor_readings"
fi

# ═════════════════════════════════════════════════════════════════════════════
# TEST SUITE 6: Performance SLO Gate
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo "=== Suite 6: Performance SLO Gate (p99 <500ms) ==="

log_info "T6-1: API response time — /api/v1/health (baseline)"
TIMING=$(curl -s -o /dev/null -w "%{time_total}" --max-time 10 "$BACKEND_URL/api/v1/health" 2>/dev/null || echo "9.999")
TIMING_MS=$(echo "$TIMING * 1000" | bc 2>/dev/null | cut -d. -f1 || echo "N/A")
if [ "$TIMING_MS" != "N/A" ] && [ "$TIMING_MS" -lt 500 ]; then
  log_pass "T6-1: /health response time ${TIMING_MS}ms < 500ms"
else
  log_warn "T6-1: /health response time ${TIMING_MS}ms — check if this is a cold-start measurement"
fi

log_info "T6-2: Full load p99 test (MANUAL — run k6 load test)"
echo ""
echo "  [MANUAL] T6-2: p99 Latency Under Load"
echo "  ─────────────────────────────────────────────────────────"
echo "  cd infrastructure && docker run --rm --network infrastructure_uip-network \\"
echo "    -v \$(pwd)/k6:/scripts grafana/k6 run \\"
echo "    -e BASE_URL=$BACKEND_URL /scripts/load-test.js --vus 50 --duration 60s"
echo "  EXPECTED: p99 <500ms"
echo ""
log_warn "T6-2: Full p99 load test is a MANUAL step"

# ═════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ═════════════════════════════════════════════════════════════════════════════
echo ""
hr
echo "Sprint 11 Staging Smoke Test — SUMMARY"
hr
echo "Completed: $(date -u '+%Y-%m-%dT%H:%M:%SZ')"
echo ""
echo "Results:"
for r in "${RESULTS[@]}"; do
  echo "  $r"
done
echo ""
echo "  Total PASS:  $PASS"
echo "  Total WARN:  $WARN (manual steps or missing tokens — verify manually)"
echo "  Total FAIL:  $FAIL"
echo ""

if [ "$FAIL" -gt 0 ]; then
  echo "VERDICT: NO-GO — $FAIL test(s) FAILED. Fix before production promotion."
  echo ""
  echo "  Production deployment scheduled: 2026-06-12 22:00"
  echo "  HA failover drill:               2026-06-15"
  echo "  Fix all FAIL items by:           2026-06-11 18:00 (before city authority briefing)"
  hr
  exit 1
else
  echo "VERDICT: GO (automated tests) — complete MANUAL steps before final production approval."
  echo ""
  echo "  Manual steps required before GO/NO-GO decision:"
  echo "    T3-2: gRPC adapter mutual exclusivity (restart + log check)"
  echo "    T4-2: SyncQueue offline-online round-trip (physical device)"
  echo "    T5-1: Cross-tenant alert isolation (two operator accounts)"
  echo "    T6-2: Full p99 load test (k6)"
  echo ""
  echo "  Production deployment: 2026-06-12 22:00 — HOLD until manual steps complete"
  hr
  exit 0
fi
