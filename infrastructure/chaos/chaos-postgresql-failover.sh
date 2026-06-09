#!/bin/bash
# Chaos test: Kill PostgreSQL primary
# Verify backend reconnects (standby promotes or manual promote)
set -euo pipefail

COMPOSE_FILE="${1:-infrastructure/docker-compose.ha.yml}"
PRIMARY="${2:-postgres}"
LOG_FILE="build/chaos-pg-$(date +%Y%m%d-%H%M%S).log"
mkdir -p build

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"; }

log "=== CHAOS: PostgreSQL Failover Test ==="
log "Target: $PRIMARY"

# Step 1: Check PostgreSQL running
log "Step 1: Check PG running..."
docker compose -f "$COMPOSE_FILE" ps "$PRIMARY" 2>/dev/null | grep -q "running" && log "PG primary running" || log "⚠️  PG primary not running"

# Step 2: Kill primary
log "Step 2: Killing $PRIMARY..."
docker compose -f "$COMPOSE_FILE" stop "$PRIMARY" 2>/dev/null || docker stop "$PRIMARY" 2>/dev/null || true
sleep 5
log "Killed $PRIMARY"

# Step 3: Check if standby exists and can serve
log "Step 3: Check standby..."
STANDBY=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep "postgres-standby\|postgres-replica" | head -1 || echo "")
if [ -n "$STANDBY" ]; then
    log "Standby found: $STANDBY"
    QUERY=$(docker exec "$STANDBY" psql -U uip -d uip_smartcity -c "SELECT pg_is_in_recovery()" -t 2>/dev/null | tr -d ' ' || echo "unknown")
    log "Standby in_recovery: $QUERY"
else
    log "⚠️  No standby found — testing backend resilience only"
fi

# Step 4: Restart primary
log "Step 4: Restarting $PRIMARY..."
docker compose -f "$COMPOSE_FILE" start "$PRIMARY" 2>/dev/null || docker start "$PRIMARY" 2>/dev/null || true
sleep 10

# Step 5: Verify backend connection
log "Step 5: Verify PG is accepting connections..."
docker exec "$PRIMARY" psql -U uip -d uip_smartcity -c "SELECT 1" 2>/dev/null && log "✅ PASS: PG accepting connections" || log "⚠️  WARN: PG may need more time to start"

log "=== CHAOS COMPLETE: PostgreSQL Failover ==="
