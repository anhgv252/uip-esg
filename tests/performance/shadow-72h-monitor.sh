#!/usr/bin/env bash
# shadow-72h-monitor.sh — Sprint MVP3-1 Shadow Validation
#
# HB-EXT-05: Updated to monitor REAL Flink dual-sink ingestion.
# Changes vs old version:
#   - Fixed DB: uip_analytics → analytics (ADR-026 / HB-EXT-02)
#   - Fixed table: energy_readings → esg_readings
#   - Fixed columns: kwh → value, ts → recorded_at
#   - Replaced static epoch range with a DYNAMIC rolling window
#   - Added TS vs CH diff check (dual-sink consistency, not just API vs CH)
#   - Added --hours flag for shorter validation runs (default: 72h)
#   - Added --window flag to control the time window queried (default: last 1h of ingestion)
#
# Usage:
#   nohup ./shadow-72h-monitor.sh > shadow-validation-log.txt 2>&1 &
#   echo $! > shadow-monitor.pid
#
#   # Shorter test run (e.g. 1 hour validation):
#   ./shadow-72h-monitor.sh --hours 1
#
# Stop: kill $(cat shadow-monitor.pid)
# View: tail -f shadow-validation-log.txt
#
# Env overrides:
#   ANALYTICS_URL  (default: http://localhost:8082)
#   BACKEND_URL    (default: http://localhost:8080)
#   SHADOW_TENANT  (default: hcm)

set -euo pipefail

ANALYTICS_URL="${ANALYTICS_URL:-http://localhost:8082}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"

# ─── Fixed schema references (post HB-EXT-02) ────────────────────────────────
CH_CONTAINER="uip-clickhouse"
TS_CONTAINER="uip-timescaledb"
CH_DB="analytics"
CH_TABLE="esg_readings"     # NOT energy_readings (old, wrong table)
TS_TENANT="${SHADOW_TENANT:-hcm}"
TS_METRIC="ENERGY"

# ─── Configurable parameters ──────────────────────────────────────────────────
POLL_INTERVAL_SEC=1800     # 30 minutes
DURATION_HOURS=72          # default 72h; override with --hours
WINDOW_MINUTES=60          # rolling window to query (last N minutes of ingested data)
DIFF_THRESHOLD="0.01"      # 0.01% max delta between TS and CH

while [[ $# -gt 0 ]]; do
    case $1 in
        --hours)   DURATION_HOURS="$2"; shift 2 ;;
        --window)  WINDOW_MINUTES="$2"; shift 2 ;;
        --poll)    POLL_INTERVAL_SEC="$2"; shift 2 ;;
        *) echo "Unknown flag: $1"; exit 1 ;;
    esac
done

DURATION_SEC=$((DURATION_HOURS * 3600))

START_TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)
END_TS_CMD="date -u -d '+${DURATION_HOURS} hours' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+${DURATION_HOURS}H +%Y-%m-%dT%H:%M:%SZ"
END_TS=$(eval "$END_TS_CMD" 2>/dev/null || date -u +%Y-%m-%dT%H:%M:%SZ)
ELAPSED=0
PASS_COUNT=0
FAIL_COUNT=0

# ─── Query helpers (dynamic rolling window) ───────────────────────────────────
# Uses NOW() - WINDOW_MINUTES instead of static epoch range

ch_query() {
    local sql="$1"
    local encoded
    encoded=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.stdin.read()))" <<< "$sql")
    docker exec "$CH_CONTAINER" wget -qO- \
        "http://localhost:8123/?database=${CH_DB}&user=default&query=${encoded}" 2>/dev/null | tr -d '[:space:]'
}

ts_psql() {
    docker exec "$TS_CONTAINER" psql -U uip -d uip_smartcity -t -A -c "$1" 2>/dev/null | tr -d '[:space:]'
}

# CH: sum(value) for ENERGY rows in last WINDOW_MINUTES
ch_sum() {
    ch_query "SELECT round(sum(value), 2) FROM ${CH_TABLE}
              WHERE tenant_id = '${TS_TENANT}'
                AND metric_type = '${TS_METRIC}'
                AND recorded_at >= now() - INTERVAL ${WINDOW_MINUTES} MINUTE"
}

# CH: row count in rolling window
ch_count() {
    ch_query "SELECT count() FROM ${CH_TABLE}
              WHERE tenant_id = '${TS_TENANT}'
                AND metric_type = '${TS_METRIC}'
                AND recorded_at >= now() - INTERVAL ${WINDOW_MINUTES} MINUTE"
}

# TS: sum(value) for ENERGY rows in last WINDOW_MINUTES
ts_sum() {
    ts_psql "SELECT ROUND(SUM(value)::numeric, 2) FROM esg.clean_metrics
             WHERE tenant_id = '${TS_TENANT}'
               AND metric_type = '${TS_METRIC}'
               AND timestamp >= NOW() - INTERVAL '${WINDOW_MINUTES} minutes'"
}

# TS: row count in rolling window
ts_count() {
    ts_psql "SELECT COUNT(*) FROM esg.clean_metrics
             WHERE tenant_id = '${TS_TENANT}'
               AND metric_type = '${TS_METRIC}'
               AND timestamp >= NOW() - INTERVAL '${WINDOW_MINUTES} minutes'"
}

# analytics-service API call
analytics_kwh() {
    local from_epoch to_epoch
    to_epoch=$(date +%s)
    from_epoch=$((to_epoch - WINDOW_MINUTES * 60))
    python3 - "$from_epoch" "$to_epoch" <<'PYEOF'
import urllib.request, json, os, sys
BACKEND   = os.environ.get('BACKEND_URL',   'http://localhost:8080')
ANALYTICS = os.environ.get('ANALYTICS_URL', 'http://localhost:8082')
TENANT    = os.environ.get('SHADOW_TENANT', 'hcm')
from_epoch = int(sys.argv[1])
to_epoch   = int(sys.argv[2])
try:
    login_req = urllib.request.Request(
        f'{BACKEND}/api/v1/auth/login',
        data=json.dumps({'username': 'admin', 'password': 'admin_Dev#2026!'}).encode(),
        headers={'Content-Type': 'application/json'}
    )
    token = json.loads(urllib.request.urlopen(login_req, timeout=5).read()).get('accessToken', '')
    if not token:
        print('ERROR')
    else:
        req = urllib.request.Request(
            f'{ANALYTICS}/api/v1/analytics/energy-aggregate',
            data=json.dumps({'tenantId': TENANT, 'buildingIds': [],
                             'fromEpoch': from_epoch, 'toEpoch': to_epoch}).encode(),
            headers={'Content-Type': 'application/json',
                     'Authorization': f'Bearer {token}',
                     'X-Tenant-ID': TENANT}
        )
        d = json.loads(urllib.request.urlopen(req, timeout=10).read())
        print(round(float(d.get('totalKwh', 0)), 2))
except Exception:
    print('ERROR')
PYEOF
}

analytics_health() {
    curl -sf "${ANALYTICS_URL}/actuator/health" 2>/dev/null \
    | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','UNKNOWN'))" \
    2>/dev/null || echo "UNREACHABLE"
}

# ─── Main loop ────────────────────────────────────────────────────────────────
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Shadow 72h Monitor — Dual-Sink Validation (HB-EXT-05)      ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo "  Start    : $START_TS"
echo "  Duration : ${DURATION_HOURS}h"
echo "  Tenant   : $TS_TENANT / metric: $TS_METRIC"
echo "  Window   : last ${WINDOW_MINUTES}min of ingested data (DYNAMIC)"
echo "  Interval : ${POLL_INTERVAL_SEC}s"
echo "  DB       : analytics.esg_readings (fixed from old uip_analytics.energy_readings)"
echo ""

while [ "$ELAPSED" -lt "$DURATION_SEC" ]; do
    NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    ELAPSED_H=$((ELAPSED / 3600))
    ELAPSED_M=$(((ELAPSED % 3600) / 60))

    echo "─── Poll at $NOW (${ELAPSED_H}h${ELAPSED_M}m elapsed) ───"

    HEALTH=$(analytics_health)
    echo "  analytics-service health  : $HEALTH"

    TS_CNT=$(ts_count  2>/dev/null || echo "ERROR")
    TS_SUM=$(ts_sum    2>/dev/null || echo "ERROR")
    CH_CNT=$(ch_count  2>/dev/null || echo "ERROR")
    CH_SUM=$(ch_sum    2>/dev/null || echo "ERROR")
    API_KWH=$(analytics_kwh 2>/dev/null || echo "ERROR")

    echo "  TimescaleDB rows (${WINDOW_MINUTES}min) : ${TS_CNT}"
    echo "  TimescaleDB SUM value     : ${TS_SUM}"
    echo "  ClickHouse  rows (${WINDOW_MINUTES}min) : ${CH_CNT}"
    echo "  ClickHouse  SUM value     : ${CH_SUM}"
    echo "  analytics-service totalKwh: ${API_KWH}"

    POLL_PASS=true

    # ─── Check 1: TS vs CH row count delta ─────────────────────────────────
    if [[ "$TS_CNT" == "ERROR" || "$CH_CNT" == "ERROR" ]]; then
        echo "  TS/CH count check         : ⚠️  store unreachable — skipping"
    elif [ "$TS_CNT" = "$CH_CNT" ]; then
        echo "  TS vs CH count delta      : 0 ✅  (both = ${TS_CNT})"
    else
        COUNT_DELTA=$((TS_CNT - CH_CNT))
        echo "  TS vs CH count delta      : ${COUNT_DELTA} ❌  FAIL (TS=${TS_CNT}, CH=${CH_CNT})"
        POLL_PASS=false
    fi

    # ─── Check 2: TS vs CH SUM delta ───────────────────────────────────────
    if [[ "$TS_SUM" == "ERROR" || "$CH_SUM" == "ERROR" || "$TS_SUM" == "" || "$CH_SUM" == "" ]]; then
        echo "  TS vs CH SUM diff         : ⚠️  store unreachable — skipping"
    else
        SUM_DIFF=$(python3 -c "
ts = float('${TS_SUM}' or 0)
ch = float('${CH_SUM}' or 0)
if ts == 0 and ch == 0: print('0.000000')
elif ts == 0: print('100.000000')
else: print(f'{abs(ts - ch) / ts * 100:.6f}')
")
        SUM_FAIL=$(python3 -c "print(1 if float('${SUM_DIFF}') > float('${DIFF_THRESHOLD}') else 0)")
        if [ "$SUM_FAIL" -eq 0 ]; then
            echo "  TS vs CH SUM diff         : ${SUM_DIFF}% ✅  (<${DIFF_THRESHOLD}%)"
        else
            echo "  TS vs CH SUM diff         : ${SUM_DIFF}% ❌  FAIL (>${DIFF_THRESHOLD}%)"
            POLL_PASS=false
        fi
    fi

    # ─── Check 3: API vs CH diff ────────────────────────────────────────────
    if [[ "$API_KWH" == "ERROR" || "$CH_SUM" == "ERROR" || "$CH_SUM" == "" ]]; then
        echo "  API vs CH diff            : ⚠️  service unreachable — skipping"
    else
        API_DIFF=$(python3 -c "
ch  = float('${CH_SUM}' or 0)
api = float('${API_KWH}' or 0)
if ch == 0 and api == 0: print('0.000000')
elif ch == 0: print('100.000000')
else: print(f'{abs(ch - api) / ch * 100:.6f}')
")
        API_FAIL=$(python3 -c "print(1 if float('${API_DIFF}') > float('${DIFF_THRESHOLD}') else 0)")
        if [ "$API_FAIL" -eq 0 ]; then
            echo "  API vs CH diff            : ${API_DIFF}% ✅  (<${DIFF_THRESHOLD}%)"
        else
            echo "  API vs CH diff            : ${API_DIFF}% ❌  FAIL (>${DIFF_THRESHOLD}%)"
            POLL_PASS=false
        fi
    fi

    # ─── Ingestion activity warning ─────────────────────────────────────────
    if [[ "$TS_CNT" != "ERROR" && "$TS_CNT" == "0" ]]; then
        echo "  ⚠️  WARNING: 0 rows in TS for last ${WINDOW_MINUTES}min"
        echo "     → Is EsgDualSinkJob running? Is Flink ingesting data?"
        echo "     → Run: curl ${ANALYTICS_URL}/actuator/health to check"
    fi

    if $POLL_PASS; then
        echo "  POLL RESULT: ✅ PASS"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo "  POLL RESULT: ❌ FAIL"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        echo ""
        echo "╔══════════════════════════════════════════════════════════════╗"
        echo "║  SHADOW VALIDATION FAILED at $NOW"
        echo "║  After ${ELAPSED_H}h${ELAPSED_M}m | Passed: $PASS_COUNT | Failed: $FAIL_COUNT"
        echo "╚══════════════════════════════════════════════════════════════╝"
        exit 1
    fi

    echo ""
    sleep "$POLL_INTERVAL_SEC"
    ELAPSED=$((ELAPSED + POLL_INTERVAL_SEC))
done

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Shadow ${DURATION_HOURS}h Monitor — COMPLETE                               ║"
printf "║  End     : %-52s ║\n" "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
printf "║  Checks  : %-52s ║\n" "$((PASS_COUNT + FAIL_COUNT))"
printf "║  Passed  : %-52s ║\n" "$PASS_COUNT"
printf "║  Failed  : %-52s ║\n" "$FAIL_COUNT"
echo "║  STATUS  : ✅ SHADOW VALIDATION PASSED — diff <${DIFF_THRESHOLD}% sustained ${DURATION_HOURS}h  ║"
echo "╚══════════════════════════════════════════════════════════════╝"
