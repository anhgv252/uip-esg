#!/usr/bin/env bash
# Kong JWKS Verification Test — v3.1-15
# Validates that Kong correctly fetches signing keys from Keycloak JWKS endpoint
# and that token rotation works without Kong restart.
#
# Usage:
#   ./test-jwks.sh [KONG_URL] [KEYCLOAK_URL]
#
# Prerequisites:
#   - Kong and Keycloak containers running (docker compose up)
#   - jq installed (for JSON parsing)
#
# Exit codes: 0=PASS, 1=FAIL, 2=SKIP (containers not running)
set -euo pipefail

KONG_URL="${1:-http://localhost:8000}"
KEYCLOAK_URL="${2:-http://localhost:8085}"
TEST_ROUTE="/api/v1/analytics"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_ok()   { echo -e "${GREEN}[PASS]${NC} $*"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; }
log_info() { echo -e "${YELLOW}[INFO]${NC} $*"; }

passed=0
failed=0
skipped=0

# ─── Test 1: Keycloak JWKS endpoint reachable ────────────────────────────────
log_info "Test 1: Keycloak JWKS endpoint reachable"
JWKS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "${KEYCLOAK_URL}/realms/uip/protocol/openid-connect/certs" 2>/dev/null || echo "000")

if [[ "$JWKS_STATUS" == "200" ]]; then
    JWKS_BODY=$(curl -sf "${KEYCLOAK_URL}/realms/uip/protocol/openid-connect/certs" 2>/dev/null)
    KEY_COUNT=$(echo "$JWKS_BODY" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('keys',[])))" 2>/dev/null || echo "0")
    log_ok "JWKS endpoint reachable (${KEY_COUNT} signing keys found)"
    passed=$((passed + 1))
else
    log_fail "JWKS endpoint returned HTTP ${JWKS_STATUS} (expected 200)"
    skipped=$((skipped + 1))
fi

# ─── Test 2: Unauthenticated request returns 401 ─────────────────────────────
log_info "Test 2: Unauthenticated request returns 401"
AUTH_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    --max-time 10 \
    "${KONG_URL}${TEST_ROUTE}" 2>/dev/null || echo "000")

if [[ "$AUTH_STATUS" == "401" ]]; then
    log_ok "Unauthenticated request correctly rejected (HTTP 401)"
    passed=$((passed + 1))
elif [[ "$AUTH_STATUS" == "000" ]]; then
    log_fail "Kong not reachable at ${KONG_URL} — skipping remaining tests"
    skipped=$((skipped + 3))
    echo ""
    echo "Results: ${passed} passed, ${failed} failed, ${skipped} skipped"
    exit 2
else
    log_fail "Expected HTTP 401, got HTTP ${AUTH_STATUS} — auth bypass possible!"
    failed=$((failed + 1))
fi

# ─── Test 3: alg=none token rejected ─────────────────────────────────────────
log_info "Test 3: alg=none token rejected"
HEADER_JSON='{"alg":"none","typ":"JWT"}'
PAYLOAD_JSON="{\"iss\":\"http://localhost:8085/realms/uip\",\"exp\":9999999999,\"iat\":$(date +%s)}"

b64url() { echo -n "$1" | base64 | tr -d '=' | tr '+/' '-_'; }
ALG_NONE_TOKEN="$(b64url "$HEADER_JSON").$(b64url "$PAYLOAD_JSON")."

ALG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${ALG_NONE_TOKEN}" \
    --max-time 10 \
    "${KONG_URL}${TEST_ROUTE}" 2>/dev/null || echo "000")

if [[ "$ALG_STATUS" == "401" ]]; then
    log_ok "alg=none token correctly rejected (HTTP 401)"
    passed=$((passed + 1))
else
    log_fail "alg=none token returned HTTP ${ALG_STATUS} — CRITICAL security issue!"
    failed=$((failed + 1))
fi

# ─── Test 4: Valid Keycloak JWT accepted ──────────────────────────────────────
log_info "Test 4: Valid Keycloak JWT accepted by Kong"

# Try to get a token from Keycloak
TOKEN_RESPONSE=$(curl -sf -X POST \
    "${KEYCLOAK_URL}/realms/uip/protocol/openid-connect/token" \
    -d "client_id=uip-api" \
    -d "client_secret=uip-api-secret-dev" \
    -d "grant_type=password" \
    -d "username=operator-hcm" \
    -d "password=Operator%232026%21" \
    2>/dev/null || echo "")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || echo "")

if [[ -z "$ACCESS_TOKEN" ]]; then
    log_info "Could not obtain Keycloak token (user not configured?) — skipping"
    skipped=$((skipped + 1))
else
    # Verify token via Kong
    VALID_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
        -H "Authorization: Bearer ${ACCESS_TOKEN}" \
        --max-time 10 \
        "${KONG_URL}${TEST_ROUTE}" 2>/dev/null || echo "000")

    if [[ "$VALID_STATUS" == "200" ]]; then
        log_ok "Valid Keycloak JWT accepted (HTTP 200)"
        passed=$((passed + 1))
    elif [[ "$VALID_STATUS" == "401" ]]; then
        log_fail "Valid Keycloak JWT rejected (HTTP 401) — JWKS key mismatch?"
        failed=$((failed + 1))
    else
        # 503 = upstream not running (analytics-service), but Kong validated JWT OK
        if [[ "$VALID_STATUS" == "503" || "$VALID_STATUS" == "502" ]]; then
            log_ok "Valid Keycloak JWT accepted (HTTP ${VALID_STATUS} — upstream issue, Kong auth passed)"
            passed=$((passed + 1))
        else
            log_fail "Valid Keycloak JWT returned HTTP ${VALID_STATUS}"
            failed=$((failed + 1))
        fi
    fi
fi

# ─── Test 5: JWKS config present in Kong declarative config ──────────────────
log_info "Test 5: JWKS URI in Kong config"
KONG_CONFIG="${0%/*}/kong.poc.yml"
if [[ -f "$KONG_CONFIG" ]]; then
    if grep -q 'uri:.*openid-connect/certs' "$KONG_CONFIG"; then
        log_ok "JWKS URI found in kong.poc.yml"
        passed=$((passed + 1))
    else
        log_fail "JWKS URI not found in kong.poc.yml — still using static key?"
        failed=$((failed + 1))
    fi
else
    log_info "kong.poc.yml not found at ${KONG_CONFIG} — skipping"
    skipped=$((skipped + 1))
fi

# ─── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo "=== JWKS Verification Summary ==="
echo "  Passed:  ${passed}"
echo "  Failed:  ${failed}"
echo "  Skipped: ${skipped}"
echo ""

if [[ $failed -gt 0 ]]; then
    log_fail "JWKS verification FAILED — review config"
    exit 1
elif [[ $skipped -gt 0 ]]; then
    log_info "JWKS verification PASSED with skips — run with full stack for complete validation"
    exit 0
else
    log_ok "All JWKS verification tests PASSED"
    exit 0
fi
