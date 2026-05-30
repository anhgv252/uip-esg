#!/usr/bin/env bash
#
# S6-OPS1 — Blue-Green Deploy Switch Script
#
# Usage:
#   ./blue-green-switch.sh deploy   — Build and deploy new version to inactive slot
#   ./blue-green-switch.sh switch   — Switch nginx to the new active slot
#   ./blue-green-switch.sh rollback — Switch back to previous slot
#   ./blue-green-switch.sh status   — Show current active slot
#
# The script manages two backend instances:
#   - BLUE: uip-backend-blue (port 8081)
#   - GREEN: uip-backend-green (port 8082)
#
# Nginx routes traffic to the active slot. Switching updates the
# nginx upstream config and reloads nginx (< 1 second).
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
NGINX_CONF="${PROJECT_DIR}/frontend/nginx.conf"
STATE_FILE="${PROJECT_DIR}/.blue-green-state"
COMPOSE_FILE="${PROJECT_DIR}/infrastructure/docker-compose.yml"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# Get current active slot (default: blue)
get_active() {
  if [ -f "$STATE_FILE" ]; then
    cat "$STATE_FILE"
  else
    echo "blue"
  fi
}

get_inactive() {
  local active=$(get_active)
  if [ "$active" = "blue" ]; then echo "green"; else echo "blue"; fi
}

# --- Status ---
cmd_status() {
  local active=$(get_active)
  local inactive=$(get_inactive)
  echo -e "Current active:  ${GREEN}${active}${NC}"
  echo -e "Current inactive: ${BLUE}${inactive}${NC}"

  # Check if both containers are running
  for slot in blue green; do
    local port=$((slot == "blue" ? 8081 : 8082))
    local name="uip-backend-${slot}"
    local running=$(docker ps --filter "name=${name}" --format "{{.Status}}" 2>/dev/null || echo "not running")
    echo -e "  ${slot}: ${name} (port ${port}) — ${running}"
  done
}

# --- Deploy to inactive slot ---
cmd_deploy() {
  local inactive=$(get_inactive)
  local port=$((inactive == "blue" ? 8081 : 8082))
  local name="uip-backend-${inactive}"

  echo -e "${YELLOW}Deploying to ${inactive} slot...${NC}"
  echo "  Container: ${name}"
  echo "  Port: ${port}"

  # Build new image
  echo "Building backend image..."
  docker build -t uip-backend:${inactive} "${PROJECT_DIR}/backend" --quiet

  # Stop old inactive container if running
  if docker ps --format "{{.Names}}" | grep -q "^${name}$"; then
    echo "Stopping old ${name}..."
    docker stop "$name" && docker rm "$name"
  fi

  # Start new inactive container
  echo "Starting ${name} on port ${port}..."
  docker run -d \
    --name "$name" \
    --network infrastructure_default \
    -e SPRING_PROFILES_ACTIVE=docker \
    -e SERVER_PORT=${port} \
    -p ${port}:${port} \
    uip-backend:${inactive}

  # Wait for health check
  echo "Waiting for health check..."
  local retries=0
  local max_retries=30
  while [ $retries -lt $max_retries ]; do
    if curl -sf "http://localhost:${port}/actuator/health" > /dev/null 2>&1; then
      echo -e "${GREEN}${name} is HEALTHY${NC}"
      return 0
    fi
    retries=$((retries + 1))
    sleep 2
  done

  echo -e "${RED}ERROR: ${name} failed health check after 60s${NC}"
  return 1
}

# --- Switch active slot ---
cmd_switch() {
  local inactive=$(get_inactive)
  local active=$(get_active)
  local new_port=$((inactive == "blue" ? 8081 : 8082))
  local name="uip-backend-${inactive}"

  # Verify new container is healthy
  if ! curl -sf "http://localhost:${new_port}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}ERROR: ${name} is not healthy. Aborting switch.${NC}"
    exit 1
  fi

  echo -e "${YELLOW}Switching traffic from ${active} to ${inactive}...${NC}"
  local start_time=$(date +%s%N)

  # Update nginx upstream
  if [ -f "$NGINX_CONF" ]; then
    sed -i.bak "s/server uip-backend:${active == "blue" ? 8081 : 8082}/server uip-backend:${new_port}/g" "$NGINX_CONF"
    sed -i.bak "s/server 127.0.0.1:${active == "blue" ? 8081 : 8082}/server 127.0.0.1:${new_port}/g" "$NGINX_CONF"
    # Reload nginx
    docker exec uip-nginx nginx -s reload 2>/dev/null || true
  fi

  # Update state file
  echo "$inactive" > "$STATE_FILE"

  local end_time=$(date +%s%N)
  local duration_ms=$(( (end_time - start_time) / 1000000 ))

  echo -e "${GREEN}Switch complete in ${duration_ms}ms${NC}"
  echo -e "  Active:  ${GREEN}${inactive}${NC} (port ${new_port})"
  echo -e "  Inactive: ${BLUE}${active}${NC}"
}

# --- Rollback ---
cmd_rollback() {
  local active=$(get_active)
  local old_port=$((active == "blue" ? 8081 : 8082))
  local previous_name="uip-backend-${active}"

  echo -e "${YELLOW}Rolling back to previous: ${active} (port ${old_port})...${NC}"

  if ! docker ps --format "{{.Names}}" | grep -q "^${previous_name}$"; then
    echo -e "${RED}ERROR: ${previous_name} is not running. Cannot rollback.${NC}"
    exit 1
  fi

  if ! curl -sf "http://localhost:${old_port}/actuator/health" > /dev/null 2>&1; then
    echo -e "${RED}ERROR: ${previous_name} is not healthy. Cannot rollback.${NC}"
    exit 1
  fi

  # Swap state back
  local previous=$(get_inactive)
  echo "$previous" > "$STATE_FILE"

  # Update nginx
  if [ -f "$NGINX_CONF" ]; then
    sed -i.bak "s/server 127.0.0.1:${old_port}/server 127.0.0.1:$((previous == "blue" ? 8081 : 8082))/g" "$NGINX_CONF"
    docker exec uip-nginx nginx -s reload 2>/dev/null || true
  fi

  echo -e "${GREEN}Rollback complete! Active: ${previous}${NC}"
}

# --- Main ---
case "${1:-}" in
  deploy)   cmd_deploy ;;
  switch)   cmd_switch ;;
  rollback) cmd_rollback ;;
  status)   cmd_status ;;
  *)
    echo "Usage: $0 {deploy|switch|rollback|status}"
    echo ""
    echo "Commands:"
    echo "  deploy   — Build and deploy to inactive slot"
    echo "  switch   — Switch traffic to inactive slot (<30s)"
    echo "  rollback — Switch back to previous slot"
    echo "  status   — Show current active/inactive status"
    exit 1
    ;;
esac
