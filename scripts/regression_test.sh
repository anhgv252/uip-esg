#!/usr/bin/env bash
# =============================================================================
# UIP Smart City — Full Regression Test Runner
# =============================================================================
# Mục đích: Chạy lại TOÀN BỘ test suite sau khi thêm feature mới để phát hiện
#           regression sớm, trước khi merge / deploy.
#
# Bao gồm:
#   1. Kiểm tra service health (backend bắt buộc, frontend tuỳ chọn)
#   2. Unit + Integration tests  (./gradlew test)
#   3. API regression tests      (scripts/api_regression_test.py)
#   4. E2E Playwright tests       (frontend/e2e/ — tuỳ chọn, cần frontend up)
#
# Usage:
#   ./scripts/regression_test.sh                # Chạy tất cả (trừ e2e)
#   ./scripts/regression_test.sh --unit-only    # Chỉ Gradle unit/IT tests
#   ./scripts/regression_test.sh --api-only     # Chỉ API regression tests
#   ./scripts/regression_test.sh --e2e          # Thêm Playwright e2e tests
#   ./scripts/regression_test.sh --no-unit      # Bỏ qua Gradle (nhanh hơn)
#   ./scripts/regression_test.sh --fail-fast    # Dừng ngay khi có lỗi
#   BASE_URL=http://staging:8080 ./scripts/regression_test.sh --api-only
#
# Biến môi trường:
#   BASE_URL       Backend URL         (default: http://localhost:8080)
#   FRONTEND_URL   Frontend URL        (default: http://localhost:3000)
#   BACKEND_DIR    Thư mục backend     (default: auto-detect)
#   FRONTEND_DIR   Thư mục frontend    (default: auto-detect)
# =============================================================================

set -euo pipefail

# ─── Paths ────────────────────────────────────────────────────────────────────

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="${BACKEND_DIR:-$ROOT_DIR/backend}"
FRONTEND_DIR="${FRONTEND_DIR:-$ROOT_DIR/frontend}"

# ─── Config ───────────────────────────────────────────────────────────────────

BASE_URL="${BASE_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"

RUN_UNIT=true
RUN_API=true
RUN_E2E=false
FAIL_FAST=false
API_VERBOSE=false

# ─── Colors ───────────────────────────────────────────────────────────────────

RESET=$'\033[0m'
GREEN=$'\033[32m'
RED=$'\033[31m'
YELLOW=$'\033[33m'
CYAN=$'\033[36m'
BOLD=$'\033[1m'
DIM=$'\033[2m'

ok()   { echo "${GREEN}  ✓ $*${RESET}"; }
fail() { echo "${RED}  ✗ $*${RESET}"; }
info() { echo "${CYAN}  ▶ $*${RESET}"; }
warn() { echo "${YELLOW}  ⚠ $*${RESET}"; }
sep()  { echo "${DIM}──────────────────────────────────────────────────${RESET}"; }

# ─── Arg parsing ─────────────────────────────────────────────────────────────

for arg in "$@"; do
  case $arg in
    --unit-only)  RUN_API=false  RUN_E2E=false ;;
    --api-only)   RUN_UNIT=false RUN_E2E=false ;;
    --no-unit)    RUN_UNIT=false ;;
    --e2e)        RUN_E2E=true   ;;
    --fail-fast)  FAIL_FAST=true ;;
    --verbose|-v) API_VERBOSE=true ;;
    --url=*)      BASE_URL="${arg#--url=}" ;;
    --help|-h)
      head -40 "$0" | grep '^#' | sed 's/^# \?//'
      exit 0
      ;;
  esac
done

# ─── Tracking ─────────────────────────────────────────────────────────────────

PHASE_RESULTS=()   # "name:pass|fail"
OVERALL_FAIL=0

record_phase() {
  local name="$1" status="$2"
  PHASE_RESULTS+=("$name:$status")
  [[ "$status" == "fail" ]] && (( OVERALL_FAIL++ )) || true
}

maybe_fail_fast() {
  if [[ "$FAIL_FAST" == "true" && "$OVERALL_FAIL" -gt 0 ]]; then
    echo ""
    fail "FAIL FAST — stopping after first failure"
    print_summary
    exit 1
  fi
}

# ─── Phase helpers ────────────────────────────────────────────────────────────

# wait_for_url <url> <label> <timeout_seconds>
# Polls every 2s until the URL returns HTTP 200 or timeout expires.
# Returns 0 on success, 1 on timeout.
wait_for_url() {
  local url="$1" label="$2" timeout_sec="${3:-30}"
  local elapsed=0 code

  printf "  ${CYAN}Waiting for %s (%s)${RESET}" "$label" "$url"
  while (( elapsed < timeout_sec )); do
    code=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 "$url" 2>/dev/null || echo "000")
    if [[ "$code" == "200" ]]; then
      echo "  ${GREEN}✓ UP${RESET}  ${DIM}(${elapsed}s)${RESET}"
      return 0
    fi
    printf "."
    sleep 2
    (( elapsed += 2 ))
  done
  echo ""
  return 1
}

# ─── PHASE 0: Pre-flight Health Check ────────────────────────────────────────
# IMPORTANT: All services must be UP before any test phase is allowed to run.
# If a required service is not reachable after the wait timeout, the script
# aborts immediately with exit 1 — no tests are executed.

echo ""
echo "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}"
echo "${BOLD}${CYAN}  UIP Smart City — Regression Test Runner${RESET}"
echo "${BOLD}${CYAN}═══════════════════════════════════════════════════${RESET}"
echo "${DIM}  Backend : $BASE_URL${RESET}"
echo "${DIM}  Frontend: $FRONTEND_URL${RESET}"
echo "${DIM}  Phases  : $([ "$RUN_UNIT" = "true" ] && echo "unit+it " || echo "")$([ "$RUN_API" = "true" ] && echo "api " || echo "")$([ "$RUN_E2E" = "true" ] && echo "e2e" || echo "")${RESET}"
echo ""

sep
info "PHASE 0 — Pre-flight: Verify Services Are Running"
sep
echo "${DIM}  Tests will NOT start until all required services are confirmed UP.${RESET}"
echo ""

BACKEND_UP=false
FRONTEND_UP=false

# ── Backend (required for all phases) ────────────────────────────────────────
if wait_for_url "$BASE_URL/actuator/health" "Backend (Spring Boot)" 60; then
  BACKEND_UP=true
else
  echo ""
  fail "Backend did not become available within 60s at $BASE_URL"
  echo ""
  echo "  Start the backend first, then re-run this script:"
  echo "  ${DIM}cd backend && SPRING_PROFILES_ACTIVE=dev nohup ./gradlew bootRun > /tmp/uip-backend.log 2>&1 &${RESET}"
  echo "  ${DIM}tail -f /tmp/uip-backend.log   # wait for 'Started UipBackendApplication'${RESET}"
  echo ""
  echo "${RED}${BOLD}  ✗ ABORTED — backend not running${RESET}"
  echo ""
  exit 1
fi

# ── Frontend (required only when --e2e is requested) ─────────────────────────
if [[ "$RUN_E2E" == "true" ]]; then
  if wait_for_url "$FRONTEND_URL" "Frontend  (Vite)" 30; then
    FRONTEND_UP=true
  else
    echo ""
    fail "Frontend did not become available within 30s at $FRONTEND_URL"
    echo ""
    echo "  Start the frontend first, then re-run with --e2e:"
    echo "  ${DIM}cd frontend && npm run dev -- --host 0.0.0.0${RESET}"
    echo ""
    echo "${RED}${BOLD}  ✗ ABORTED — frontend not running (required for --e2e)${RESET}"
    echo ""
    exit 1
  fi
fi

echo ""
ok "All required services are UP — proceeding with test phases"
echo ""

# ─── PHASE 1: Unit + Integration Tests ───────────────────────────────────────

if [[ "$RUN_UNIT" == "true" ]]; then
  echo ""
  sep
  info "PHASE 1 — Unit + Integration Tests (./gradlew test)"
  sep

  if [[ ! -f "$BACKEND_DIR/gradlew" ]]; then
    fail "gradlew not found at $BACKEND_DIR"
    record_phase "Unit+IT" "fail"
  else
    GRADLE_ARGS="test"
    # Parallel execution for speed; disable when debugging flaky tests
    GRADLE_ARGS="$GRADLE_ARGS --parallel"

    echo "${DIM}  Working dir: $BACKEND_DIR${RESET}"
    echo ""

    UNIT_EXIT=0
    (cd "$BACKEND_DIR" && ./gradlew $GRADLE_ARGS 2>&1) || UNIT_EXIT=$?

    echo ""
    if [[ "$UNIT_EXIT" -eq 0 ]]; then
      ok "Gradle test: BUILD SUCCESSFUL"
      record_phase "Unit+IT" "pass"
    else
      fail "Gradle test: BUILD FAILED (exit $UNIT_EXIT)"
      echo "  ${DIM}Check test report: $BACKEND_DIR/build/reports/tests/test/index.html${RESET}"
      record_phase "Unit+IT" "fail"
      maybe_fail_fast
    fi
  fi
fi

# ─── PHASE 2: API Regression Tests ───────────────────────────────────────────

if [[ "$RUN_API" == "true" ]]; then
  echo ""
  sep
  info "PHASE 2 — API Regression Tests (api_regression_test.py)"
  sep

  API_SCRIPT="$SCRIPT_DIR/api_regression_test.py"
  if [[ ! -f "$API_SCRIPT" ]]; then
    fail "api_regression_test.py not found at $API_SCRIPT"
    record_phase "API" "fail"
  else
    API_ARGS="--url $BASE_URL"
    [[ "$API_VERBOSE" == "true" ]]  && API_ARGS="$API_ARGS --verbose"
    [[ "$FAIL_FAST"   == "true" ]]  && API_ARGS="$API_ARGS --fail-fast"

    API_EXIT=0
    python3 "$API_SCRIPT" $API_ARGS || API_EXIT=$?

    if [[ "$API_EXIT" -eq 0 ]]; then
      record_phase "API" "pass"
    else
      record_phase "API" "fail"
      maybe_fail_fast
    fi
  fi
fi

# ─── PHASE 3: E2E Playwright Tests ───────────────────────────────────────────

if [[ "$RUN_E2E" == "true" && "$FRONTEND_UP" == "true" ]]; then
  echo ""
  sep
  info "PHASE 3 — E2E / UI Regression Tests (e2e_regression_test.py)"
  sep

  E2E_SCRIPT="$SCRIPT_DIR/e2e_regression_test.py"
  if [[ ! -f "$E2E_SCRIPT" ]]; then
    fail "e2e_regression_test.py not found at $E2E_SCRIPT"
    record_phase "E2E" "fail"
  else
    E2E_ARGS="--url $FRONTEND_URL"
    [[ "$API_VERBOSE" == "true" ]] && E2E_ARGS="$E2E_ARGS --verbose"
    [[ "$FAIL_FAST"   == "true" ]] && E2E_ARGS="$E2E_ARGS --fail-fast"

    E2E_EXIT=0
    python3 "$E2E_SCRIPT" $E2E_ARGS || E2E_EXIT=$?

    if [[ "$E2E_EXIT" -eq 0 ]]; then
      record_phase "E2E" "pass"
    else
      record_phase "E2E" "fail"
      echo "  ${DIM}Check HTML report: $FRONTEND_DIR/playwright-report/index.html${RESET}"
      maybe_fail_fast
    fi
  fi
fi

# ─── Final Summary ────────────────────────────────────────────────────────────

print_summary() {
  echo ""
  sep
  echo "${BOLD}  REGRESSION TEST SUMMARY${RESET}"
  sep

  for entry in "${PHASE_RESULTS[@]}"; do
    name="${entry%%:*}"
    status="${entry##*:}"
    if [[ "$status" == "pass" ]]; then
      printf "  ${GREEN}✓${RESET}  %-30s ${GREEN}PASS${RESET}\n" "$name"
    else
      printf "  ${RED}✗${RESET}  %-30s ${RED}FAIL${RESET}\n" "$name"
    fi
  done

  sep

  if [[ "$OVERALL_FAIL" -eq 0 ]]; then
    echo "${GREEN}${BOLD}  ✓ ALL PHASES PASSED — safe to merge / deploy${RESET}"
  else
    echo "${RED}${BOLD}  ✗ $OVERALL_FAIL PHASE(S) FAILED — do NOT merge${RESET}"
    echo "${DIM}  Fix failures before adding new features.${RESET}"
  fi
  echo ""
}

print_summary
exit "$OVERALL_FAIL"
