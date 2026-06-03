#!/usr/bin/env python3
"""
BMS Modbus TCP Slave Simulator — Sprint 8 (S8-QA03)

Simulates a building management system device with realistic sensor data.
Runs as a Modbus TCP server on port 5020 (non-privileged alternative to 502).

Register map (Input Registers — Function Code 4):
  0  — Temperature    (°C × 10, e.g. 245 = 24.5°C)
  1  — Humidity       (% × 10,  e.g. 650 = 65.0%)
  2  — Energy (kWh)   (integer kWh)
  3  — CO2 (ppm)      (integer ppm)
  4  — Occupancy      (0 or 1)
  5  — AQI            (integer)
  6  — Water (L/h)    (integer)
  7  — Vibration (mg) (mg × 100, e.g. 350 = 3.50 mg)

Holding Registers (FC3) — control setpoints:
  0  — HVAC setpoint  (°C × 10)
  1  — Lighting level (0-100%)

Usage:
  pip install pymodbus==3.6.4
  python3 modbus-slave.py [--port 5020] [--scenario normal|alarm|degraded]

Scenarios:
  normal    — stable building readings (default)
  alarm     — CO2 spike + high temperature → triggers alerts
  degraded  — intermittent NaN registers → tests error handling
"""

import argparse
import logging
import random
import math
import signal
import sys
import time
import threading

try:
    from pymodbus.server import StartTcpServer
    from pymodbus.datastore import ModbusSequentialDataBlock, ModbusSlaveContext, ModbusServerContext
    from pymodbus.device import ModbusDeviceIdentification
except ImportError:
    print("[ERROR] pymodbus not installed. Run: pip install pymodbus==3.6.4")
    sys.exit(1)

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [BMS-SIM] %(levelname)s %(message)s',
    datefmt='%H:%M:%S'
)
log = logging.getLogger(__name__)

# ─── Sensor value generators ──────────────────────────────────────────────────

def _normal_readings(t: float) -> list[int]:
    """Realistic building sensor values with sinusoidal variation."""
    temp   = int((22.0 + 2.5 * math.sin(t / 60)) * 10)   # 19.5–24.5°C
    humid  = int((62.0 + 5.0 * math.sin(t / 90)) * 10)   # 57–67%
    energy = int(120 + 30 * math.sin(t / 120))             # 90–150 kWh
    co2    = int(550 + 100 * math.sin(t / 45))             # 450–650 ppm
    occup  = 1 if 8 * 3600 < (t % 86400) < 18 * 3600 else 0
    aqi    = int(35 + 10 * math.sin(t / 80))               # 25–45 (Good)
    water  = int(45 + 10 * random.uniform(-1, 1))           # ~45 L/h
    vib    = int(random.gauss(150, 20))                     # ~1.50 mg (normal)
    return [temp, humid, energy, co2, occup, aqi, water, vib]

def _alarm_readings(t: float) -> list[int]:
    """CO2 spike + high temp — should trigger Kafka alerts."""
    temp   = int(32.5 * 10)    # 32.5°C — above threshold
    humid  = int(78.0 * 10)    # 78%
    energy = int(210)
    co2    = int(1200)         # 1200 ppm — CRITICAL threshold
    occup  = 1
    aqi    = int(155)          # Unhealthy
    water  = int(80)
    vib    = int(850)          # 8.50 mg — vibration anomaly
    return [temp, humid, energy, co2, occup, aqi, water, vib]

def _degraded_readings(t: float) -> list[int]:
    """Intermittent failures — some registers return 0xFFFF (65535 = sensor fault)."""
    base = _normal_readings(t)
    # Every 5s, corrupt one register to simulate sensor fault
    if int(t) % 5 == 0:
        fault_idx = int(t / 5) % len(base)
        base[fault_idx] = 65535  # 0xFFFF = FAULT
    return base

SCENARIO_GENERATORS = {
    'normal':   _normal_readings,
    'alarm':    _alarm_readings,
    'degraded': _degraded_readings,
}

# ─── Register updater thread ──────────────────────────────────────────────────

class RegisterUpdater(threading.Thread):
    def __init__(self, context: ModbusServerContext, scenario: str, interval: float = 1.0):
        super().__init__(daemon=True)
        self.context   = context
        self.generator = SCENARIO_GENERATORS[scenario]
        self.interval  = interval
        self._stop     = threading.Event()

    def stop(self):
        self._stop.set()

    def run(self):
        start = time.monotonic()
        while not self._stop.wait(self.interval):
            t = time.monotonic() - start
            values = self.generator(t)
            # Slave ID 1 — input registers (FC4, address 0x04)
            self.context[0x00].setValues(4, 0, values)
            log.debug("Registers updated: %s", values)

# ─── Server identity ──────────────────────────────────────────────────────────

def build_identity() -> ModbusDeviceIdentification:
    ident = ModbusDeviceIdentification()
    ident.VendorName          = 'UIP SmartCity'
    ident.ProductCode         = 'BMS-SIM-001'
    ident.VendorUrl           = 'https://uip.smartcity.vn'
    ident.ProductName         = 'BMS Hardware Simulator'
    ident.ModelName           = 'Sprint 8 S8-QA03'
    ident.MajorMinorRevision  = '1.0.0'
    return ident

# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description='BMS Modbus TCP Slave Simulator')
    parser.add_argument('--port',     type=int, default=5020,    help='TCP port (default: 5020)')
    parser.add_argument('--host',     type=str, default='0.0.0.0', help='Bind address')
    parser.add_argument('--scenario', type=str, default='normal',
                        choices=['normal', 'alarm', 'degraded'],
                        help='Sensor scenario (default: normal)')
    parser.add_argument('--interval', type=float, default=1.0,  help='Update interval seconds')
    args = parser.parse_args()

    log.info("Starting BMS Modbus Slave: %s:%d scenario=%s", args.host, args.port, args.scenario)

    # 8 input registers (FC4) + 2 holding registers (FC3)
    store = ModbusSlaveContext(
        di=ModbusSequentialDataBlock(0, [0] * 10),   # discrete inputs
        co=ModbusSequentialDataBlock(0, [0] * 10),   # coils
        hr=ModbusSequentialDataBlock(0, [250, 50]),  # holding: HVAC=25.0°C, lighting=50%
        ir=ModbusSequentialDataBlock(0, [0] * 8),    # input registers (sensor readings)
    )
    context = ModbusServerContext(slaves=store, single=True)

    updater = RegisterUpdater(context, args.scenario, args.interval)
    updater.start()

    def _shutdown(sig, frame):
        log.info("Shutting down BMS simulator...")
        updater.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT,  _shutdown)
    signal.signal(signal.SIGTERM, _shutdown)

    log.info("BMS Simulator ready — connect ModbusTCPMaster to %s:%d unit_id=1", args.host, args.port)
    log.info("Register map: 0=temp(x10) 1=humid(x10) 2=energy(kWh) 3=co2(ppm) 4=occupancy 5=aqi 6=water(L/h) 7=vib(mgx100)")
    log.info("Scenario: %s | Press Ctrl+C to stop", args.scenario)

    StartTcpServer(context=context, identity=build_identity(), address=(args.host, args.port))


if __name__ == '__main__':
    main()
