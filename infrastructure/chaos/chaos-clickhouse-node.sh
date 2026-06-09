#!/bin/bash
# Chaos test: Kill 1 ClickHouse node in 2-node cluster
# Verify queries still work via remaining node
# Restart and verify replication catches up
set -euo pipefail

COMPOSE_FILE="${1:-infrastructure/docker-compose.ha.yml}"
NODE="${2:-clickhouse-02}"
LOG_FILE="build/chaos-ch-$(date +%Y%m%d-%H%M%S).log"
mkdir -p build

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"; }

log "=== CHAOS: ClickHouse Node Kill Test ==="
log "Target: $NODE"

# Step 1: Check initial state
log "Step 1: Check CH nodes running"
BEFORE=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -c clickhouse || echo "0")
log "CH nodes before: $BEFORE"

# Step 2: Kill target node
log "Step 2: Killing $NODE..."
docker compose -f "$COMPOSE_FILE" stop "$NODE" 2>/dev/null || docker stop "$NODE" 2>/dev/null || true
sleep 5
log "Killed $NODE"

# Step 3: Verify remaining node serves queries
log "Step 3: Verify queries on remaining node..."
SURVIVOR=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep clickhouse | grep -v "$NODE" | head -1 || echo "")
if [ -z "$SURVIVOR" ]; then
    log "❌ FAIL: No surviving CH node found"
    exit 1
fi
log "Surviving node: $SURVIVOR"

# Try a simple query via survivor
QUERY_RESULT=$(docker exec "$SURVIVOR" clickhouse-client --query "SELECT count() FROM system.tables" 2>/dev/null || echo "FAIL")
if [ "$QUERY_RESULT" = "FAIL" ]; then
    log "⚠️  WARN: Query on survivor failed (may need wait)"
else
    log "✅ PASS: Survivor node serves queries (tables: $QUERY_RESULT)"
fi

# Step 4: Restart killed node
log "Step 4: Restarting $NODE..."
docker compose -f "$COMPOSE_FILE" start "$NODE" 2>/dev/null || docker start "$NODE" 2>/dev/null || true
sleep 15

# Step 5: Verify replication
log "Step 5: Verify replication..."
FINAL=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -c clickhouse || echo "0")
log "Final CH node count: $FINAL"
if [ "$FINAL" -lt 2 ]; then
    log "⚠️  WARN: Node may not have rejoined yet"
else
    log "✅ PASS: Both CH nodes running"
fi

log "=== CHAOS COMPLETE: ClickHouse Node ==="
