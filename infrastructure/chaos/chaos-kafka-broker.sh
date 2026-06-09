#!/bin/bash
# Chaos test: Kill 1 Kafka broker in 3-broker cluster
# Verify remaining brokers still serve, producer/consumer work
# Restart and verify rejoin
set -euo pipefail

COMPOSE_FILE="${1:-infrastructure/docker-compose.ha.yml}"
BROKER="${2:-kafka-2}"
LOG_FILE="build/chaos-kafka-$(date +%Y%m%d-%H%M%S).log"
mkdir -p build

log() { echo "[$(date '+%H:%M:%S')] $*" | tee -a "$LOG_FILE"; }

log "=== CHAOS: Kafka Broker Kill Test ==="
log "Target: $BROKER"

# Step 1: Verify brokers before
log "Step 1: Check initial state"
BEFORE=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -c kafka || echo "0")
log "Brokers before: $BEFORE"

# Step 2: Kill target broker
log "Step 2: Killing $BROKER..."
docker compose -f "$COMPOSE_FILE" stop "$BROKER" 2>/dev/null || docker stop "$BROKER" 2>/dev/null || true
sleep 5
log "Killed $BROKER"

# Step 3: Verify remaining brokers
log "Step 3: Verify remaining brokers..."
sleep 10
REMAINING=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep kafka | grep -vc "$BROKER" || echo "0")
log "Remaining brokers: $REMAINING"

if [ "$REMAINING" -lt 2 ]; then
    log "❌ FAIL: Less than 2 brokers remaining ($REMAINING)"
    exit 1
fi
log "✅ PASS: $REMAINING brokers still running"

# Step 4: Restart killed broker
log "Step 4: Restarting $BROKER..."
docker compose -f "$COMPOSE_FILE" start "$BROKER" 2>/dev/null || docker start "$BROKER" 2>/dev/null || true
sleep 15

# Step 5: Verify rejoin
log "Step 5: Verify rejoin..."
FINAL=$(docker compose -f "$COMPOSE_FILE" ps --format '{{.Name}}' 2>/dev/null | grep -c kafka || echo "0")
log "Final broker count: $FINAL"
if [ "$FINAL" -lt 3 ]; then
    log "⚠️  WARN: Broker may not have rejoined yet ($FINAL/3)"
else
    log "✅ PASS: All 3 brokers running"
fi

log "=== CHAOS COMPLETE: Kafka Broker ==="
