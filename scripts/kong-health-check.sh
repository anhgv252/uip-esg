#!/usr/bin/env bash
# Kong health check — TD-05
# Usage: ./scripts/kong-health-check.sh [KONG_ADMIN_URL]
# Exit 0 = healthy, Exit 1 = unhealthy
set -euo pipefail

KONG_ADMIN_URL="${1:-http://localhost:8001}"

response=$(curl -s -o /dev/null -w "%{http_code}" "${KONG_ADMIN_URL}/status" --max-time 5 2>/dev/null || echo "000")

if [ "$response" = "200" ]; then
    echo "Kong HEALTHY (status=200)"
    exit 0
else
    echo "Kong UNHEALTHY (status=${response})" >&2
    exit 1
fi
