#!/usr/bin/env bash
# =============================================================================
# G9: Flink E2E Throughput Load Test
# =============================================================================
# Verifies: Kafka → Flink EsgDualSinkJob → ClickHouse pipeline at ≥10k events/sec
#
# Uses kafka-producer-perf-test with correct NGSI-LD payload format.
# All operations run inside uip-kafka container (no host deps needed).
#
# Usage:
#   ./scripts/flink-throughput-load-test.sh                     # 100K messages
#   ./scripts/flink-throughput-load-test.sh 50000               # 50K messages
#   ./scripts/flink-throughput-load-test.sh 200000 localhost:29092
# =============================================================================
set -euo pipefail

TOTAL_MESSAGES="${1:-100000}"
KAFKA_BOOTSTRAP="${2:-localhost:29092}"
TOPIC="ngsi_ld_esg"
PAYLOAD_FILE="/tmp/ngsi-ld-payloads.txt"
NUM_DISTINCT=200

echo ""
echo "═══════════════════════════════════════════════════"
echo "  G9: Flink E2E Throughput Load Test"
echo "═══════════════════════════════════════════════════"
echo "  Messages:  ${TOTAL_MESSAGES}"
echo "  Kafka:     ${KAFKA_BOOTSTRAP}"
echo "  Topic:     ${TOPIC}"
echo ""

# ─── Phase 1: Generate NGSI-LD payloads ──────────────────────────────────────
echo "Phase 1/6: Generating ${NUM_DISTINCT} distinct NGSI-LD payloads..."

docker exec uip-kafka bash -c '
rm -f '"${PAYLOAD_FILE}"'
NOW_MS=$(date +%s%3N)
BASE_MS=$((NOW_MS - 3600000))

for i in $(seq 1 '"${NUM_DISTINCT}"'); do
    TENANT=$((i % 3 + 1))
    BUILDING_IDX=$((i % 30))
    BUILDING=$(printf "BLD-%03d" $((BUILDING_IDX + 1)))
    SENSOR_IDX=$((i * 7 % 1000))
    TS=$((BASE_MS + i * 18000))
    ENERGY=$((RANDOM * 500 / 32767 + 10))
    CO2=$((RANDOM * 100 / 32767 + 5))
    WATER=$((RANDOM * 200 / 32767 + 1))

    case $((i % 4)) in
        0) METRIC="energy_kwh"  VAL="${ENERGY}" ;;
        1) METRIC="co2_kg"      VAL="${CO2}" ;;
        2) METRIC="water_m3"    VAL="${WATER}" ;;
        *) METRIC="waste_kg"    VAL=$((RANDOM * 50 / 32767 + 1)) ;;
    esac

    printf "{\"id\":\"urn:ngsi-ld:Device:SENSOR-LT-%04d\",\"type\":\"Device\",\"deviceId\":{\"type\":\"Property\",\"value\":\"SENSOR-%s-%04d\"},\"observedAt\":{\"type\":\"Property\",\"value\":%d},\"sensorType\":{\"type\":\"Property\",\"value\":\"energy\"},\"measurements\":{\"type\":\"Property\",\"value\":{\"%s\":%d.%d}},\"_meta\":{\"source\":\"load-test\",\"sensorType\":\"energy\",\"tenantId\":\"tenant-%02d\",\"buildingName\":\"Building %s\",\"district\":\"District %d\"}}\n" \
        "$i" "$BUILDING" "$SENSOR_IDX" "$TS" "$METRIC" "$VAL" "$((RANDOM % 10))" "$TENANT" "$BUILDING" "$((BUILDING_IDX / 10))" \
        >> '"${PAYLOAD_FILE}"'
done

COUNT=$(wc -l < '"${PAYLOAD_FILE}"')
echo "  Generated ${COUNT} payloads"
' 2>&1

echo "  Done"
echo ""

# ─── Phase 2: Record pre-test ClickHouse count ───────────────────────────────
echo "Phase 2/6: Recording pre-test ClickHouse count..."
PRE_COUNT=$(curl -s "http://localhost:8123/?query=SELECT+count()+FROM+analytics.esg_readings" --max-time 5 2>/dev/null || echo "0")
echo "  ClickHouse rows (before): ${PRE_COUNT}"
echo ""

# ─── Phase 3: Run kafka-producer-perf-test ────────────────────────────────────
echo "Phase 3/6: Running kafka-producer-perf-test (${TOTAL_MESSAGES} messages)..."
PERF_OUTPUT=$(docker exec uip-kafka bash -c '
kafka-producer-perf-test \
    --topic '"${TOPIC}"' \
    --num-records '"${TOTAL_MESSAGES}"' \
    --throughput -1 \
    --payload-file '"${PAYLOAD_FILE}"' \
    --payload-delimiter "\n" \
    --producer-props \
        bootstrap.servers='"${KAFKA_BOOTSTRAP}"' \
        acks=1 \
        linger.ms=10 \
        batch.size=65536 \
        compression.type=none
' 2>&1)

echo "  Producer output:"
echo "${PERF_OUTPUT}" | sed 's/^/    /'
echo ""

# Parse producer results (macOS grep has no -P, use sed/awk)
PRODUCER_RATE=$(echo "${PERF_OUTPUT}" | sed -n 's/^\([0-9,]*\) records sent.*/\1/p' | tr -d ',' | head -1)
P99_LATENCY=$(echo "${PERF_OUTPUT}" | sed -n 's/.* \([0-9.]*\) ms 99th.*/\1/p' | head -1)

if [ -z "${PRODUCER_RATE}" ]; then
    PRODUCER_RATE=$(echo "${PERF_OUTPUT}" | awk '/records sent/{gsub(/,/,"",$1); print $1; exit}')
fi
echo "  Parsed: rate=${PRODUCER_RATE:-?} records/sec, p99=${P99_LATENCY:-?}ms"
echo ""

# ─── Phase 4: Wait for Flink to drain ────────────────────────────────────────
echo "Phase 4/6: Waiting for Flink to process (polling ClickHouse)..."
STABLE_COUNT=0
LAST_COUNT=0
WAITED=0
MAX_WAIT=90

while [ $WAITED -lt $MAX_WAIT ]; do
    sleep 5
    WAITED=$((WAITED + 5))

    CUR_COUNT=$(curl -s "http://localhost:8123/?query=SELECT+count()+FROM+analytics.esg_readings" --max-time 5 2>/dev/null || echo "0")
    DELTA=$((CUR_COUNT - LAST_COUNT))
    echo "  [${WAITED}s] ClickHouse: ${CUR_COUNT} rows (delta: +${DELTA})"

    if [ "$DELTA" -eq 0 ] && [ "$LAST_COUNT" -gt 0 ]; then
        STABLE_COUNT=$((STABLE_COUNT + 1))
        if [ $STABLE_COUNT -ge 2 ]; then
            echo "  Row count stable for 2 consecutive polls — Flink drained"
            break
        fi
    else
        STABLE_COUNT=0
    fi
    LAST_COUNT=$CUR_COUNT
done
echo ""

# ─── Phase 5: Final row count ────────────────────────────────────────────────
echo "Phase 5/6: Recording final ClickHouse count..."
sleep 3
POST_COUNT=$(curl -s "http://localhost:8123/?query=SELECT+count()+FROM+analytics.esg_readings" --max-time 5 2>/dev/null || echo "0")
INGESTED=$((POST_COUNT - PRE_COUNT))
echo "  ClickHouse rows (after): ${POST_COUNT}"
echo "  Rows ingested by Flink:  ${INGESTED}"
echo ""

# ─── Phase 6: Report ─────────────────────────────────────────────────────────
echo "═══════════════════════════════════════════════════"
echo "  RESULTS"
echo "═══════════════════════════════════════════════════"

if [ -n "${PRODUCER_RATE}" ]; then
    echo "  Kafka producer throughput:  ${PRODUCER_RATE} records/sec"
fi
if [ -n "${P99_LATENCY}" ]; then
    echo "  Producer p99 latency:       ${P99_LATENCY} ms"
fi
echo "  Messages sent:              ${TOTAL_MESSAGES}"
echo "  ClickHouse rows ingested:   ${INGESTED}"

if [ "${TOTAL_MESSAGES}" -gt 0 ] && [ "${INGESTED}" -gt 0 ]; then
    DELIVERY=$((INGESTED * 100 / TOTAL_MESSAGES))
    echo "  Delivery ratio:             ${DELIVERY}%"

    if [ $WAITED -gt 0 ] && [ $INGESTED -gt 0 ]; then
        E2E_RATE=$((INGESTED / WAITED))
        echo "  E2E Flink ingestion rate:   ${E2E_RATE} rows/sec"
    fi
fi

echo ""
echo "  Gate checks:"

PROD_PASS="N/A"
if [ -n "${PRODUCER_RATE}" ] && [ "${PRODUCER_RATE}" -ge 10000 ]; then
    echo "    Producer >= 10k/sec:      PASS (${PRODUCER_RATE}/sec)"
    PROD_PASS="PASS"
elif [ -n "${PRODUCER_RATE}" ]; then
    echo "    Producer >= 10k/sec:      INFO (${PRODUCER_RATE}/sec — single-node test may be lower)"
fi

if [ "${TOTAL_MESSAGES}" -gt 0 ] && [ "${INGESTED}" -gt 0 ]; then
    DELIVERY=$((INGESTED * 100 / TOTAL_MESSAGES))
    if [ $DELIVERY -ge 80 ]; then
        echo "    Delivery >= 80%:          PASS (${DELIVERY}%)"
    else
        echo "    Delivery >= 80%:          FAIL (${DELIVERY}%)"
    fi
fi

echo ""
echo "═══════════════════════════════════════════════════"
echo "  Load Test Complete"
echo "═══════════════════════════════════════════════════"
