#!/usr/bin/env bash
set -euo pipefail

# HA Environment Health Check Script
# Verifies that all containers in the HA stack are running and healthy.
#
# Usage:
#   ./ha-health-check.sh           — Check once, exit immediately
#   ./ha-health-check.sh --wait    — Retry every 10s for up to 5 min

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "$SCRIPT_DIR")"

WAIT_MODE=false
MAX_ATTEMPTS=30  # 30 attempts × 10s = 5 minutes
WAIT_INTERVAL=10

# Critical HA services that MUST be healthy
# Format: "container_name:require_health" where require_health=1 means health check is mandatory
CRITICAL_SERVICES=(
  "uip-kafka:1"
  "uip-kafka-2:1"
  "uip-kafka-3:1"
  "uip-clickhouse-keeper:1"
  "uip-clickhouse-01:1"
  "uip-clickhouse-02:1"
  "uip-timescaledb:1"
  "uip-timescaledb-standby:1"
  "uip-flink-jobmanager:1"
  "uip-flink-taskmanager:0"
  "uip-backend:1"
  "uip-keycloak:1"
  "uip-kong:1"
  "uip-redis:1"
  "uip-emqx:1"
  "uip-frontend:0"
  "infrastructure-analytics-service-1:1"
)

# Optional services (won't fail overall check if down)
OPTIONAL_SERVICES=(
  "uip-kafka-ui:0"
  "uip-minio:1"
  "uip-apicurio-registry:1"
  "uip-redpanda-connect:0"
  "uip-forecast-service:1"
)

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;36m'
NC='\033[0m' # No Color

parse_args() {
  while [[ $# -gt 0 ]]; do
    case $1 in
      --wait)
        WAIT_MODE=true
        shift
        ;;
      *)
        echo "Unknown option: $1"
        echo "Usage: $0 [--wait]"
        exit 1
        ;;
    esac
  done
}

check_container_health() {
  local container_name=$1
  local require_health=$2
  
  # Check if container exists
  if ! docker ps -a --format '{{.Names}}' | grep -q "^${container_name}$"; then
    echo "NOT_FOUND"
    return
  fi
  
  # Check if container is running
  local state=$(docker inspect -f '{{.State.Status}}' "$container_name" 2>/dev/null || echo "unknown")
  if [[ "$state" != "running" ]]; then
    echo "$state"
    return
  fi
  
  # Check health status if required
  if [[ "$require_health" == "1" ]]; then
    local health=$(docker inspect -f '{{.State.Health.Status}}' "$container_name" 2>/dev/null || echo "none")
    if [[ "$health" == "none" ]]; then
      # No healthcheck defined, consider it healthy if running
      echo "running"
    else
      echo "$health"
    fi
  else
    echo "running"
  fi
}

run_health_check() {
  local all_services=("${CRITICAL_SERVICES[@]}" "${OPTIONAL_SERVICES[@]}")
  local total_count=${#CRITICAL_SERVICES[@]}
  local healthy_count=0
  local failed_services=()
  local optional_failed=()
  
  echo -e "${BLUE}[HA-CHECK]${NC} Starting health check for HA environment..."
  echo ""
  
  # Check critical services
  for service_entry in "${CRITICAL_SERVICES[@]}"; do
    IFS=':' read -r container_name require_health <<< "$service_entry"
    
    local status=$(check_container_health "$container_name" "$require_health")
    
    case "$status" in
      healthy|running)
        echo -e "${GREEN}[HA-CHECK]${NC} ${container_name} ... ${GREEN}✓ ${status}${NC}"
        ((healthy_count++))
        ;;
      starting)
        echo -e "${YELLOW}[HA-CHECK]${NC} ${container_name} ... ${YELLOW}⚠ starting${NC}"
        failed_services+=("$container_name (starting)")
        ;;
      unhealthy)
        echo -e "${RED}[HA-CHECK]${NC} ${container_name} ... ${RED}✗ unhealthy${NC}"
        failed_services+=("$container_name (unhealthy)")
        ;;
      NOT_FOUND)
        echo -e "${RED}[HA-CHECK]${NC} ${container_name} ... ${RED}✗ not found${NC}"
        failed_services+=("$container_name (not found)")
        ;;
      *)
        echo -e "${RED}[HA-CHECK]${NC} ${container_name} ... ${RED}✗ $status${NC}"
        failed_services+=("$container_name ($status)")
        ;;
    esac
  done
  
  # Check optional services (informational only)
  echo ""
  echo -e "${BLUE}[HA-CHECK]${NC} Optional services:"
  for service_entry in "${OPTIONAL_SERVICES[@]}"; do
    IFS=':' read -r container_name require_health <<< "$service_entry"
    
    local status=$(check_container_health "$container_name" "$require_health")
    
    case "$status" in
      healthy|running)
        echo -e "${GREEN}[HA-CHECK]${NC} ${container_name} ... ${GREEN}✓ ${status}${NC}"
        ;;
      *)
        echo -e "${YELLOW}[HA-CHECK]${NC} ${container_name} ... ${YELLOW}⚠ $status${NC}"
        optional_failed+=("$container_name ($status)")
        ;;
    esac
  done
  
  echo ""
  echo -e "${BLUE}[HA-CHECK]${NC} ────────────────────────────────────────────────"
  echo -e "${BLUE}[HA-CHECK]${NC} Result: ${healthy_count}/${total_count} critical services healthy"
  
  if [[ ${#failed_services[@]} -eq 0 ]]; then
    echo -e "${GREEN}[HA-CHECK]${NC} ${GREEN}✓ ALL CRITICAL SERVICES HEALTHY${NC}"
    if [[ ${#optional_failed[@]} -gt 0 ]]; then
      echo -e "${YELLOW}[HA-CHECK]${NC} Optional services with issues:"
      for svc in "${optional_failed[@]}"; do
        echo -e "${YELLOW}[HA-CHECK]${NC}   - $svc"
      done
    fi
    return 0
  else
    echo -e "${RED}[HA-CHECK]${NC} ${RED}✗ FAILED${NC} — The following services are not healthy:"
    for svc in "${failed_services[@]}"; do
      echo -e "${RED}[HA-CHECK]${NC}   - $svc"
    done
    return 1
  fi
}

main() {
  parse_args "$@"
  
  if [[ "$WAIT_MODE" == "false" ]]; then
    # Single check
    if run_health_check; then
      exit 0
    else
      exit 1
    fi
  else
    # Wait mode: retry for up to 5 minutes
    echo -e "${BLUE}[HA-CHECK]${NC} Wait mode enabled: will retry every ${WAIT_INTERVAL}s for up to 5 minutes"
    echo ""
    
    for attempt in $(seq 1 $MAX_ATTEMPTS); do
      echo -e "${BLUE}[HA-CHECK]${NC} Attempt $attempt/$MAX_ATTEMPTS ($(( (attempt - 1) * WAIT_INTERVAL ))s elapsed)"
      echo ""
      
      if run_health_check; then
        echo ""
        echo -e "${GREEN}[HA-CHECK]${NC} ${GREEN}✓ HA environment is healthy after $(( (attempt - 1) * WAIT_INTERVAL ))s${NC}"
        exit 0
      fi
      
      if [[ $attempt -lt $MAX_ATTEMPTS ]]; then
        echo ""
        echo -e "${YELLOW}[HA-CHECK]${NC} Waiting ${WAIT_INTERVAL}s before retry..."
        echo ""
        sleep $WAIT_INTERVAL
      fi
    done
    
    echo ""
    echo -e "${RED}[HA-CHECK]${NC} ${RED}✗ Timeout: HA environment did not become healthy within 5 minutes${NC}"
    exit 1
  fi
}

main "$@"
