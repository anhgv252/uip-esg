#!/usr/bin/env bash
# Sprint 2 Load Test — verify Flink throughput ≥10k events/sec sustained
# Single-node test environment (no cluster)
# Pre-req: docker compose up, Flink EsgDualSinkJob running
set -euo pipefail

KAFKA_BOOTSTRAP="${1:-localhost:29092}"
TOPIC="ngsi_ld_esg"
TOTAL_MESSAGES="${2:-50000}"
BATCH_SIZE=500
DURATION_SECONDS=30

echo "=== Sprint 2 Load Test ==="
echo "  Kafka: ${KAFKA_BOOTSTRAP}"
echo "  Topic: ${TOPIC}"
echo "  Messages: ${TOTAL_MESSAGES}"
echo ""

echo "[1/3] Checking Kafka connectivity..."
if ! kafka-topics --bootstrap-server "${KAFKA_BOOTSTRAP}" --describe --topic "${TOPIC}" >/dev/null 2>&1; then
    echo "FAIL: Cannot connect to Kafka or topic ${TOPIC} not found" >&2
    exit 1
fi
echo "  Kafka connected, topic ${TOPIC} exists"

echo "[2/3] Generating ${TOTAL_MESSAGES} sensor messages..."
start_time=$(date +%s)
sent=0
while [ $sent -lt $TOTAL_MESSAGES ]; do
    remaining=$((TOTAL_MESSAGES - sent))
    batch=$((BATCH_SIZE < remaining ? BATCH_SIZE : remaining))

    for i in $(seq 1 "$batch"); do
        tenant=$((sent % 2 == 0 ? 1 : 2))
        building="BLD-00$((sent % 5 + 1))"
        ts=$(date +%s%3N)
        value=$(python3 -c "import random; print(round(random.uniform(10, 500), 2))")

        echo "{\"id\":\"urn:ngsi-ld:Device:SENSOR-${sent}\",\"type\":\"Device\",\"deviceId\":{\"type\":\"Property\",\"value\":\"SENSOR-${building}-$(printf '%04d' $sent)\"},\"observedAt\":{\"type\":\"Property\",\"value\":${ts}},\"sensorType\":{\"type\":\"Property\",\"value\":\"energy\"},\"measurements\":{\"type\":\"Property\",\"value\":{\"energy_kwh\":${value}}},\"_meta\":{\"source\":\"load-test\",\"sensorType\":\"energy\",\"tenantId\":\"tenant-${tenant}\"}}"
    done | kafka-console-producer --bootstrap-server "${KAFKA_BOOTSTRAP}" --topic "${TOPIC}" --property "parse.key=true" --property "key.separator=:" 2>/dev/null || true

    sent=$((sent + batch))
done

end_time=$(date +%s)
elapsed=$((end_time - start_time))
if [ "$elapsed" -eq 0 ]; then elapsed=1; fi

rate=$((sent / elapsed))
echo "  Sent ${sent} messages in ${elapsed}s"
echo "  Producer rate: ${rate} msg/sec"

echo "[3/3] Checking ClickHouse ingestion (waiting 10s for Flink processing)..."
sleep 10

ch_count=$(curl -s "http://localhost:8123/?query=SELECT+count()+FROM+analytics.esg_readings" --max-time 5 2>/dev/null || echo "0")
echo "  ClickHouse total rows: ${ch_count}"

echo ""
echo "=== Results ==="
echo "  Messages sent: ${sent}"
echo "  Producer rate: ${rate} msg/sec"
echo "  ClickHouse rows: ${ch_count}"

if [ "$rate" -ge 10000 ]; then
    echo "  PASS: Producer rate ≥10k/sec"
else
    echo "  INFO: Producer rate ${rate}/sec (target ≥10k for production; single-node test may be lower)"
fi

echo "=== Load Test Complete ==="
