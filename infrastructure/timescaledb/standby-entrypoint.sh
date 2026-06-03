#!/bin/bash
# TimescaleDB Standby Bootstrap Script
# Runs pg_basebackup from primary if data dir is empty, then starts as standby.
#
# Environment:
#   STANDBY_PRIMARY_HOST  - Primary hostname (default: timescaledb)
#   STANDBY_PRIMARY_PORT  - Primary port (default: 5432)
#   PGUSER               - Replication user (default: POSTGRES_USER)
#   PGPASSWORD           - Replication password
#
# Manual promotion (SA recommendation — no auto-failover):
#   docker compose exec timescaledb-standby pg_ctl promote -D /var/lib/postgresql/data
#
# Part of Sprint 8 (S8-OPS04) — PG Streaming Replication
set -euo pipefail

DATA_DIR="/var/lib/postgresql/data"
PRIMARY_HOST="${STANDBY_PRIMARY_HOST:-timescaledb}"
PRIMARY_PORT="${STANDBY_PRIMARY_PORT:-5432}"
REPL_USER="${REPLICATION_USER:-replicator}"
REPL_PASS="${REPLICATION_PASSWORD:-${POSTGRES_PASSWORD}}"

echo "=== TimescaleDB Standby Bootstrap ==="
echo "  Primary: ${PRIMARY_HOST}:${PRIMARY_PORT}"
echo "  Data dir: ${DATA_DIR}"

# Check if data directory is empty (first start)
if [ -z "$(ls -A "${DATA_DIR}" 2>/dev/null)" ]; then
    echo "[INFO] Empty data directory — running pg_basebackup from primary..."

    # Wait for primary to be ready
    MAX_RETRIES=60
    RETRY=0
    until pg_isready -h "${PRIMARY_HOST}" -p "${PRIMARY_PORT}" -U "${REPL_USER}" -q 2>/dev/null; do
        RETRY=$((RETRY + 1))
        if [ $RETRY -ge $MAX_RETRIES ]; then
            echo "[ERROR] Primary not ready after ${MAX_RETRIES} retries"
            exit 1
        fi
        echo "  Waiting for primary... (${RETRY}/${MAX_RETRIES})"
        sleep 2
    done
    echo "[OK] Primary is ready"

    # Run pg_basebackup as dedicated replication role (-R writes standby.signal + primary_conninfo)
    echo "[INFO] Running pg_basebackup as ${REPL_USER}..."
    PGPASSWORD="${REPL_PASS}" pg_basebackup \
        -h "${PRIMARY_HOST}" \
        -p "${PRIMARY_PORT}" \
        -U "${REPL_USER}" \
        -D "${DATA_DIR}" \
        -Fp -Xs -P -R \
        --checkpoint=fast

    echo "[OK] pg_basebackup complete"

    # Add promote_trigger_file (not written by pg_basebackup -R)
    echo "promote_trigger_file = '/tmp/promote.trigger'" >> "${DATA_DIR}/postgresql.auto.conf"
    echo "[OK] Standby configured"
else
    echo "[INFO] Data directory exists — skipping pg_basebackup"
fi
echo "[INFO] Starting PostgreSQL in standby mode..."

# Start PostgreSQL (original entrypoint)
exec /usr/local/bin/docker-entrypoint.sh postgres
