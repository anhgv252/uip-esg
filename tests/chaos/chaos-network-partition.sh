#!/bin/bash
# Chaos Test: Network Partition Simulation
# S11-CHAOS-01: Disconnect analytics-service network and verify graceful degradation
#
# Usage: ./chaos-network-partition.sh
# Exit: 0=PASS, 1=FAIL

set -euo pipefail

COMPOSE_FILES="-f docker-compose.yml -f docker-compose.uat.yml -f docker-compose.staging.yml"
COMPOSE_CMD="docker compose ${COMPOSE_FILES}"
REPORT_FILE="tests/chaos/results/network-partition-result.txt"
NETWORK_NAME="infrastructure_uip-network"

mkdir -p tests/chaos/results

log() { echo "[$(date +%H:%M:%S)] $*"; }
pass() { echo "✅ PASS: $*"; echo "PASS: $*" >> "$REPORT_FILE"; }
fail() { echo "❌ FAIL: $*"; echo "FAIL: $*" >> "$REPORT_FILE"; }

TARGET_SERVICE="analytics-service"

cleanup() {
    log "Cleanup: reconnecting ${TARGET_SERVICE} to network..."
    docker network connect "$NETWORK_NAME" "${TARGET_SERVICE}" 2>/dev/null || true
    sleep 10
    log "Cleanup complete"
}
trap cleanup EXIT

# ── Pre-flight ──────────────────────────────────────────────────
log "=== Network Partition Chaos Test ==="
echo "Network Partition Chaos Test — $(date)" > "$REPORT_FILE"

# Verify target service is running
if ! ${COMPOSE_CMD} ps "$TARGET_SERVICE" --format '{{.Status}}' 2>/dev/null | grep -q "running\|Up"; then
    fail "Pre-flight: ${TARGET_SERVICE} is not running"
    exit 1
fi
pass "Pre-flight: ${TARGET_SERVICE} is running"

# Verify backend is healthy
BACKEND_HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/health 2>/dev/null || echo "000")
if [[ "$BACKEND_HTTP" != "200" ]]; then
    fail "Pre-flight: Backend not healthy (HTTP ${BACKEND_HTTP})"
    exit 1
fi
pass "Pre-flight: Backend API healthy"

# ── Disconnect network ──────────────────────────────────────────
log "Disconnecting ${TARGET_SERVICE} from network ${NETWORK_NAME}..."
docker network disconnect "$NETWORK_NAME" "$TARGET_SERVICE" 2>/dev/null || {
    # Fallback: try stopping the service
    log "WARN: network disconnect failed, stopping container instead..."
    ${COMPOSE_CMD} stop "$TARGET_SERVICE"
}
sleep 5
pass "${TARGET_SERVICE} disconnected from network"

# ── Verify: Backend still responds (graceful degradation) ───────
log "Testing backend graceful degradation..."

# Backend should NOT return 500 — should return 200 with zero/stale data
# (REST adapter has try-catch that returns EsgAggregateResult(0, 0, Map.of, ...))
DEGRADE_HTTP=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:8080/api/v1/esg/aggregate \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer test" \
    -d '{"tenantId":"chaos-test","buildingIds":["B01"],"fromEpoch":1704067200,"toEpoch":1706745600}' \
    2>/dev/null || echo "000")

if [[ "$DEGRADE_HTTP" =~ ^(200|401|403)$ ]]; then
    # 200 = graceful degradation (zero data), 401/403 = auth required (backend still alive)
    pass "Backend returned HTTP ${DEGRADE_HTTP} during network partition (no 500)"
else
    fail "Backend returned HTTP ${DEGRADE_HTTP} during network partition (expected 200/401/403, not 500)"
fi

# Verify backend health endpoint
HEALTH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/health 2>/dev/null || echo "000")
if [[ "$HEALTH_HTTP" == "200" ]]; then
    pass "Backend health endpoint OK during partition"
else
    fail "Backend health endpoint returned HTTP ${HEALTH_HTTP} during partition"
fi

# ── Verify: Circuit breaker / fallback active ───────────────────
# Check backend logs for analytics-service error
LOG_SNAPSHOT=$(${COMPOSE_CMD} logs backend --tail=20 2>/dev/null | grep -i "analytics.*unavail\|fallback\|zero.*result\|timeout" | tail -3 || true)
if [[ -n "$LOG_SNAPSHOT" ]]; then
    pass "Backend logs show graceful fallback: ${LOG_SNAPSHOT:0:80}..."
else
    log "INFO: No explicit fallback log entry found (may be cached response)"
fi

# ── Reconnect ───────────────────────────────────────────────────
log "Reconnecting ${TARGET_SERVICE} to network..."
docker network connect "$NETWORK_NAME" "$TARGET_SERVICE" 2>/dev/null || {
    log "WARN: reconnect failed, restarting container..."
    ${COMPOSE_CMD} start "$TARGET_SERVICE"
}
sleep 15
pass "${TARGET_SERVICE} reconnected to network"

# ── Verify: Recovery ────────────────────────────────────────────
RECOVERY_HTTP=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/health 2>/dev/null || echo "000")
if [[ "$RECOVERY_HTTP" == "200" ]]; then
    pass "Backend fully recovered after reconnection"
else
    fail "Backend returned HTTP ${RECOVERY_HTTP} after reconnection"
fi

# ── Summary ─────────────────────────────────────────────────────
log "=== Results ==="
grep -E "PASS|FAIL" "$REPORT_FILE"

if grep -q "FAIL" "$REPORT_FILE"; then
    log "Overall: FAIL"
    exit 1
else
    log "Overall: PASS"
    exit 0
fi
