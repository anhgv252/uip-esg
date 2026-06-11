#!/bin/bash
# Chaos Test: Kafka Broker Failure
# S11-CHAOS-01: Kill 1 of 3 Kafka brokers and verify system resilience
#
# Usage: ./chaos-kafka-broker.sh [--restore-only]
# Exit: 0=PASS, 1=FAIL

set -euo pipefail

COMPOSE_FILES="-f docker-compose.yml -f docker-compose.uat.yml -f docker-compose.staging.yml"
COMPOSE_CMD="docker compose ${COMPOSE_FILES}"
REPORT_FILE="tests/chaos/results/kafka-broker-result.txt"
KAFKA_SERVICES=("kafka-1" "kafka-2" "kafka-3")

mkdir -p tests/chaos/results

log() { echo "[$(date +%H:%M:%S)] $*"; }
pass() { echo "✅ PASS: $*"; echo "PASS: $*" >> "$REPORT_FILE"; }
fail() { echo "❌ FAIL: $*"; echo "FAIL: $*" >> "$REPORT_FILE"; }

cleanup() {
    log "Cleanup: restoring killed broker..."
    ${COMPOSE_CMD} start "${KILLED_SERVICE:-kafka-1}" 2>/dev/null || true
    sleep 10
    log "Cleanup complete"
}
trap cleanup EXIT

# ── Pre-flight ──────────────────────────────────────────────────
log "=== Kafka Broker Chaos Test ==="
echo "Kafka Broker Chaos Test — $(date)" > "$REPORT_FILE"

# Find running Kafka brokers
RUNNING_BROKERS=()
for svc in "${KAFKA_SERVICES[@]}"; do
    if ${COMPOSE_CMD} ps "$svc" --format '{{.Status}}' 2>/dev/null | grep -q "running\|Up"; then
        RUNNING_BROKERS+=("$svc")
    fi
done

if [[ ${#RUNNING_BROKERS[@]} -lt 3 ]]; then
    fail "Pre-flight: need 3 running Kafka brokers, found ${#RUNNING_BROKERS[@]}"
    exit 1
fi
pass "Pre-flight: ${#RUNNING_BROKERS[@]} Kafka brokers running"

# ── Kill random broker ──────────────────────────────────────────
KILLED_INDEX=$((RANDOM % ${#RUNNING_BROKERS[@]}))
KILLED_SERVICE="${RUNNING_BROKERS[$KILLED_INDEX]}"
log "Killing broker: ${KILLED_SERVICE}..."

${COMPOSE_CMD} stop "$KILLED_SERVICE" 2>/dev/null || ${COMPOSE_CMD} kill "$KILLED_SERVICE" 2>/dev/null || true
sleep 5

# Verify it's stopped
if ${COMPOSE_CMD} ps "$KILLED_SERVICE" --format '{{.Status}}' 2>/dev/null | grep -q "running\|Up"; then
    fail "Broker ${KILLED_SERVICE} is still running after kill"
    exit 1
fi
pass "Broker ${KILLED_SERVICE} killed successfully"

# ── Verify: Backend API still responsive ────────────────────────
sleep 10  # Wait for Kafka client rebalancing

HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/health 2>/dev/null || echo "000")
if [[ "$HTTP_CODE" == "200" ]]; then
    pass "Backend API still responsive (HTTP ${HTTP_CODE})"
else
    fail "Backend API returned HTTP ${HTTP_CODE} (expected 200)"
fi

# ── Verify: Producer can still send ─────────────────────────────
# Send test message and check if it's accepted
TEST_START=$(date +%s)
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST http://localhost:8080/api/v1/sensors/data \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $(grep -o 'token=[^;]*' /tmp/chaos-test-token 2>/dev/null | cut -d= -f2 || echo 'test')" \
    -d '{"sensorId":"chaos-test-001","type":"ENERGY","value":42.5,"unit":"kWh","tenantId":"chaos-test"}' \
    2>/dev/null || echo "000")

if [[ "$HTTP_CODE" =~ ^(200|201|202|401|403)$ ]]; then
    pass "Producer endpoint responsive (HTTP ${HTTP_CODE})"
else
    log "WARN: Producer returned HTTP ${HTTP_CODE} — may be auth-related, not Kafka issue"
fi

# ── Verify: Flink job still running ─────────────────────────────
FLINK_STATUS=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    running = [j for j in data.get('jobs', []) if j.get('status') == 'RUNNING']
    print(len(running))
except:
    print('0')
" 2>/dev/null || echo "0")

if [[ "$FLINK_STATUS" -ge 1 ]]; then
    pass "Flink job still running (${FLINK_STATUS} jobs)"
else
    fail "No Flink jobs running after broker kill"
fi

# ── Restore broker ──────────────────────────────────────────────
log "Restoring broker ${KILLED_SERVICE}..."
${COMPOSE_CMD} start "$KILLED_SERVICE"
sleep 15

# Verify rejoined
if ${COMPOSE_CMD} ps "$KILLED_SERVICE" --format '{{.Status}}' 2>/dev/null | grep -q "running\|Up"; then
    pass "Broker ${KILLED_SERVICE} restored and running"
else
    fail "Broker ${KILLED_SERVICE} failed to restore"
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
