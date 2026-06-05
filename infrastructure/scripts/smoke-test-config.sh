#!/usr/bin/env bash
# Sprint 9 S9-CI-01: Configuration smoke tests
# Verifies Keycloak clients, ClickHouse tables, and Kafka topics exist after deployment
set -uo pipefail

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
PASS_COUNT=0
FAIL_COUNT=0

# Config
KC_URL="${KEYCLOAK_URL:-http://localhost:8085}"
KC_ADMIN="${KC_ADMIN_USER:-admin}"
KC_PASS="${KC_ADMIN_PASSWORD:-admin_Dev#2026!}"
CH_HOST="${CH_HOST:-localhost}"
CH_HTTP_PORT="${CH_HTTP_PORT:-8125}"  # HA mode: node-01 on 8125; single-node: 8123
CH_USER="${CH_USER:-default}"
CH_PASSWORD="${CH_PASSWORD:-}"
KAFKA_BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"

# Service filter
RUN_KEYCLOAK=true
RUN_CLICKHOUSE=true
RUN_KAFKA=true

# Parse CLI args
while [[ $# -gt 0 ]]; do
  case $1 in
    --service)
      RUN_KEYCLOAK=false
      RUN_CLICKHOUSE=false
      RUN_KAFKA=false
      case $2 in
        keycloak) RUN_KEYCLOAK=true ;;
        clickhouse) RUN_CLICKHOUSE=true ;;
        kafka) RUN_KAFKA=true ;;
        *) echo "Unknown service: $2"; exit 1 ;;
      esac
      shift 2
      ;;
    *)
      echo "Usage: $0 [--service keycloak|clickhouse|kafka]"
      exit 1
      ;;
  esac
done

log_pass() {
  echo -e "${GREEN}[SMOKE]${NC} $1... PASS"
  PASS_COUNT=$((PASS_COUNT + 1))
}

log_fail() {
  echo -e "${RED}[SMOKE]${NC} $1... FAIL"
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

# ─── Keycloak Checks ──────────────────────────────────────────────────────────

check_keycloak() {
  echo -e "${YELLOW}[SMOKE]${NC} Checking Keycloak..."
  
  # Get admin token
  local token
  token=$(timeout 10 curl -sf -X POST "${KC_URL}/realms/master/protocol/openid-connect/token" \
    -d "grant_type=password" \
    -d "client_id=admin-cli" \
    --data-urlencode "username=${KC_ADMIN}" \
    --data-urlencode "password=${KC_PASS}" 2>/dev/null | \
    python3 -c "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null) || true
  
  if [[ -z "$token" ]]; then
    log_fail "Keycloak realm 'master' (admin token)"
    return
  fi
  
  # Check realm 'uip' exists
  local realm_check
  realm_check=$(timeout 10 curl -sf -H "Authorization: Bearer $token" \
    "${KC_URL}/admin/realms/uip" 2>/dev/null | python3 -c "import sys,json; print(json.load(sys.stdin).get('realm',''))" 2>/dev/null) || true
  
  if [[ "$realm_check" == "uip" ]]; then
    log_pass "Keycloak realm 'uip'"
  else
    log_fail "Keycloak realm 'uip'"
    return
  fi
  
  # Check clients
  local clients
  clients=$(timeout 10 curl -sf -H "Authorization: Bearer $token" \
    "${KC_URL}/admin/realms/uip/clients" 2>/dev/null) || true
  
  for client_id in "uip-frontend" "uip-mobile" "uip-api"; do
    if echo "$clients" | python3 -c "import sys,json; clients=[c['clientId'] for c in json.load(sys.stdin) if 'clientId' in c]; print('$client_id' in clients)" 2>/dev/null | grep -q "True"; then
      log_pass "Keycloak client '$client_id'"
    else
      log_fail "Keycloak client '$client_id'"
    fi
  done
}

# ─── ClickHouse Checks ────────────────────────────────────────────────────────

check_clickhouse() {
  echo -e "${YELLOW}[SMOKE]${NC} Checking ClickHouse..."
  
  # Build auth param
  local auth_param=""
  if [[ -n "$CH_PASSWORD" ]]; then
    auth_param="--user ${CH_USER}:${CH_PASSWORD}"
  fi
  
  # Check tables
  local tables
  tables=$(timeout 10 curl -sf "http://${CH_HOST}:${CH_HTTP_PORT}/" \
    ${auth_param} \
    --data "SELECT name FROM system.tables WHERE database='analytics' FORMAT TabSeparated" 2>/dev/null) || true
  
  if [[ -z "$tables" ]]; then
    log_fail "ClickHouse database 'analytics'"
    return
  fi
  
  # Check each required table (or distributed variants)
  for table in "sensor_reading_hourly" "esg_metric_monthly"; do
    # Check for exact match or distributed variant (_dist suffix)
    if echo "$tables" | grep -qE "^${table}(_dist)?$"; then
      log_pass "ClickHouse table 'analytics.${table}'"
    else
      log_fail "ClickHouse table 'analytics.${table}'"
    fi
  done
}

# ─── Kafka Checks ─────────────────────────────────────────────────────────────

check_kafka() {
  echo -e "${YELLOW}[SMOKE]${NC} Checking Kafka..."
  
  # Try docker exec first (for CI), fall back to kafkacat/kcat
  local topics=""
  
  # Detect if we're in Docker environment
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -q "uip-kafka$"; then
    topics=$(timeout 10 docker exec uip-kafka kafka-topics \
      --bootstrap-server localhost:9092 \
      --list 2>/dev/null) || true
  elif docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^kafka$"; then
    topics=$(timeout 10 docker exec kafka kafka-topics \
      --bootstrap-server localhost:9092 \
      --list 2>/dev/null) || true
  elif command -v kcat >/dev/null 2>&1; then
    topics=$(timeout 10 kcat -b "${KAFKA_BOOTSTRAP}" -L 2>/dev/null | \
      grep "topic" | awk '{print $2}' | tr -d '"') || true
  elif command -v kafkacat >/dev/null 2>&1; then
    topics=$(timeout 10 kafkacat -b "${KAFKA_BOOTSTRAP}" -L 2>/dev/null | \
      grep "topic" | awk '{print $2}' | tr -d '"') || true
  else
    log_fail "Kafka (no client available: docker exec, kcat, or kafkacat)"
    return
  fi
  
  if [[ -z "$topics" ]]; then
    log_fail "Kafka broker connection"
    return
  fi
  
  # Check each required topic
  for topic in "UIP.iot.sensor.reading.v2" "UIP.flink.alert.detected.v2" "ngsi_ld_esg"; do
    if echo "$topics" | grep -q "^${topic}$"; then
      log_pass "Kafka topic '${topic}'"
    else
      log_fail "Kafka topic '${topic}'"
    fi
  done
}

# ─── Main ─────────────────────────────────────────────────────────────────────

main() {
  echo -e "${YELLOW}=== Config Smoke Tests ===${NC}"
  echo ""
  
  [[ "$RUN_KEYCLOAK" == "true" ]] && check_keycloak
  [[ "$RUN_CLICKHOUSE" == "true" ]] && check_clickhouse
  [[ "$RUN_KAFKA" == "true" ]] && check_kafka
  
  echo ""
  local total=$((PASS_COUNT + FAIL_COUNT))
  
  if [[ $FAIL_COUNT -eq 0 ]]; then
    echo -e "${GREEN}[SMOKE] Result: ${PASS_COUNT}/${total} checks PASSED${NC}"
    exit 0
  else
    echo -e "${RED}[SMOKE] Result: ${PASS_COUNT}/${total} checks PASSED, ${FAIL_COUNT} FAILED${NC}"
    exit 1
  fi
}

main
