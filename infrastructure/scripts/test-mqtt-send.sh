#!/usr/bin/env bash
# Send 10 MQTT test messages to EMQX for pipeline verification
set -euo pipefail

BROKER="${MQTT_HOST:-localhost}"
PORT="${MQTT_PORT:-1883}"
TOPIC="v1/devices/me/telemetry"
COUNT=10

echo "Sending ${COUNT} test MQTT messages to ${BROKER}:${PORT}..."

for i in $(seq 1 "${COUNT}"); do
  PAYLOAD=$(printf '{"ts":%d,"values":{"aqi":%.1f,"pm25":%.1f,"pm10":%.1f,"temperature":%.1f}}' \
    "$(date +%s%3N)" \
    "$(awk 'BEGIN{srand(); print 50 + rand()*150}')" \
    "$(awk 'BEGIN{srand(); print 10 + rand()*80}')" \
    "$(awk 'BEGIN{srand(); print 20 + rand()*100}')" \
    "$(awk 'BEGIN{srand(); print 20 + rand()*15}')")

  mosquitto_pub -h "${BROKER}" -p "${PORT}" \
    -t "${TOPIC}" \
    -m "${PAYLOAD}" \
    -q 1 2>/dev/null && echo "  [${i}/${COUNT}] Sent: ${PAYLOAD}" || echo "  [${i}/${COUNT}] FAILED (is mosquitto_pub installed?)"
done

echo "Done. Check Kafka topic 'raw_telemetry' via Kafka UI at http://localhost:8090"
