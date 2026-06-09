#!/bin/bash
# Master chaos engineering script — runs all chaos scenarios sequentially
# Generates HTML report with pass/fail results
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="${1:-infrastructure/docker-compose.ha.yml}"
RESULTS_DIR="build/chaos-results"
mkdir -p "$RESULTS_DIR"

PASS=0
FAIL=0
TOTAL=0
RESULTS=()

echo "╔══════════════════════════════════════════════╗"
echo "║   UIP Chaos Engineering — Automated Suite    ║"
echo "╚══════════════════════════════════════════════╝"
echo "Start: $(date '+%Y-%m-%d %H:%M:%S')"
echo "Compose: $COMPOSE_FILE"
echo ""

run_test() {
    local name="$1"
    local script="$2"
    TOTAL=$((TOTAL + 1))
    echo ""
    echo "━━━ Running: $name ━━━"
    if bash "$script" "$COMPOSE_FILE"; then
        echo "✅ PASS: $name"
        PASS=$((PASS + 1))
        RESULTS+=("$name|PASS")
    else
        echo "❌ FAIL: $name"
        FAIL=$((FAIL + 1))
        RESULTS+=("$name|FAIL")
    fi
}

run_test "Kafka Broker Kill" "$SCRIPT_DIR/chaos-kafka-broker.sh"
run_test "ClickHouse Node Kill" "$SCRIPT_DIR/chaos-clickhouse-node.sh"
run_test "PostgreSQL Failover" "$SCRIPT_DIR/chaos-postgresql-failover.sh"
run_test "Flink JobManager Kill" "$SCRIPT_DIR/chaos-flink-jobmanager.sh"

# Generate HTML report
REPORT="$RESULTS_DIR/report-$(date +%Y%m%d-%H%M%S).html"

{
    echo "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
    echo "<title>Chaos Engineering Report — $(date '+%Y-%m-%d %H:%M')</title>"
    echo "<style>"
    echo "body{font-family:-apple-system,sans-serif;margin:40px;max-width:800px}"
    echo "h1{color:#333}table{border-collapse:collapse;width:100%;margin-top:20px}"
    echo "th,td{border:1px solid #ddd;padding:10px 16px;text-align:left}"
    echo "th{background:#f5f5f5;font-weight:600}"
    echo ".pass{color:#2e7d32;font-weight:600}.fail{color:#c62828;font-weight:600}"
    echo ".summary{margin-top:24px;padding:16px;background:#f5f5f5;border-radius:8px}"
    echo "</style></head><body>"
    echo "<h1>🔥 Chaos Engineering Test Report</h1>"
    echo "<p>Generated: $(date '+%Y-%m-%d %H:%M:%S')</p>"
    echo "<p>Environment: <code>$COMPOSE_FILE</code></p>"
    echo "<table><tr><th>#</th><th>Test Scenario</th><th>Result</th></tr>"

    I=1
    for result in "${RESULTS[@]}"; do
        NAME="${result%|*}"
        STATUS="${result#*|}"
        CLASS="pass"
        [ "$STATUS" = "FAIL" ] && CLASS="fail"
        echo "<tr><td>$I</td><td>$NAME</td><td class='$CLASS'>$STATUS</td></tr>"
        I=$((I + 1))
    done

    echo "</table>"
    echo "<div class='summary'>"
    echo "<h2>Summary: $PASS/$TOTAL passed"
    [ "$FAIL" -gt 0 ] && echo " — $FAIL failed"
    echo "</h2></div></body></html>"
} > "$REPORT"

echo ""
echo "╔══════════════════════════════════════════════╗"
echo "║  Total: $TOTAL  |  ✅ Pass: $PASS  |  ❌ Fail: $FAIL  ║"
echo "╚══════════════════════════════════════════════╝"
echo "Report: $REPORT"
echo "End: $(date '+%Y-%m-%d %H:%M:%S')"

[ "$FAIL" -gt 0 ] && exit 1 || exit 0
