#!/usr/bin/env bash
# Kafka Partition Rebalancing Script — ADR-037 (S8-OPS02)
# Reassigns partitions from 1 broker to 3 brokers with replication.factor=3.
#
# Usage:
#   ./kafka-rebalance.sh [BOOTSTRAP_SERVER]
#
# Prerequisites:
#   - HA stack running with kafka, kafka-2, kafka-3 all healthy
#   - Topics already created (via kafka-init / create-topics.sh)
#
# Part of Sprint 8 (S8-OPS02) — Kafka 3-broker KRaft
set -euo pipefail

BOOTSTRAP="${1:-kafka:9092}"
REPLICATION_FACTOR=3

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC} $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

# ─── Verify 3 brokers ────────────────────────────────────────────────────────

verify_brokers() {
    log_info "Verifying 3-broker quorum..."
    local broker_count
    broker_count=$(kafka-broker-api-versions --bootstrap-server "${BOOTSTRAP}" 2>/dev/null | grep -c "^[0-9]" || echo "0")

    if [[ "$broker_count" -lt 3 ]]; then
        log_error "Expected 3 brokers, found ${broker_count}"
        log_error "Start HA stack first: make up-ha"
        exit 1
    fi
    log_ok "Found ${broker_count} brokers"

    log_info "KRaft quorum status:"
    kafka-metadata-quorum --bootstrap-server "${BOOTSTRAP}" describe --status 2>/dev/null || \
        log_warn "kafka-metadata-quorum not available (may need newer Kafka CLI)"
}

# ─── Generate & Execute Reassignment ─────────────────────────────────────────

rebalance_topic() {
    local topic="$1"
    log_info "Rebalancing: ${topic}..."

    # Get current partition count
    local partitions
    partitions=$(kafka-topics --bootstrap-server "${BOOTSTRAP}" --describe --topic "${topic}" 2>/dev/null | \
        grep -c "Partition:" || echo "0")

    if [[ "$partitions" -eq 0 ]]; then
        log_warn "Topic ${topic} not found — skipping"
        return 0
    fi

    # Generate reassignment JSON
    local current_rf
    current_rf=$(kafka-topics --bootstrap-server "${BOOTSTRAP}" --describe --topic "${topic}" 2>/dev/null | \
        head -2 | grep -o "ReplicaFactor:[[:space:]]*[0-9]*" | grep -o "[0-9]*" || echo "1")

    if [[ "$current_rf" -ge 3 ]]; then
        log_ok "Topic ${topic} already has replication.factor=${current_rf} — skipping"
        return 0
    fi

    # Create reassignment plan: spread partitions across all 3 brokers
    local tmpdir
    tmpdir=$(mktemp -d)
    local plan="${tmpdir}/reassign-${topic}.json"

    # Generate plan using --generate
    kafka-reassign-partitions --bootstrap-server "${BOOTSTRAP}" \
        --topics-to-move-json-file <(echo "{\"topics\":[{\"topic\":\"${topic}\"}],\"version\":1}") \
        --broker-list "1,2,3" \
        --generate 2>/dev/null | \
        tail -2 | head -1 > "${plan}" 2>/dev/null

    if [[ ! -s "$plan" ]]; then
        log_warn "Could not generate plan for ${topic} — manual rebalance needed"
        rm -rf "${tmpdir}"
        return 0
    fi

    log_info "  Executing reassignment..."
    kafka-reassign-partitions --bootstrap-server "${BOOTSTRAP}" \
        --reassignment-json-file "${plan}" \
        --execute 2>/dev/null

    # Wait for completion
    log_info "  Waiting for reassignment to complete..."
    local max_wait=120
    local waited=0
    while [[ $waited -lt $max_wait ]]; do
        local status
        status=$(kafka-reassign-partitions --bootstrap-server "${BOOTSTRAP}" \
            --reassignment-json-file "${plan}" \
            --verify 2>/dev/null | grep -c "completed successfully" || echo "0")

        if [[ "$status" -gt 0 ]]; then
            log_ok "Topic ${topic}: reassignment completed"
            break
        fi
        sleep 5
        waited=$((waited + 5))
    done

    if [[ $waited -ge $max_wait ]]; then
        log_warn "Topic ${topic}: reassignment still in progress (waited ${max_wait}s)"
    fi

    rm -rf "${tmpdir}"
}

# ─── Main ─────────────────────────────────────────────────────────────────────

echo "╔══════════════════════════════════════════════════════════╗"
echo "║   Kafka Partition Rebalancing — 1→3 Brokers (ADR-037)   ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

verify_brokers
echo ""

# Get all topics
log_info "Listing all topics..."
TOPICS=$(kafka-topics --bootstrap-server "${BOOTSTRAP}" --list 2>/dev/null | grep -v "^$")
TOPIC_COUNT=$(echo "$TOPICS" | wc -l | tr -d ' ')
log_info "Found ${TOPIC_COUNT} topics"
echo ""

# Rebalance each topic
rebalanced=0
skipped=0
for topic in $TOPICS; do
    rebalance_topic "$topic" && rebalanced=$((rebalanced + 1)) || skipped=$((skipped + 1))
done

echo ""
log_ok "=== Rebalancing Complete ==="
echo "  Rebalanced: ${rebalanced}"
echo "  Skipped:    ${skipped}"
echo ""

# Verify final state
log_info "Final topic verification:"
kafka-topics --bootstrap-server "${BOOTSTRAP}" --describe 2>/dev/null | \
    grep "^Topic:" | sort -u | while read -r line; do
    echo "  ${line}"
done
echo ""
log_ok "All topics should now have ReplicationFactor: ${REPLICATION_FACTOR}"
