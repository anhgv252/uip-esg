#!/bin/bash
# Creates a dedicated replication role on the primary.
# Runs once via Docker initdb (only when data dir is empty = first boot).
# Part of Sprint 8 (S8-OPS04) — least-privilege PG replication.
set -euo pipefail

REPLICATION_USER="${REPLICATION_USER:-replicator}"
REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-${POSTGRES_PASSWORD}}"

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${REPLICATION_USER}') THEN
            CREATE ROLE ${REPLICATION_USER} WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';
        END IF;
    END
    \$\$;
EOSQL

echo "[OK] Replication role '${REPLICATION_USER}' ensured"
