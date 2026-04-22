#!/usr/bin/env bash
# S4-05 Performance Test — Orchestration Script
# Runs all performance tests and generates report.
#
# Prerequisites:
#   - Docker Compose stack running (infrastructure/)
#   - Python 3.11+ with: pip install -r requirements.txt
#   - k6 installed: brew install k6 (macOS)
#
# Usage:
#   cd tests/performance && bash run_perf.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DIR="$PROJECT_ROOT/docs/reports/performance"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[PERF]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
fail() { echo -e "${RED}[FAIL]${NC} $*"; }

mkdir -p "$REPORT_DIR"

# ─── Configuration ──────────────────────────────────────────────────────────
EMQX_HOST="${EMQX_HOST:-localhost}"
EMQX_PORT="${EMQX_PORT:-1883}"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP:-localhost:29092}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
DB_URL="${DB_URL:-postgresql://uip:changeme_db_password@localhost:5432/uip_smartcity}"

TARGET_RATE="${TARGET_RATE:-2000}"
TEST_DURATION="${TEST_DURATION:-60}"  # default 60s for quick test; set 600 for full
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-admin_Dev#2026!}"

# ─── Prerequisites Check ───────────────────────────────────────────────────
log "=== S4-05 Performance Test Suite ==="
log "Target rate: $TARGET_RATE msg/s"
log "Duration: ${TEST_DURATION}s per test"
log "Report dir: $REPORT_DIR"
echo ""

# Check backend
if ! curl -sf "$BACKEND_URL/api/v1/health" > /dev/null 2>&1; then
    fail "Backend not reachable at $BACKEND_URL"
    fail "Start stack: cd infrastructure && docker compose up -d"
    exit 1
fi
log "Backend: OK"

# Check k6
if ! command -v k6 &> /dev/null; then
    warn "k6 not found. Install: brew install k6"
    warn "Skipping API load test"
    RUN_API_TEST=false
else
    log "k6: $(k6 version)"
    RUN_API_TEST=true
fi

# Check Python packages
python3 -c "import paho.mqtt" 2>/dev/null || { warn "paho-mqtt not installed. Run: pip install paho-mqtt"; }
python3 -c "import confluent_kafka" 2>/dev/null || { warn "confluent-kafka not installed. Run: pip install confluent-kafka"; }

echo ""
log "Starting tests..."
echo ""

# ─── HikariCP Monitor ──────────────────────────────────────────────────────
monitor_hikari() {
    local outfile="$1"
    echo "timestamp,active_connections,idle_connections,total_connections" > "$outfile"
    while true; do
        docker exec uip-timescaledb psql -U uip -d uip_smartcity -t -A -c \
            "SELECT NOW(), state, COUNT(*) FROM pg_stat_activity WHERE datname='uip_smartcity' GROUP BY state" \
            2>/dev/null >> "$outfile" || true
        sleep 2
    done
}

HIKARI_LOG="$REPORT_DIR/hikari-monitor.csv"
monitor_hikari "$HIKARI_LOG" &
HIKARI_PID=$!
trap "kill $HIKARI_PID 2>/dev/null" EXIT

# ─── Test 1: Kafka Direct Producer ─────────────────────────────────────────
log "=== Test 1: Kafka Direct Producer (alert_events) ==="
log "Sends $TARGET_RATE msg/s directly to Kafka bypassing Flink"
cd "$SCRIPT_DIR"

python3 kafka_producer.py \
    --bootstrap "$KAFKA_BOOTSTRAP" \
    --rate "$TARGET_RATE" \
    --duration "$TEST_DURATION" \
    --db-url "$DB_URL" \
    || warn "Kafka producer test had issues"

echo ""

# ─── Test 2: MQTT Load Test ────────────────────────────────────────────────
log "=== Test 2: MQTT Load Test (EMQX) ==="
log "Sends $TARGET_RATE msg/s to EMQX MQTT broker"

python3 mqtt_load_test.py \
    --host "$EMQX_HOST" \
    --port "$EMQX_PORT" \
    --rate "$TARGET_RATE" \
    --duration "$TEST_DURATION" \
    || warn "MQTT load test had issues"

echo ""

# ─── Test 3: API Load Test ─────────────────────────────────────────────────
if [ "$RUN_API_TEST" = true ]; then
    log "=== Test 3: API Load Test (k6, 50 VUs) ==="
    cd "$SCRIPT_DIR"

    BASE_URL="$BACKEND_URL" \
    ADMIN_USER="$ADMIN_USER" \
    ADMIN_PASS="$ADMIN_PASS" \
    k6 run \
        --out json="$REPORT_DIR/k6-results.json" \
        api_load_test.js \
        || warn "API load test had issues"
else
    warn "Skipping API load test (k6 not installed)"
fi

# Stop HikariCP monitor
kill $HIKARI_PID 2>/dev/null || true

echo ""
log "=== All tests completed ==="
log "Results saved in: $REPORT_DIR/"
echo ""

# ─── Generate Summary Report ───────────────────────────────────────────────
REPORT_FILE="$REPORT_DIR/s4-05-report.md"

cat > "$REPORT_FILE" << 'REPORT_HEADER'
# S4-05 Performance Test Report

**Date:** $(date -u +"%Y-%m-%d %H:%M UTC")
**Target:** 2,000 msg/s sustained × 10 minutes

## Test Environment

| Component | Version |
|-----------|---------|
| Backend | Spring Boot 3.1.x (Java 17) |
| TimescaleDB | 2.13.1-pg15 |
| Kafka | 7.5.0 (KRaft) |
| EMQX | 5.3.2 |
| Redis | 7.2 |
| Flink | 1.19-java17 |
REPORT_HEADER

# Replace date placeholder
sed -i '' "s/\$(date -u +\"%Y-%m-%d %H:%M UTC\")/$(date -u +"%Y-%m-%d %H:%M UTC")/" "$REPORT_FILE" 2>/dev/null || \
    sed -i "0,/RE/s/\$(date -u +\"%Y-%m-%d %H:%M UTC\")/$(date -u +"%Y-%m-%d %H:%M UTC")/" "$REPORT_FILE" 2>/dev/null || true

# Append results
cat >> "$REPORT_FILE" << 'EOF'

## HikariCP Configuration

| Parameter | Value |
|-----------|-------|
| maximum-pool-size | 20 |
| minimum-idle | 5 |
| connection-timeout | 5000ms |
| idle-timeout | 600000ms |
| leak-detection | 10000ms |

## Results

### Kafka Direct Producer (alert_events)

EOF

# Append JSON results if available
for result_file in "$REPORT_DIR/kafka-producer-results.json" "$REPORT_DIR/mqtt-load-results.json" "$REPORT_DIR/api-load-results.json"; do
    if [ -f "$result_file" ]; then
        echo "### $(basename "$result_file" | sed 's/-results.json//' | sed 's/-/ /g')" >> "$REPORT_FILE"
        echo '```json' >> "$REPORT_FILE"
        cat "$result_file" >> "$REPORT_FILE"
        echo '```' >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    fi
done

cat >> "$REPORT_FILE" << 'EOF'

## Acceptance Criteria Verification

| AC | Target | Result | Status |
|----|--------|--------|--------|
| MQTT throughput | 2,000 msg/s × 10 min | See mqtt-load-results.json | — |
| Flink throughput | ≥2,000 records/s | Check Flink dashboard :8081 | — |
| TimescaleDB p99 | <500ms | See kafka-producer-results.json | — |
| HikariCP pool | No connection timeout | See hikari-monitor.csv | — |
| API p95 latency | <200ms × 50 VUs | See api-load-results.json | — |
| Test report | docs/reports/performance/ | This file | ✅ |

EOF

log "Report saved to: $REPORT_FILE"
log "Done."
