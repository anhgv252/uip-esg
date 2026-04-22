#!/usr/bin/env python3
"""
S4-05 Kafka Direct Producer — sends alert events to Kafka UIP.flink.alert.detected.v1 topic.
Tests backend consumer + TimescaleDB write throughput in isolation (bypass Flink).

Usage:
    python3 kafka_producer.py [--bootstrap HOST:PORT] [--rate 2000] [--duration 600]
"""

import argparse
import json
import random
import sys
import time
import threading
from dataclasses import dataclass, field
from datetime import datetime, timezone

try:
    from confluent_kafka import Producer
except ImportError:
    print("ERROR: confluent-kafka not installed. Run: pip install confluent-kafka")
    sys.exit(1)

try:
    import psycopg2
except ImportError:
    psycopg2 = None

KAFKA_TOPIC = "UIP.flink.alert.detected.v1"

SENSOR_IDS = [f"AQI-IT-{i:03d}" for i in range(1, 51)]
DISTRICTS = ["D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9"]
MODULES = ["ENVIRONMENT", "FLOOD", "TRAFFIC"]
MEASURES = {
    "ENVIRONMENT": [("AQI", 150.0), ("PM25", 55.0)],
    "FLOOD": [("WATER_LEVEL", 2.5)],
    "TRAFFIC": [("CONGESTION", 80.0)],
}
SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]


@dataclass
class Metrics:
    produced: int = 0
    failed: int = 0
    delivery_reports: int = 0
    start_time: float = 0.0
    end_time: float = 0.0
    lock: threading.Lock = field(default_factory=threading.Lock)

    def record_produce(self):
        with self.lock:
            self.produced += 1

    def record_failure(self):
        with self.lock:
            self.failed += 1

    def record_delivery(self):
        with self.lock:
            self.delivery_reports += 1

    def summary(self):
        elapsed = self.end_time - self.start_time if self.end_time else time.time() - self.start_time
        rate = self.produced / elapsed if elapsed > 0 else 0
        return {
            "test": "Kafka Direct Producer (UIP.flink.alert.detected.v1)",
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "duration_seconds": round(elapsed, 1),
            "target_rate": args.rate,
            "actual_rate": round(rate, 1),
            "produced": self.produced,
            "delivered": self.delivery_reports,
            "failed": self.failed,
            "status": "PASS" if rate >= args.rate * 0.9 else "FAIL",
        }


def delivery_callback(err, msg):
    """Kafka producer delivery report callback."""
    if err:
        args.metrics.record_failure()
    else:
        args.metrics.record_delivery()


def generate_alert() -> dict:
    module = random.choice(MODULES)
    measure, threshold = random.choice(MEASURES[module])
    value = threshold * random.uniform(1.1, 3.0)

    severity = "HIGH"
    if value > threshold * 2.5:
        severity = "CRITICAL"
    elif value > threshold * 1.5:
        severity = "HIGH"
    else:
        severity = "MEDIUM"

    return {
        "sensorId": random.choice(SENSOR_IDS),
        "module": module,
        "measureType": measure,
        "value": round(value, 2),
        "threshold": round(threshold, 2),
        "severity": severity,
        "districtCode": random.choice(DISTRICTS),
        "detectedAt": datetime.now(timezone.utc).isoformat(),
    }


def get_db_count(db_url: str) -> int:
    """Get current alert count from DB."""
    if not psycopg2:
        return -1
    try:
        conn = psycopg2.connect(db_url)
        cur = conn.cursor()
        cur.execute("SELECT count(*) FROM alerts.alert_events")
        count = cur.fetchone()[0]
        cur.close()
        conn.close()
        return count
    except Exception as e:
        print(f"Warning: DB query failed: {e}")
        return -1


def progress_reporter(metrics: Metrics, stop_event: threading.Event):
    while not stop_event.is_set():
        stop_event.wait(5)
        if stop_event.is_set():
            break
        elapsed = time.time() - metrics.start_time
        rate = metrics.produced / elapsed if elapsed > 0 else 0
        print(f"  [{elapsed:.0f}s] produced={metrics.produced:,} delivered={metrics.delivery_reports:,} "
              f"rate={rate:.0f}/s failed={metrics.failed}")


def main():
    global args
    parser = argparse.ArgumentParser(description="S4-05 Kafka Direct Producer")
    parser.add_argument("--bootstrap", default="localhost:29092", help="Kafka bootstrap server")
    parser.add_argument("--rate", type=int, default=2000, help="Target messages/second")
    parser.add_argument("--duration", type=int, default=600, help="Test duration in seconds")
    parser.add_argument("--db-url", default=None, help="DB URL for verification (optional)")
    args = parser.parse_args()

    total_msgs = args.rate * args.duration
    print(f"=== S4-05 Kafka Direct Producer ===")
    print(f"Target: {args.rate} msg/s × {args.duration}s = {total_msgs:,} messages")
    print(f"Kafka: {args.bootstrap}")
    print(f"Topic: {KAFKA_TOPIC}")
    print()

    # DB count before
    count_before = -1
    if args.db_url:
        count_before = get_db_count(args.db_url)
        print(f"DB alert count BEFORE: {count_before}")

    # Create producer
    producer = Producer({
        "bootstrap.servers": args.bootstrap,
        "linger.ms": 50,
        "batch.size": 65536,
        "compression.type": "snappy",
        "queue.buffering.max.messages": 500000,
    })

    metrics = Metrics()
    args.metrics = metrics
    metrics.start_time = time.time()

    stop_event = threading.Event()
    reporter = threading.Thread(target=progress_reporter, args=(metrics, stop_event), daemon=True)
    reporter.start()

    interval = 1.0 / args.rate
    batch_size = 200
    batch_interval = batch_size * interval

    print(f"Producing for {args.duration} seconds...")
    try:
        deadline = time.time() + args.duration
        while time.time() < deadline:
            batch_start = time.time()
            for _ in range(batch_size):
                if time.time() >= deadline:
                    break

                alert = generate_alert()
                try:
                    producer.produce(
                        KAFKA_TOPIC,
                        key=alert["sensorId"],
                        value=json.dumps(alert),
                        callback=delivery_callback,
                    )
                    metrics.record_produce()
                except BufferError:
                    metrics.record_failure()
                    producer.poll(0.1)

            producer.poll(0)
            elapsed_batch = time.time() - batch_start
            sleep_time = batch_interval - elapsed_batch
            if sleep_time > 0:
                time.sleep(sleep_time)

    except KeyboardInterrupt:
        print("\nTest interrupted")

    stop_event.set()
    producer.flush(timeout=30)
    metrics.end_time = time.time()

    # DB count after
    if args.db_url:
        time.sleep(5)  # wait for backend to process
        count_after = get_db_count(args.db_url)
        print(f"DB alert count AFTER: {count_after}")
        if count_before >= 0 and count_after >= 0:
            ingested = count_after - count_before
            print(f"DB ingestion: {ingested:,} new alerts")

    result = metrics.summary()
    print()
    print("=== Results ===")
    print(f"Produced:     {result['produced']:,}")
    print(f"Delivered:    {result['delivered']:,}")
    print(f"Failed:       {result['failed']}")
    print(f"Duration:     {result['duration_seconds']}s")
    print(f"Actual rate:  {result['actual_rate']:.1f} msg/s (target: {result['target_rate']})")
    print(f"Status:       {result['status']}")

    report_path = "docs/reports/performance/kafka-producer-results.json"
    try:
        with open(report_path, "w") as f:
            json.dump(result, f, indent=2)
        print(f"\nResults saved to {report_path}")
    except Exception as e:
        print(f"Warning: Could not save results: {e}")

    sys.exit(0 if result["status"] == "PASS" else 1)


if __name__ == "__main__":
    main()
