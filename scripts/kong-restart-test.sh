#!/usr/bin/env bash
# Kong restart test — TD-05
# Verifies Kong recovers correctly after restart (DB-less mode)
set -euo pipefail

KONG_ADMIN_URL="${1:-http://localhost:8001}"
KONG_PROXY_URL="${2:-http://localhost:8000}"
COMPOSE_FILE="${3:-infra/kong/kong.local.yml}"

echo "=== Kong Restart Test ==="

echo "[1/5] Recording pre-restart state..."
pre_routes=$(curl -s "${KONG_ADMIN_URL}/routes" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo "0")
pre_plugins=$(curl -s "${KONG_ADMIN_URL}/plugins" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo "0")
echo "  Routes: ${pre_routes}, Plugins: ${pre_plugins}"

echo "[2/5] Restarting Kong..."
docker compose -f "${COMPOSE_FILE}" restart kong 2>/dev/null || docker restart kong 2>/dev/null || true

echo "[3/5] Waiting for Kong healthy..."
retries=0
max_retries=30
while [ $retries -lt $max_retries ]; do
    if curl -s -o /dev/null "${KONG_ADMIN_URL}/status" --max-time 2 2>/dev/null; then
        echo "  Kong healthy after $((retries * 2))s"
        break
    fi
    retries=$((retries + 1))
    sleep 2
done

if [ $retries -ge $max_retries ]; then
    echo "FAIL: Kong did not become healthy within 60s" >&2
    exit 1
fi

echo "[4/5] Verifying post-restart state..."
post_routes=$(curl -s "${KONG_ADMIN_URL}/routes" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo "0")
post_plugins=$(curl -s "${KONG_ADMIN_URL}/plugins" | python3 -c "import sys,json; print(len(json.load(sys.stdin).get('data',[])))" 2>/dev/null || echo "0")
echo "  Routes: ${post_routes}, Plugins: ${post_plugins}"

if [ "${pre_routes}" != "${post_routes}" ]; then
    echo "FAIL: Route count mismatch (pre=${pre_routes}, post=${post_routes})" >&2
    exit 1
fi

echo "[5/5] Testing auth (alg=none rejection)..."
response=$(curl -s -o /dev/null -w "%{http_code}" "${KONG_PROXY_URL}/api/v1/analytics/energy-aggregate" \
    -H "Authorization: Bearer eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJ0ZW5hbnRfaWQiOiJ0ZXN0In0." \
    --max-time 5 2>/dev/null || echo "000")

if [ "$response" = "401" ]; then
    echo "  alg=none correctly rejected (401)"
else
    echo "FAIL: alg=none not rejected (got ${response}, expected 401)" >&2
    exit 1
fi

echo "=== Kong Restart Test PASSED ==="
