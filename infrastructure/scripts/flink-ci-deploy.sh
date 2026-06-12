#!/usr/bin/env bash
# Flink CI Deployment Pipeline — GAP-036
# Build JAR → Upload to Flink REST API → Verify job running
#
# Usage:
#   ./flink-ci-deploy.sh [JAR_PATH] [ENTRY_CLASS]
#   ./flink-ci-deploy.sh                              # Deploy all jobs from default JAR
#   ./flink-ci-deploy.sh build/libs/job.jar com.uip.flink.esg.EsgDualSinkJob  # Single job
#
# CI Integration (GitHub Actions / GitLab CI):
#   - Set FLINK_URL to remote Flink JobManager
#   - Set SAVEPOINT_DIR for checkpoint storage
#   - Artifact: flink JAR from build stage
#
# Rollback:
#   ./flink-ci-deploy.sh rollback
#   (or: make flink-rollback)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[CI-INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[CI-PASS]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[CI-WARN]${NC} $*"; }
log_error() { echo -e "${RED}[CI-FAIL]${NC} $*"; }

FLINK_URL="${FLINK_URL:-http://localhost:8081}"
JAR_PATH="${1:-}"
ENTRY_CLASS="${2:-}"
MAX_VERIFY_RETRIES=30
VERIFY_INTERVAL=5

# ─── Phase 1: Build ───────────────────────────────────────────────────────────
phase_build() {
    log_info "Phase 1: Building Flink JAR..."
    bash "${SCRIPT_DIR}/flink-deploy.sh" build
    log_ok "Phase 1 complete: JAR built"
}

# ─── Phase 2: Deploy ──────────────────────────────────────────────────────────
phase_deploy() {
    log_info "Phase 2: Deploying to Flink cluster at ${FLINK_URL}..."

    if [[ -n "$JAR_PATH" ]]; then
        log_info "Deploying specific JAR: ${JAR_PATH} (${ENTRY_CLASS:-all jobs})"
        JAR="$JAR_PATH" ENTRY_CLASS="${ENTRY_CLASS:-}" \
            FLINK_URL="$FLINK_URL" \
            bash "${SCRIPT_DIR}/flink-deploy.sh" deploy
    else
        FLINK_URL="$FLINK_URL" bash "${SCRIPT_DIR}/flink-deploy.sh" deploy
    fi

    log_ok "Phase 2 complete: Jobs deployed"
}

# ─── Phase 3: Verify ─────────────────────────────────────────────────────────
phase_verify() {
    log_info "Phase 3: Verifying jobs are running (max $((MAX_VERIFY_RETRIES * VERIFY_INTERVAL))s)..."

    local retry=0
    local running=0

    while [[ $retry -lt $MAX_VERIFY_RETRIES ]]; do
        running=$(curl -sf "${FLINK_URL}/overview" 2>/dev/null | \
            python3 -c "import sys,json; print(json.load(sys.stdin).get('jobs-running',0))" 2>/dev/null || echo "0")

        if [[ "$running" -gt 0 ]]; then
            log_ok "Phase 3 complete: ${running} job(s) RUNNING"
            return 0
        fi

        retry=$((retry + 1))
        log_info "Waiting for jobs to start... (${retry}/${MAX_VERIFY_RETRIES})"
        sleep $VERIFY_INTERVAL
    done

    log_error "Phase 3 FAILED: No jobs running after $((MAX_VERIFY_RETRIES * VERIFY_INTERVAL))s"
    log_error "Check Flink logs: docker compose logs flink-jobmanager"
    return 1
}

# ─── Rollback ─────────────────────────────────────────────────────────────────
phase_rollback() {
    log_info "Rollback: Restoring from last savepoint..."
    FLINK_URL="$FLINK_URL" bash "${SCRIPT_DIR}/flink-deploy.sh" rollback
    log_ok "Rollback complete"
}

# ─── Main ─────────────────────────────────────────────────────────────────────

echo ""
echo "=== Flink CI Deployment Pipeline (GAP-036) ==="
echo "  Flink URL:     ${FLINK_URL}"
echo "  JAR Override:  ${JAR_PATH:-<auto>}"
echo "  Entry Class:   ${ENTRY_CLASS:-<all>}"
echo ""

START_TIME=$(date +%s)

case "${1:-deploy}" in
    rollback)
        phase_rollback
        ;;
    build-only)
        phase_build
        ;;
    deploy)
        phase_build
        echo ""
        phase_deploy
        echo ""
        phase_verify
        ;;
    verify-only)
        phase_verify
        ;;
    *)
        echo "Usage: $0 [deploy|rollback|build-only|verify-only] [JAR_PATH] [ENTRY_CLASS]"
        exit 1
        ;;
esac

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

echo ""
log_ok "Pipeline completed in ${ELAPSED}s"
echo ""
