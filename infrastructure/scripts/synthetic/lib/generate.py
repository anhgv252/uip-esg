#!/usr/bin/env python3
"""
UIP synthetic 50-tenant test — test-data generator (R16 mitigation, T12 scaffold).

Produces realistic NGSI-LD sensor-event payloads for a fleet of synthetic
tenants. Each tenant owns N sensors (default 100) that periodically emit
environment readings identical in shape to those produced by
`scripts/sensor_simulator.py` and consumed by the Flink `EnvironmentFlinkJob`
on topic `ngsi_ld_environment`.

The generator exercises the REAL tenant-isolation code path:
    - NGSI-LD envelope with `_meta.tenantId` (parsed by `NgsiLdDeserializer`
      into `NgsiLdMessage.getTenantId()`)
    - `TenantBindingProcessFunction` fails-closed on null/blank tenant →
      dropped records surface in the `uip.tenant.dropped_no_tenant` metric
    - `RowPolicyEngine.executeWithTenant(tenantId, ...)` SETs `SQL_tenant_id`
      per ClickHouse connection (V32 RowPolicy, ADR-047)

Two sinks are supported (select via CLI flag):
    --sink kafka    Publish to `ngsi_ld_environment` (DEFAULT — exercises full
                    Simulator → Kafka → Flink → DB pipeline; needs Kafka up).
    --sink api      POST to analytics-service REST endpoints (port 8081 by
                    default). Used by the runner's isolation invariants.
    --sink stdout   Dry-run — emit payloads to stdout only (no infra needed;
                    use to validate generator correctness / payload shape).

Output of `--sink stdout` is JSON-per-line (one event per line) so it can be
piped to `jq`, replayed via `kafka-console-producer`, or fed into the runner.

Usage:
    # Dry-run — verify payload shape, no infra required
    python3 -m generate --tenants 3 --sensors-per-tenant 5 --rate 10 \\
        --sink stdout --events-per-sensor 1

    # Publish 50 tenants × 100 sensors × 1 event each to Kafka
    python3 -m generate --tenants 50 --sensors-per-tenant 100 \\
        --events-per-sensor 1 --sink kafka \\
        --kafka-bootstrap localhost:29092 --kafka-topic ngsi_ld_environment
"""
from __future__ import annotations

import argparse
import json
import random
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Iterable, Iterator

# Kafka is optional — only needed for --sink kafka
try:
    from kafka import KafkaProducer  # type: ignore
    _KAFKA_OK = True
except ImportError:  # pragma: no cover
    _KAFKA_OK = False


# ─── Reference data (mirrors scripts/sensor_simulator.py baselines) ──────────
_DISTRICTS = ["D1", "TB", "BT", "GV", "D7", "TD", "HM", "BCh"]
_BASELINES = {
    "D1":  {"pm25": 35, "pm10": 55, "o3": 65, "no2": 40, "so2": 8,  "co": 0.8},
    "TB":  {"pm25": 30, "pm10": 50, "o3": 60, "no2": 35, "so2": 7,  "co": 0.7},
    "BT":  {"pm25": 28, "pm10": 45, "o3": 58, "no2": 32, "so2": 6,  "co": 0.6},
    "GV":  {"pm25": 32, "pm10": 52, "o3": 62, "no2": 37, "so2": 8,  "co": 0.75},
    "D7":  {"pm25": 25, "pm10": 42, "o3": 55, "no2": 28, "so2": 5,  "co": 0.55},
    "TD":  {"pm25": 22, "pm10": 38, "o3": 50, "no2": 25, "so2": 4,  "co": 0.5},
    "HM":  {"pm25": 20, "pm10": 35, "o3": 48, "no2": 22, "so2": 3,  "co": 0.45},
    "BCh": {"pm25": 18, "pm10": 32, "o3": 45, "no2": 20, "so2": 3,  "co": 0.4},
}


def _jitter(value: float, pct: float = 0.15) -> float:
    return round(value * (1 + random.uniform(-pct, pct)), 2)


def _calc_aqi_us(pm25: float, pm10: float, o3: float, no2: float,
                 so2: float, co: float) -> int:
    """US EPA sub-index approximation (mirrors scripts/sensor_simulator.py)."""
    if pm25 < 12:
        aqi = pm25 / 12 * 50
    elif pm25 < 35.4:
        aqi = 50 + (pm25 - 12) / 23.4 * 50
    elif pm25 < 55.4:
        aqi = 100 + (pm25 - 35.4) / 19.9 * 50
    elif pm25 < 150:
        aqi = 150 + (pm25 - 55.4) / 94.4 * 100
    else:
        aqi = 200 + (pm25 - 150) / 100 * 100
    return max(0, min(500, int(aqi)))


# ─── Sensor fleet model ──────────────────────────────────────────────────────
@dataclass
class Sensor:
    """A synthetic sensor owned by a tenant. Stable across the run."""
    tenant_id: str
    sensor_id: str
    district: str
    building_id: str
    lat: float
    lon: float

    @property
    def baseline(self) -> dict[str, float]:
        return _BASELINES[self.district]


def build_fleet(tenants: int, sensors_per_tenant: int,
                seed: int = 0) -> list[Sensor]:
    """Deterministically build a fleet of `tenants × sensors_per_tenant` sensors.

    Deterministic given the seed so two runs against the same stack produce
    the same sensor_ids (letting the runner assert that sensors persisted by
    tenant A are NOT visible to tenant B).
    """
    rng = random.Random(seed)
    fleet: list[Sensor] = []
    for t in range(1, tenants + 1):
        tenant_id = f"tenant-{t:03d}"
        # Distribute sensors across districts + buildings within the tenant
        for s in range(1, sensors_per_tenant + 1):
            district = _DISTRICTS[(s - 1) % len(_DISTRICTS)]
            building_id = f"{tenant_id}-BLDG-{(s - 1) // 10 + 1:03d}"
            lat = round(10.6 + rng.uniform(-0.1, 0.3), 6)
            lon = round(106.5 + rng.uniform(-0.1, 0.3), 6)
            fleet.append(Sensor(
                tenant_id=tenant_id,
                sensor_id=f"{tenant_id}-ENV-{s:03d}",
                district=district,
                building_id=building_id,
                lat=lat,
                lon=lon,
            ))
    return fleet


# ─── NGSI-LD payload builder ─────────────────────────────────────────────────
def build_ngsi_ld(sensor: Sensor, observed_at_ms: int | None = None) -> dict:
    """Build an NGSI-LD envelope matching `NgsiLdMessage` (Flink deserializer).

    The `_meta.tenantId` field is what `NgsiLdMessage.getTenantId()` reads —
    this is the contract enforced by `TenantBindingProcessFunction` and
    `TenantKeyedProcessFunctionDelegate` (ADR-047 §1.3).
    """
    b = sensor.baseline
    pm25, pm10 = _jitter(b["pm25"]), _jitter(b["pm10"])
    o3, no2 = _jitter(b["o3"]), _jitter(b["no2"])
    so2, co = _jitter(b["so2"]), _jitter(b["co"])
    aqi = _calc_aqi_us(pm25, pm10, o3, no2, so2, co)
    now_ms = observed_at_ms if observed_at_ms is not None else int(
        datetime.now(timezone.utc).timestamp() * 1000)

    return {
        "@context": ["https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"],
        "id": f"urn:ngsi-ld:Device:{sensor.sensor_id}",
        "type": "Device",
        "deviceId":   {"type": "Property", "value": sensor.sensor_id},
        "observedAt": {"type": "Property", "value": now_ms},
        "sensorType": {"type": "Property", "value": "environment"},
        "measurements": {"type": "Property", "value": {
            "aqi": float(aqi),
            "pm25": pm25, "pm10": pm10,
            "o3": o3, "no2": no2, "so2": so2, "co": co,
            "temperature": round(random.uniform(28.5, 35.5), 1),
            "humidity": round(random.uniform(55.0, 80.0), 1),
        }},
        # Critical tenant-isolation field — parsed by NgsiLdMessage.Meta.tenantId.
        # TenantBindingProcessFunction fail-closed drops records where this is
        # null/blank; the uip.tenant.dropped_no_tenant counter must stay flat.
        "_meta": {
            "source": "synthetic-harness",
            "sensorType": "environment",
            "tenantId": sensor.tenant_id,
            "buildingName": sensor.building_id,
            "district": sensor.district,
            "category": "air_quality",
        },
    }


def event_stream(fleet: list[Sensor], events_per_sensor: int,
                 rate_rps: int) -> Iterator[tuple[Sensor, dict]]:
    """Yield (sensor, event) pairs at the requested aggregate rate.

    `rate_rps` is the TOTAL rate across the whole fleet — individual sensors
    emit at rate_rps / len(fleet). Spacing is computed up-front so the runner
    can pre-allocate, and the loop sleeps to hit the target rate.
    """
    total = len(fleet) * events_per_sensor
    interval = 1.0 / rate_rps if rate_rps > 0 else 0.0
    for ev_idx in range(events_per_sensor):
        for sensor in fleet:
            yield sensor, build_ngsi_ld(sensor)
            # rate-limit only between events, not after the last one
            total -= 1
            if total > 0 and interval > 0:
                time.sleep(interval)


# ─── Sink implementations ────────────────────────────────────────────────────
def _stdout_sink(events: Iterable[tuple[Sensor, dict]]) -> int:
    count = 0
    try:
        for _, event in events:
            sys.stdout.write(json.dumps(event) + "\n")
            sys.stdout.flush()
            count += 1
    except BrokenPipeError:
        # Downstream pipe closed (e.g. `| head -N`) — stop emitting cleanly.
        try:
            sys.stdout.close()
        except Exception:
            pass
    return count


def _kafka_sink(events: Iterable[tuple[Sensor, dict]],
                bootstrap: str, topic: str) -> int:
    if not _KAFKA_OK:
        raise RuntimeError(
            "kafka-python not installed — run `pip install kafka-python` "
            "or use --sink stdout / --sink api")
    producer = KafkaProducer(
        bootstrap_servers=bootstrap.split(","),
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        acks="all",
        request_timeout_ms=5000,
        retries=3,
    )
    count = 0
    try:
        for sensor, event in events:
            # Partition key = sensor_id so per-sensor order is preserved.
            producer.send(topic, key=sensor.sensor_id.encode("utf-8"),
                          value=event)
            count += 1
        producer.flush(timeout=30)
    finally:
        producer.close()
    return count


# ─── CLI ─────────────────────────────────────────────────────────────────────
def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--tenants", type=int, default=50,
                   help="Number of synthetic tenants (default 50)")
    p.add_argument("--sensors-per-tenant", type=int, default=100,
                   help="Sensors per tenant (default 100)")
    p.add_argument("--events-per-sensor", type=int, default=1,
                   help="Events to emit per sensor (default 1)")
    p.add_argument("--rate", type=int, default=100,
                   help="Aggregate emission rate in events/sec (default 100)")
    p.add_argument("--seed", type=int, default=0,
                   help="RNG seed for deterministic fleet (default 0)")
    p.add_argument("--sink", choices=["stdout", "kafka", "api"],
                   default="stdout",
                   help="Where to send events (default stdout)")
    p.add_argument("--kafka-bootstrap",
                   default=os_getenv("KAFKA_BOOTSTRAP", "localhost:29092"))
    p.add_argument("--kafka-topic",
                   default=os_getenv("KAFKA_TOPIC", "ngsi_ld_environment"))
    args = p.parse_args(argv)

    fleet = build_fleet(args.tenants, args.sensors_per_tenant, seed=args.seed)
    print(f"[generate] fleet: {len(fleet)} sensors across "
          f"{args.tenants} tenants ({args.sensors_per_tenant}/tenant)",
          file=sys.stderr)
    print(f"[generate] target rate: {args.rate} ev/s, "
          f"events_per_sensor={args.events_per_sensor}", file=sys.stderr)

    events = event_stream(fleet, args.events_per_sensor, args.rate)

    if args.sink == "stdout":
        n = _stdout_sink(events)
    elif args.sink == "kafka":
        n = _kafka_sink(events, args.kafka_bootstrap, args.kafka_topic)
        print(f"[generate] published {n} events to "
              f"{args.kafka_topic} @ {args.kafka_bootstrap}", file=sys.stderr)
    else:
        print("[generate] --sink api is invoked via the runner, not the "
              "standalone generator (use runner.py for API ingest)",
              file=sys.stderr)
        return 2
    print(f"[generate] emitted {n} events", file=sys.stderr)
    return 0


def os_getenv(name: str, default: str) -> str:
    import os
    return os.getenv(name, default)


if __name__ == "__main__":
    sys.exit(main())
