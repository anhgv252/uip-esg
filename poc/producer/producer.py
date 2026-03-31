"""
ESG Telemetry Producer  –  POC Load Generator
==============================================

Sends TOTAL_MESSAGES messages to Kafka topic 'raw_telemetry'.
  • VALID_RATIO %  – correctly formed records   (default 70%)
  • Remaining  %  – broken across 7 error categories

Error distribution (of the 30% invalid):
  MISSING_METER_ID       20%
  MISSING_VALUE          20%
  OUT_OF_RANGE_NEGATIVE  15%
  OUT_OF_RANGE_HIGH      15%
  INVALID_VALUE_FORMAT   15%
  MISSING_UNIT           10%
  MISSING_TIMESTAMP       5%
"""

import os
import json
import random
import time
import logging
from datetime import datetime, timedelta, timezone
from typing import Optional
from confluent_kafka import Producer
from confluent_kafka.admin import AdminClient

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("esg-producer")

# ── Config ────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
TOTAL_MESSAGES  = int(os.getenv("TOTAL_MESSAGES", "100000"))
VALID_RATIO     = float(os.getenv("VALID_RATIO", "0.70"))
BATCH_SIZE      = int(os.getenv("BATCH_SIZE", "500"))
TOPIC           = "raw_telemetry"

# ── Domain data ───────────────────────────────────────────────────
SITES = {
    "site-a": {"buildings": ["bldg-a1", "bldg-a2"], "floors": 5,  "zones_per_floor": 4},
    "site-b": {"buildings": ["bldg-b1", "bldg-b2", "bldg-b3"], "floors": 8, "zones_per_floor": 6},
    "site-c": {"buildings": ["bldg-c1"], "floors": 12, "zones_per_floor": 8},
}

METERS: list[dict] = []
for site_id, info in SITES.items():
    for building_id in info["buildings"]:
        for floor_num in range(1, info["floors"] + 1):
            for zone_num in range(1, info["zones_per_floor"] + 1):
                for suffix in ["elec", "water", "env"]:
                    METERS.append({
                        "meter_id":    f"{building_id}-F{floor_num:02d}-Z{zone_num:02d}-{suffix}",
                        "site_id":     site_id,
                        "building_id": building_id,
                        "floor_id":    f"floor-{floor_num:02d}",
                        "zone_id":     f"zone-{floor_num:02d}-{zone_num:02d}",
                    })

MEASURE_TYPES = {
    "electric_kwh": {"unit": "kWh", "min": 0.1,  "max": 450.0},
    "water_m3":     {"unit": "m³",  "min": 0.01, "max": 80.0},
    "temp_celsius": {"unit": "°C",  "min": 16.0, "max": 32.0},
    "co2_ppm":      {"unit": "ppm", "min": 350,  "max": 1500},
    "humidity_pct": {"unit": "%",   "min": 30,   "max": 80},
}

ERROR_SCENARIOS = [
    ("missing_meter_id",      0.20),
    ("missing_value",         0.20),
    ("out_of_range_negative", 0.15),
    ("out_of_range_high",     0.15),
    ("invalid_value_format",  0.15),
    ("missing_unit",          0.10),
    ("missing_timestamp",     0.05),
]


def _base_message(meter: dict, ts: datetime) -> dict:
    measure_type = random.choice(list(MEASURE_TYPES.keys()))
    meta = MEASURE_TYPES[measure_type]
    value = round(random.uniform(meta["min"], meta["max"]), 3)
    return {
        "deviceId":     meter["meter_id"],
        "siteCode":     meter["site_id"],
        "buildingCode": meter["building_id"],
        "floorCode":    meter["floor_id"],
        "zoneCode":     meter["zone_id"],
        "ts":           ts.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "type":         measure_type,
        "v":            str(value),
        "u":            meta["unit"],
    }


def make_valid_message(meter: dict, ts: datetime) -> dict:
    return _base_message(meter, ts)


def make_invalid_message(meter: dict, ts: datetime) -> tuple[dict, str]:
    scenario_names, scenario_weights = zip(*ERROR_SCENARIOS)
    scenario = random.choices(scenario_names, weights=scenario_weights, k=1)[0]
    msg = _base_message(meter, ts)

    if scenario == "missing_meter_id":
        msg["deviceId"] = None
    elif scenario == "missing_value":
        msg["v"] = None
    elif scenario == "out_of_range_negative":
        msg["v"] = str(round(random.uniform(-999.0, -0.1), 3))
    elif scenario == "out_of_range_high":
        msg["v"] = str(round(random.uniform(10001.0, 99999.0), 3))
    elif scenario == "invalid_value_format":
        msg["v"] = random.choice(["N/A", "ERR", "--", "null", "undefined", "nan"])
    elif scenario == "missing_unit":
        msg["u"] = None
    elif scenario == "missing_timestamp":
        msg["ts"] = None

    return msg, scenario


def wait_for_kafka(bootstrap: str, retries: int = 20, delay: float = 3.0) -> bool:
    admin = AdminClient({"bootstrap.servers": bootstrap})
    for attempt in range(1, retries + 1):
        try:
            meta = admin.list_topics(timeout=5)
            log.info(f"✅ Kafka ready (attempt {attempt}). Topics: {list(meta.topics.keys())}")
            return True
        except Exception as exc:
            log.warning(f"⏳ Waiting for Kafka ({attempt}/{retries}): {exc}")
            time.sleep(delay)
    return False


def delivery_report(err, msg) -> None:
    if err is not None:
        log.error(f"❌ Delivery failed – {err}")


def main() -> None:
    log.info("═" * 60)
    log.info("  UIP ESG Producer  –  POC Load Generator")
    log.info(f"  Broker  : {KAFKA_BOOTSTRAP}")
    log.info(f"  Topic   : {TOPIC}")
    log.info(f"  Messages: {TOTAL_MESSAGES:,}")
    log.info(f"  Valid   : {VALID_RATIO*100:.0f}%  |  Invalid: {(1-VALID_RATIO)*100:.0f}%")
    log.info(f"  Meters  : {len(METERS):,}")
    log.info("═" * 60)

    if not wait_for_kafka(KAFKA_BOOTSTRAP):
        log.error("❌ Could not connect to Kafka – aborting.")
        raise SystemExit(1)

    producer = Producer({
        "bootstrap.servers": KAFKA_BOOTSTRAP,
        "linger.ms": 20,
        "batch.size": 65536,
        "compression.type": "snappy",
        "acks": "1",
    })

    n_valid = 0
    n_invalid = 0
    invalid_by_type: dict[str, int] = {s: 0 for s, _ in ERROR_SCENARIOS}
    errors_send = 0
    start_time = time.time()
    base_ts = datetime.now(timezone.utc) - timedelta(hours=2)

    for i in range(TOTAL_MESSAGES):
        meter = random.choice(METERS)
        ts    = base_ts + timedelta(seconds=i * 0.072)

        if random.random() < VALID_RATIO:
            msg = make_valid_message(meter, ts)
            key = meter["meter_id"]
            n_valid += 1
        else:
            msg, scenario = make_invalid_message(meter, ts)
            key = msg.get("deviceId") or "no-meter"
            n_invalid += 1
            invalid_by_type[scenario] += 1

        try:
            producer.produce(
                topic=TOPIC,
                key=key.encode() if key else b"",
                value=json.dumps(msg).encode(),
                on_delivery=delivery_report,
            )
        except BufferError:
            producer.poll(0.1)
            errors_send += 1

        if (i + 1) % BATCH_SIZE == 0:
            producer.flush(timeout=10)
            elapsed = time.time() - start_time
            rate    = (i + 1) / elapsed
            log.info(
                f"  ✉  {i+1:>7,}/{TOTAL_MESSAGES:,}  "
                f"valid={n_valid:,}  invalid={n_invalid:,}  "
                f"rate={rate:,.0f} msg/s"
            )

    producer.flush(timeout=30)
    elapsed = time.time() - start_time

    log.info("")
    log.info("═" * 60)
    log.info("  PRODUCER SUMMARY")
    log.info("═" * 60)
    log.info(f"  Total sent    : {TOTAL_MESSAGES:,}")
    log.info(f"  Valid         : {n_valid:,}  ({n_valid/TOTAL_MESSAGES*100:.1f}%)")
    log.info(f"  Invalid       : {n_invalid:,}  ({n_invalid/TOTAL_MESSAGES*100:.1f}%)")
    log.info(f"  Send errors   : {errors_send}")
    log.info(f"  Elapsed       : {elapsed:.1f} s  ({TOTAL_MESSAGES/elapsed:,.0f} msg/s)")
    log.info("  ── Invalid breakdown ──────────────────────────────")
    for scenario, count in invalid_by_type.items():
        if count > 0:
            log.info(f"    {scenario:<28} {count:>7,}")
    log.info("═" * 60)
    log.info("✅ Producer finished.")


if __name__ == "__main__":
    time.sleep(5)  # ensure topics are ready
    main()
