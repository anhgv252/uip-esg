#!/usr/bin/env python3
"""
UIP Sensor Simulator
====================
Giả lập dữ liệu AQI từ 8 sensor stations:
- Publish NGSI-LD messages lên Kafka topic `ngsi_ld_environment`
- Flink (EnvironmentFlinkJob) tiêu thụ topic và INSERT vào DB
- DB trigger `trg_sensor_last_seen` tự động UPDATE sensors.last_seen_at

Flow: Simulator → Kafka → Flink → DB (sensor_readings) → trigger → sensors.last_seen_at

Usage:
  python3 sensor_simulator.py             # chạy liên tục, interval 30s
  python3 sensor_simulator.py --once      # chạy 1 lần rồi thoát
  python3 sensor_simulator.py --interval 10  # interval 10 giây
"""
import argparse
import json
import random
import time
import sys
from datetime import datetime, timezone

# ── Optional Kafka dependency ─────────────────────────────────────────────────
try:
    from kafka import KafkaProducer
    KAFKA_AVAILABLE = True
except ImportError:
    KAFKA_AVAILABLE = False
    print("[WARN] kafka-python not installed. Install with: pip3 install kafka-python")
    print("[INFO] Continuing with DB-only mode...\n")

# ── Config ────────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP = "localhost:29092"
KAFKA_TOPIC     = "ngsi_ld_environment"

# 8 sensors mirroring DB data
SENSORS = [
    {"id": "ENV-001", "name": "Bến Nghé AQI Station",   "district": "D1",  "lat": 10.7769, "lon": 106.7009},
    {"id": "ENV-002", "name": "Tân Bình AQI Station",   "district": "TB",  "lat": 10.8011, "lon": 106.6526},
    {"id": "ENV-003", "name": "Bình Thạnh AQI Station", "district": "BT",  "lat": 10.8120, "lon": 106.7127},
    {"id": "ENV-004", "name": "Gò Vấp AQI Station",     "district": "GV",  "lat": 10.8382, "lon": 106.6639},
    {"id": "ENV-005", "name": "District 7 AQI Station", "district": "D7",  "lat": 10.7347, "lon": 106.7218},
    {"id": "ENV-006", "name": "Thủ Đức AQI Station",    "district": "TD",  "lat": 10.8580, "lon": 106.7619},
    {"id": "ENV-007", "name": "Hóc Môn AQI Station",    "district": "HM",  "lat": 10.8913, "lon": 106.5946},
    {"id": "ENV-008", "name": "Bình Chánh AQI Station", "district": "BCh", "lat": 10.6923, "lon": 106.5734},
]

# AQI baseline ranges per district (simulated diurnal variation)
BASELINES = {
    "D1":  {"pm25": 35, "pm10": 55, "o3": 65, "no2": 40, "so2": 8,  "co": 0.8},
    "TB":  {"pm25": 30, "pm10": 50, "o3": 60, "no2": 35, "so2": 7,  "co": 0.7},
    "BT":  {"pm25": 28, "pm10": 45, "o3": 58, "no2": 32, "so2": 6,  "co": 0.6},
    "GV":  {"pm25": 32, "pm10": 52, "o3": 62, "no2": 37, "so2": 8,  "co": 0.75},
    "D7":  {"pm25": 25, "pm10": 42, "o3": 55, "no2": 28, "so2": 5,  "co": 0.55},
    "TD":  {"pm25": 22, "pm10": 38, "o3": 50, "no2": 25, "so2": 4,  "co": 0.5},
    "HM":  {"pm25": 20, "pm10": 35, "o3": 48, "no2": 22, "so2": 3,  "co": 0.45},
    "BCh": {"pm25": 18, "pm10": 32, "o3": 45, "no2": 20, "so2": 3,  "co": 0.4},
}

def jitter(value, pct=0.15):
    """Add ±pct random variation to simulate sensor noise."""
    return round(value * (1 + random.uniform(-pct, pct)), 2)

def calc_aqi_us(pm25, pm10, o3, no2, so2, co):
    """Simple AQI calculation (US EPA breakpoints, approximate)."""
    # PM2.5 sub-index
    if   pm25 < 12:   aqi = pm25 / 12 * 50
    elif pm25 < 35.4: aqi = 50  + (pm25 - 12)   / 23.4  * 50
    elif pm25 < 55.4: aqi = 100 + (pm25 - 35.4) / 19.9  * 50
    elif pm25 < 150:  aqi = 150 + (pm25 - 55.4) / 94.4  * 100
    else:             aqi = 200 + (pm25 - 150)   / 100   * 100
    return max(0, min(500, int(aqi)))

def generate_reading(sensor):
    """Generate a realistic AQI reading for a sensor."""
    base = BASELINES.get(sensor["district"], BASELINES["D1"])
    now  = datetime.now(timezone.utc)

    pm25 = jitter(base["pm25"])
    pm10 = jitter(base["pm10"])
    o3   = jitter(base["o3"])
    no2  = jitter(base["no2"])
    so2  = jitter(base["so2"])
    co   = jitter(base["co"])
    aqi  = calc_aqi_us(pm25, pm10, o3, no2, so2, co)
    temp = round(random.uniform(28.5, 35.5), 1)
    hum  = round(random.uniform(55.0, 80.0), 1)

    return {
        "sensor_id":   sensor["id"],
        "timestamp":   now.isoformat(),
        "aqi":         aqi,
        "pm25":        pm25,
        "pm10":        pm10,
        "o3":          o3,
        "no2":         no2,
        "so2":         so2,
        "co":          co,
        "temperature": temp,
        "humidity":    hum,
    }

def build_ngsi_ld(reading, sensor):
    """
    Build NGSI-LD envelope matching the format produced by Redpanda Connect pipeline.
    NgsiLdMessage (Flink) expects:
      - deviceId.value: sensor ID string
      - observedAt.value: epoch millis (Long)
      - sensorType.value: "environment"
      - measurements.value: flat Map<String, Double>
    """
    ts_ms = int(datetime.fromisoformat(reading["timestamp"]).timestamp() * 1000)
    return {
        "@context": ["https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"],
        "id": f"urn:ngsi-ld:Device:{reading['sensor_id']}",
        "type": "Device",
        "deviceId":    {"type": "Property", "value": reading["sensor_id"]},
        "observedAt":  {"type": "Property", "value": ts_ms},
        "sensorType":  {"type": "Property", "value": "environment"},
        "measurements": {
            "type": "Property",
            "value": {
                "aqi":         reading["aqi"],
                "pm25":        reading["pm25"],
                "pm10":        reading["pm10"],
                "o3":          reading["o3"],
                "no2":         reading["no2"],
                "so2":         reading["so2"],
                "co":          reading["co"],
                "temperature": reading["temperature"],
                "humidity":    reading["humidity"],
            }
        },
        "_meta": {
            "source":     "sensor-simulator",
            "sensorType": "environment",
        },
    }

def send_to_kafka(producer, readings):
    """Publish NGSI-LD messages to Kafka."""
    sent = 0
    for reading, sensor in readings:
        msg = build_ngsi_ld(reading, sensor)
        producer.send(KAFKA_TOPIC, value=msg, key=reading["sensor_id"].encode())
        sent += 1
    producer.flush()
    print(f"  [Kafka] Published {sent} messages → topic={KAFKA_TOPIC}")

def run_once(producer):
    now_str = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    print(f"\n[{now_str}] Simulating {len(SENSORS)} sensors...")

    readings = [(generate_reading(s), s) for s in SENSORS]

    # Print summary
    for r, s in readings:
        print(f"  {r['sensor_id']} ({s['district']:3s}) AQI={r['aqi']:3d}  PM2.5={r['pm25']:.1f}  T={r['temperature']}°C")

    if producer:
        send_to_kafka(producer, readings)
    else:
        print("  [WARN] Kafka not available — data generated but not sent.")

def main():
    parser = argparse.ArgumentParser(description="UIP Sensor Simulator")
    parser.add_argument("--once",     action="store_true", help="Run once and exit")
    parser.add_argument("--interval", type=int, default=30, help="Push interval in seconds (default: 30)")
    args = parser.parse_args()

    # ── Connect Kafka ─────────────────────────────────────────────────────────
    producer = None
    if KAFKA_AVAILABLE:
        try:
            producer = KafkaProducer(
                bootstrap_servers=[KAFKA_BOOTSTRAP],
                value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                acks="all",
                request_timeout_ms=5000,
            )
            print(f"[OK] Kafka connected → {KAFKA_BOOTSTRAP}")
        except Exception as e:
            print(f"[WARN] Kafka connection failed: {e}")
            producer = None

    if not producer:
        print("[ERROR] Kafka not available. Install kafka-python and ensure Kafka is running.")
        sys.exit(1)

    print(f"\nSimulator starting — interval={args.interval}s, sensors={len(SENSORS)}")
    print(f"Flow: Simulator → Kafka({KAFKA_TOPIC}) → Flink → DB → trigger → sensors.last_seen_at")
    print("Press Ctrl+C to stop.\n")

    try:
        if args.once:
            run_once(producer)
        else:
            while True:
                run_once(producer)
                time.sleep(args.interval)
    except KeyboardInterrupt:
        print("\n[INFO] Simulator stopped.")
    finally:
        if producer:
            producer.close()

if __name__ == "__main__":
    main()
