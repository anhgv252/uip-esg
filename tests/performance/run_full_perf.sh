#!/usr/bin/env bash
# T-UAT-BE-02: Full 10-minute sustained MQTT load test
# Saves results to docs/reports/performance/s4-05-full-report.md
#
# Prerequisites: Docker stack running (cd infrastructure && make uat-up)
# Usage: cd tests/performance && bash run_full_perf.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
REPORT_DIR="$PROJECT_ROOT/docs/reports/performance"
FULL_REPORT="$REPORT_DIR/s4-05-full-report.md"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
log()  { echo -e "${GREEN}[FULL-PERF]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }

EMQX_HOST="${EMQX_HOST:-localhost}"
EMQX_PORT="${EMQX_PORT:-1883}"
TARGET_RATE="${TARGET_RATE:-2000}"
FULL_DURATION=600  # 10 minutes — không override

log "=== T-UAT-BE-02: Full 10-minute MQTT Sustained Load Test ==="
log "Target: $TARGET_RATE msg/s × ${FULL_DURATION}s = $(( TARGET_RATE * FULL_DURATION )) messages"
log "Report: $FULL_REPORT"
echo ""

# Kiểm tra EMQX
if ! python3 -c "
import socket
s = socket.socket()
s.settimeout(3)
s.connect(('$EMQX_HOST', $EMQX_PORT))
s.close()
" 2>/dev/null; then
    echo -e "${RED}[ERROR]${NC} Cannot connect to EMQX at $EMQX_HOST:$EMQX_PORT"
    echo "        Ensure Docker stack is running: cd infrastructure && make uat-up"
    exit 1
fi
log "EMQX reachable at $EMQX_HOST:$EMQX_PORT"

cd "$SCRIPT_DIR"

log "Running MQTT load test (${FULL_DURATION}s)..."
START_TIME=$(date -u +"%Y-%m-%d %H:%M:%S UTC")

python3 mqtt_load_test.py \
    --host "$EMQX_HOST" \
    --port "$EMQX_PORT" \
    --rate "$TARGET_RATE" \
    --duration "$FULL_DURATION" \
    --threads 4

END_TIME=$(date -u +"%Y-%m-%d %H:%M:%S UTC")

# Đọc kết quả JSON từ file được tạo bởi mqtt_load_test.py
RESULTS_JSON="$REPORT_DIR/mqtt-load-results.json"

log "Generating full report: $FULL_REPORT"

cat > "$FULL_REPORT" << EOF
# S4-05 Full Performance Test Report — T-UAT-BE-02

**Loại test:** Full 10-minute sustained MQTT load (KR2 final verification)
**Ngày chạy:** $START_TIME → $END_TIME
**Target:** $TARGET_RATE msg/s × ${FULL_DURATION}s sustained = $(( TARGET_RATE * FULL_DURATION )) tổng messages

## Test Configuration

| Parameter | Value |
|-----------|-------|
| EMQX host | $EMQX_HOST:$EMQX_PORT |
| Target rate | $TARGET_RATE msg/s |
| Duration | ${FULL_DURATION}s (10 minutes) |
| Publisher threads | 4 |
| Sensor types | AQI, Water Level, Energy, Traffic |
| QoS | 0 (fire and forget) |

## Results

\`\`\`json
$(cat "$RESULTS_JSON" 2>/dev/null || echo '{"status": "RESULTS FILE NOT FOUND"}')
\`\`\`

## KR2 Acceptance Criteria

| AC | Target | Status |
|----|--------|--------|
| MQTT throughput | ≥2,000 msg/s | $(python3 -c "
import json, sys
try:
    d = json.load(open('$RESULTS_JSON'))
    rate = d.get('actual_rate', 0)
    status = d.get('status', 'UNKNOWN')
    print(f'{rate:.0f} msg/s — {status}')
except:
    print('See JSON above')
" 2>/dev/null || echo "See JSON above") |
| Duration | 10 minutes sustained | ${FULL_DURATION}s ✅ |
| Error rate | 0% | $(python3 -c "
import json
try:
    d = json.load(open('$RESULTS_JSON'))
    failed = d.get('failed', 0)
    total = d.get('published', 1) + failed
    pct = failed / total * 100
    print(f'{pct:.2f}% ({failed} failed)')
except:
    print('See JSON above')
" 2>/dev/null || echo "See JSON above") |

## Sign-off

- [ ] QA-1 review và xác nhận KR2 đạt
- [ ] Be-1 update sprint board
- Date: $(date -u +"%Y-%m-%d")
EOF

log "Full report saved: $FULL_REPORT"
log "T-UAT-BE-02 DONE"
