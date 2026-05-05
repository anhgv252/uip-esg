#!/bin/bash
# PostgreSQL PITR Restore Drill Script
# Usage: ./restore-drill.sh [target-time]
# Example: ./restore-drill.sh "2026-06-20 14:30:00"

set -euo pipefail

TARGET_TIME="${1:-$(date -d '1 hour ago' '+%Y-%m-%d %H:%M:%S' 2>/dev/null || date -v-1H '+%Y-%m-%d %H:%M:%S')}"
STANZA="uip-prod"
START_TIME=$(date +%s)

echo "=== UIP PostgreSQL Restore Drill ==="
echo "Target time: $TARGET_TIME"
echo "Start: $(date)"

echo "[1/6] Stopping application traffic..."
kubectl scale deployment uip-backend-blue uip-backend-green --replicas=0 -n uip-prod 2>/dev/null || echo "  (skip: not in K8s env)"

echo "[2/6] Available backups:"
pgbackrest --stanza=$STANZA info

echo "[3/6] Restoring to $TARGET_TIME..."
pgbackrest --stanza=$STANZA restore \
  --delta \
  --type=time \
  "--target=$TARGET_TIME" \
  --target-action=promote

echo "[4/6] Verifying data integrity..."
psql "$DATABASE_URL" -c "SELECT count(*) AS tenants FROM public.tenants;"
psql "$DATABASE_URL" -c "SELECT count(*) AS recent_readings FROM environment.sensor_readings WHERE timestamp > NOW() - INTERVAL '24h';"

echo "[5/6] Resuming application..."
kubectl scale deployment uip-backend-blue --replicas=2 -n uip-prod 2>/dev/null || echo "  (skip: not in K8s env)"

END_TIME=$(date +%s)
RTO_SECONDS=$((END_TIME - START_TIME))
RTO_MINUTES=$((RTO_SECONDS / 60))

echo "[6/6] === RESTORE DRILL RESULTS ==="
echo "RTO: ${RTO_MINUTES}m ${RTO_SECONDS}s"
echo "Target: < 60 minutes"
if [ "$RTO_MINUTES" -lt 60 ]; then
  echo "STATUS: PASS"
else
  echo "STATUS: FAIL — RTO exceeded 1 hour"
  exit 1
fi
