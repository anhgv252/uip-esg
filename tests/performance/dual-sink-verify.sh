#!/usr/bin/env bash
# dual-sink-verify.sh — Sprint MVP3-1 SA-02 DoD
#
# Tests the ACTUAL Flink dual-sink pipeline end-to-end:
#   1. Produces NGSI-LD JSON messages to Kafka topic ngsi_ld_esg
#   2. Waits for EsgDualSinkJob to process them into TimescaleDB AND ClickHouse
#   3. Verifies count delta = 0 and sum(value) delta < 0.01%
#
# The old implementation directly inserted into both stores (bypassing Flink).
# This version tests the real pipeline.
#
# Prerequisites:
#   - Docker containers running: uip-kafka, uip-timescaledb, uip-clickhouse
#   - EsgDualSinkJob deployed and running (reads from ngsi_ld_esg, latest offset)
#   - python3 on PATH
#
# Usage:
#   chmod +x dual-sink-verify.sh
#   ./dual-sink-verify.sh [--messages N] [--timeout S] [--flink-rest URL]
#
# Environment overrides (optional):
#   KAFKA_CONTAINER   (default: uip-kafka)
#   TS_CONTAINER      (default: uip-timescaledb)
#   CH_CONTAINER      (default: uip-clickhouse)
#   FLINK_REST        (default: http://localhost:8081)

set -euo pipefail

# ─── Defaults ────────────────────────────────────────────────────────────────
KAFKA_CONTAINER="${KAFKA_CONTAINER:-uip-kafka}"
TS_CONTAINER="${TS_CONTAINER:-uip-timescaledb}"
CH_CONTAINER="${CH_CONTAINER:-uip-clickhouse}"
FLINK_REST="${FLINK_REST:-http://localhost:8081}"

KAFKA_BROKER_INTERNAL="kafka:9092"
KAFKA_TOPIC="ngsi_ld_esg"
CH_DB="analytics"

NUM_MESSAGES=1000
TIMEOUT_SECS=120
POLL_INTERVAL=5
TEST_TENANT="verify-$(date +%s)"
TEST_BUILDING="BLD-VERIFY-001"
VALUE_PER_MSG=42.5   # Each message has exactly 1 measurement at this value

PASS=0
FAIL=0

# ─── Parse args ──────────────────────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case $1 in
        --messages)  NUM_MESSAGES="$2"; shift 2 ;;
        --timeout)   TIMEOUT_SECS="$2"; shift 2 ;;
        --flink-rest) FLINK_REST="$2"; shift 2 ;;
        *) echo "Unknown arg: $1"; exit 1 ;;
    esac
done

# ─── Helpers ─────────────────────────────────────────────────────────────────
ts_psql() {
    docker exec "$TS_CONTAINER" psql -U uip -d uip_smartcity -t -A -c "$1" 2>/dev/null
}

ch_query() {
    local sql="$1"
    local encoded
    encoded=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.stdin.read()))" <<< "$sql")
    docker exec "$CH_CONTAINER" wget -qO- \
        "http://localhost:8123/?database=${CH_DB}&user=default&query=${encoded}" 2>/dev/null
}

container_running() {
    docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null | grep -q "true"
}

flink_job_running() {
    local status
    status=$(curl -sf "${FLINK_REST}/jobs" 2>/dev/null | python3 -c "
import json,sys
jobs = json.load(sys.stdin).get('jobs', [])
running = [j for j in jobs if j.get('status') == 'RUNNING']
print(len(running))
" 2>/dev/null || echo "0")
    [ "$status" -gt 0 ]
}

# ─── Banner ──────────────────────────────────────────────────────────────────
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Dual-Sink Pipeline Verification (HB-EXT-04)                ║"
echo "║  Kafka → EsgDualSinkJob → TimescaleDB + ClickHouse           ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""
echo "  Messages      : $NUM_MESSAGES"
echo "  Tenant        : $TEST_TENANT"
echo "  Timeout       : ${TIMEOUT_SECS}s"
echo "  Timestamp     : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo ""

# ─── Step 1: Prerequisite checks ─────────────────────────────────────────────
echo "[1/6] Checking prerequisites..."

PREREQ_OK=true
for c in "$KAFKA_CONTAINER" "$TS_CONTAINER" "$CH_CONTAINER"; do
    if container_running "$c"; then
        echo "      ✅  $c is running"
    else
        echo "      ❌  $c is NOT running"
        PREREQ_OK=false
    fi
done

# Check Flink job (soft warning — job may be running outside Docker)
if flink_job_running; then
    RUNNING_JOBS=$(curl -sf "${FLINK_REST}/jobs" 2>/dev/null | python3 -c "
import json,sys; jobs=json.load(sys.stdin).get('jobs',[]); print([j.get('id') for j in jobs if j.get('status')=='RUNNING'])
" 2>/dev/null)
    echo "      ✅  EsgDualSinkJob running: $RUNNING_JOBS"
else
    echo "      ⚠️   Flink REST ${FLINK_REST} unreachable or no RUNNING jobs"
    echo "      ⚠️   Ensure EsgDualSinkJob is deployed before producing messages"
fi

if ! $PREREQ_OK; then
    echo ""
    echo "FATAL: Required containers are not running. Aborting."
    exit 2
fi

# ─── Step 2: Cleanup previous test data ──────────────────────────────────────
echo ""
echo "[2/6] Cleaning up any previous test data for tenant=${TEST_TENANT}..."
ts_psql "DELETE FROM esg.clean_metrics WHERE tenant_id = '${TEST_TENANT}';" > /dev/null 2>&1 || true
ch_query "ALTER TABLE esg_readings DELETE WHERE tenant_id = '${TEST_TENANT}'" > /dev/null 2>&1 || true
# ClickHouse async mutation — brief wait
sleep 2
echo "      OK"

# ─── Step 3: Capture baseline counts (must be 0 for this tenant) ─────────────
echo ""
echo "[3/6] Capturing baseline counts for tenant=${TEST_TENANT}..."
TS_BASELINE=$(ts_psql "SELECT COUNT(*) FROM esg.clean_metrics WHERE tenant_id = '${TEST_TENANT}';" | tr -d '[:space:]')
CH_BASELINE=$(ch_query "SELECT count() FROM esg_readings WHERE tenant_id = '${TEST_TENANT}'" | tr -d '[:space:]')
echo "      TimescaleDB baseline : ${TS_BASELINE}"
echo "      ClickHouse  baseline : ${CH_BASELINE}"

if [ "${TS_BASELINE}" != "0" ] || [ "${CH_BASELINE}" != "0" ]; then
    echo "      WARN: Baseline is non-zero — prior test data not fully cleaned"
fi

# ─── Step 4: Produce NGSI-LD messages to Kafka ───────────────────────────────
echo ""
echo "[4/6] Producing ${NUM_MESSAGES} NGSI-LD messages to Kafka topic '${KAFKA_TOPIC}'..."
echo "      Each message: tenant=${TEST_TENANT}, building=${TEST_BUILDING}, energy_kwh=${VALUE_PER_MSG}"

BASE_TS=$(date +%s%3N)   # epoch millis

python3 - <<PYEOF | docker exec -i "$KAFKA_CONTAINER" \
    kafka-console-producer \
    --bootstrap-server "$KAFKA_BROKER_INTERNAL" \
    --topic "$KAFKA_TOPIC" \
    2>/dev/null
import json, sys

n         = ${NUM_MESSAGES}
base_ts   = ${BASE_TS}
tenant    = "${TEST_TENANT}"
building  = "${TEST_BUILDING}"
value     = ${VALUE_PER_MSG}

for i in range(1, n + 1):
    device_id = f"{building}-SENSOR-{i:05d}"
    msg = {
        "id":   f"urn:ngsi-ld:Device:{device_id}",
        "type": "ESGSensor",
        "deviceId":    {"type": "Property", "value": device_id},
        "observedAt":  {"type": "Property", "value": base_ts + i * 1000},
        "measurements":{"type": "Property", "value": {"energy_kwh": value}},
        "_meta": {"tenantId": tenant, "source": "dual-sink-verify"}
    }
    print(json.dumps(msg))
PYEOF

echo "      OK — ${NUM_MESSAGES} messages produced"

# ─── Step 5: Poll until both sinks reach expected count (or timeout) ─────────
echo ""
echo "[5/6] Polling for Flink to process messages (timeout=${TIMEOUT_SECS}s)..."
echo "      Expected rows in each sink: ${NUM_MESSAGES}"
echo ""

EXPECTED=$((TS_BASELINE + NUM_MESSAGES))
DEADLINE=$(($(date +%s) + TIMEOUT_SECS))
PREV_TS_COUNT=-1
PREV_CH_COUNT=-1
STABLE_ROUNDS=0
STABLE_REQUIRED=2   # Both stores must hold the same count for 2 consecutive polls

TS_FINAL=0
CH_FINAL=0

while [ "$(date +%s)" -lt "$DEADLINE" ]; do
    TS_COUNT=$(ts_psql "SELECT COUNT(*) FROM esg.clean_metrics WHERE tenant_id = '${TEST_TENANT}';" | tr -d '[:space:]' || echo "0")
    CH_COUNT=$(ch_query "SELECT count() FROM esg_readings WHERE tenant_id = '${TEST_TENANT}'" | tr -d '[:space:]' || echo "0")

    printf "      [%3ds] TS=%s  CH=%s  (target=%s)\n" \
        "$((TIMEOUT_SECS - (DEADLINE - $(date +%s))))" \
        "$TS_COUNT" "$CH_COUNT" "$EXPECTED"

    if [ "$TS_COUNT" = "$PREV_TS_COUNT" ] && [ "$CH_COUNT" = "$PREV_CH_COUNT" ] \
        && [ "$TS_COUNT" = "$CH_COUNT" ] && [ "$TS_COUNT" -ge "$EXPECTED" ]; then
        STABLE_ROUNDS=$((STABLE_ROUNDS + 1))
        if [ "$STABLE_ROUNDS" -ge "$STABLE_REQUIRED" ]; then
            TS_FINAL="$TS_COUNT"
            CH_FINAL="$CH_COUNT"
            break
        fi
    else
        STABLE_ROUNDS=0
    fi

    PREV_TS_COUNT="$TS_COUNT"
    PREV_CH_COUNT="$CH_COUNT"
    sleep "$POLL_INTERVAL"
done

if [ "$TS_FINAL" -eq 0 ] && [ "$CH_FINAL" -eq 0 ]; then
    # Timeout — use last known values
    TS_FINAL=$(ts_psql "SELECT COUNT(*) FROM esg.clean_metrics WHERE tenant_id = '${TEST_TENANT}';" | tr -d '[:space:]' || echo "0")
    CH_FINAL=$(ch_query "SELECT count() FROM esg_readings WHERE tenant_id = '${TEST_TENANT}'" | tr -d '[:space:]' || echo "0")
    echo ""
    echo "      ⚠️   TIMEOUT after ${TIMEOUT_SECS}s — using last observed counts"
fi

# ─── Step 6: Verify count delta and sum delta ─────────────────────────────────
echo ""
echo "[6/6] Verifying count delta and sum(value) delta..."

echo "      TimescaleDB final rows : ${TS_FINAL}"
echo "      ClickHouse  final rows : ${CH_FINAL}"

# Count check
if [ "$TS_FINAL" -eq "$CH_FINAL" ] && [ "$TS_FINAL" -eq "$EXPECTED" ]; then
    echo "      Row count delta  : 0 ✅  (both sinks = ${EXPECTED})"
    PASS=$((PASS + 1))
else
    COUNT_DELTA=$((TS_FINAL - CH_FINAL))
    echo "      Row count delta  : ${COUNT_DELTA} ❌  FAIL"
    echo "      HINT: TS=${TS_FINAL}, CH=${CH_FINAL}, expected=${EXPECTED}"
    echo "      Is EsgDualSinkJob running? Check: curl ${FLINK_REST}/jobs"
    FAIL=$((FAIL + 1))
fi

# Sum check
TS_SUM=$(ts_psql "SELECT ROUND(SUM(value)::numeric, 4) FROM esg.clean_metrics WHERE tenant_id = '${TEST_TENANT}';" | tr -d '[:space:]' || echo "0")
CH_SUM=$(ch_query "SELECT round(sum(value), 4) FROM esg_readings WHERE tenant_id = '${TEST_TENANT}'" | tr -d '[:space:]' || echo "0")

EXPECTED_SUM=$(python3 -c "print(round(${NUM_MESSAGES} * ${VALUE_PER_MSG}, 4))")
echo "      TimescaleDB SUM  : ${TS_SUM}  (expected ~${EXPECTED_SUM})"
echo "      ClickHouse  SUM  : ${CH_SUM}  (expected ~${EXPECTED_SUM})"

DIFF_PCT=$(python3 -c "
ts = float('${TS_SUM}' or 0)
ch = float('${CH_SUM}' or 0)
if ts == 0 and ch == 0:
    print('0.000000')
elif ts == 0:
    print('100.000000')
else:
    diff = abs(ts - ch) / ts * 100
    print(f'{diff:.6f}')
")

echo "      Relative diff    : ${DIFF_PCT}%"
THRESHOLD="0.010000"
PASS_SUM=$(python3 -c "print(1 if float('${DIFF_PCT}') < float('${THRESHOLD}') else 0)")

if [ "$PASS_SUM" -eq 1 ]; then
    echo "      SUM delta        : ${DIFF_PCT}% ✅  (threshold: <0.01%)"
    PASS=$((PASS + 1))
else
    echo "      SUM delta        : ${DIFF_PCT}% ❌  FAIL (threshold: <0.01%)"
    FAIL=$((FAIL + 1))
fi

# source_id coverage check (HB-EXT-02 regression guard)
CH_SOURCEID_NULLS=$(ch_query "SELECT count() FROM esg_readings WHERE tenant_id = '${TEST_TENANT}' AND (source_id = '' OR source_id IS NULL)" | tr -d '[:space:]' || echo "unknown")
if [ "$CH_SOURCEID_NULLS" = "0" ]; then
    echo "      source_id nulls  : 0 ✅  (all ${TS_FINAL} CH rows have source_id)"
    PASS=$((PASS + 1))
else
    echo "      source_id nulls  : ${CH_SOURCEID_NULLS} ❌  FAIL (source_id not populated in ClickHouse)"
    FAIL=$((FAIL + 1))
fi

# ─── Cleanup test data ────────────────────────────────────────────────────────
echo ""
echo "      Cleaning up test data..."
ts_psql "DELETE FROM esg.clean_metrics WHERE tenant_id = '${TEST_TENANT}';" > /dev/null 2>&1 || true
ch_query "ALTER TABLE esg_readings DELETE WHERE tenant_id = '${TEST_TENANT}'" > /dev/null 2>&1 || true

# ─── Final result ─────────────────────────────────────────────────────────────
echo ""
echo "╔══════════════════════════════════════════════════════════════╗"
printf "║  RESULT: %d passed, %d failed                              \n" "$PASS" "$FAIL"
if [ "$FAIL" -eq 0 ]; then
    echo "║  DUAL-SINK PIPELINE VERIFY: PASS ✅                          ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    exit 0
else
    echo "║  DUAL-SINK PIPELINE VERIFY: FAIL ❌                          ║"
    echo "║                                                              ║"
    echo "║  Troubleshooting:                                            ║"
    echo "║    1. Verify EsgDualSinkJob is deployed and RUNNING          ║"
    echo "║    2. Check Flink job logs for errors                        ║"
    echo "║    3. Confirm topic 'ngsi_ld_esg' has correct partitions     ║"
    echo "║    4. Increase --timeout if sinks are slow to flush          ║"
    echo "╚══════════════════════════════════════════════════════════════╝"
    exit 1
fi
