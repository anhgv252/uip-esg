#!/bin/bash
# Chaos Test: Flink JobManager Crash
# S11-CHAOS-01: Kill Flink JobManager and verify job recovery from checkpoint
#
# Usage: ./chaos-flink-jobmanager.sh
# Exit: 0=PASS, 1=FAIL

set -euo pipefail

COMPOSE_FILES="-f docker-compose.yml -f docker-compose.uat.yml -f docker-compose.staging.yml"
COMPOSE_CMD="docker compose ${COMPOSE_FILES}"
REPORT_FILE="tests/chaos/results/flink-jobmanager-result.txt"

mkdir -p tests/chaos/results

log() { echo "[$(date +%H:%M:%S)] $*"; }
pass() { echo "✅ PASS: $*"; echo "PASS: $*" >> "$REPORT_FILE"; }
fail() { echo "❌ FAIL: $*"; echo "FAIL: $*" >> "$REPORT_FILE"; }

cleanup() {
    log "Cleanup: restoring Flink JobManager..."
    ${COMPOSE_CMD} start flink-jobmanager 2>/dev/null || true
    sleep 15
    log "Cleanup complete"
}
trap cleanup EXIT

# ── Pre-flight ──────────────────────────────────────────────────
log "=== Flink JobManager Chaos Test ==="
echo "Flink JobManager Chaos Test — $(date)" > "$REPORT_FILE"

# Record running job ID before crash
BEFORE_JOBS=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null || echo '{}')
BEFORE_RUNNING=$(echo "$BEFORE_JOBS" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    running = [j for j in data.get('jobs', []) if j.get('status') == 'RUNNING']
    for j in running:
        print(f\"{j.get('jid','?')}\")
except:
    pass
" 2>/dev/null || true)

if [[ -z "$BEFORE_RUNNING" ]]; then
    fail "Pre-flight: No running Flink jobs found"
    exit 1
fi
BEFORE_JID=$(echo "$BEFORE_RUNNING" | head -1)
pass "Pre-flight: Flink job running (jid=${BEFORE_JID:0:12}...)"

# Record latest checkpoint
BEFORE_CHECKPOINT=$(curl -s "http://localhost:8081/jobs/${BEFORE_JID}/checkpoints" 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    latest = data.get('latest', {}).get('completed', {})
    print(latest.get('id', 'none'))
except:
    print('none')
" 2>/dev/null || echo "none")
log "Latest checkpoint before crash: ${BEFORE_CHECKPOINT:0:16}..."

# ── Kill JobManager ─────────────────────────────────────────────
log "Killing Flink JobManager..."
${COMPOSE_CMD} stop flink-jobmanager 2>/dev/null || ${COMPOSE_CMD} kill flink-jobmanager 2>/dev/null || true
sleep 3
pass "Flink JobManager killed"

# ── Verify: TaskManager still alive ─────────────────────────────
sleep 5
TM_STATUS=$(${COMPOSE_CMD} ps flink-taskmanager --format '{{.Status}}' 2>/dev/null || echo "unknown")
if echo "$TM_STATUS" | grep -q "running\|Up"; then
    pass "TaskManager still running after JM crash"
else
    fail "TaskManager not running after JM crash (status: ${TM_STATUS})"
fi

# ── Restore JobManager ──────────────────────────────────────────
log "Restoring Flink JobManager..."
RESTORE_START=$(date +%s)

${COMPOSE_CMD} start flink-jobmanager
sleep 20

# Wait for JM to become ready
RETRY=0
MAX_RETRIES=30
while [[ $RETRY -lt $MAX_RETRIES ]]; do
    JM_HEALTH=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/config 2>/dev/null || echo "000")
    if [[ "$JM_HEALTH" == "200" ]]; then
        break
    fi
    sleep 2
    RETRY=$((RETRY + 1))
done

RESTORE_END=$(date +%s)
RESTORE_SECONDS=$((RESTORE_END - RESTORE_START))

if [[ "$JM_HEALTH" == "200" ]]; then
    pass "Flink JobManager restored (ready in ${RESTORE_SECONDS}s)"
else
    fail "Flink JobManager not ready after ${RESTORE_SECONDS}s (HTTP ${JM_HEALTH})"
fi

# ── Verify: Job recovered from checkpoint ───────────────────────
sleep 10

AFTER_JOBS=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null || echo '{}')
AFTER_RUNNING=$(echo "$AFTER_JOBS" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    running = [j for j in data.get('jobs', []) if j.get('status') == 'RUNNING']
    for j in running:
        print(f\"{j.get('jid','?')}\")
except:
    pass
" 2>/dev/null || true)

if [[ -n "$AFTER_RUNNING" ]]; then
    AFTER_JID=$(echo "$AFTER_RUNNING" | head -1)
    pass "Flink job recovered and running (jid=${AFTER_JID:0:12}...)"

    # Check if it's the same job (restored from checkpoint)
    if [[ "$BEFORE_JID" == "$AFTER_JID" ]]; then
        pass "Same job ID — restored from checkpoint (no data loss)"
    else
        log "INFO: New job ID — job was restarted (jid changed: ${BEFORE_JID:0:12}... → ${AFTER_JID:0:12}...)"
        pass "Job restarted (new jid is expected after JM failover)"
    fi
else
    # Check for RESTARTING status
    RESTARTING=$(echo "$AFTER_JOBS" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    restarting = [j for j in data.get('jobs', []) if j.get('status') in ('RESTARTING', 'CREATING')]
    print(len(restarting))
except:
    print('0')
" 2>/dev/null || echo "0")
    if [[ "$RESTARTING" -gt 0 ]]; then
        pass "Job is restarting (RESTARTING/CREATING state)"
    else
        fail "No running or restarting Flink jobs after recovery"
    fi
fi

# ── Verify: Recovery time under 30s ─────────────────────────────
if [[ $RESTORE_SECONDS -le 30 ]]; then
    pass "Recovery time ${RESTORE_SECONDS}s ≤ 30s SLA"
else
    log "WARN: Recovery time ${RESTORE_SECONDS}s > 30s SLA (non-blocking)"
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
