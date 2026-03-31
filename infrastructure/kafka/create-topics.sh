#!/usr/bin/env bash
# Creates all Kafka topics required by UIP Smart City system
set -euo pipefail

BOOTSTRAP="kafka:9092"
WAIT_SECS=10

echo "Waiting ${WAIT_SECS}s for Kafka to be fully ready..."
sleep "${WAIT_SECS}"

create_topic() {
  local topic="$1"
  local partitions="${2:-3}"
  local retention_ms="${3:-604800000}"  # 7 days default
  kafka-topics --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${topic}" \
    --partitions "${partitions}" \
    --replication-factor 1 \
    --config retention.ms="${retention_ms}"
  echo "  [OK] ${topic}"
}

echo "=== Creating UIP Kafka Topics ==="

# Raw ingestion
create_topic "raw_telemetry" 6
create_topic "esg_dlq" 1 2592000000  # 30 days DLQ

# NGSI-LD normalised topics
create_topic "ngsi_ld_environment" 6
create_topic "ngsi_ld_esg" 3
create_topic "ngsi_ld_traffic" 3
create_topic "ngsi_ld_citizen" 3

# Alert pipeline
create_topic "alert_events" 3

echo "=== All topics created successfully ==="
kafka-topics --bootstrap-server "${BOOTSTRAP}" --list
