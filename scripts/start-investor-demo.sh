#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────
# UIP Smart City — Investor Demo Launcher
# ─────────────────────────────────────────────────────────────
# Usage:
#   bash scripts/start-investor-demo.sh          # start simulator
#   bash scripts/start-investor-demo.sh stop     # stop simulator
#   bash scripts/start-investor-demo.sh status   # check status
# ─────────────────────────────────────────────────────────────

set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
PID_FILE="/tmp/uip-demo-simulator.pid"
LOG_FILE="/tmp/uip-demo-simulator.log"
SIMULATOR="$SCRIPT_DIR/investor-demo-simulator.py"

GREEN='\033[92m'; YELLOW='\033[93m'; RED='\033[91m'; BOLD='\033[1m'; NC='\033[0m'

action="${1:-start}"

stop_simulator() {
    if [[ -f "$PID_FILE" ]]; then
        PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            kill "$PID" 2>/dev/null
            rm -f "$PID_FILE"
            echo -e "  ${GREEN}✅ Simulator stopped (PID $PID)${NC}"
        else
            rm -f "$PID_FILE"
            echo -e "  ${YELLOW}⚠ Simulator was not running${NC}"
        fi
    else
        # Try by process name
        pkill -f "investor-demo-simulator.py" 2>/dev/null && \
            echo -e "  ${GREEN}✅ Simulator stopped${NC}" || \
            echo -e "  ${YELLOW}⚠ No simulator running${NC}"
    fi
}

case "$action" in

  stop)
    echo -e "${BOLD}Stopping demo simulator...${NC}"
    stop_simulator
    exit 0
    ;;

  status)
    if [[ -f "$PID_FILE" ]] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
        PID=$(cat "$PID_FILE")
        echo -e "  ${GREEN}✅ Simulator RUNNING (PID $PID)${NC}"
        echo -e "  Log: tail -f $LOG_FILE"
    else
        echo -e "  ${RED}✗ Simulator NOT running${NC}"
    fi
    exit 0
    ;;

  start|*)
    echo ""
    echo -e "${BOLD}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BOLD}║   UIP Smart City — INVESTOR DEMO LAUNCHER                   ║${NC}"
    echo -e "${BOLD}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    # Stop any existing instance
    stop_simulator 2>/dev/null || true

    # Check backend health
    echo -e "  Checking backend..."
    if ! curl -sf http://localhost:8080/api/v1/health > /dev/null 2>&1; then
        echo -e "  ${RED}✗ Backend not responding. Start infrastructure first:${NC}"
        echo -e "    cd infrastructure && make up-ha"
        exit 1
    fi
    echo -e "  ${GREEN}✅ Backend: UP${NC}"

    # Check Python3 available
    if ! command -v python3 &>/dev/null; then
        echo -e "  ${RED}✗ python3 not found${NC}"; exit 1
    fi

    # Check requests library
    if ! python3 -c "import requests" 2>/dev/null; then
        echo -e "  ${YELLOW}Installing requests...${NC}"
        pip3 install requests --quiet
    fi

    echo ""
    echo -e "  ${BOLD}Starting IoT device simulator in background...${NC}"
    python3 "$SIMULATOR" > "$LOG_FILE" 2>&1 &
    SIMUL_PID=$!
    echo "$SIMUL_PID" > "$PID_FILE"

    sleep 3

    if kill -0 "$SIMUL_PID" 2>/dev/null; then
        echo -e "  ${GREEN}✅ Simulator running (PID $SIMUL_PID)${NC}"
    else
        echo -e "  ${RED}✗ Simulator failed to start. Check: tail -20 $LOG_FILE${NC}"
        exit 1
    fi

    echo ""
    echo -e "  ${BOLD}📊 Live terminal output:${NC}"
    echo -e "    tail -f $LOG_FILE"
    echo ""
    echo -e "  ${BOLD}📱 Open browser → ${GREEN}http://localhost:3000${NC}"
    echo ""
    echo -e "  ${BOLD}Demo pages (show in this order for investors):${NC}"

    pages=(
        "/            Dashboard       — Platform overview, KPI cards"
        "/city-ops    City Ops Map    — Sensor map with live AQI markers"
        "/environment Environment     — AQI gauges + trend charts"
        "/alerts      Alerts          — Live alert feed (CRITICAL/WARNING)"
        "/bms/devices BMS Devices     — Device list, send commands live"
        "/buildings   Buildings       — Cross-building energy analytics"
        "/esg         ESG Reports     — Energy, Carbon, click Export PDF"
    )
    for p in "${pages[@]}"; do
        echo -e "    ${YELLOW}http://localhost:3000/${p}${NC}"
    done

    echo ""
    echo -e "  ${BOLD}Demo scenarios (auto-cycle every ~60 seconds):${NC}"
    echo -e "    1. ${GREEN}NORMAL     — All 8 sensors green AQI 50–100${NC}"
    echo -e "    2. ${YELLOW}WARNING    — ENV-001 Bến Nghé spikes to 155+ (yellow alert)${NC}"
    echo -e "    3. ${RED}CRITICAL   — ENV-003 Bình Thạnh hits 220+ (red alert + workflow)${NC}"
    echo -e "    4. ${GREEN}RECOVERY   — System self-resolves, back to normal${NC}"
    echo -e "    5. ${RED}MULTI-ZONE — 3 districts spike simultaneously${NC}"
    echo ""
    echo -e "  ${BOLD}Stop simulator:${NC}  bash scripts/start-investor-demo.sh stop"
    echo ""
    ;;
esac
