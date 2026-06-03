#!/bin/bash
# =============================================================================
# OWASP ZAP Automated Security Scan — UIP Smart City Platform
# Updated: zaproxy/zap-stable image (June 2026)
# Usage: ./run-zap-scan.sh [backend_url] [frontend_url]
# =============================================================================
set -euo pipefail

TARGET="${1:-http://host.docker.internal:8080}"
FRONTEND="${2:-http://host.docker.internal:3000}"
REPORT_DIR="$(cd "$(dirname "$0")" && pwd)/reports"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
ZAP_IMAGE="zaproxy/zap-stable:latest"
# ZAP scripts write reports relative to /zap/wrk — mount there
ZAP_MOUNT_DIR="/zap/wrk"

echo "=== OWASP ZAP Security Scan — UIP Smart City ==="
echo "Backend : ${TARGET}"
echo "Frontend: ${FRONTEND}"
echo "Image   : ${ZAP_IMAGE}"
echo "Reports : ${REPORT_DIR}"
echo ""

# Create report directory
mkdir -p "${REPORT_DIR}"

# --- Phase 1: Baseline scan against backend (passive) ---
echo "[Phase 1/3] Baseline scan — backend passive..."
docker run -t --rm \
  --name zap-baseline \
  -v "${REPORT_DIR}:${ZAP_MOUNT_DIR}:rw" \
  "${ZAP_IMAGE}" \
  zap-baseline.py \
  -t "${TARGET}" \
  -r "zap-baseline-backend-${TIMESTAMP}.html" \
  -w "zap-baseline-backend-${TIMESTAMP}.md" \
  -j \
  || true

echo "[Phase 1] Complete."
echo ""

# --- Phase 2: Baseline scan against frontend (passive) ---
echo "[Phase 2/3] Baseline scan — frontend passive..."
docker run -t --rm \
  --name zap-frontend \
  -v "${REPORT_DIR}:${ZAP_MOUNT_DIR}:rw" \
  "${ZAP_IMAGE}" \
  zap-baseline.py \
  -t "${FRONTEND}" \
  -r "zap-baseline-frontend-${TIMESTAMP}.html" \
  -w "zap-baseline-frontend-${TIMESTAMP}.md" \
  -j \
  || true

echo "[Phase 2] Complete."
echo ""

# --- Phase 3: Active scan against backend API ---
echo "[Phase 3/3] Active scan — backend API..."
docker run -t --rm \
  --name zap-active \
  -v "${REPORT_DIR}:${ZAP_MOUNT_DIR}:rw" \
  "${ZAP_IMAGE}" \
  zap-full-scan.py \
  -t "${TARGET}" \
  -r "zap-active-backend-${TIMESTAMP}.html" \
  -w "zap-active-backend-${TIMESTAMP}.md" \
  -z "-config api.disablekey=true" \
  || true

echo "[Phase 3] Complete."
echo ""

# --- Summary ---
echo "=========================================="
echo "  Scan Complete — Reports saved to:"
echo "  ${REPORT_DIR}/"
echo ""
ls -la "${REPORT_DIR}/"*"${TIMESTAMP}"* 2>/dev/null || echo "  (check directory for report files)"
echo ""
echo "  Next steps:"
echo "  1. Review findings in HTML reports"
echo "  2. Update docs/mvp3/security/owasp-report-template.md"
echo "  3. Remediate Critical/High findings"
echo "  4. Re-scan to verify fixes"
echo "=========================================="
