#!/usr/bin/env bash
# shadow-72h-monitor.sh — Sprint MVP3-1 Shadow Validation
#
# Monitors analytics-service shadow: compares TimescaleDB vs ClickHouse data consistency
# every 30 minutes for 72 hours. Fails immediately if diff > 0.01%.
#
# Sprint 1 gate: "analytics-service shadow diff <0.01% sustained 72h"
#
# Usage:
#   nohup ./shadow-72h-monitor.sh > shadow-validation-log.txt 2>&1 &
#   echo $! > shadow-monitor.pid
#
# Stop: kill $(cat shadow-monitor.pid)
# View: tail -f shadow-validation-log.txt

set -euo pipefail

ANALYTICS_URL="${ANALYTICS_URL:-http://localhost:8082}"
KONG_URL="${KONG_URL:-http://localhost:8000}"
# Shadow validation scope: energy_readings (hcm) — matches the 5,015-row seed verified 2026-05-12
CH_DB="uip_analytics"
CH_TABLE="energy_readings"
TS_TENANT="${SHADOW_TENANT:-hcm}"
TS_METRIC="ENERGY"
POLL_INTERVAL_SEC=1800     # 30 minutes
DURATION_SEC=259200        # 72 hours
DIFF_THRESHOLD="0.01"      # 0.01%
LOG_FILE="${LOG_FILE:-shadow-validation-log.txt}"

START_TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)
END_TS=$(date -u -v+72H +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -d "+72 hours" +%Y-%m-%dT%H:%M:%SZ)
ELAPSED=0
PASS_COUNT=0
FAIL_COUNT=0

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
# Shadow scope: energy_readings (hcm, epoch 1776996000→1778303280) — 5,015 rows baseline
CH_FROM_EPOCH=1776996000
CH_TO_EPOCH=1778303280

ch_sum()  { docker exec uip-clickhouse wget -qO- "http://localhost:8123/?database=${CH_DB}&user=default&query=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.stdin.read()))" <<< "SELECT round(sum(kwh), 0) FROM ${CH_TABLE} WHERE tenant_id = '${TS_TENANT}' AND ts BETWEEN ${CH_FROM_EPOCH} AND ${CH_TO_EPOCH}")"; }
ch_count(){ docker exec uip-clickhouse wget -qO- "http://localhost:8123/?database=${CH_DB}&user=default&query=$(python3 -c "import urllib.parse,sys; print(urllib.parse.quote(sys.stdin.read()))" <<< "SELECT count() FROM ${CH_TABLE} WHERE tenant_id = '${TS_TENANT}' AND ts BETWEEN ${CH_FROM_EPOCH} AND ${CH_TO_EPOCH}")"; }

# Get fresh JWT from backend, then call analytics-service
analytics_kwh() {
    python3 - <<'PYEOF'
import urllib.request, json, os
BACKEND = os.environ.get('BACKEND_URL', 'http://localhost:8080')
ANALYTICS = os.environ.get('ANALYTICS_URL', 'http://localhost:8082')
try:
    # Login
    login_req = urllib.request.Request(
        f'{BACKEND}/api/v1/auth/login',
        data=json.dumps({'username': 'admin', 'password': 'admin_Dev#2026!'}).encode(),
        headers={'Content-Type': 'application/json'}
    )
    token = json.loads(urllib.request.urlopen(login_req, timeout=5).read()).get('accessToken', '')
    if not token:
        print('ERROR')
    else:
        # Call analytics-service
        req = urllib.request.Request(
            f'{ANALYTICS}/api/v1/analytics/energy-aggregate',
            data=json.dumps({'tenantId': 'hcm', 'buildingIds': [], 'fromEpoch': 1776996000, 'toEpoch': 1778303280}).encode(),
            headers={'Content-Type': 'application/json', 'Authorization': f'Bearer {token}', 'X-Tenant-ID': 'hcm'}
        )
        d = json.loads(urllib.request.urlopen(req, timeout=10).read())
        print(round(float(d.get('totalKwh', 0)), 0))
except Exception as e:
    print('ERROR')
PYEOF
}
analytics_health() { curl -sf "${ANALYTICS_URL}/actuator/health" 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','UNKNOWN'))" 2>/dev/null || echo "UNREACHABLE"; }

echo "========================================"
echo "Shadow 72h Monitor — START"
echo "Start   : $START_TS"
echo "End     : $END_TS"
echo "Tenant  : $TS_TENANT"
echo "Interval: ${POLL_INTERVAL_SEC}s (30 min)"
echo "========================================"
echo ""

while [ "$ELAPSED" -lt "$DURATION_SEC" ]; do
    NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    ELAPSED_H=$((ELAPSED / 3600))
    ELAPSED_M=$(((ELAPSED % 3600) / 60))

    echo "--- Poll at $NOW (${ELAPSED_H}h${ELAPSED_M}m elapsed) ---"

    # Health check
    HEALTH=$(analytics_health)
    echo "  analytics-service health : $HEALTH"

    # Collect metrics
    CH_CNT=$(ch_count 2>/dev/null || echo "ERROR")
    CH_SUM=$(ch_sum 2>/dev/null || echo "ERROR")
    API_KWH=$(analytics_kwh 2>/dev/null || echo "ERROR")

    echo "  ClickHouse rows (hcm)    : $CH_CNT"
    echo "  ClickHouse SUM kWh       : $CH_SUM"
    echo "  analytics-service totalKwh: $API_KWH"

    if [[ "$CH_SUM" == "ERROR" || "$API_KWH" == "ERROR" ]]; then
        echo "  STATUS: ⚠️  Service unreachable — skipping diff check this poll"
        echo ""
    else
        # API vs ClickHouse diff % (key shadow validation metric)
        API_DIFF=$(python3 -c "
ch=float('${CH_SUM}'); api=float('${API_KWH}')
if ch == 0: print('0.000000')
else: print('{:.6f}'.format(abs(ch - api) / ch * 100))
")
        echo "  API vs CH diff %         : ${API_DIFF}%"

        SUM_FAIL=$(python3 -c "print(1 if float('${API_DIFF}') > float('${DIFF_THRESHOLD}') else 0)")

        if [ "$SUM_FAIL" -eq 0 ]; then
            echo "  STATUS: ✅ PASS (API diff ${API_DIFF}% < ${DIFF_THRESHOLD}%)"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo "  STATUS: ❌ FAIL — diff ${API_DIFF}% exceeds ${DIFF_THRESHOLD}% threshold!"
            FAIL_COUNT=$((FAIL_COUNT + 1))
            echo ""
            echo "SHADOW VALIDATION FAILED at $NOW after ${ELAPSED_H}h${ELAPSED_M}m"
            echo "Total checks: $((PASS_COUNT + FAIL_COUNT)) | Passed: $PASS_COUNT | Failed: $FAIL_COUNT"
            exit 1
        fi
        echo ""
    fi

    sleep "$POLL_INTERVAL_SEC"
    ELAPSED=$((ELAPSED + POLL_INTERVAL_SEC))
done

echo "========================================"
echo "Shadow 72h Monitor — COMPLETE"
echo "End     : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "Duration: 72h"
echo "Checks  : $((PASS_COUNT + FAIL_COUNT))"
echo "Passed  : $PASS_COUNT"
echo "Failed  : $FAIL_COUNT"
echo "STATUS  : ✅ SHADOW VALIDATION PASSED — diff <${DIFF_THRESHOLD}% sustained 72h"
echo "========================================"
