#!/usr/bin/env bash
# demo-setup.sh — Automated pre-demo setup for UIP Smart City
# Usage: bash scripts/demo-setup.sh
# Run this 30 minutes before demo to verify everything is ready

set -euo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0
WARN=0

check() {
  local label="$1" cmd="$2" expect="${3:-}"
  local result
  result=$(eval "$cmd" 2>&1) || result=""
  if [[ -n "$expect" && "$result" == *"$expect"* ]]; then
    echo -e "  ${GREEN}✓${NC} $label"
    ((PASS++))
  elif [[ -z "$expect" && $? -eq 0 ]]; then
    echo -e "  ${GREEN}✓${NC} $label"
    ((PASS++))
  else
    echo -e "  ${RED}✗${NC} $label — got: ${result:0:80}"
    ((FAIL++))
  fi
}

warn() {
  echo -e "  ${YELLOW}⚠${NC} $1"
  ((WARN++))
}

echo "========================================="
echo "  UIP Smart City — Pre-Demo Setup"
echo "  $(date)"
echo "========================================="
echo ""

# ─── 1. Infrastructure Health ────────────────────────────────────────────
echo "─── Infrastructure ───"

check "Backend health" \
  "curl -sf http://localhost:8080/actuator/health" \
  '"status":"UP"'

check "Frontend dev server" \
  "curl -sf -o /dev/null -w '%{http_code}' http://localhost:3000" \
  '200'

check "TimescaleDB" \
  "curl -sf http://localhost:8080/actuator/health" \
  'db'

check "Redis" \
  "curl -sf http://localhost:8080/actuator/health" \
  'redis'

echo ""

# ─── 2. Authentication ────────────────────────────────────────────────────
echo "─── Authentication ───"

TOKEN=$(curl -sf http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin_Dev#2026!"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))" 2>/dev/null || echo "")

if [[ -n "$TOKEN" && "$TOKEN" != "" ]]; then
  echo -e "  ${GREEN}✓${NC} Admin login → JWT obtained"
  ((PASS++))
else
  echo -e "  ${RED}✗${NC} Admin login FAILED"
  ((FAIL++))
fi

echo ""

# ─── 3. Core API Endpoints ────────────────────────────────────────────────
echo "─── Core APIs ───"

if [[ -n "$TOKEN" ]]; then
  AUTH="Authorization: Bearer $TOKEN"

  check "GET /environment/sensors" \
    "curl -sf -H '$AUTH' http://localhost:8080/api/v1/environment/sensors | python3 -c 'import sys,json; d=json.load(sys.stdin); print(len(d))'" \
    ''

  check "GET /esg/summary" \
    "curl -sf -H '$AUTH' 'http://localhost:8080/api/v1/esg/summary?period=quarterly&year=2026&quarter=1'" \
    'totalEnergy'

  check "GET /alerts" \
    "curl -sf -H '$AUTH' 'http://localhost:8080/api/v1/alerts?page=0&size=5'" \
    'content'

  check "GET /tenant/config" \
    "curl -sf -H '$AUTH' http://localhost:8080/api/v1/tenant/config" \
    'features'
else
  warn "Skipping API checks — no auth token"
fi

echo ""

# ─── 4. Multi-Tenant Isolation (quick check) ──────────────────────────────
echo "─── Multi-Tenant ───"

if [[ -n "$TOKEN" ]]; then
  # Decode JWT to check claims
  CLAIMS=$(echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null || echo "{}")
  if echo "$CLAIMS" | python3 -c "import sys,json; d=json.load(sys.stdin); assert 'tenant_id' in d" 2>/dev/null; then
    echo -e "  ${GREEN}✓${NC} JWT contains tenant_id claim"
    ((PASS++))
  else
    echo -e "  ${YELLOW}⚠${NC} JWT missing tenant_id claim (may be default tenant)"
    ((WARN++))
  fi
else
  warn "Skipping multi-tenant check — no token"
fi

echo ""

# ─── 5. Demo Users ────────────────────────────────────────────────────────
echo "─── Demo Users ───"

for user in "admin:admin_Dev#2026!" "operator:operator123" "citizen:citizen123"; do
  IFS=':' read -r u p <<< "$user"
  result=$(curl -sf http://localhost:8080/api/v1/auth/login \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$u\",\"password\":\"$p\"}" 2>/dev/null || echo "")
  if [[ "$result" == *"accessToken"* ]]; then
    echo -e "  ${GREEN}✓${NC} User '$u' can login"
    ((PASS++))
  else
    echo -e "  ${RED}✗${NC} User '$u' CANNOT login"
    ((FAIL++))
  fi
done

echo ""

# ─── 6. Performance Quick Check ───────────────────────────────────────────
echo "─── Performance (3 requests avg) ───"

if [[ -n "$TOKEN" ]]; then
  total_ms=0
  for i in 1 2 3; do
    ms=$(curl -sf -o /dev/null -w '%{time_total}' \
      -H "Authorization: Bearer $TOKEN" \
      "http://localhost:8080/api/v1/esg/summary?period=quarterly&year=2026&quarter=1" 2>/dev/null || echo "999")
    total_ms=$(python3 -c "print($total_ms + $ms * 1000)")
  done
  avg=$(python3 -c "print(round($total_ms / 3))")
  if [[ "$avg" -lt 500 ]]; then
    echo -e "  ${GREEN}✓${NC} ESG summary avg: ${avg}ms (target <500ms)"
    ((PASS++))
  else
    echo -e "  ${YELLOW}⚠${NC} ESG summary avg: ${avg}ms (slow — check cache)"
    ((WARN++))
  fi
fi

echo ""

# ─── Summary ──────────────────────────────────────────────────────────────
echo "========================================="
echo -e "  ${GREEN}PASS:${NC} $PASS  ${RED}FAIL:${NC} $FAIL  ${YELLOW}WARN:${NC} $WARN"
echo "========================================="

if [[ $FAIL -gt 0 ]]; then
  echo ""
  echo -e "${RED}BLOCKER: $FAIL checks failed. DO NOT demo until fixed.${NC}"
  echo ""
  echo "Quick fixes:"
  echo "  Backend not running:  cd backend && ./gradlew bootRun"
  echo "  Frontend not running: cd frontend && npm run dev"
  echo "  DB not running:       docker compose up -d timescaledb redis kafka"
  exit 1
else
  echo ""
  echo -e "${GREEN}All checks passed. Demo ready!${NC}"
  echo ""
  echo "Quick links:"
  echo "  Frontend:   http://localhost:3000"
  echo "  Backend:    http://localhost:8080/actuator/health"
  echo "  Swagger:    http://localhost:8080/swagger-ui.html"
  exit 0
fi
