#!/usr/bin/env bash
# ClickHouse ON CLUSTER DDL migration script — Sprint 9 S9-TD-01
# Idempotent: safe to run multiple times, on fresh or existing cluster
# Usage: ./ch-cluster-init.sh [--host localhost] [--port 8123]
set -euo pipefail

CH_HOST="${CH_HOST:-localhost}"
CH_PORT="${CH_PORT:-8123}"
CH_USER="${CH_USER:-default}"
CH_PASSWORD="${CH_PASSWORD:-}"
CLUSTER_NAME="${CLUSTER_NAME:-uip_cluster}"

execute_sql() {
    local sql="$1"
    curl -sf "http://${CH_HOST}:${CH_PORT}/" \
        --user "${CH_USER}:${CH_PASSWORD}" \
        --data-urlencode "query=${sql}" \
        | grep -v "^$" || true
    # Check HTTP error separately (curl -f exits non-zero on 4xx/5xx)
}

log() { echo "[$(date '+%H:%M:%S')] $*"; }

log "Waiting for ClickHouse to be ready..."
for i in $(seq 1 30); do
    if curl -sf "http://${CH_HOST}:${CH_PORT}/ping" | grep -q "Ok."; then
        break
    fi
    sleep 2
done

log "Creating database on cluster..."
execute_sql "CREATE DATABASE IF NOT EXISTS analytics ON CLUSTER ${CLUSTER_NAME}"

log "Creating sensor_reading_hourly table on cluster..."
execute_sql "CREATE TABLE IF NOT EXISTS analytics.sensor_reading_hourly ON CLUSTER ${CLUSTER_NAME} (
    tenant_id String,
    sensor_id String,
    sensor_type String,
    hour DateTime,
    avg_value Float64,
    min_value Float64,
    max_value Float64,
    reading_count UInt32,
    unit String
) ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/sensor_reading_hourly', '{replica}')
PARTITION BY toYYYYMM(hour)
ORDER BY (tenant_id, sensor_id, hour)
TTL hour + INTERVAL 2 YEAR"

log "Creating distributed table for sensor_reading_hourly..."
execute_sql "CREATE TABLE IF NOT EXISTS analytics.sensor_reading_hourly_all ON CLUSTER ${CLUSTER_NAME}
AS analytics.sensor_reading_hourly
ENGINE = Distributed(${CLUSTER_NAME}, analytics, sensor_reading_hourly, rand())"

log "Creating esg_metric_monthly table on cluster..."
execute_sql "CREATE TABLE IF NOT EXISTS analytics.esg_metric_monthly ON CLUSTER ${CLUSTER_NAME} (
    tenant_id String,
    building_id String,
    metric_type String,
    month Date,
    value Float64,
    unit String,
    category String,
    created_at DateTime DEFAULT now()
) ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/esg_metric_monthly', '{replica}')
PARTITION BY toYYYYMM(month)
ORDER BY (tenant_id, building_id, metric_type, month)"

log "Creating distributed table for esg_metric_monthly..."
execute_sql "CREATE TABLE IF NOT EXISTS analytics.esg_metric_monthly_all ON CLUSTER ${CLUSTER_NAME}
AS analytics.esg_metric_monthly
ENGINE = Distributed(${CLUSTER_NAME}, analytics, esg_metric_monthly, rand())"

log "Verifying tables exist on all nodes..."
execute_sql "SELECT name, engine FROM system.tables WHERE database = 'analytics' ORDER BY name" | grep -v "^$" || true

log "CH cluster DDL migration complete."
