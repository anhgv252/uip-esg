#!/usr/bin/env bash
# Avro Schema Auto-Registration Script — Sprint 8 (S8-OPS06)
# Registers all Avro schemas into Apicurio Schema Registry on deploy.
# Idempotent: re-running doesn't fail if schemas already exist.
#
# Usage:
#   ./register-avro-schemas.sh [APICURIO_URL]
#
# Environment:
#   APICURIO_URL  Apicurio Registry URL (default: http://localhost:8087/apis/registry/v2)
#   SCHEMA_DIR    Avro schema directory (default: ../backend/src/main/resources/avro)
#
# Part of Sprint 8 — Avro auto-registration (EA P1 automation gap fix)
set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
APICURIO_URL="${1:-${APICURIO_URL:-http://localhost:8087/apis/registry/v2}}"
SCHEMA_DIR="${SCHEMA_DIR:-../backend/src/main/resources/avro}"

# Schema definitions: artifactId → filename
declare -A SCHEMAS=(
    ["sensor-reading-event"]="SensorReadingEvent.avsc"
    ["bms-reading-event"]="BmsReadingEvent.avsc"
    ["alert-detected-event"]="AlertDetectedEvent.avsc"
    ["hourly-rollup-event"]="HourlyRollupEvent.avsc"
)

# ─── Colors ───────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ─── Health Check ─────────────────────────────────────────────────────────────

check_apicurio_health() {
    log_info "Checking Apicurio Registry health at ${APICURIO_URL}..."
    local max_retries=30
    local retry=0

    while [[ $retry -lt $max_retries ]]; do
        if curl -sf "${APICURIO_URL}/system/info" > /dev/null 2>&1; then
            log_ok "Apicurio Registry is UP"
            return 0
        fi
        retry=$((retry + 1))
        log_info "Waiting for Apicurio... (${retry}/${max_retries})"
        sleep 2
    done

    log_error "Apicurio Registry not reachable after ${max_retries} retries"
    log_error "Ensure apicurio-registry service is running: docker compose ps"
    exit 1
}

# ─── Schema Registration ─────────────────────────────────────────────────────

register_schema() {
    local artifact_id="$1"
    local schema_file="${SCHEMA_DIR}/$2"

    if [[ ! -f "$schema_file" ]]; then
        log_error "Schema file not found: ${schema_file}"
        return 1
    fi

    log_info "Registering: ${artifact_id} ← ${schema_file}"

    # Check if artifact already exists
    local existing
    existing=$(curl -sf -o /dev/null -w "%{http_code}" \
        "${APICURIO_URL}/groups/default/artifacts/${artifact_id}" 2>/dev/null || echo "000")

    if [[ "$existing" == "200" ]]; then
        # Artifact exists — create new version
        local result
        result=$(curl -sf -X POST \
            "${APICURIO_URL}/groups/default/artifacts/${artifact_id}/versions" \
            -H "Content-Type: application/json; artifactType=AVRO" \
            -H "X-Registry-ArtifactId: ${artifact_id}" \
            -d @"${schema_file}" 2>&1)

        if [[ $? -eq 0 ]]; then
            local version
            version=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('version','?'))" 2>/dev/null || echo "?")
            log_ok "Updated: ${artifact_id} → version ${version}"
        else
            # If content identical, Apicurio returns 409 — that's OK (idempotent)
            if echo "$result" | grep -q "409\|Conflict\|already exists"; then
                log_ok "No change: ${artifact_id} (content identical)"
            else
                log_error "Failed to update ${artifact_id}: ${result}"
                return 1
            fi
        fi
    else
        # Artifact doesn't exist — create new
        local result
        result=$(curl -sf -X POST \
            "${APICURIO_URL}/groups/default/artifacts" \
            -H "Content-Type: application/json; artifactType=AVRO" \
            -H "X-Registry-ArtifactId: ${artifact_id}" \
            -d @"${schema_file}" 2>&1)

        if [[ $? -eq 0 ]]; then
            local version
            version=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('version','1'))" 2>/dev/null || echo "1")
            log_ok "Registered: ${artifact_id} → version ${version}"
        else
            log_error "Failed to register ${artifact_id}: ${result}"
            return 1
        fi
    fi
}

list_artifacts() {
    log_info "Registered artifacts:"
    curl -sf "${APICURIO_URL}/groups/default/artifacts" 2>/dev/null | \
        python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    artifacts = data if isinstance(data, list) else data.get('artifacts', data.get('count', []))
    if isinstance(artifacts, list):
        for a in artifacts:
            aid = a.get('id', a.get('artifactId', '?'))
            print(f'  - {aid}')
    else:
        print(f'  (count: {data.get(\"count\", \"?\")})')
except:
    print('  (no artifacts or parse error)')
" 2>/dev/null || echo "  (unable to list)"
}

# ─── Main ─────────────────────────────────────────────────────────────────────

echo "╔══════════════════════════════════════════════════════════╗"
echo "║   Avro Schema Auto-Registration (S8-OPS06)             ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

check_apicurio_health
echo ""

log_info "Registering ${#SCHEMAS[@]} schemas from ${SCHEMA_DIR}..."
echo ""

local_failed=0
for artifact_id in "${!SCHEMAS[@]}"; do
    register_schema "$artifact_id" "${SCHEMAS[$artifact_id]}" || local_failed=$((local_failed + 1))
done

echo ""
if [[ $local_failed -eq 0 ]]; then
    log_ok "All ${#SCHEMAS[@]} schemas registered successfully"
else
    log_error "${local_failed}/${#SCHEMAS[@]} schemas failed to register"
    exit 1
fi

echo ""
list_artifacts
echo ""
log_info "Done. Schemas are BACKWARD compatible (Apicurio default)."
