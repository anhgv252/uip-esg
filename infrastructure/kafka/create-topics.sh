#!/usr/bin/env bash
# Creates all Kafka topics required by UIP Smart City system
set -euo pipefail

BOOTSTRAP="kafka:9092"
WAIT_SECS=10
REPLICATION_FACTOR="${KAFKA_REPLICATION_FACTOR:-1}"

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
    --replication-factor "${REPLICATION_FACTOR}" \
    --config retention.ms="${retention_ms}"
  echo "  [OK] ${topic}"
}

echo "=== Creating UIP Kafka Topics (RF=${REPLICATION_FACTOR}) ==="

# Raw ingestion
create_topic "raw_telemetry" 6
create_topic "esg_dlq" 1 2592000000  # 30 days DLQ

# NGSI-LD normalised topics
create_topic "ngsi_ld_environment" 6
create_topic "ngsi_ld_esg" 3
create_topic "ngsi_ld_traffic" 3
create_topic "ngsi_ld_citizen" 3

# Alert pipeline — Flink AlertDetectionJob → backend AlertEventKafkaConsumer + GenericKafkaTriggerService
create_topic "UIP.flink.alert.detected.v1" 3

# Flood alert pipeline — Flink FloodAlertJob → backend FloodAlertConsumer (Sprint 6)
create_topic "UIP.flink.alert.flood.v1" 3
create_topic "UIP.flink.alert.flood.v1.dlq" 1 2592000000  # 30 days DLQ

# BMS command ACK topic — EMQX MQTT bridge → Kafka (Sprint 6)
create_topic "UIP.bms.command.ack.v1" 3

# Alert events — internal alert storage topic
create_topic "alert_events" 3

# Workflow admin — TriggerConfigCacheInvalidator listens for config updates
create_topic "UIP.admin.trigger-config.updated.v1" 1

# Workflow DLQ — GenericKafkaTriggerService sends failed trigger events here
create_topic "UIP.workflow.trigger.dlq.v1" 1 2592000000  # 30 days

# ─── Structural safety monitoring (Sprint 7) ─────────────────────────────────
create_topic "UIP.structural.alert.critical.v1" 3
create_topic "UIP.structural.alert.dlq.v1" 1 2592000000  # 30 days DLQ

# BMS reading (Sprint 7) — v1 JSON (existing) + v2 Avro (dual-publish B1-3)
create_topic "UIP.bms.reading.raw.v1" 6
create_topic "UIP.bms.reading.raw.v1.dlq" 1 2592000000  # 30 days DLQ

# ─── Avro v2 topics (Sprint 7 B1-3 dual-publish) ─────────────────────────────
# v1 JSON topics remain active until Phase 3 deprecation — see kafka-avro-schema-versioning.md
create_topic "UIP.bms.reading.raw.v2" 6            # Avro BmsReadingEvent
create_topic "UIP.iot.sensor.reading.v2" 6         # Avro SensorReadingEvent
create_topic "UIP.flink.alert.detected.v2" 3       # Avro AlertDetectedEvent
create_topic "UIP.analytics.hourly.rollup.v2" 3    # Avro HourlyRollupEvent

# ─── MVP4 AI Scale + Correlation Engine ──────────────────────────────────────
# These topics are consumed by backend @KafkaListener but were missing from this
# script — caused UNKNOWN_TOPIC_OR_PARTITION warns after any Kafka volume reset
# (KAFKA_AUTO_CREATE_TOPICS_ENABLE=false). Added 2026-06-18.
create_topic "ai.district.aggregations" 3            # Flink DistrictAggregationJob → DistrictAggregationConsumer (G1 AI pipeline)
create_topic "correlated.incidents" 3                # Flink IncidentCorrelationJob → CorrelationService (G2 correlation)
create_topic "correlated.incidents-retry-0" 1 2592000000  # @RetryableTopic(attempts=3) 1st retry  (BUG-003 fix: retry-0 was missing)
create_topic "correlated.incidents-retry-1" 1 2592000000  # @RetryableTopic(attempts=3) 2nd retry
create_topic "correlated.incidents-dlt" 1 2592000000      # CorrelationService dead-letter after 3 attempts (30 days)
create_topic "bms.feedback.dlq" 1 2592000000             # BMS feedback retry dead-letter (M4-COR-04)
create_topic "uip.esg.metrics.v1" 3                  # ESG metrics → forecast cache eviction
create_topic "UIP.esg.telemetry.error.v1" 3          # ESG telemetry error consumer

echo "=== All topics created successfully ==="
kafka-topics --bootstrap-server "${BOOTSTRAP}" --list
