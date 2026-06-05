#!/usr/bin/env bash
# Sprint 9 S9-SEC-01-PREP — Keycloak realm localhost URI parameterization
#
# Replaces hardcoded localhost redirect URIs in infra/keycloak/realm-uip-export.json
# with environment-variable-driven production URLs.
#
# Usage:
#   FRONTEND_URL=https://smartcity.hcmc.gov.vn UIP_API_CLIENT_SECRET=<secret> ./keycloak-realm-patch.sh
#   ./keycloak-realm-patch.sh --dry-run   # show diff only
#
# Environment variables:
#   FRONTEND_URL         Production frontend URL (default: http://localhost:3000)
#   BACKEND_URL          Production backend URL  (default: http://localhost:8080)
#   UIP_API_CLIENT_SECRET  uip-api confidential client secret (required for production)
#
# Audit findings (S9-SEC-01-PREP):
#   - uip-frontend client: redirectUris + webOrigins use localhost:3000, localhost:8080
#   - uip-mobile client:   localhost:8081 entries are dev-only; removed in production
#     (uipmobile://* and exp+operator-mobile://* already cover production mobile redirects)
#   - uip-api client: secret is hardcoded 'uip-api-secret-dev' — must be rotated for production
#   - sslRequired is "none" — must be changed to "external" for UAT/production
set -euo pipefail

REALM_FILE="${REALM_FILE:-$(dirname "$0")/../../infra/keycloak/realm-uip-export.json}"
REALM_FILE="$(cd "$(dirname "$REALM_FILE")" && pwd)/$(basename "$REALM_FILE")"

FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
UIP_API_CLIENT_SECRET="${UIP_API_CLIENT_SECRET:-uip-api-secret-dev}"
DRY_RUN=false

for arg in "$@"; do
    [[ "$arg" == "--dry-run" ]] && DRY_RUN=true
done

log() { echo "[$(date '+%H:%M:%S')] $*"; }

if [[ ! -f "$REALM_FILE" ]]; then
    echo "ERROR: Realm file not found: $REALM_FILE"
    exit 1
fi

PATCHED_FILE="${REALM_FILE%.json}-patched.json"

# Build patched version using sed (jq-free to avoid dependency)
# Mobile localhost:8081 entries are REMOVED (not replaced); uipmobile:// + exp+operator-mobile:// cover production
sed \
    -e "s|http://localhost:3000/\*|${FRONTEND_URL}/*|g" \
    -e "s|http://localhost:3000\"|${FRONTEND_URL}\"|g" \
    -e "s|http://localhost:8080/\*|${BACKEND_URL}/*|g" \
    -e "s|http://localhost:8080\"|${BACKEND_URL}\"|g" \
    -e 's|"sslRequired": "none"|"sslRequired": "external"|g' \
    -e 's|"uip-api-secret-dev"|"'"${UIP_API_CLIENT_SECRET}"'"|g' \
    "$REALM_FILE" | \
    python3 -c "
import sys, json
data = json.load(sys.stdin)
for client in data.get('clients', []):
    if client.get('clientId') == 'uip-mobile':
        client['redirectUris'] = [u for u in client.get('redirectUris', []) if 'localhost:8081' not in u]
        client['webOrigins'] = [u for u in client.get('webOrigins', []) if 'localhost:8081' not in u]
print(json.dumps(data, indent=2))
" > "$PATCHED_FILE"

if [[ "$DRY_RUN" == "true" ]]; then
    log "DRY RUN — diff (realm file unchanged):"
    diff "$REALM_FILE" "$PATCHED_FILE" || true
    rm -f "$PATCHED_FILE"
    exit 0
fi

log "Patching realm file: $REALM_FILE"
log "  FRONTEND_URL           = $FRONTEND_URL"
log "  BACKEND_URL            = $BACKEND_URL"
log "  UIP_API_CLIENT_SECRET  = ${UIP_API_CLIENT_SECRET:0:4}****"
log "  uip-mobile localhost:8081 entries: REMOVED"
mv "$PATCHED_FILE" "$REALM_FILE"
log "Done. Realm file updated. Reload Keycloak realm to apply."
log ""
log "To reload in running Keycloak:"
log "  docker exec uip-keycloak /opt/keycloak/bin/kcadm.sh update realms/uip -s enabled=true --server http://localhost:8080 --realm master --user admin --password \$KEYCLOAK_ADMIN_PASSWORD"
log ""
log "=============================="
log "S9-SEC-01 uip-api Secret Rotation (requires live Keycloak)"
log "=============================="
log "The only confidential client is 'uip-api'. Rotate its secret with:"
log ""
log "  KC_URL=\${KEYCLOAK_URL:-http://localhost:8085}"
log "  KC_PASS=\${KEYCLOAK_ADMIN_PASSWORD:-admin}"
log "  TOKEN=\$(curl -sf -X POST \"\${KC_URL}/realms/master/protocol/openid-connect/token\" \\"
log "    -d 'grant_type=password' -d 'client_id=admin-cli' \\"
log "    --data-urlencode \"username=admin\" --data-urlencode \"password=\${KC_PASS}\" \\"
log "    | python3 -c \"import sys,json; print(json.load(sys.stdin)['access_token'])\")"
log ""
log "  # Get uip-api internal client ID"
log "  CLIENT_ID=\$(curl -sf \"\${KC_URL}/admin/realms/uip/clients?clientId=uip-api\" \\"
log "    -H \"Authorization: Bearer \$TOKEN\" | python3 -c \"import sys,json; print(json.load(sys.stdin)[0]['id'])\")"
log ""
log "  # Regenerate secret (Keycloak generates a cryptographically secure value)"
log "  NEW_SECRET=\$(curl -sf -X POST \"\${KC_URL}/admin/realms/uip/clients/\${CLIENT_ID}/client-secret\" \\"
log "    -H \"Authorization: Bearer \$TOKEN\" \\"
log "    | python3 -c \"import sys,json; print(json.load(sys.stdin)['value'])\")"
log "  echo \"New uip-api secret: \${NEW_SECRET:0:4}****  → store full value in infrastructure/.env as KEYCLOAK_CLIENT_SECRET\""
log ""
log "  # Update backend env var and restart"
log "  # 1. Set KEYCLOAK_CLIENT_SECRET=\$NEW_SECRET in infrastructure/.env"
log "  # 2. docker compose up -d --no-deps backend"
