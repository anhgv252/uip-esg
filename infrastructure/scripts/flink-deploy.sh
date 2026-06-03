#!/usr/bin/env bash
# Flink Job Deployment Script — ADR-038
# Manages Flink job lifecycle: build → savepoint → cancel → submit
#
# Usage:
#   ./flink-deploy.sh [command] [options]
#
# Commands:
#   list              List running Flink jobs
#   build             Build Flink JAR with git hash
#   submit            Submit all Flink jobs to cluster
#   cancel            Cancel all running Flink jobs
#   savepoint         Take savepoint of all running jobs
#   deploy            Full deploy cycle: savepoint → cancel → submit
#   status            Show detailed job status
#
# Environment:
#   FLINK_URL         Flink JobManager URL (default: http://localhost:8081)
#   SAVEPOINT_DIR     Savepoint directory (default: s3://uip-flink-checkpoints/savepoints)
#   JAR_DIR           JAR directory (default: ../flink-jobs/target)
#
# Part of Sprint 8 (S8-OPS03) — Flink CI/CD automation
set -euo pipefail

# ─── Configuration ────────────────────────────────────────────────────────────
FLINK_URL="${FLINK_URL:-http://localhost:8081}"
SAVEPOINT_DIR="${SAVEPOINT_DIR:-s3://uip-flink-checkpoints/savepoints}"
JAR_DIR="${JAR_DIR:-../flink-jobs/target}"
JAR_PATTERN="uip-flink-jobs-*.jar"

# All Flink jobs to manage (class name → entry point)
declare -A FLINK_JOBS=(
    ["EsgDualSinkJob"]="com.uip.flink.esg.EsgDualSinkJob"
    ["Structural Vibration Anomaly Detection Job"]="com.uip.flink.structural.VibrationAnomalyJob"
)

# ─── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ─── Utility Functions ────────────────────────────────────────────────────────

check_flink_health() {
    if ! curl -sf "${FLINK_URL}/overview" > /dev/null 2>&1; then
        log_error "Flink JobManager not reachable at ${FLINK_URL}"
        log_error "Start the stack first: make up"
        exit 1
    fi
}

find_jar() {
    local jar
    jar=$(ls -t ${JAR_DIR}/${JAR_PATTERN} 2>/dev/null | head -1)
    if [[ -z "$jar" ]]; then
        log_error "No JAR found in ${JAR_DIR}/${JAR_PATTERN}"
        log_error "Build first: cd ../flink-jobs && mvn package -DskipTests"
        exit 1
    fi
    echo "$jar"
}

get_running_jobs() {
    curl -sf "${FLINK_URL}/jobs/overview" 2>/dev/null | \
        python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    for job in data.get('jobs', []):
        if job.get('state') == 'RUNNING':
            print(f\"{job['id']}\t{job['name']}\")
except: pass
" 2>/dev/null || true
}

# ─── Commands ─────────────────────────────────────────────────────────────────

cmd_list() {
    log_info "Flink jobs at ${FLINK_URL}:"
    local count
    count=$(curl -sf "${FLINK_URL}/jobs/overview" 2>/dev/null | \
        python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('jobs',[])))" 2>/dev/null || echo "0")

    echo ""
    printf "  %-40s %-45s %-12s\n" "JOB ID" "NAME" "STATE"
    printf "  %s\n" "$(printf '─%.0s' {1..97})"
    curl -sf "${FLINK_URL}/jobs/overview" 2>/dev/null | \
        python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    for job in data.get('jobs', []):
        state = job.get('state', 'UNKNOWN')
        color = '\033[32m' if state == 'RUNNING' else '\033[31m'
        print(f\"  {job['id']:<40} {job['name']:<45} {color}{state}\033[0m\")
except: pass
" 2>/dev/null || true
    echo ""
    log_info "Total: ${count} jobs"
}

cmd_build() {
    log_info "Building Flink jobs JAR..."
    local jar_dir
    jar_dir="$(cd "${JAR_DIR}/.." && pwd)"
    local git_hash
    git_hash=$(cd "$(dirname "$0")/../.." && git rev-parse --short HEAD 2>/dev/null || echo "unknown")
    log_info "Git hash: ${git_hash}"

    if [[ -f "${jar_dir}/pom.xml" ]]; then
        (cd "${jar_dir}" && mvn package -DskipTests -q)
    elif [[ -f "${jar_dir}/build.gradle" ]]; then
        (cd "${jar_dir}" && ./gradlew shadowJar --quiet)
    else
        log_error "No build system found in ${jar_dir}"
        exit 1
    fi

    local jar
    jar=$(find_jar)
    local size
    size=$(du -h "$jar" | cut -f1)
    log_ok "Built: $(basename "$jar") (${size})"
}

cmd_savepoint() {
    check_flink_health
    log_info "Taking savepoints of running jobs..."

    while IFS=$'\t' read -r job_id job_name; do
        [[ -z "$job_id" ]] && continue
        log_info "Savepoint: ${job_name} (${job_id})..."
        local result
        result=$(curl -sf -X PATCH \
            "${FLINK_URL}/jobs/${job_id}?mode=cancel_with_savepoint" \
            -H "Content-Type: application/json" \
            -d "{\"target-directory\":\"${SAVEPOINT_DIR}\"}" 2>&1 || true)

        if echo "$result" | grep -q "triggered"; then
            log_ok "Savepoint triggered for ${job_name}"
        else
            log_warn "Savepoint failed for ${job_name}: ${result}"
            log_warn "Proceeding without savepoint..."
        fi
    done <<< "$(get_running_jobs)"
}

cmd_cancel() {
    check_flink_health
    log_info "Cancelling running jobs..."

    while IFS=$'\t' read -r job_id job_name; do
        [[ -z "$job_id" ]] && continue
        log_info "Cancelling: ${job_name} (${job_id})..."
        curl -sf -X PATCH "${FLINK_URL}/jobs/${job_id}?mode=cancel" > /dev/null 2>&1 && \
            log_ok "Cancelled: ${job_name}" || \
            log_warn "Failed to cancel: ${job_name}"
    done <<< "$(get_running_jobs)"
}

cmd_submit() {
    check_flink_health
    local jar
    jar=$(find_jar)

    log_info "Submitting jobs from $(basename "$jar")..."

    for job_name in "${!FLINK_JOBS[@]}"; do
        local entry_class="${FLINK_JOBS[$job_name]}"

        # Check if already running
        local existing
        existing=$(get_running_jobs | grep -F "$job_name" || true)
        if [[ -n "$existing" ]]; then
            log_info "Job '${job_name}' already RUNNING — skipping"
            continue
        fi

        log_info "Submitting: ${entry_class} (name: ${job_name})..."
        local result
        result=$(curl -sf -X POST \
            "${FLINK_URL}/jars/upload" \
            -F "jarfile=@${jar}" 2>&1)

        local jar_id
        jar_id=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('filename',''))" 2>/dev/null || true)

        if [[ -z "$jar_id" ]]; then
            log_error "Failed to upload JAR: ${result}"
            continue
        fi

        # Submit with entry class
        local submit_result
        submit_result=$(curl -sf -X POST \
            "${FLINK_URL}/jars/$(basename "$jar_id")/run?entry-class=${entry_class}" 2>&1)

        if echo "$submit_result" | grep -q "jobid"; then
            local new_job_id
            new_job_id=$(echo "$submit_result" | python3 -c "import sys,json; print(json.load(sys.stdin).get('jobid',''))" 2>/dev/null || echo "unknown")
            log_ok "Submitted: ${job_name} → ${new_job_id}"
        else
            log_error "Failed to submit ${job_name}: ${submit_result}"
        fi
    done
}

cmd_deploy() {
    log_info "=== Flink Full Deploy Cycle ==="
    echo ""
    cmd_savepoint || true
    sleep 3
    cmd_cancel
    sleep 2
    cmd_submit
    echo ""
    log_ok "=== Deploy complete ==="
    cmd_list
}

cmd_status() {
    check_flink_health
    echo ""
    log_info "Flink Cluster Status: ${FLINK_URL}"
    echo ""
    curl -sf "${FLINK_URL}/overview" 2>/dev/null | \
        python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    print(f\"  Task Managers:    {d.get('taskmanagers', '?')}\")
    print(f\"  Slots Total:      {d.get('slots-total', '?')}\")
    print(f\"  Slots Available:  {d.get('slots-available', '?')}\")
    print(f\"  Jobs Running:     {d.get('jobs-running', '?')}\")
    print(f\"  Jobs Finished:    {d.get('jobs-finished', '?')}\")
    print(f\"  Jobs Cancelled:   {d.get('jobs-cancelled', '?')}\")
    print(f\"  Jobs Failed:      {d.get('jobs-failed', '?')}\")
except Exception as e:
    print(f'  Error reading status: {e}')
" 2>/dev/null
    echo ""
    cmd_list
}

# ─── Main ─────────────────────────────────────────────────────────────────────

case "${1:-help}" in
    list)       cmd_list ;;
    build)      cmd_build ;;
    submit)     cmd_submit ;;
    cancel)     cmd_cancel ;;
    savepoint)  cmd_savepoint ;;
    deploy)     cmd_deploy ;;
    status)     cmd_status ;;
    help|*)
        echo "Flink Job Deployment Tool (ADR-038)"
        echo ""
        echo "Usage: $0 <command>"
        echo ""
        echo "Commands:"
        echo "  list        List running Flink jobs"
        echo "  build       Build Flink JAR"
        echo "  submit      Submit all jobs to cluster"
        echo "  cancel      Cancel all running jobs"
        echo "  savepoint   Take savepoint of running jobs"
        echo "  deploy      Full cycle: savepoint → cancel → submit"
        echo "  status      Show cluster + job status"
        echo ""
        echo "Environment:"
        echo "  FLINK_URL    ${FLINK_URL}"
        echo "  SAVEPOINT_DIR ${SAVEPOINT_DIR}"
        echo "  JAR_DIR      ${JAR_DIR}"
        ;;
esac
