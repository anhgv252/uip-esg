#!/usr/bin/env python3
"""
S4-05 MQTT Load Test — 2,000 msg/s sustained for 10 minutes.
Sends sensor telemetry to EMQX via MQTT, measures throughput and latency.

Usage:
    python3 mqtt_load_test.py [--host HOST] [--port PORT] [--rate 2000] [--duration 600]
"""

import argparse
import json
import random
import time
import threading
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone

try:
    import paho.mqtt.client as mqtt
except ImportError:
    print("ERROR: paho-mqtt not installed. Run: pip install paho-mqtt")
    sys.exit(1)

MQTT_TOPIC = "v1/devices/me/telemetry"
SENSOR_TYPES = [
    ("AQI-IT-{i:03d}", {"aqi": (30, 350), "pm25": (5, 150)}),
    ("AQI-D{i:02d}-{j:03d}", {"aqi": (30, 350), "pm25": (5, 150)}),
    ("WL-CANAL-{i:03d}", {"water_level": (0.5, 5.0)}),
    ("ENERGY-BLDG-{i:03d}", {"energy_kwh": (10, 500), "water_m3": (1, 50)}),
    ("TRAFFIC-INT-{i:03d}", {"vehicle_count": (10, 500), "speed_kmh": (10, 80)}),
]


@dataclass
class Metrics:
    published: int = 0
    failed: int = 0
    start_time: float = 0.0
    end_time: float = 0.0
    latencies: list = field(default_factory=list)
    lock: threading.Lock = field(default_factory=threading.Lock)

    def record_publish(self, latency_ms=0.0):
        with self.lock:
            self.published += 1
            if latency_ms > 0:
                self.latencies.append(latency_ms)

    def record_failure(self):
        with self.lock:
            self.failed += 1

    def summary(self):
        elapsed = self.end_time - self.start_time if self.end_time else time.time() - self.start_time
        rate = self.published / elapsed if elapsed > 0 else 0

        latencies = sorted(self.latencies) if self.latencies else [0]
        p50 = latencies[int(len(latencies) * 0.50)]
        p95 = latencies[int(len(latencies) * 0.95)]
        p99 = latencies[min(int(len(latencies) * 0.99), len(latencies) - 1)]

        return {
            "test": "MQTT Load Test",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "duration_seconds": round(elapsed, 1),
            "target_rate": args.rate,
            "actual_rate": round(rate, 1),
            "published": self.published,
            "failed": self.failed,
            "latency_ms": {"p50": round(p50, 2), "p95": round(p95, 2), "p99": round(p99, 2)},
            "status": "PASS" if rate >= args.rate * 0.9 and self.failed == 0 else "FAIL",
        }


def generate_sensor_data(sensor_idx: int) -> tuple[str, dict]:
    """Generate random sensor ID and telemetry payload."""
    type_idx = sensor_idx % len(SENSOR_TYPES)
    id_template, measures = SENSOR_TYPES[type_idx]

    # Generate sensor ID
    sensor_id = id_template.format(
        i=random.randint(1, 50),
        j=random.randint(1, 20),
    )

    values = {}
    for metric, (low, high) in measures.items():
        values[metric] = round(random.uniform(low, high), 2)

    payload = {
        "ts": int(time.time() * 1000),
        "values": values,
    }
    return sensor_id, payload


def publisher(client: mqtt.Client, metrics: Metrics, stop_event: threading.Event):
    """Publish messages at target rate."""
    interval = 1.0 / args.rate
    batch_size = 100
    batch_interval = batch_size * interval

    sensor_count = 100

    while not stop_event.is_set():
        batch_start = time.time()
        for _ in range(batch_size):
            if stop_event.is_set():
                break

            sensor_idx = random.randint(0, sensor_count - 1)
            sensor_id, payload = generate_sensor_data(sensor_idx)

            publish_start = time.time()
            result = client.publish(
                MQTT_TOPIC,
                payload=json.dumps(payload),
                qos=0,
            )

            if result.rc == mqtt.MQTT_ERR_SUCCESS:
                latency = (time.time() - publish_start) * 1000
                metrics.record_publish(latency)
            else:
                metrics.record_failure()

        elapsed_batch = time.time() - batch_start
        sleep_time = batch_interval - elapsed_batch
        if sleep_time > 0:
            time.sleep(sleep_time)


def progress_reporter(metrics: Metrics, stop_event: threading.Event):
    """Print progress every 5 seconds."""
    while not stop_event.is_set():
        stop_event.wait(5)
        if stop_event.is_set():
            break
        elapsed = time.time() - metrics.start_time
        rate = metrics.published / elapsed if elapsed > 0 else 0
        print(f"  [{elapsed:.0f}s] published={metrics.published} rate={rate:.0f}/s failed={metrics.failed}")


def main():
    global args
    parser = argparse.ArgumentParser(description="S4-05 MQTT Load Test")
    parser.add_argument("--host", default="localhost", help="EMQX host")
    parser.add_argument("--port", type=int, default=1883, help="EMQX port")
    parser.add_argument("--rate", type=int, default=2000, help="Target messages/second")
    parser.add_argument("--duration", type=int, default=600, help="Test duration in seconds")
    parser.add_argument("--threads", type=int, default=4, help="Publisher threads")
    args = parser.parse_args()

    total_msgs = args.rate * args.duration
    print(f"=== S4-05 MQTT Load Test ===")
    print(f"Target: {args.rate} msg/s × {args.duration}s = {total_msgs:,} messages")
    print(f"Broker: {args.host}:{args.port}")
    print(f"Threads: {args.threads}")
    print()

    # Connect to EMQX
    client = mqtt.Client(
        mqtt.CallbackAPIVersion.VERSION2,
        client_id=f"perf-test-{random.randint(1000,9999)}",
    )
    try:
        client.connect(args.host, args.port, keepalive=60)
    except Exception as e:
        print(f"ERROR: Cannot connect to EMQX at {args.host}:{args.port}: {e}")
        sys.exit(1)

    client.loop_start()

    metrics = Metrics()
    metrics.start_time = time.time()
    stop_event = threading.Event()

    # Start publisher threads
    threads = []
    for t in range(args.threads):
        th = threading.Thread(target=publisher, args=(client, metrics, stop_event), daemon=True)
        th.start()
        threads.append(th)

    # Start progress reporter
    reporter = threading.Thread(target=progress_reporter, args=(metrics, stop_event), daemon=True)
    reporter.start()

    # Wait for test duration
    print(f"Test running for {args.duration} seconds...")
    try:
        time.sleep(args.duration)
    except KeyboardInterrupt:
        print("\nTest interrupted by user")

    stop_event.set()
    metrics.end_time = time.time()

    # Wait for threads
    for th in threads:
        th.join(timeout=5)

    client.loop_stop()
    client.disconnect()

    # Print summary
    result = metrics.summary()
    print()
    print("=== Results ===")
    print(f"Published:    {result['published']:,}")
    print(f"Failed:       {result['failed']}")
    print(f"Duration:     {result['duration_seconds']}s")
    print(f"Actual rate:  {result['actual_rate']:.1f} msg/s (target: {result['target_rate']})")
    print(f"Latency p50:  {result['latency_ms']['p50']:.2f}ms")
    print(f"Latency p95:  {result['latency_ms']['p95']:.2f}ms")
    print(f"Latency p99:  {result['latency_ms']['p99']:.2f}ms")
    print(f"Status:       {result['status']}")

    # Save results
    report_path = "docs/reports/performance/mqtt-load-results.json"
    try:
        with open(report_path, "w") as f:
            json.dump(result, f, indent=2)
        print(f"\nResults saved to {report_path}")
    except Exception as e:
        print(f"Warning: Could not save results: {e}")

    sys.exit(0 if result["status"] == "PASS" else 1)


if __name__ == "__main__":
    main()
