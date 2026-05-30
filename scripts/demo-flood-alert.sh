#!/usr/bin/env bash
#
# S6-FL04 — Flood Alert Demo Script
#
# Prerequisites:
#   - Backend running with profile "test"
#   - Kafka running (ngsi_ld_environment + UIP.flink.alert.flood.v1 topics)
#   - FloodAlertJob running on Flink (or use /inject-flood-alert bypass)
#
# Demo scenario: Inject 3 RAINFALL readings >80mm/h to trigger P1 flood alert.
# Expected: Alert appears in UI within 30 seconds.
#

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
SENSOR_ID="SENSOR-FLOOD-DEMO-001"
SENSOR_TYPE="RAINFALL"
TENANT="hcm"
DISTRICT="district-7"

echo "========================================"
echo "🌊 Flood Alert Demo — Sprint 6"
echo "========================================"
echo "Backend: $BASE_URL"
echo "Sensor:  $SENSOR_ID"
echo "Type:    $SENSOR_TYPE"
echo ""

# Seed flood sensors (simulated — backend creates alert events)
echo "📡 Step 1: Injecting 3 consecutive RAINFALL readings >80mm/h..."

for i in 1 2 3; do
  VALUE=$((80 + RANDOM % 40))  # Random between 80-120 mm/h
  echo "   Reading #$i: ${VALUE} mm/h"

  RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/test/inject-reading" \
    -H "Content-Type: application/json" \
    "?sensorId=${SENSOR_ID}&sensorType=${SENSOR_TYPE}&value=${VALUE}&tenantId=${TENANT}&district=${DISTRICT}")

  echo "   Response: $(echo "$RESPONSE" | python3 -m json.tool 2>/dev/null || echo "$RESPONSE")"

  if [ "$i" -lt 3 ]; then
    echo "   Waiting 5 seconds..."
    sleep 5
  fi
done

echo ""
echo "✅ Step 2: 3 readings injected. Flink CEP should detect pattern."
echo "   Waiting 30 seconds for alert to propagate..."
sleep 30

echo ""
echo "🔍 Step 3: Checking for flood alerts in the system..."

ALERTS=$(curl -s "${BASE_URL}/api/v1/alerts?module=FLOOD&page=0&size=5" \
  -H "Authorization: Bearer ${DEMO_TOKEN:-dummy}" 2>/dev/null || echo "[]")

echo "   Alerts response: $ALERTS"
echo ""
echo "========================================"
echo "Demo complete! Check the Operations Center UI for flood alert."
echo "Expected: P1_WARNING flood alert from $DISTRICT"
echo "========================================"
