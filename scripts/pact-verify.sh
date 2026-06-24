#!/bin/bash
# Pact Contract Verification — S11-PACT-01
# Runs consumer tests, copies Pact files, runs provider verification.
#
# Usage: ./scripts/pact-verify.sh
# Exit: 0 if all contracts verified, 1 if any failure

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="${PROJECT_ROOT}/backend"
ANALYTICS_DIR="${PROJECT_ROOT}/applications/analytics-service"
PACT_SOURCE="${BACKEND_DIR}/build/pacts"
PACT_TARGET="${ANALYTICS_DIR}/src/test/resources/pacts"

log() { echo "[$(date +%H:%M:%S)] $*"; }
pass() { echo "✅ $*"; }
fail() { echo "❌ $*"; }

log "=== Pact Contract Verification ==="
echo ""

# ── Step 1: Run Consumer Tests ──────────────────────────────────
log "Step 1: Running consumer tests (backend → analytics-service contract)..."

cd "${BACKEND_DIR}"
./gradlew test --tests "com.uip.backend.esg.config.analytics.AnalyticsServiceConsumerPactTest" \
    -Dpact.writer.overwrite=true \
    --info 2>&1 | tail -5

if [[ ! -d "${PACT_SOURCE}" ]] || [[ -z "$(ls -A "${PACT_SOURCE}" 2>/dev/null)" ]]; then
    fail "No Pact files generated in ${PACT_SOURCE}"
    exit 1
fi
pass "Consumer tests passed — Pact files generated:"
ls -la "${PACT_SOURCE}/"*.json 2>/dev/null || true

# ── Step 2: Copy Pact files to provider ─────────────────────────
log ""
log "Step 2: Copying Pact files to analytics-service..."

mkdir -p "${PACT_TARGET}"
cp -v "${PACT_SOURCE}"/*.json "${PACT_TARGET}/"

# ── Step 3: Run Provider Verification ───────────────────────────
log ""
log "Step 3: Running provider verification (analytics-service)..."

cd "${ANALYTICS_DIR}"
# IMPORTANT: the provider test is tagged @Tag("integration") and is excluded
# from the default `test` task (excludeTags 'integration' in build.gradle).
# It must run via the `integrationTest` task, otherwise Gradle reports
# "No tests found for given includes". See analytics-service/build.gradle.
./gradlew integrationTest --tests "com.uip.analytics.contract.AnalyticsServiceProviderPactTest" 2>&1 | tail -5

EXIT_CODE=$?

echo ""
if [[ $EXIT_CODE -eq 0 ]]; then
    pass "Pact contract verification PASSED — all contracts verified"
    log "Pact files retained in ${PACT_TARGET}/ — they are the committed"
    log "source-of-truth for standalone provider verification"
    log "('./gradlew integrationTest --tests '...PactTest' without the consumer step)."
    log "Re-running this script refreshes them from the consumer test output."
    exit 0
else
    fail "Pact contract verification FAILED"
    log "Pact files retained in ${PACT_TARGET}/ for debugging"
    exit 1
fi
