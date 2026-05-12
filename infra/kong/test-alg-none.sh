#!/usr/bin/env bash
# CI Test: Kong MUST reject JWT với alg=none (ADR-028 security requirement)
# Usage: ./test-alg-none.sh [KONG_URL] [TEST_ROUTE]
# Example: ./test-alg-none.sh http://localhost:8000 /api/v1/analytics
# Exit 0 = PASS, Exit 1 = FAIL

set -euo pipefail

KONG_URL="${1:-http://localhost:8000}"
TEST_ROUTE="${2:-/api/v1/analytics}"

echo "=== Kong Security Test: alg=none rejection ==="
echo "Kong URL: $KONG_URL"
echo "Test route: $TEST_ROUTE"
echo ""

# Tạo JWT với alg=none (không có signature)
HEADER_JSON='{"alg":"none","typ":"JWT"}'
PAYLOAD_JSON="{\"iss\":\"attacker\",\"tenant_id\":\"tenant-a\",\"exp\":9999999999,\"iat\":$(date +%s)}"

b64url() {
    echo -n "$1" | base64 | tr -d '=' | tr '+/' '-_'
}

HEADER_B64=$(b64url "$HEADER_JSON")
PAYLOAD_B64=$(b64url "$PAYLOAD_JSON")
ALG_NONE_TOKEN="${HEADER_B64}.${PAYLOAD_B64}."

echo "Crafted alg=none token (no signature):"
echo "  Header: $HEADER_JSON"
echo "  Payload: $PAYLOAD_JSON"
echo ""

HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${ALG_NONE_TOKEN}" \
    -H "Content-Type: application/json" \
    --max-time 10 \
    "${KONG_URL}${TEST_ROUTE}")

echo "HTTP response: $HTTP_STATUS"
echo ""

if [ "$HTTP_STATUS" = "401" ]; then
    echo "✓ PASS: Kong correctly rejected alg=none token → HTTP 401"
    echo "  Security: Token without signature cannot bypass JWT validation."
    exit 0
elif [ "$HTTP_STATUS" = "000" ]; then
    echo "✗ SKIP: Cannot reach Kong at $KONG_URL — Kong not deployed yet"
    echo "  Run this test after Kong non-prod deployment (Sprint 4)"
    exit 2
else
    echo "✗ FAIL: Expected 401, got $HTTP_STATUS — Kong NOT rejecting alg=none!"
    echo "  CRITICAL: This means JWT bypass is possible. Check Kong jwt plugin config."
    exit 1
fi
