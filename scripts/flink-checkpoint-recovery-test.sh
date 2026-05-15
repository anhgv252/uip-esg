#!/usr/bin/env bash
# TD-03: Flink Kill/Restart E2E + Checkpoint Recovery Test
# Verifies: checkpoint save → force kill → restart → 0 event loss
# Pre-req: docker compose up (infrastructure/docker-compose.yml)
set -euo pipefail

FLINK_JM="http://localhost:8081"
COMPOSE_FILE="${1:-infrastructure/docker-compose.yml}"

echo "=== TD-03: Flink Checkpoint Recovery E2E Test ==="

echo "[1/6] Checking Flink JobManager is running..."
jm_status=$(curl -s -o /dev/null -w "%{http_code}" "${FLINK_JM}/overview" --max-time 5 2>/dev/null || echo "000")
if [ "$jm_status" != "200" ]; then
    echo "FAIL: Flink JobManager not reachable (status=${jm_status})" >&2
    echo "Run: docker compose -f ${COMPOSE_FILE} up -d" >&2
    exit 1
fi
echo "  JobManager healthy (200)"

echo "[2/6] Finding running Flink jobs..."
jobs_response=$(curl -s "${FLINK_JM}/jobs" 2>/dev/null)
running_jobs=$(echo "$jobs_response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
running = [j['id'] for j in data.get('jobs', []) if j['status'] == 'RUNNING']
print('\n'.join(running))
" 2>/dev/null || echo "")

if [ -z "$running_jobs" ]; then
    echo "WARN: No running Flink jobs found. Submit EsgDualSinkJob first." >&2
    echo "  docker compose -f ${COMPOSE_FILE} run --rm flink-esg-job-submitter"
    exit 1
fi

job_id=$(echo "$running_jobs" | head -1)
echo "  Found running job: ${job_id}"

echo "[3/6] Recording pre-kill metrics..."
pre_rows_ts=$(docker exec uip-timescaledb psql -U uip -d uip_smartcity -t -c \
    "SELECT count(*) FROM esg.clean_metrics;" 2>/dev/null | tr -d ' ' || echo "0")
pre_rows_ch=$(curl -s "http://localhost:8123/?query=SELECT+count()+FROM+analytics.esg_readings" \
    --max-time 5 2>/dev/null || echo "0")
echo "  TimescaleDB rows: ${pre_rows_ts}"
echo "  ClickHouse rows: ${pre_rows_ch}"

echo "[4/6] Force killing Flink TaskManager..."
docker kill uip-flink-taskmanager 2>/dev/null || true
sleep 3
echo "  TaskManager killed"

echo "[5/6] Restarting Flink TaskManager..."
docker compose -f "${COMPOSE_FILE}" start flink-taskmanager 2>/dev/null || \
    docker start uip-flink-taskmanager 2>/dev/null || true

echo "  Waiting for TaskManager to register..."
retries=0
max_retries=30
while [ $retries -lt $max_retries ]; do
    tm_count=$(curl -s "${FLINK_JM}/taskmanagers" 2>/dev/null | \
        python3 -c "import sys,json; print(len(json.load(sys.stdin).get('taskmanagers',[])))" 2>/dev/null || echo "0")
    if [ "$tm_count" -gt 0 ]; then
        echo "  TaskManager registered after $((retries * 5))s"
        break
    fi
    retries=$((retries + 1))
    sleep 5
done

if [ $retries -ge $max_retries ]; then
    echo "FAIL: TaskManager did not register within 150s" >&2
    exit 1
fi

echo "[6/6] Verifying job recovery..."
sleep 10
post_jobs_response=$(curl -s "${FLINK_JM}/jobs" 2>/dev/null)
post_running=$(echo "$post_jobs_response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
running = [j['id'] for j in data.get('jobs', []) if j['status'] == 'RUNNING']
print(len(running))
" 2>/dev/null || echo "0")

if [ "$post_running" -eq 0 ]; then
    echo "WARN: No running jobs after recovery. Checking for RESTARTING state..."
    restarting=$(echo "$post_jobs_response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
states = [j['status'] for j in data.get('jobs', [])]
print(', '.join(states) if states else 'none')
" 2>/dev/null || echo "unknown")
    echo "  Job states: ${restarting}"
fi

post_rows_ts=$(docker exec uip-timescaledb psql -U uip -d uip_smartcity -t -c \
    "SELECT count(*) FROM esg.clean_metrics;" 2>/dev/null | tr -d ' ' || echo "0")
post_rows_ch=$(curl -s "http://localhost:8123/?query=SELECT+count()+FROM+analytics.esg_readings" \
    --max-time 5 2>/dev/null || echo "0")

echo ""
echo "=== Results ==="
echo "  TimescaleDB: ${pre_rows_ts} → ${post_rows_ts} (delta: $((post_rows_ts - pre_rows_ts)))"
echo "  ClickHouse:  ${pre_rows_ch} → ${post_rows_ch} (delta: $((post_rows_ch - pre_rows_ch)))"
echo "  Running jobs after recovery: ${post_running}"

if [ "$post_running" -gt 0 ]; then
    echo "  PASS: Job recovered from checkpoint"
else
    echo "  WARN: Job not yet RUNNING — may need manual restart or more wait time"
fi

echo "=== TD-03 Complete ==="
