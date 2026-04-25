#!/usr/bin/env bash
# T-DEBT-06: Update docs/api/openapi.json from running backend.
# Chạy sau khi backend đang hoạt động tại localhost:8080.
#
# Usage: bash scripts/update-openapi-spec.sh [--host HOST] [--port PORT]
set -euo pipefail

HOST="${1:-localhost}"
PORT="${2:-8080}"
OUTPUT="docs/api/openapi.json"
URL="http://$HOST:$PORT/v3/api-docs"

echo "[openapi] Fetching spec from $URL ..."
curl -sf "$URL" -o /tmp/openapi-raw.json || {
  echo "ERROR: Backend không reachable tại $URL"
  echo "       Chạy: cd infrastructure && docker compose up -d"
  exit 1
}

# Format JSON đẹp để git diff dễ đọc
python3 -m json.tool /tmp/openapi-raw.json > "$OUTPUT"
echo "[openapi] Spec saved: $OUTPUT ($(wc -c < "$OUTPUT" | tr -d ' ') bytes)"

# Kiểm tra breaking changes so với HEAD
if command -v git &>/dev/null && git diff --quiet HEAD -- "$OUTPUT" 2>/dev/null; then
  echo "[openapi] No changes vs HEAD."
else
  echo "[openapi] Spec has changed. Review diff:"
  git diff --stat HEAD -- "$OUTPUT" 2>/dev/null || true
  echo ""
  echo "Next steps:"
  echo "  1. cd frontend && npm run generate:types"
  echo "  2. git add docs/api/openapi.json frontend/src/generated/api-types.ts"
  echo "  3. git commit -m 'chore(api): update OpenAPI spec'"
fi
