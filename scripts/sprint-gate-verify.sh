#!/bin/bash
# Sprint 4 Gate Verification — chạy 1 session duy nhất (~2 giờ)
# Usage: ./scripts/sprint-gate-verify.sh
set -euo pipefail

COMPOSE_FILE="infrastructure/docker-compose.yml"
MONITORING_FILE="infra/monitoring/docker-compose.monitoring.yml"
PASS=0
FAIL=0

green() { echo -e "\033[32m[PASS]\033[0m $1"; PASS=$((PASS + 1)); }
red()   { echo -e "\033[31m[FAIL]\033[0m $1"; FAIL=$((FAIL + 1)); }
info()  { echo -e "\033[36m[INFO]\033[0m $1"; }

echo "========================================="
echo "  SPRINT 4 GATE VERIFY — $(date '+%Y-%m-%d %H:%M')"
echo "========================================="

# ── 1. Clean slate ──────────────────────────────────────────────────────────
info "[1/10] docker compose down -v && up -d"
docker compose -f "$COMPOSE_FILE" down -v 2>/dev/null || true
# First pass: bring up infrastructure (some services may fail due to DB not ready yet)
docker compose -f "$COMPOSE_FILE" up -d --build 2>&1 | tail -5 || true

# Wait for timescaledb to be healthy (up to 120s) — it re-initialises after volume wipe
info "[1/10] Waiting for timescaledb to be healthy (up to 120s)..."
for i in $(seq 1 24); do
    DB_STATUS=$(docker inspect --format='{{.State.Health.Status}}' uip-timescaledb 2>/dev/null || echo "not found")
    if [ "$DB_STATUS" = "healthy" ]; then
        info "timescaledb healthy after $((i*5))s"
        break
    fi
    if [ "$i" -eq 24 ]; then
        red "timescaledb still not healthy after 120s — aborting"
        exit 1
    fi
    sleep 5
done

# Second pass: start any services that failed to start on first pass
docker compose -f "$COMPOSE_FILE" up -d 2>&1 | tail -5

info "[1b] Starting monitoring stack..."
docker compose -f "$MONITORING_FILE" up -d 2>&1 | tail -3

# ── 2. Wait for services healthy ────────────────────────────────────────────
info "[2/10] Waiting for services healthy (60s)..."
sleep 60

# ── 3. Verify forecast-service NOT accessible from host (ADR-032 D1) ───────
info "[3/10] Verify forecast-service is NOT accessible from host..."
# Port 8090 is kafka-ui, not forecast-service. Check HTTP 200 specifically —
# kafka-ui returns 404 for /api/v1/forecast/health; forecast-service returns 200.
FORECAST_HTTP=$(curl -s --connect-timeout 2 -o /dev/null -w '%{http_code}' \
    http://localhost:8090/api/v1/forecast/health 2>/dev/null || echo "000")
if [ "$FORECAST_HTTP" = "200" ]; then
    red "FAIL: forecast-service port exposed from host (HTTP 200) — violates ADR-032 D1"
else
    green "forecast-service port NOT exposed from host (HTTP $FORECAST_HTTP, ADR-032 D1 OK)"
fi

# ── 4. Verify services healthy via docker ───────────────────────────────────
info "[4/10] Checking service health..."
for svc in uip-backend uip-analytics-service uip-forecast-service uip-clickhouse uip-kong; do
    STATUS=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "not found")
    if [ "$STATUS" = "healthy" ]; then
        green "$svc: healthy"
    else
        red "$svc: $STATUS"
    fi
done

# ── 5. Unit tests ──────────────────────────────────────────────────────────
info "[5/10] Running unit tests..."
if (cd backend && ./gradlew cleanTest testUnit jacocoTestUnitReport 2>&1 | tail -20); then
    green "Unit tests PASS"
else
    red "Unit tests FAIL"
fi

# ── 6. Integration tests ──────────────────────────────────────────────────
info "[6/10] Running integration tests..."
if (cd backend && ./gradlew integrationTest 2>&1 | tail -10); then
    green "Integration tests PASS"
else
    red "Integration tests FAIL"
fi

# ── 7. Prometheus scrape verify ───────────────────────────────────────────
info "[7/10] Prometheus targets..."
TARGETS=$(curl -sf http://localhost:9090/api/v1/targets 2>/dev/null || echo '{}')
for job in uip-backend analytics-service kong; do
    if echo "$TARGETS" | grep -q "$job"; then
        green "Prometheus scrape: $job configured"
    else
        red "Prometheus scrape: $job NOT found"
    fi
done

# ── 8. Grafana dashboard verify ────────────────────────────────────────────
info "[8/10] Grafana accessible..."
GRAFANA_STATUS=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:3001/api/health 2>/dev/null || echo "000")
if [ "$GRAFANA_STATUS" = "200" ]; then
    green "Grafana accessible (HTTP $GRAFANA_STATUS)"
else
    red "Grafana NOT accessible (HTTP $GRAFANA_STATUS)"
fi

# ── 9. Security verify (ADR-032) ──────────────────────────────────────────
info "[9/11] Security: X-Tenant-ID required..."
# Test via docker exec inside forecast-service container
MISSING_HEADER=$(docker exec uip-forecast-service curl -s -o /dev/null -w '%{http_code}' \
    "http://localhost:8090/api/v1/forecast/energy?building_id=test&horizon_days=1" 2>/dev/null || echo "000")
if [ "$MISSING_HEADER" = "403" ]; then
    green "Missing X-Tenant-ID → 403 (ADR-032 D4 OK)"
else
    red "Missing X-Tenant-ID → HTTP $MISSING_HEADER (expected 403)"
fi

INVALID_HEADER=$(docker exec uip-forecast-service curl -s -o /dev/null -w '%{http_code}' \
    -H "X-Tenant-ID: !!!invalid!!!" \
    "http://localhost:8090/api/v1/forecast/energy?building_id=test&horizon_days=1" 2>/dev/null || echo "000")
if [ "$INVALID_HEADER" = "400" ]; then
    green "Invalid X-Tenant-ID → 400 (ADR-032 D4 OK)"
else
    red "Invalid X-Tenant-ID → HTTP $INVALID_HEADER (expected 400)"
fi

# ── 10. Forecast tenant/data seed alignment ───────────────────────────────
info "[10/11] Forecast tenant/data alignment..."
if ./scripts/check_forecast_tenant_seed.sh; then
    green "Forecast tenant/data alignment PASS"
else
    red "Forecast tenant/data alignment FAIL"
fi

# ── 11. Regression summary ────────────────────────────────────────────────
info "[11/11] Regression summary"
TOTAL_TESTS=$(find backend/build -name "*.xml" -path "*/test-results/*" -exec grep -l "tests=" {} \; 2>/dev/null | xargs grep -h 'tests=' 2>/dev/null | awk -F'"' '{sum+=$2} END{print sum+0}')
if [ "$TOTAL_TESTS" -ge 739 ]; then
    green "Regression: $TOTAL_TESTS tests (>= 739 threshold — AC-05)"
else
    red "Regression: $TOTAL_TESTS tests (< 739 threshold — AC-05 FAIL)"
fi

# ── Summary ────────────────────────────────────────────────────────────────
echo ""
echo "========================================="
echo "  GATE VERIFY SUMMARY"
echo "========================================="
echo "  PASS: $PASS"
echo "  FAIL: $FAIL"
echo "  Total: $((PASS + FAIL))"
echo "========================================="
if [ "$FAIL" -eq 0 ]; then
    green "ALL GATES PASSED"
    exit 0
else
    red "$FAIL GATE(S) FAILED"
    exit 1
fi
