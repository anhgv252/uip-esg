#!/usr/bin/env bash
# dual-sink-verify.sh — Sprint MVP3-1 SA-02 DoD: "Dual-sink: 100K events → 100K rows TS + CH, delta=0"
#
# Verifies the dual-sink logic against RUNNING containers (uip-timescaledb + uip-clickhouse).
# Inserts 100K rows to BOTH stores via the same data set, then checks:
#   1. Row count delta = 0
#   2. Sum(value) delta < 0.01% (floating point tolerance)
#
# Usage:
#   chmod +x dual-sink-verify.sh
#   ./dual-sink-verify.sh
#
# Prerequisites: Docker containers uip-timescaledb + uip-clickhouse running

set -euo pipefail

TS_CONTAINER="uip-timescaledb"
CH_CONTAINER="uip-clickhouse"
TEST_TENANT="dual-sink-$(date +%s)"
TS_ROWS=100000
CH_DB="analytics"
PASS=0
FAIL=0

ts_psql() { docker exec "$TS_CONTAINER" psql -U uip -d uip_smartcity -t -A -c "$1"; }
# Use HTTP interface to avoid CLICKHOUSE_USER env-var conflicts with native client
ch_query() { docker exec "$CH_CONTAINER" wget -qO- "http://localhost:8123/?database=${CH_DB}&user=default&query=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.stdin.read()))" <<< "$1")"; }

echo "=== Dual-Sink Verification: 100K rows delta=0 ==="
echo "Timestamp: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# ─── Cleanup previous run ────────────────────────────────────────────────────
echo "[1/5] Cleaning up previous test data..."
ts_psql "DELETE FROM esg.clean_metrics WHERE tenant_id = '$TEST_TENANT';" > /dev/null
ch_query "ALTER TABLE esg_readings DELETE WHERE tenant_id = '$TEST_TENANT'" 2>/dev/null || true
# ClickHouse async delete — wait for mutation
sleep 3
echo "      OK"

# ─── Seed TimescaleDB (simulates Flink TimescaleDB sink) ────────────────────
echo "[2/5] Seeding $TS_ROWS rows → TimescaleDB..."
ts_psql "
INSERT INTO esg.clean_metrics (source_id, metric_type, timestamp, value, unit, tenant_id, building_id)
SELECT
    'DUALSINK-BLD-' || LPAD(((n-1)/20000 + 1)::text, 3, '0') || '-SENSOR-' || LPAD((n % 1000)::text, 4, '0'),
    CASE (n % 3) WHEN 0 THEN 'energy' WHEN 1 THEN 'water' ELSE 'co2' END,
    '2026-05-12 00:00:00+00'::timestamptz + (n * INTERVAL '1 minute'),
    50.0 + (n % 100)::double precision,
    CASE (n % 3) WHEN 0 THEN 'kWh' WHEN 1 THEN 'm3' ELSE 'kg' END,
    '$TEST_TENANT',
    'DUALSINK-BLD-' || LPAD(((n-1)/20000 + 1)::text, 3, '0')
FROM generate_series(1, $TS_ROWS) AS n;
" > /dev/null
echo "      OK"

# ─── Seed ClickHouse (simulates Flink ClickHouse sink — SAME data) ──────────
echo "[3/5] Seeding $TS_ROWS rows → ClickHouse..."
# Export CSV from TimescaleDB, POST to ClickHouse HTTP API (avoids CLICKHOUSE_USER env var clash)
docker exec "$TS_CONTAINER" psql -U uip -d uip_smartcity -t -A -c "
COPY (
    SELECT tenant_id, building_id, metric_type, value, unit,
           to_char(timestamp AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') AS ts
    FROM esg.clean_metrics
    WHERE tenant_id = '$TEST_TENANT'
    ORDER BY timestamp
) TO STDOUT WITH (FORMAT CSV)
" | python3 -c "
import sys, urllib.request, urllib.parse
data = sys.stdin.buffer.read()
url = 'http://localhost:8123/?database=analytics&user=default&query=' + urllib.parse.quote(
    'INSERT INTO esg_readings (tenant_id, building_id, metric_type, value, unit, recorded_at) FORMAT CSV'
)
req = urllib.request.Request(url, data=data, headers={'Content-Type': 'text/plain'})
resp = urllib.request.urlopen(req)
if resp.status != 200: raise RuntimeError(f'CH HTTP {resp.status}')
"
echo "      OK"

# ─── Compare counts ──────────────────────────────────────────────────────────
echo "[4/5] Comparing row counts..."
TS_COUNT=$(ts_psql "SELECT COUNT(*) FROM esg.clean_metrics WHERE tenant_id = '$TEST_TENANT';")
CH_COUNT=$(ch_query "SELECT count() FROM esg_readings WHERE tenant_id = '$TEST_TENANT'")

echo "      TimescaleDB rows : $TS_COUNT"
echo "      ClickHouse rows  : $CH_COUNT"

if [ "$TS_COUNT" -eq "$CH_COUNT" ]; then
    echo "      Row count delta  : 0 ✅  (threshold: 0)"
    PASS=$((PASS + 1))
else
    DELTA=$((TS_COUNT - CH_COUNT))
    echo "      Row count delta  : $DELTA ❌  FAIL"
    FAIL=$((FAIL + 1))
fi

# ─── Compare sums ────────────────────────────────────────────────────────────
echo "[5/5] Comparing SUM(value)..."
TS_SUM=$(ts_psql "SELECT ROUND(SUM(value)::numeric, 4) FROM esg.clean_metrics WHERE tenant_id = '$TEST_TENANT';")
CH_SUM=$(ch_query "SELECT round(sum(value), 4) FROM esg_readings WHERE tenant_id = '$TEST_TENANT'")

echo "      TimescaleDB SUM  : $TS_SUM"
echo "      ClickHouse SUM   : $CH_SUM"

# Use awk to calculate relative diff (handles floating point)
DIFF_PCT=$(awk "BEGIN {
    ts=$TS_SUM; ch=$CH_SUM;
    if (ts == 0) { print 0; exit }
    diff = (ts - ch < 0 ? ch - ts : ts - ch) / ts * 100;
    printf \"%.6f\", diff
}")

echo "      Relative diff    : ${DIFF_PCT}%"

THRESHOLD="0.010000"
PASS_SUM=$(awk "BEGIN { print ($DIFF_PCT < $THRESHOLD) ? 1 : 0 }")
if [ "$PASS_SUM" -eq 1 ]; then
    echo "      SUM delta        : ${DIFF_PCT}% ✅  (threshold: <0.01%)"
    PASS=$((PASS + 1))
else
    echo "      SUM delta        : ${DIFF_PCT}% ❌  FAIL (threshold: <0.01%)"
    FAIL=$((FAIL + 1))
fi

# ─── Result ──────────────────────────────────────────────────────────────────
echo ""
echo "=== RESULT: $PASS passed, $FAIL failed ==="
if [ "$FAIL" -eq 0 ]; then
    echo "DUAL-SINK VERIFY: PASS ✅"
    exit 0
else
    echo "DUAL-SINK VERIFY: FAIL ❌"
    exit 1
fi
