#!/bin/bash
# Quick health check for all UIP services
# Usage: ./health-check.sh
# Make executable: chmod +x infrastructure/scripts/health-check.sh
set -e

check_service() {
    local name="$1"
    local url="$2"
    if curl -sf "$url" > /dev/null 2>&1; then
        echo "  [OK] $name"
    else
        echo "  [FAIL] $name (UNREACHABLE: $url)"
    fi
}

echo "=========================================="
echo "  UIP Service Health Check"
echo "=========================================="
echo ""

check_service "Backend API"     "http://localhost:8080/actuator/health"
check_service "Frontend"        "http://localhost:3000"
check_service "Kong Gateway"    "http://localhost:8000"
check_service "Keycloak"        "http://localhost:8080/realms/uip"
check_service "Grafana"         "http://localhost:3001/api/health"
check_service "Kafka UI"        "http://localhost:8090"

echo ""
echo "Health check complete"
