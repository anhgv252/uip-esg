#!/usr/bin/env python3
"""
UIP Smart City — Investor Demo Simulator
=========================================
Continuously simulates IoT devices, BMS events, and alerts so every
frontend page looks live during the investor presentation.

Usage:
    python3 scripts/investor-demo-simulator.py

Open browser at: http://localhost:3000
Pages to show:   / (Dashboard) | /environment | /alerts | /bms/devices
                 /buildings | /esg | /city-ops

Stop with: Ctrl+C
"""

import time
import json
import random
import math
import subprocess
import threading
import requests
from datetime import datetime, timezone
from typing import Optional

# ──────────────────────────────────────────────
# CONFIG
# ──────────────────────────────────────────────
BASE_URL = "http://localhost:8080"
LOGIN_URL = f"{BASE_URL}/api/v1/auth/login"
CREDS = {"username": "admin", "password": "admin_Dev#2026!"}

# Sensors: (sensorId, name, district, lat, lon)
SENSORS = [
    ("ENV-001", "Bến Nghé",    "D1",  10.7769, 106.7009),
    ("ENV-002", "Tân Bình",    "TB",  10.8011, 106.6526),
    ("ENV-003", "Bình Thạnh",  "BT",  10.8120, 106.7127),
    ("ENV-004", "Gò Vấp",      "GV",  10.8382, 106.6639),
    ("ENV-005", "District 7",  "D7",  10.7347, 106.7218),
    ("ENV-006", "Thủ Đức",     "TD",  10.8580, 106.7619),
    ("ENV-007", "Hóc Môn",     "HM",  10.8913, 106.5946),
    ("ENV-008", "Bình Chánh",  "BCh", 10.6923, 106.5734),
]

# BMS Devices: (id, name, protocol)
BMS_DEVICES = [
    ("97b87cec-2767-4f1c-a3b9-116e6c469a6e", "ELEC-METER-FLOOR1",  "MODBUS_TCP"),
    ("bd0878f0-49d1-4f97-96b5-d343dcf71923", "HVAC-AHU-B2",        "BACNET_IP"),
    ("ca9096de-49d4-4381-a4d1-10cc923c45f0", "WATER-METER-ROOF",   "MANUAL"),
    ("931fad52-6368-4808-87d7-4044145bbc23", "IOT-GATEWAY-FLOOR3", "MQTT"),
    ("1be27b82-f6e9-43b9-9a62-6bc5f691b79b", "UPS-SERVER-ROOM",    "MODBUS_TCP"),
]

# Demo scenarios — cycle through these over time
SCENARIOS = [
    "normal",       # AQI 50–100, all sensors green
    "aqi_warning",  # 1 sensor spikes to 155 → WARNING alert
    "aqi_critical", # 1 sensor spikes to 220 → CRITICAL alert
    "aqi_recovery", # Back to normal range
    "multi_spike",  # 3 sensors spike simultaneously
    "normal",
    "normal",
]

# ──────────────────────────────────────────────
# STATE
# ──────────────────────────────────────────────
_token: Optional[str] = None
_token_ts: float = 0
_scenario_idx = 0
_tick = 0
_stats = {"iot_sent": 0, "alerts_triggered": 0, "bms_events": 0, "errors": 0}


# ──────────────────────────────────────────────
# AUTH
# ──────────────────────────────────────────────
def get_token() -> str:
    global _token, _token_ts
    if _token and (time.time() - _token_ts < 3300):
        return _token
    try:
        r = requests.post(LOGIN_URL, json=CREDS, timeout=5)
        r.raise_for_status()
        _token = r.json()["accessToken"]
        _token_ts = time.time()
        return _token
    except Exception as e:
        print(f"  [AUTH ERROR] {e}")
        return ""


def headers() -> dict:
    return {
        "Authorization": f"Bearer {get_token()}",
        "Content-Type": "application/json",
    }


# ──────────────────────────────────────────────
# AQI HELPERS
# ──────────────────────────────────────────────
def aqi_color(v: float) -> str:
    if v <= 50:   return "🟢 GOOD"
    if v <= 100:  return "🟡 MODERATE"
    if v <= 150:  return "🟠 UNHEALTHY(sensitive)"
    if v <= 200:  return "🔴 UNHEALTHY"
    if v <= 300:  return "🟣 VERY UNHEALTHY"
    return "⚫ HAZARDOUS"


def wave_aqi(sensor_idx: int, t: float, scenario: str) -> float:
    """Generate realistic AQI values with sinusoidal drift + scenario spikes."""
    base = 45 + 20 * math.sin(t / 60 + sensor_idx * 0.7)  # slow daily cycle
    noise = random.gauss(0, 4)

    if scenario == "normal":
        return round(max(20, base + noise), 1)

    spike_sensors = {
        "aqi_warning":  [0],        # ENV-001 spikes
        "aqi_critical": [2],        # ENV-003 spikes
        "aqi_recovery": [],
        "multi_spike":  [1, 4, 6],  # TB, D7, HM
    }.get(scenario, [])

    if sensor_idx in spike_sensors:
        if scenario == "aqi_warning":
            return round(random.uniform(155, 175), 1)
        elif scenario == "aqi_critical":
            return round(random.uniform(215, 260), 1)
        elif scenario == "multi_spike":
            return round(random.uniform(160, 195), 1)

    return round(max(20, base + noise), 1)


# ──────────────────────────────────────────────
# KAFKA DIRECT INJECT (for real DB alerts)
# ──────────────────────────────────────────────
ALERT_TOPIC = "UIP.flink.alert.detected.v1"
KAFKA_CONTAINER = "uip-kafka"
KAFKA_BOOTSTRAP = "localhost:9092"

def kafka_produce(topic: str, key: str, value: dict) -> bool:
    """Publish a message to Kafka via docker exec kafka-console-producer."""
    try:
        msg = json.dumps(value)
        cmd = [
            "docker", "exec", "-i", KAFKA_CONTAINER,
            "kafka-console-producer",
            "--bootstrap-server", KAFKA_BOOTSTRAP,
            "--topic", topic,
            "--property", "parse.key=true",
            "--property", "key.separator=|",
        ]
        proc = subprocess.run(
            cmd,
            input=f"{key}|{msg}\n",
            capture_output=True,
            text=True,
            timeout=5,
        )
        return proc.returncode == 0
    except Exception:
        return False


def inject_alert(sensor_id: str, district: str, aqi_value: float) -> str:
    """Publish an alert event directly to UIP.flink.alert.detected.v1 so the
    AlertEventKafkaConsumer persists it to DB and the frontend alert feed refreshes."""
    if aqi_value > 300:
        severity = "EMERGENCY"
    elif aqi_value > 200:
        severity = "CRITICAL"
    else:
        severity = "WARNING"

    payload = {
        "sensorId":    sensor_id,
        "module":      "ENVIRONMENT",
        "measureType": "aqi",
        "value":       str(round(aqi_value, 1)),
        "threshold":   "150.0",
        "severity":    severity,
        "status":      "ACTIVE",
        "detectedAt":  datetime.now(timezone.utc).isoformat(),
        "districtCode": district,
        "tenantId":    "default",
    }
    dedup_key = f"{sensor_id}:aqi:{severity}"
    ok = kafka_produce(ALERT_TOPIC, dedup_key, payload)
    return severity if ok else ""


# ──────────────────────────────────────────────
# SIMULATE IOT SENSOR
# ──────────────────────────────────────────────
def simulate_sensor(sensor: tuple, aqi_value: float) -> bool:
    sid, name, district, lat, lon = sensor
    payload = {
        "sensorId": sid,
        "type": "AIR_QUALITY",
        "value": aqi_value,
        "unit": "AQI",
        "latitude": lat,
        "longitude": lon,
    }
    try:
        r = requests.post(
            f"{BASE_URL}/api/v1/simulate/iot-sensor",
            json=payload,
            headers=headers(),
            timeout=5,
        )
        if r.status_code == 200:
            data = r.json()
            alert_triggered = data.get("alertTriggered", False)
            kafka_ok = data.get("kafkaPublished", False)
            _stats["iot_sent"] += 1
            if alert_triggered:
                _stats["alerts_triggered"] += 1
            return True
    except Exception as e:
        _stats["errors"] += 1
    return False


# ──────────────────────────────────────────────
# BMS DEVICE COMMANDS
# ──────────────────────────────────────────────
BMS_COMMAND_CYCLES = [
    # (device_name_fragment, commandType, payload_description)
    ("HVAC",    "SET_TEMPERATURE",  {"targetTemp": 22, "mode": "COOLING"}),
    ("HVAC",    "SET_TEMPERATURE",  {"targetTemp": 24, "mode": "COOLING"}),
    ("ELEC",    "READ_METER",       {"register": "kWh_total"}),
    ("WATER",   "READ_METER",       {"register": "m3_total"}),
    ("GATEWAY", "PING",             {"timeout": 3000}),
    ("UPS",     "STATUS_CHECK",     {"checkBattery": True}),
    ("HVAC",    "FAN_SPEED",        {"speed": "AUTO", "zone": "B2"}),
]
_bms_cmd_idx = 0


def send_bms_command():
    global _bms_cmd_idx
    name_frag, cmd_type, payload = BMS_COMMAND_CYCLES[_bms_cmd_idx % len(BMS_COMMAND_CYCLES)]
    _bms_cmd_idx += 1

    # find matching device
    device = next((d for d in BMS_DEVICES if name_frag in d[1]), BMS_DEVICES[0])
    dev_id, dev_name, protocol = device

    body = {"commandType": cmd_type, "payload": payload}
    try:
        r = requests.post(
            f"{BASE_URL}/api/v1/bms/devices/{dev_id}/commands",
            json=body,
            headers=headers(),
            timeout=5,
        )
        _stats["bms_events"] += 1
        status = "✅" if r.status_code in (200, 201, 202) else f"⚠️ {r.status_code}"
        return dev_name, cmd_type, status
    except Exception as e:
        _stats["errors"] += 1
        return dev_name, cmd_type, "❌"


# ──────────────────────────────────────────────
# ESG REPORT TRIGGER
# ──────────────────────────────────────────────
def trigger_esg_report():
    try:
        r = requests.post(
            f"{BASE_URL}/api/v1/esg/reports/generate",
            json={"year": 2026, "quarter": 1, "reportType": "ENERGY"},
            headers=headers(),
            timeout=10,
        )
        return r.status_code in (200, 201, 202)
    except Exception:
        return False


def fetch_esg_pdf():
    try:
        r = requests.post(
            f"{BASE_URL}/api/v1/esg/reports/pdf",
            params={"year": 2026, "quarter": 1},
            headers=headers(),
            timeout=10,
        )
        size_kb = len(r.content) / 1024
        return r.status_code == 200, size_kb
    except Exception:
        return False, 0


# ──────────────────────────────────────────────
# ALERT ACKNOWLEDGE (keep alert list fresh)
# ──────────────────────────────────────────────
def acknowledge_old_alerts():
    try:
        r = requests.get(
            f"{BASE_URL}/api/v1/alerts",
            params={"size": 5, "status": "ACTIVE"},
            headers=headers(),
            timeout=5,
        )
        if r.status_code != 200:
            return 0
        data = r.json()
        items = data.get("content", data if isinstance(data, list) else [])
        # Acknowledge alerts older than 2 minutes to cycle fresh ones
        acked = 0
        for alert in items:
            alert_id = alert.get("id")
            if alert_id and acked < 2:
                requests.put(
                    f"{BASE_URL}/api/v1/alerts/{alert_id}/acknowledge",
                    json={"note": "Demo auto-acknowledge"},
                    headers=headers(),
                    timeout=5,
                )
                acked += 1
        return acked
    except Exception:
        return 0


# ──────────────────────────────────────────────
# FORECAST WARM-UP
# ──────────────────────────────────────────────
def fetch_forecast():
    try:
        r = requests.get(
            f"{BASE_URL}/api/v1/forecast/energy",
            params={"tenantId": "default", "days": 7},
            headers=headers(),
            timeout=10,
        )
        if r.status_code == 200:
            d = r.json()
            return d.get("model"), d.get("isFallback"), len(d.get("points", []))
    except Exception:
        pass
    return None, True, 0


# ──────────────────────────────────────────────
# PRINT HELPERS
# ──────────────────────────────────────────────
CYAN   = "\033[96m"
GREEN  = "\033[92m"
YELLOW = "\033[93m"
RED    = "\033[91m"
BOLD   = "\033[1m"
RESET  = "\033[0m"
CLEAR  = "\033[2J\033[H"


def print_header(scenario: str, tick: int):
    now = datetime.now().strftime("%H:%M:%S")
    print(f"{CLEAR}", end="")
    print(f"{BOLD}{CYAN}╔══════════════════════════════════════════════════════════════╗{RESET}")
    print(f"{BOLD}{CYAN}║   UIP Smart City — INVESTOR DEMO SIMULATOR  🏙️              ║{RESET}")
    print(f"{BOLD}{CYAN}║   {now}  |  Tick #{tick:04d}  |  Scenario: {scenario:<22}  ║{RESET}")
    print(f"{BOLD}{CYAN}╚══════════════════════════════════════════════════════════════╝{RESET}")
    print()
    print(f"  {BOLD}📱 Open browser:  {GREEN}http://localhost:3000{RESET}")
    print()


def print_browser_guide():
    pages = [
        ("/",               "Dashboard",        "KPI cards, overview"),
        ("/city-ops",       "City Ops Map",     "Sensor map + real-time alerts"),
        ("/environment",    "Environment",      "AQI gauges + trend charts"),
        ("/alerts",         "Alerts",           "Live alert feed"),
        ("/bms/devices",    "BMS Devices",      "Device list + commands"),
        ("/buildings",      "Buildings",        "Cross-building analytics"),
        ("/esg",            "ESG Reports",      "Energy, Carbon, PDF export"),
    ]
    print(f"  {BOLD}Browser Pages:{RESET}")
    for path, name, desc in pages:
        print(f"    {YELLOW}http://localhost:3000{path:<22}{RESET}  {name:<22}  {desc}")
    print()


def print_sensor_table(readings: list):
    print(f"  {BOLD}📡 Sensor Readings (live):{RESET}")
    print(f"  {'Sensor':<8}  {'Location':<15}  {'AQI':>6}  {'Status':<28}  {'Alert'}")
    print(f"  {'─'*8}  {'─'*15}  {'─'*6}  {'─'*28}  {'─'*10}")
    for sid, name, aqi, triggered in readings:
        alert_mark = f"{RED}⚠ ALERT{RESET}" if triggered else ""
        color = RED if triggered else (YELLOW if aqi > 100 else GREEN)
        print(f"  {sid:<8}  {name:<15}  {color}{aqi:>6.1f}{RESET}  {aqi_color(aqi):<28}  {alert_mark}")
    print()


def print_stats():
    print(f"  {BOLD}📊 Session Stats:{RESET}")
    print(f"    IoT readings sent:  {GREEN}{_stats['iot_sent']:>5}{RESET}")
    print(f"    Alerts triggered:   {RED}{_stats['alerts_triggered']:>5}{RESET}")
    print(f"    BMS commands sent:  {CYAN}{_stats['bms_events']:>5}{RESET}")
    print(f"    Errors:             {_stats['errors']:>5}")
    print()
    print(f"  {BOLD}🔗 Live API calls:{RESET}")
    print(f"    Auth:      POST /api/v1/auth/login             → JWT RSA-256")
    print(f"    IoT:       POST /api/v1/simulate/iot-sensor    → Kafka → Flink")
    print(f"    Alerts:    GET  /api/v1/alerts                 → real-time feed")
    print(f"    BMS:       POST /api/v1/bms/devices/{{id}}/commands")
    print(f"    Forecast:  GET  /api/v1/forecast/energy        → ARIMA")
    print(f"    ESG PDF:   POST /api/v1/esg/reports/pdf        → GRI 302+305")
    print()


# ──────────────────────────────────────────────
# MAIN SIMULATION LOOP
# ──────────────────────────────────────────────
def main():
    global _scenario_idx, _tick

    print(f"{BOLD}{GREEN}Starting UIP Investor Demo Simulator...{RESET}")
    print(f"  Backend: {BASE_URL}")
    print(f"  Getting auth token...", end="", flush=True)

    tok = get_token()
    if not tok:
        print(f"{RED} FAILED — is backend running?{RESET}")
        return
    print(f"{GREEN} OK{RESET}")
    print()

    # Initial warm-up: send one reading per sensor to establish baseline
    print("  Warm-up: sending initial readings to all sensors...")
    for i, sensor in enumerate(SENSORS):
        aqi = round(random.uniform(45, 95), 1)
        simulate_sensor(sensor, aqi)
        print(f"    {sensor[0]}: AQI={aqi} ✓")
    print()
    print(f"  {BOLD}Ready! Open: {GREEN}http://localhost:3000{RESET}")
    print(f"  {BOLD}Press Ctrl+C to stop.{RESET}")
    time.sleep(2)

    scenario_duration = {
        "normal":       12,   # ticks
        "aqi_warning":  6,
        "aqi_critical": 5,
        "aqi_recovery": 4,
        "multi_spike":  5,
    }

    scenario = SCENARIOS[0]
    ticks_in_scenario = 0
    max_ticks_this = scenario_duration.get(scenario, 10)

    while True:
        try:
            _tick += 1
            ticks_in_scenario += 1

            # Advance scenario
            if ticks_in_scenario > max_ticks_this:
                _scenario_idx = (_scenario_idx + 1) % len(SCENARIOS)
                scenario = SCENARIOS[_scenario_idx]
                max_ticks_this = scenario_duration.get(scenario, 10)
                ticks_in_scenario = 0

            # ── IoT: send all 8 sensors ──
            readings = []
            for i, sensor in enumerate(SENSORS):
                aqi = wave_aqi(i, _tick * 5, scenario)
                simulate_sensor(sensor, aqi)
                # Inject real DB alert via Kafka when AQI exceeds threshold
                alert_severity = ""
                if aqi > 150:
                    alert_severity = inject_alert(sensor[0], sensor[2], aqi)
                    if alert_severity:
                        _stats["alerts_triggered"] += 1
                readings.append((sensor[0], sensor[1], aqi, bool(alert_severity)))

            # ── BMS: send command every 3 ticks ──
            bms_info = None
            if _tick % 3 == 0:
                dev_name, cmd_type, status = send_bms_command()
                bms_info = (dev_name, cmd_type, status)

            # ── Acknowledge old alerts every 10 ticks (keep list fresh) ──
            if _tick % 10 == 0:
                acknowledge_old_alerts()

            # ── Fetch forecast status every 20 ticks ──
            forecast_info = None
            if _tick % 20 == 0:
                model, fallback, pts = fetch_forecast()
                forecast_info = (model, fallback, pts)

            # ── ESG PDF on tick 30 ──
            esg_pdf_info = None
            if _tick == 30:
                ok, size_kb = fetch_esg_pdf()
                esg_pdf_info = (ok, size_kb)

            # ── Render ──
            print_header(scenario, _tick)
            print_browser_guide()
            print_sensor_table(readings)

            # BMS event
            if bms_info:
                dev, cmd, st = bms_info
                print(f"  {BOLD}🔌 BMS Command:{RESET}")
                print(f"    Device: {dev}  →  {cmd}  {st}")
                print()

            # Forecast
            if forecast_info:
                model, fallback, pts = forecast_info
                fb_note = " (waiting 90-day data)" if fallback else ""
                print(f"  {BOLD}📈 Forecast:{RESET}")
                print(f"    Model: {model}{fb_note}  Points: {pts}")
                print()

            # ESG PDF
            if esg_pdf_info:
                ok, size_kb = esg_pdf_info
                if ok:
                    print(f"  {BOLD}📊 ESG PDF:{RESET}  {GREEN}✅ Generated {size_kb:.1f} KB GRI 302+305 report{RESET}")
                    print()

            # Scenario narration
            narration = {
                "normal":       f"{GREEN}All sensors NORMAL — AQI 50–100{RESET}",
                "aqi_warning":  f"{YELLOW}⚠ ENV-001 Bến Nghé: AQI spike → WARNING alert triggered{RESET}",
                "aqi_critical": f"{RED}🚨 ENV-003 Bình Thạnh: CRITICAL — AQI > 200 — Workflow started{RESET}",
                "aqi_recovery": f"{GREEN}✅ AQI recovering — system auto-resolved after operator ACK{RESET}",
                "multi_spike":  f"{RED}🚨🚨🚨 MULTI-ZONE: 3 sensors exceed threshold simultaneously{RESET}",
            }.get(scenario, "")
            if narration:
                print(f"  {BOLD}🎬 Scenario:{RESET}  {narration}")
                print()

            print_stats()

            # Presenter tip
            tips = [
                "Show /city-ops — sensor map updates when alerts fire",
                "Show /alerts — new CRITICAL/WARNING entries appear live",
                "Show /bms/devices — send a command, watch status change",
                "Show /environment — AQI gauge color changes with spikes",
                "Show /esg — click 'Export PDF' for GRI report download",
                "Show /buildings — cross-building energy comparison charts",
                "Show /city-ops — kill a Kafka broker while map stays live",
            ]
            tip = tips[_tick % len(tips)]
            print(f"  {BOLD}💡 Presenter tip:{RESET}  {YELLOW}{tip}{RESET}")

            time.sleep(5)  # 5-second interval = realistic IoT cadence

        except KeyboardInterrupt:
            print(f"\n\n  {BOLD}{GREEN}Demo Simulator stopped.{RESET}")
            print(f"  Final stats: {_stats}")
            break
        except Exception as e:
            _stats["errors"] += 1
            time.sleep(3)


if __name__ == "__main__":
    main()
