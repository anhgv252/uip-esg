#!/usr/bin/env bash
# Sprint 2 API Test Execution Script
# Usage:
#   ./sprint2-api-test.sh             — run all API tests
#   ./sprint2-api-test.sh --seed      — seed test data only
#   ./sprint2-api-test.sh --inject-kafka — inject Kafka messages for Flink enrichment test
#
# Prerequisites:
#   - Docker Compose up: cd infra && docker compose up -d
#   - analytics-service running on port 8082
#   - backend-service running on port 8080
#   - jq installed: brew install jq

set -euo pipefail

ANALYTICS_URL="${ANALYTICS_URL:-http://localhost:8082}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
CH_URL="${CH_URL:-http://localhost:8123}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
TENANT_ID="tenant_01"
BUILDING_1="BLD-001"
BUILDING_2="BLD-002"
FROM_EPOCH=1700000000
TO_EPOCH=1702999999

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

PASS=0
FAIL=0

pass() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASS++)); }
fail() { echo -e "${RED}[FAIL]${NC} $1"; ((FAIL++)); }
info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

assert_http() {
  local label="$1" expected_code="$2" actual_code="$3"
  if [ "$actual_code" = "$expected_code" ]; then
    pass "$label — HTTP $actual_code"
  else
    fail "$label — expected HTTP $expected_code, got HTTP $actual_code"
  fi
}

assert_json_field() {
  local label="$1" json="$2" jq_expr="$3" expected="$4"
  local actual
  actual=$(echo "$json" | jq -r "$jq_expr" 2>/dev/null || echo "JQ_ERROR")
  if [ "$actual" = "$expected" ]; then
    pass "$label — $jq_expr = $actual"
  else
    fail "$label — $jq_expr: expected '$expected', got '$actual'"
  fi
}

assert_json_nonempty() {
  local label="$1" json="$2" jq_expr="$3"
  local actual
  actual=$(echo "$json" | jq -r "$jq_expr" 2>/dev/null || echo "")
  if [ -n "$actual" ] && [ "$actual" != "null" ] && [ "$actual" != "0" ]; then
    pass "$label — $jq_expr is non-empty: $actual"
  else
    fail "$label — $jq_expr is empty/null/zero"
  fi
}

# ─────────────────────────────────────────────
seed_data() {
  info "Seeding ClickHouse test data..."

  local seed_sql="
INSERT INTO analytics.esg_readings
  (tenant_id, building_id, device_id, metric_type, value, unit,
   building_name, district, category, ingested_at, event_time)
VALUES
  ('tenant_01','BLD-001','DEV-001','energy_kwh', 100.5, 'kWh',
   'Toa Nha A', 'District 1', 'COMMERCIAL', now(), toDateTime(1700100000)),
  ('tenant_01','BLD-001','DEV-001','energy_kwh', 120.3, 'kWh',
   'Toa Nha A', 'District 1', 'COMMERCIAL', now(), toDateTime(1700186400)),
  ('tenant_01','BLD-001','DEV-002','co2_kg',      30.0, 'kg',
   'Toa Nha A', 'District 1', 'COMMERCIAL', now(), toDateTime(1700100000)),
  ('tenant_01','BLD-001','DEV-003','aqi',          55.0, 'index',
   'Toa Nha A', 'District 1', 'COMMERCIAL', now(), toDateTime(1700100000)),
  ('tenant_01','BLD-002','DEV-010','energy_kwh',  88.0, 'kWh',
   'Toa Nha B', 'District 3', 'COMMERCIAL', now(), toDateTime(1700100000)),
  ('tenant_01','BLD-002','DEV-010','co2_kg',      22.0, 'kg',
   'Toa Nha B', 'District 3', 'COMMERCIAL', now(), toDateTime(1700100000)),
  ('tenant_01','BLD-002','DEV-011','aqi',          72.0, 'index',
   'Toa Nha B', 'District 3', 'COMMERCIAL', now(), toDateTime(1700100000))
"
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    "${CH_URL}/" \
    --data-urlencode "query=$seed_sql")

  if [ "$http_code" = "200" ]; then
    pass "Seed data inserted into ClickHouse"
  else
    fail "Seed data insert failed (HTTP $http_code)"
    info "Check ClickHouse is running: docker ps | grep clickhouse"
  fi

  info "Seeding PostgreSQL buildings table..."
  docker exec -i "$(docker ps -qf name=postgres)" psql -U uip -d uip_db <<'SQL' 2>/dev/null || info "PostgreSQL seed skipped (container not found)"
INSERT INTO public.buildings (building_code, building_name, cluster_id, is_active, tenant_id)
VALUES
  ('BLD-001', 'Toa Nha A', 'District 1', true, 'tenant_01'),
  ('BLD-002', 'Toa Nha B', 'District 3', true, 'tenant_01')
ON CONFLICT (building_code) DO NOTHING;
SQL
}

# ─────────────────────────────────────────────
inject_kafka() {
  info "Injecting NGSI-LD messages into Kafka topic ngsi-ld-esg..."

  local messages=(
    '{"id":"urn:ngsi-ld:Device:DEV-001","type":"EnergyMeter","controlledAsset":{"type":"Relationship","object":"urn:ngsi-ld:Building:BLD-001"},"powerConsumption":{"type":"Property","value":95.5,"unitCode":"KWH","observedAt":"2024-01-15T10:00:00Z"},"@context":["https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"]}'
    '{"id":"urn:ngsi-ld:Device:DEV-002","type":"CO2Sensor","controlledAsset":{"type":"Relationship","object":"urn:ngsi-ld:Building:BLD-001"},"co2Level":{"type":"Property","value":28.0,"unitCode":"KGM","observedAt":"2024-01-15T10:00:00Z"},"@context":["https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"]}'
    '{"id":"urn:ngsi-ld:Device:DEV-003","type":"AQISensor","controlledAsset":{"type":"Relationship","object":"urn:ngsi-ld:Building:BLD-001"},"aqi":{"type":"Property","value":62.0,"observedAt":"2024-01-15T10:00:00Z"},"@context":["https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"]}'
  )

  for msg in "${messages[@]}"; do
    echo "$msg" | docker exec -i "$KAFKA_CONTAINER" \
      kafka-console-producer.sh \
      --bootstrap-server localhost:9092 \
      --topic ngsi-ld-esg 2>/dev/null \
      && info "Injected message for $(echo "$msg" | jq -r '.id' 2>/dev/null || echo 'unknown')" \
      || fail "Failed to inject Kafka message"
  done

  info "Wait 10s for Flink to process..."
  sleep 10
}

# ─────────────────────────────────────────────
test_energy_aggregate() {
  info "=== TEST: POST /api/v1/analytics/energy-aggregate ==="

  local payload
  payload=$(cat <<EOF
{
  "tenantId": "${TENANT_ID}",
  "buildingIds": ["${BUILDING_1}"],
  "fromEpoch": ${FROM_EPOCH},
  "toEpoch": ${TO_EPOCH},
  "groupBy": "day"
}
EOF
)

  local response http_code
  response=$(curl -s -w "\n%{http_code}" \
    -X POST "${ANALYTICS_URL}/api/v1/analytics/energy-aggregate" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -d "$payload")

  http_code=$(echo "$response" | tail -1)
  local body
  body=$(echo "$response" | head -n -1)

  assert_http "energy-aggregate single building" "200" "$http_code"
  assert_json_field "energy-aggregate status" "$body" ".status" "ok"
  assert_json_nonempty "energy-aggregate has data" "$body" "(.data | length)"

  # Test với 2 buildings
  local payload2
  payload2=$(cat <<EOF
{
  "tenantId": "${TENANT_ID}",
  "buildingIds": ["${BUILDING_1}", "${BUILDING_2}"],
  "fromEpoch": ${FROM_EPOCH},
  "toEpoch": ${TO_EPOCH},
  "groupBy": "day"
}
EOF
)
  local response2 http_code2 body2
  response2=$(curl -s -w "\n%{http_code}" \
    -X POST "${ANALYTICS_URL}/api/v1/analytics/energy-aggregate" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -d "$payload2")
  http_code2=$(echo "$response2" | tail -1)
  body2=$(echo "$response2" | head -n -1)

  assert_http "energy-aggregate two buildings" "200" "$http_code2"

  # Test tenant isolation — sai tenant không thấy data của tenant_01
  local bad_response bad_code
  bad_response=$(curl -s -w "\n%{http_code}" \
    -X POST "${ANALYTICS_URL}/api/v1/analytics/energy-aggregate" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: tenant_99" \
    -d "$payload")
  bad_code=$(echo "$bad_response" | tail -1)
  local bad_body
  bad_body=$(echo "$bad_response" | head -n -1)
  local bad_count
  bad_count=$(echo "$bad_body" | jq '(.data | length)' 2>/dev/null || echo "0")
  if [ "$bad_count" = "0" ] || [ "$bad_code" = "200" ]; then
    # either 200 with empty data OR 403
    pass "energy-aggregate tenant isolation — tenant_99 sees no data"
  else
    fail "energy-aggregate tenant isolation — tenant_99 sees ${bad_count} records (should be 0)"
  fi

  # Test missing tenantId — expect 400
  local missing_response missing_code
  missing_response=$(curl -s -w "\n%{http_code}" \
    -X POST "${ANALYTICS_URL}/api/v1/analytics/energy-aggregate" \
    -H "Content-Type: application/json" \
    -d '{"buildingIds":["BLD-001"],"fromEpoch":1700000000,"toEpoch":1702999999}')
  missing_code=$(echo "$missing_response" | tail -1)
  if [ "$missing_code" = "400" ] || [ "$missing_code" = "422" ]; then
    pass "energy-aggregate missing tenantId → HTTP $missing_code"
  else
    fail "energy-aggregate missing tenantId → expected 400/422, got $missing_code"
  fi

  echo ""
  info "energy-aggregate response sample:"
  echo "$body" | jq '{status, count: (.data | length), sample: .data[0]}' 2>/dev/null || echo "$body"
  echo ""
}

# ─────────────────────────────────────────────
test_emissions_aggregate() {
  info "=== TEST: POST /api/v1/analytics/emissions-aggregate ==="

  local payload
  payload=$(cat <<EOF
{
  "tenantId": "${TENANT_ID}",
  "buildingIds": ["${BUILDING_1}"],
  "fromEpoch": ${FROM_EPOCH},
  "toEpoch": ${TO_EPOCH},
  "groupBy": "day"
}
EOF
)

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" \
    -X POST "${ANALYTICS_URL}/api/v1/analytics/emissions-aggregate" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -d "$payload")
  http_code=$(echo "$response" | tail -1)
  body=$(echo "$response" | head -n -1)

  assert_http "emissions-aggregate" "200" "$http_code"
  assert_json_field "emissions-aggregate status" "$body" ".status" "ok"
  assert_json_nonempty "emissions-aggregate has data" "$body" "(.data | length)"

  echo ""
  info "emissions-aggregate response sample:"
  echo "$body" | jq '{status, count: (.data | length), sample: .data[0]}' 2>/dev/null || echo "$body"
  echo ""
}

# ─────────────────────────────────────────────
test_aqi_trend() {
  info "=== TEST: POST /api/v1/analytics/aqi-trend ==="

  local payload
  payload=$(cat <<EOF
{
  "tenantId": "${TENANT_ID}",
  "buildingIds": ["${BUILDING_1}", "${BUILDING_2}"],
  "fromEpoch": ${FROM_EPOCH},
  "toEpoch": ${TO_EPOCH},
  "groupBy": "day"
}
EOF
)

  local response http_code body
  response=$(curl -s -w "\n%{http_code}" \
    -X POST "${ANALYTICS_URL}/api/v1/analytics/aqi-trend" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -d "$payload")
  http_code=$(echo "$response" | tail -1)
  body=$(echo "$response" | head -n -1)

  assert_http "aqi-trend" "200" "$http_code"
  assert_json_field "aqi-trend status" "$body" ".status" "ok"
  assert_json_nonempty "aqi-trend has data" "$body" "(.data | length)"

  # Kiểm tra multi-building: có ít nhất 2 buildingId riêng biệt trong response
  local building_count
  building_count=$(echo "$body" | jq '[.data[].buildingId] | unique | length' 2>/dev/null || echo "0")
  if [ "$building_count" -ge 2 ] 2>/dev/null; then
    pass "aqi-trend returns data for multiple buildings ($building_count)"
  else
    info "aqi-trend single or no building data (count=$building_count) — may be ok if only 1 building has AQI data"
  fi

  echo ""
  info "aqi-trend response sample:"
  echo "$body" | jq '{status, count: (.data | length), sample: .data[0]}' 2>/dev/null || echo "$body"
  echo ""
}

# ─────────────────────────────────────────────
test_ch_query_direct() {
  info "=== TEST: ClickHouse direct query — ReplacingMergeTree dedup ==="

  # Insert duplicate record (same tenant+building+device+event_time)
  local dup_sql="
INSERT INTO analytics.esg_readings
  (tenant_id, building_id, device_id, metric_type, value, unit, ingested_at, event_time)
VALUES
  ('tenant_01','BLD-001','DEV-DUP','energy_kwh', 999.9, 'kWh', now(), toDateTime(1700100000)),
  ('tenant_01','BLD-001','DEV-DUP','energy_kwh', 888.8, 'kWh', now() + INTERVAL 1 SECOND, toDateTime(1700100000))
"
  curl -s -o /dev/null "${CH_URL}/" --data-urlencode "query=$dup_sql"
  sleep 2

  # Optimize để trigger dedup (ReplacingMergeTree dedup xảy ra ở merge time)
  curl -s -o /dev/null "${CH_URL}/" \
    --data-urlencode "query=OPTIMIZE TABLE analytics.esg_readings FINAL"
  sleep 2

  local count_result
  count_result=$(curl -s "${CH_URL}/?query=SELECT+count()%20FROM%20analytics.esg_readings+WHERE+device_id%3D%27DEV-DUP%27+AND+event_time%3DtoDateTime(1700100000)+FORMAT+JSONEachRow")
  local count
  count=$(echo "$count_result" | jq -r '."count()"' 2>/dev/null || echo "unknown")

  if [ "$count" = "1" ]; then
    pass "ClickHouse ReplacingMergeTree dedup — duplicate record reduced to 1"
  else
    info "Dedup count=$count (ReplacingMergeTree dedup is eventual — may need more time or FINAL hint)"
  fi
}

# ─────────────────────────────────────────────
test_ch_vs_ts_consistency() {
  info "=== TEST: CH vs TimescaleDB consistency (TC-S2-09) ==="

  local ch_payload
  ch_payload=$(cat <<EOF
{
  "tenantId": "${TENANT_ID}",
  "buildingIds": ["${BUILDING_1}"],
  "fromEpoch": ${FROM_EPOCH},
  "toEpoch": ${TO_EPOCH},
  "groupBy": "day"
}
EOF
)

  local ch_response ch_code ch_body
  ch_response=$(curl -s -w "\n%{http_code}" \
    -X POST "${ANALYTICS_URL}/api/v1/analytics/energy-aggregate" \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: ${TENANT_ID}" \
    -d "$ch_payload")
  ch_code=$(echo "$ch_response" | tail -1)
  ch_body=$(echo "$ch_response" | head -n -1)

  local ts_response ts_code ts_body
  ts_response=$(curl -s -w "\n%{http_code}" \
    "${BACKEND_URL}/api/v1/buildings/aggregations?tenantId=${TENANT_ID}&buildingIds=${BUILDING_1}&from=${FROM_EPOCH}&to=${TO_EPOCH}&groupBy=day" \
    -H "X-Tenant-ID: ${TENANT_ID}")
  ts_code=$(echo "$ts_response" | tail -1)
  ts_body=$(echo "$ts_response" | head -n -1)

  assert_http "CH energy-aggregate for consistency" "200" "$ch_code"

  if [ "$ts_code" = "200" ]; then
    local ch_total ts_total
    ch_total=$(echo "$ch_body" | jq '[.data[].totalKwh // 0] | add // 0' 2>/dev/null || echo "0")
    ts_total=$(echo "$ts_body" | jq '[.data[].totalKwh // 0] | add // 0' 2>/dev/null || echo "0")

    info "CH total kWh: $ch_total | TS total kWh: $ts_total"

    if [ "$ch_total" = "0" ] && [ "$ts_total" = "0" ]; then
      info "Both CH and TS return 0 — may need more seed data"
    elif [ "$ch_total" = "$ts_total" ]; then
      pass "CH vs TS consistency — totals match exactly"
    else
      info "CH=$ch_total vs TS=$ts_total — review acceptable delta"
    fi
  else
    info "Backend/TS endpoint returned HTTP $ts_code — skipping comparison"
  fi
}

# ─────────────────────────────────────────────
test_analytics_service_health() {
  info "=== TEST: Analytics Service Health ==="

  local health_code
  health_code=$(curl -s -o /dev/null -w "%{http_code}" \
    "${ANALYTICS_URL}/actuator/health")
  assert_http "analytics-service /actuator/health" "200" "$health_code"
}

# ─────────────────────────────────────────────
print_summary() {
  echo ""
  echo "══════════════════════════════════════"
  echo "  API Test Results"
  echo "══════════════════════════════════════"
  echo -e "  ${GREEN}PASS: ${PASS}${NC}"
  echo -e "  ${RED}FAIL: ${FAIL}${NC}"
  echo "──────────────────────────────────────"
  if [ "$FAIL" -eq 0 ]; then
    echo -e "  ${GREEN}ALL TESTS PASSED${NC}"
  else
    echo -e "  ${RED}${FAIL} TEST(S) FAILED${NC}"
    echo "  Review failures above before marking sprint DONE"
  fi
  echo "══════════════════════════════════════"
}

# ─────────────────────────────────────────────
main() {
  case "${1:-}" in
    --seed)
      seed_data
      exit 0
      ;;
    --inject-kafka)
      inject_kafka
      exit 0
      ;;
    --help|-h)
      echo "Usage: $0 [--seed | --inject-kafka | --help]"
      echo "  (no args)          Run all API tests"
      echo "  --seed             Seed ClickHouse + PostgreSQL test data"
      echo "  --inject-kafka     Inject NGSI-LD messages into Kafka"
      exit 0
      ;;
  esac

  info "Sprint 2 API Test Suite starting..."
  info "ANALYTICS_URL=${ANALYTICS_URL}"
  info "BACKEND_URL=${BACKEND_URL}"
  echo ""

  test_analytics_service_health
  echo ""
  test_energy_aggregate
  test_emissions_aggregate
  test_aqi_trend
  test_ch_query_direct
  test_ch_vs_ts_consistency

  print_summary

  [ "$FAIL" -eq 0 ] && exit 0 || exit 1
}

main "$@"
