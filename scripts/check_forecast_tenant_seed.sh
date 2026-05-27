#!/usr/bin/env bash
# Quick gate: active tenants in Postgres must have ENERGY seed data in ClickHouse.
set -euo pipefail

PG_CONTAINER="${PG_CONTAINER:-uip-timescaledb}"
CH_CONTAINER="${CH_CONTAINER:-uip-clickhouse}"
PG_USER="${PG_USER:-uip}"
PG_DB="${PG_DB:-uip_smartcity}"

echo "[INFO] Checking active tenants from Postgres ($PG_CONTAINER/$PG_DB)..."
ACTIVE_TENANTS_RAW=$(docker exec "$PG_CONTAINER" psql -U "$PG_USER" -d "$PG_DB" -At -c \
  "SELECT tenant_id FROM tenants WHERE is_active = true ORDER BY tenant_id;")

if [[ -z "${ACTIVE_TENANTS_RAW}" ]]; then
  echo "[FAIL] No active tenants found in Postgres"
  exit 1
fi

mapfile -t ACTIVE_TENANTS <<< "$ACTIVE_TENANTS_RAW"

echo "[INFO] Checking ENERGY seeded tenants from ClickHouse ($CH_CONTAINER)..."
SEEDED_TENANTS_RAW=$(docker exec "$CH_CONTAINER" clickhouse-client --query \
  "SELECT DISTINCT tenant_id FROM analytics.esg_readings WHERE metric_type = 'ENERGY' ORDER BY tenant_id")

if [[ -z "${SEEDED_TENANTS_RAW}" ]]; then
  echo "[FAIL] No ENERGY data found in analytics.esg_readings"
  exit 1
fi

mapfile -t SEEDED_TENANTS <<< "$SEEDED_TENANTS_RAW"

missing=()
for tenant in "${ACTIVE_TENANTS[@]}"; do
  found=false
  for seeded in "${SEEDED_TENANTS[@]}"; do
    if [[ "$tenant" == "$seeded" ]]; then
      found=true
      break
    fi
  done
  if [[ "$found" == false ]]; then
    missing+=("$tenant")
  fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "[FAIL] Missing ENERGY seed for active tenants: ${missing[*]}"
  exit 1
fi

echo "[PASS] Active tenants have ENERGY seed data: ${ACTIVE_TENANTS[*]}"
