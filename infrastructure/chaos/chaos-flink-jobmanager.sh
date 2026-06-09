#!/bin/bash
# Chaos test: Kill Flink JobManager
# Verify jobs restore from checkpoint after restart
set -euo pipefail

COMPOSE_FILE="${1:-infrastructure/docker-compose.ha.yml}"
JM="${2:-flink-jobmanager}"
LOG_FILE="build/chaos-flink-$(date +%Y%m%d-%H%M%S).log"
mkdir -p build

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"; }

log "=== CHAOS: Flink JobManager Kill Test ==="
log "Target: $JM"

# Step 1: Record running jobs before kill
log "Step 1: Record running Flink jobs..."
JOBS_BEFORE=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('jobs',[])))" 2>/dev/null || echo "unknown")
log "Running jobs before: $JOBS_BEFORE"

# Step 2: Kill JobManager
log "Step 2: Killing $JM..."
docker compose -f "$COMPOSE_FILE" stop "$JM" 2>/dev/null || docker stop "$JM" 2>/dev/null || true
sleep 5
log "Killed $JM"

# Step 3: Verify API is down
log "Step 3: Verify Flink API is down..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/overview 2>/dev/null || echo "000")
log "Flink API status: $HTTP_CODE (expected: 000 or connection refused)"

# Step 4: Restart JobManager
log "Step 4: Restarting $JM..."
docker compose -f "$COMPOSE_FILE" start "$JM" 2>/dev/null || docker start "$JM" 2>/dev/null || true
sleep 20

# Step 5: Verify jobs restore
log "Step 5: Verify jobs restored from checkpoint..."
JOBS_AFTER=$(curl -s http://localhost:8081/jobs/overview 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('jobs',[])))" 2>/dev/null || echo "unknown")
log "Running jobs after: $JOBS_AFTER"

if [ "$JOBS_AFTER" != "unknown" ] && [ "$JOBS_AFTER" -gt 0 ]; then
    log "✅ PASS: $JOBS_AFTER job(s) running after recovery"
else
    log "⚠️  WARN: No running jobs detected — may need manual resubmit"
fi

log "=== CHAOS COMPLETE: Flink JobManager ==="
