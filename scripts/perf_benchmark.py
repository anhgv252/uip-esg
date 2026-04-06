#!/usr/bin/env python3
"""
UIP Performance Benchmark
==========================
Measures end-to-end throughput across the pipeline:
  Producer → Kafka → Flink → TimescaleDB

Modes:
  --mode kafka-only    Measure Kafka producer throughput only
  --mode flink         Measure Kafka→Flink→DB throughput
  --mode db-only       Measure raw DB insert throughput (baseline)

Usage:
  python3 scripts/perf_benchmark.py --mode kafka-only --rate 50000 --duration 30
  python3 scripts/perf_benchmark.py --mode flink      --rate 10000 --duration 60
  python3 scripts/perf_benchmark.py --mode db-only    --workers 8  --duration 30
"""
import argparse
import json
import random
import time
import threading
import sys
from datetime import datetime, timezone
from dataclasses import dataclass

try:
    from kafka import KafkaProducer
    from kafka.errors import KafkaError
    KAFKA_AVAILABLE = True
except ImportError:
    KAFKA_AVAILABLE = False
    print("[WARN] kafka-python not installed: pip3 install kafka-python")

try:
    import psycopg2
    import psycopg2.pool
    DB_AVAILABLE = True
except ImportError:
    DB_AVAILABLE = False
    print("[WARN] psycopg2 not installed: pip3 install psycopg2-binary")

# ── Config ────────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP = "localhost:29092"
KAFKA_TOPIC = "ngsi_ld_environment"

DB_CONFIG = {
    "host":     "localhost",
    "port":     5432,
    "dbname":   "uip_smartcity",
    "user":     "uip",
    "password": "changeme_db_password",
}

# Simulate N devices (beyond the 8 real ones)
NUM_DEVICES = 1000   # configurable via --devices

SENSOR_POOL = [f"SIM-{i:05d}" for i in range(1, NUM_DEVICES + 1)]


@dataclass
class BenchmarkResult:
    mode: str
    target_rate: int
    actual_rate: float
    total_sent: int
    total_errors: int
    duration_s: float
    p50_ms: float
    p99_ms: float


# ── Message generation ────────────────────────────────────────────────────────

def make_ngsi_ld(sensor_id: str) -> bytes:
    ts_ms = int(time.time() * 1000)
    msg = {
        "@context": ["https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld"],
        "id": f"urn:ngsi-ld:Device:{sensor_id}",
        "type": "Device",
        "deviceId":    {"type": "Property", "value": sensor_id},
        "observedAt":  {"type": "Property", "value": ts_ms},
        "sensorType":  {"type": "Property", "value": "environment"},
        "measurements": {
            "type": "Property",
            "value": {
                "aqi":         random.randint(30, 200),
                "pm25":        round(random.uniform(5.0, 80.0), 2),
                "pm10":        round(random.uniform(10.0, 120.0), 2),
                "o3":          round(random.uniform(20.0, 100.0), 2),
                "no2":         round(random.uniform(5.0, 80.0), 2),
                "so2":         round(random.uniform(1.0, 20.0), 2),
                "co":          round(random.uniform(0.1, 2.0), 2),
                "temperature": round(random.uniform(25.0, 40.0), 1),
                "humidity":    round(random.uniform(40.0, 90.0), 1),
            }
        },
        "_meta": {"source": "perf-benchmark", "sensorType": "environment"},
    }
    return json.dumps(msg).encode("utf-8")


# ── Mode 1: Kafka Producer Benchmark ─────────────────────────────────────────

class KafkaBenchmark:
    """Measures how fast we can push messages into Kafka."""

    def __init__(self, target_rate: int, duration: int, num_producers: int = 4):
        self.target_rate = target_rate
        self.duration = duration
        self.num_producers = num_producers
        self.lock = threading.Lock()
        self.sent = 0
        self.errors = 0
        self.latencies = []

    def _producer_thread(self, thread_id: int, rate_per_thread: int):
        producer = KafkaProducer(
            bootstrap_servers=[KAFKA_BOOTSTRAP],
            # High-throughput producer settings
            batch_size=65536,          # 64KB batches
            linger_ms=5,               # 5ms linger for batching
            compression_type="lz4",   # LZ4 compression
            acks=1,                    # Leader ack only (vs. all for durability)
            buffer_memory=134217728,   # 128MB buffer
            max_block_ms=5000,
            request_timeout_ms=10000,
        )

        interval_s = 1.0 / rate_per_thread if rate_per_thread > 0 else 0.0001
        end_time = time.monotonic() + self.duration
        local_sent = 0
        local_errors = 0

        try:
            while time.monotonic() < end_time:
                loop_start = time.monotonic()
                sensor_id = SENSOR_POOL[random.randint(0, len(SENSOR_POOL) - 1)]
                payload = make_ngsi_ld(sensor_id)
                send_start = time.monotonic()
                try:
                    future = producer.send(
                        KAFKA_TOPIC,
                        value=payload,
                        key=sensor_id.encode()
                    )
                    # Non-blocking — measure callback latency
                    def on_send_success(metadata, t=send_start):
                        lat_ms = (time.monotonic() - t) * 1000
                        with self.lock:
                            self.latencies.append(lat_ms)
                    def on_send_error(excp):
                        with self.lock:
                            self.errors += 1
                    future.add_callback(on_send_success)
                    future.add_errback(on_send_error)
                    local_sent += 1
                except KafkaError as e:
                    local_errors += 1

                # Rate limiting
                elapsed = time.monotonic() - loop_start
                sleep_for = interval_s - elapsed
                if sleep_for > 0:
                    time.sleep(sleep_for)

            producer.flush(timeout=10)
        finally:
            producer.close()
            with self.lock:
                self.sent += local_sent
                self.errors += local_errors

    def run(self) -> BenchmarkResult:
        print(f"\n{'═' * 60}")
        print(f"  KAFKA PRODUCER BENCHMARK")
        print(f"  Target: {self.target_rate:,} msg/s | Duration: {self.duration}s")
        print(f"  Producers: {self.num_producers} | Devices: {NUM_DEVICES:,}")
        print(f"  Topic: {KAFKA_TOPIC} (6 partitions)")
        print(f"{'═' * 60}")

        rate_per_thread = self.target_rate // self.num_producers
        threads = []
        start = time.monotonic()

        for i in range(self.num_producers):
            t = threading.Thread(
                target=self._producer_thread,
                args=(i, rate_per_thread),
                daemon=True
            )
            threads.append(t)

        print(f"  Starting {self.num_producers} producer threads @ {rate_per_thread:,} msg/s each...")
        for t in threads:
            t.start()

        # Progress reporting
        prev_sent = 0
        prev_time = start
        while any(t.is_alive() for t in threads):
            time.sleep(5)
            now = time.monotonic()
            with self.lock:
                cur_sent = self.sent
            rate = (cur_sent - prev_sent) / (now - prev_time)
            elapsed = now - start
            print(f"  t={elapsed:5.0f}s  sent={cur_sent:>10,}  rate={rate:>9,.0f} msg/s  errors={self.errors}")
            prev_sent = cur_sent
            prev_time = now

        for t in threads:
            t.join()

        duration = time.monotonic() - start
        actual_rate = self.sent / duration

        # Percentiles
        with self.lock:
            lats = sorted(self.latencies) if self.latencies else [0]
        p50 = lats[len(lats) // 2]
        p99 = lats[int(len(lats) * 0.99)]

        return BenchmarkResult(
            mode="kafka-only",
            target_rate=self.target_rate,
            actual_rate=actual_rate,
            total_sent=self.sent,
            total_errors=self.errors,
            duration_s=duration,
            p50_ms=p50,
            p99_ms=p99,
        )


# ── Mode 2: DB Direct Insert Benchmark ───────────────────────────────────────

class DbBenchmark:
    """Measures TimescaleDB hypertable insert throughput directly."""

    def __init__(self, duration: int, workers: int = 8, batch_size: int = 500):
        self.duration = duration
        self.workers = workers
        self.batch_size = batch_size
        self.lock = threading.Lock()
        self.inserted = 0
        self.errors = 0

    def _worker(self, worker_id: int):
        try:
            conn = psycopg2.connect(**DB_CONFIG)
            conn.autocommit = False
        except Exception as e:
            print(f"  [W{worker_id}] DB connect error: {e}")
            return

        end_time = time.monotonic() + self.duration
        local_inserted = 0
        local_errors = 0

        sql = """
        INSERT INTO environment.sensor_readings
            (sensor_id, timestamp, aqi, pm25, pm10, o3, no2, so2, co, temperature, humidity)
        VALUES (%s, NOW(), %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT DO NOTHING
        """

        try:
            while time.monotonic() < end_time:
                batch = []
                for _ in range(self.batch_size):
                    sid = SENSOR_POOL[random.randint(0, len(SENSOR_POOL) - 1)]
                    batch.append((
                        sid,
                        random.randint(30, 200),
                        round(random.uniform(5, 80), 2),
                        round(random.uniform(10, 120), 2),
                        round(random.uniform(20, 100), 2),
                        round(random.uniform(5, 80), 2),
                        round(random.uniform(1, 20), 2),
                        round(random.uniform(0.1, 2.0), 2),
                        round(random.uniform(25, 40), 1),
                        round(random.uniform(40, 90), 1),
                    ))
                try:
                    with conn.cursor() as cur:
                        cur.executemany(sql, batch)
                    conn.commit()
                    local_inserted += len(batch)
                except Exception as e:
                    conn.rollback()
                    local_errors += len(batch)
        finally:
            conn.close()
            with self.lock:
                self.inserted += local_inserted
                self.errors += local_errors

    def run(self) -> BenchmarkResult:
        print(f"\n{'═' * 60}")
        print(f"  TIMESCALEDB RAW INSERT BENCHMARK")
        print(f"  Workers: {self.workers} | Batch: {self.batch_size} | Duration: {self.duration}s")
        print(f"  Devices pool: {NUM_DEVICES:,}")
        print(f"{'═' * 60}")

        threads = [
            threading.Thread(target=self._worker, args=(i,), daemon=True)
            for i in range(self.workers)
        ]

        start = time.monotonic()
        for t in threads:
            t.start()

        prev_ins = 0
        prev_time = start
        while any(t.is_alive() for t in threads):
            time.sleep(5)
            now = time.monotonic()
            with self.lock:
                cur_ins = self.inserted
            rate = (cur_ins - prev_ins) / (now - prev_time)
            elapsed = now - start
            print(f"  t={elapsed:5.0f}s  inserted={cur_ins:>10,}  rate={rate:>9,.0f} rows/s  errors={self.errors}")
            prev_ins = cur_ins
            prev_time = now

        for t in threads:
            t.join()

        duration = time.monotonic() - start
        actual_rate = self.inserted / duration

        return BenchmarkResult(
            mode="db-only",
            target_rate=0,
            actual_rate=actual_rate,
            total_sent=self.inserted,
            total_errors=self.errors,
            duration_s=duration,
            p50_ms=0,
            p99_ms=0,
        )


# ── Mode 3: Flink E2E Benchmark ───────────────────────────────────────────────

class FlinkE2EBenchmark:
    """
    Produce to Kafka at target rate, then count how many records
    Flink actually commits to DB in the same window.
    """

    def __init__(self, target_rate: int, duration: int, num_producers: int = 4):
        self.target_rate = target_rate
        self.duration = duration
        self.num_producers = num_producers
        self.lock = threading.Lock()
        self.sent = 0
        self.errors = 0

    def _get_db_row_count(self) -> int:
        try:
            conn = psycopg2.connect(**DB_CONFIG)
            with conn.cursor() as cur:
                cur.execute("SELECT COUNT(*) FROM environment.sensor_readings")
                return cur.fetchone()[0]
        except Exception:
            return 0
        finally:
            conn.close()

    def _producer_thread(self, thread_id: int, rate_per_thread: int):
        producer = KafkaProducer(
            bootstrap_servers=[KAFKA_BOOTSTRAP],
            batch_size=65536,
            linger_ms=5,
            compression_type="lz4",
            acks=1,
            buffer_memory=67108864,
            request_timeout_ms=10000,
        )
        interval_s = 1.0 / rate_per_thread if rate_per_thread > 0 else 0
        end_time = time.monotonic() + self.duration
        local_sent = 0
        local_errors = 0

        try:
            while time.monotonic() < end_time:
                loop_start = time.monotonic()
                sensor_id = SENSOR_POOL[random.randint(0, len(SENSOR_POOL) - 1)]
                try:
                    producer.send(KAFKA_TOPIC, value=make_ngsi_ld(sensor_id), key=sensor_id.encode())
                    local_sent += 1
                except Exception:
                    local_errors += 1
                elapsed = time.monotonic() - loop_start
                sleep_for = interval_s - elapsed
                if sleep_for > 0:
                    time.sleep(sleep_for)
            producer.flush(timeout=15)
        finally:
            producer.close()
            with self.lock:
                self.sent += local_sent
                self.errors += local_errors

    def run(self) -> BenchmarkResult:
        print(f"\n{'═' * 60}")
        print(f"  KAFKA → FLINK → DB END-TO-END BENCHMARK")
        print(f"  Target: {self.target_rate:,} msg/s | Duration: {self.duration}s")
        print(f"  Producers: {self.num_producers} | Devices: {NUM_DEVICES:,}")
        print(f"{'═' * 60}")
        print("  NOTE: Flink JDBC sink batches 500 rows every 1000ms")
        print("        Flink parallelism=1 (single consumer thread)")
        print()

        rows_before = self._get_db_row_count()
        print(f"  DB rows before: {rows_before:,}")

        threads = [
            threading.Thread(
                target=self._producer_thread,
                args=(i, self.target_rate // self.num_producers),
                daemon=True
            )
            for i in range(self.num_producers)
        ]

        start = time.monotonic()
        for t in threads:
            t.start()

        prev_sent = 0
        prev_rows = rows_before
        prev_time = start

        while any(t.is_alive() for t in threads):
            time.sleep(5)
            now = time.monotonic()
            with self.lock:
                cur_sent = self.sent
            cur_rows = self._get_db_row_count()
            kafka_rate = (cur_sent - prev_sent) / (now - prev_time)
            db_rate = (cur_rows - prev_rows) / (now - prev_time)
            elapsed = now - start
            print(f"  t={elapsed:5.0f}s  kafka={kafka_rate:>9,.0f} msg/s  db={db_rate:>9,.0f} rows/s  lag={cur_sent-cur_rows+rows_before:>8,}")
            prev_sent = cur_sent
            prev_rows = cur_rows
            prev_time = now

        for t in threads:
            t.join()

        # Wait extra for Flink to flush remaining batches
        print("\n  Waiting 5s for Flink to flush remaining batches...")
        time.sleep(5)

        rows_after = self._get_db_row_count()
        duration = time.monotonic() - start
        actual_kafka_rate = self.sent / (duration - 5)
        actual_db_rate = (rows_after - rows_before) / duration

        print(f"\n  DB rows after:  {rows_after:,} (committed: {rows_after - rows_before:,})")

        return BenchmarkResult(
            mode="flink-e2e",
            target_rate=self.target_rate,
            actual_rate=actual_kafka_rate,
            total_sent=self.sent,
            total_errors=self.errors,
            duration_s=duration,
            p50_ms=actual_db_rate,   # reuse field for db throughput
            p99_ms=0,
        )


# ── Report ────────────────────────────────────────────────────────────────────

def print_report(res: BenchmarkResult):
    print(f"\n{'═' * 60}")
    print(f"  BENCHMARK RESULTS  [{res.mode.upper()}]")
    print(f"{'═' * 60}")
    print(f"  Duration:        {res.duration_s:.1f}s")
    print(f"  Target rate:     {res.target_rate:>12,} msg/s")
    if res.mode == "kafka-only":
        print(f"  Actual rate:     {res.actual_rate:>12,.0f} msg/s")
        print(f"  Messages sent:   {res.total_sent:>12,}")
        print(f"  Errors:          {res.total_errors:>12,}")
        efficiency = (res.actual_rate / res.target_rate * 100) if res.target_rate > 0 else 0
        print(f"  Efficiency:      {efficiency:>11.1f}%")
        print(f"  Latency p50:     {res.p50_ms:>11.1f} ms")
        print(f"  Latency p99:     {res.p99_ms:>11.1f} ms")
    elif res.mode == "db-only":
        print(f"  Actual rate:     {res.actual_rate:>12,.0f} rows/s")
        print(f"  Rows inserted:   {res.total_sent:>12,}")
        print(f"  Errors:          {res.total_errors:>12,}")
    elif res.mode == "flink-e2e":
        print(f"  Kafka rate:      {res.actual_rate:>12,.0f} msg/s")
        print(f"  DB rate (Flink): {res.p50_ms:>12,.0f} rows/s")
        print(f"  Messages sent:   {res.total_sent:>12,}")
        print(f"  Errors:          {res.total_errors:>12,}")
        lag_factor = res.actual_rate / res.p50_ms if res.p50_ms > 0 else 999
        print(f"  Consumer lag x:  {lag_factor:>12.1f}x  (kafka/db ratio)")

    print(f"\n  ── Bottleneck Analysis ──────────────────────────────")
    if res.mode == "kafka-only":
        if res.actual_rate >= res.target_rate * 0.9:
            print(f"  ✅ Kafka broker can sustain {res.actual_rate:,.0f} msg/s")
        else:
            print(f"  ⚠️  Kafka bottleneck: only {res.actual_rate:,.0f}/{res.target_rate:,} msg/s")
            print(f"     → Increase partitions (6→12+), tune batch.size, linger.ms")
    elif res.mode == "db-only":
        if res.actual_rate >= 50000:
            print(f"  ✅ DB can sustain {res.actual_rate:,.0f} rows/s (well above 100K pipeline)")
        elif res.actual_rate >= 10000:
            print(f"  ⚠️  DB at {res.actual_rate:,.0f} rows/s — tune needed for 100K msg/s pipeline")
        else:
            print(f"  ❌ DB bottleneck at {res.actual_rate:,.0f} rows/s")
            print(f"     → Set synchronous_commit=off, increase shared_buffers")
    elif res.mode == "flink-e2e":
        kafka_rate = res.actual_rate
        db_rate = res.p50_ms
        if db_rate >= kafka_rate * 0.8:
            print(f"  ✅ Flink keeping up: DB={db_rate:,.0f} vs Kafka={kafka_rate:,.0f} msg/s")
        else:
            print(f"  ❌ Flink lagging: DB={db_rate:,.0f} vs Kafka={kafka_rate:,.0f} msg/s")
            print(f"     → Increase Flink parallelism (1→6), increase JDBC batchSize (500→5000)")
            print(f"     → Set synchronous_commit=off in DB")

    print(f"{'═' * 60}")


# ── Scaling Recommendations ───────────────────────────────────────────────────

def print_scaling_analysis(host_cpu: int = 10):
    print(f"""
╔══════════════════════════════════════════════════════════════╗
║     UIP SCALING ANALYSIS: Target 100,000 msg/s              ║
╚══════════════════════════════════════════════════════════════╝

Current environment: {host_cpu} CPU, 32GB RAM (Oracle using ~4GB)

PIPELINE BOTTLENECKS (ranked by severity):
─────────────────────────────────────────────────────────────

1. ❌ FLINK PARALLELISM = 1  (most critical)
   Current: 1 thread consuming 6 partitions → ~5-10K rows/s
   Target:  parallelism = 6 (match partition count) → 6x throughput
   Action:  Submit EnvironmentFlinkJob with parallelism=6

2. ❌ FLINK JDBC BATCH SIZE = 500 rows / 1000ms
   At 100K msg/s: 100 batches/s × DB round-trip = bottleneck
   Target:  batchSize=10000, batchIntervalMs=200ms
   Action:  Tune EnvironmentFlinkJob JdbcExecutionOptions

3. ❌ DB TRIGGER trg_sensor_last_seen (per-row UPDATE)
   100K inserts/s × 1 UPDATE each = 200K DB ops/s
   With 1000 devices: hot-row contention on sensors table
   Action:  Change to periodic/async UPDATE or materialized view

4. ⚠️  KAFKA BROKER - SINGLE NODE
   Single Kafka broker: 500MB–1GB/s throughput ceiling
   100K msg/s × ~500 bytes/msg = ~50MB/s (achievable single-node)
   Action:  Add producer linger_ms=5, batch_size=65536, lz4 compression

5. ⚠️  DB synchronous_commit = ON
   Every JDBC batch commit waits for WAL flush to disk
   Action:  SET synchronous_commit = off (for sensor data, safe)

6. ⚠️  shared_buffers = 128MB (default)
   For 100K msg/s workload: recommend 4GB+ shared_buffers
   Action:  Set shared_buffers = 4GB in PostgreSQL config

ESTIMATED THROUGHPUT WITH TUNING:
─────────────────────────────────────────────────────────────
  Component         │ Current   │ After tuning │
  ──────────────────┼───────────┼──────────────┤
  Kafka producer    │ ~50K/s    │ ~500K/s      │ (LZ4, batching)
  Kafka broker      │ ~100K/s   │ ~500K/s      │ (single node)
  Flink consumer    │ ~5-10K/s  │ ~100K/s      │ (parallelism=6)
  DB (TimescaleDB)  │ ~20K/s    │ ~150K/s      │ (sync_commit=off, batching)
  PIPELINE E2E      │ ~5K/s     │ ~100K/s ✅   │

PRODUCTION REQUIREMENTS FOR 100K msg/s:
─────────────────────────────────────────────────────────────
  Kafka:       3 brokers, 12+ partitions, replication-factor=2
  Flink:       3 TaskManagers × 4 slots, parallelism=12
  TimescaleDB: dedicated server, 16+ cores, 64GB RAM, NVMe SSD
               OR ClickHouse for pure analytics workloads
  Network:     10Gbps between components (avoid bridge docker networking)
""")


# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="UIP Performance Benchmark")
    parser.add_argument("--mode", choices=["kafka-only", "db-only", "flink", "analysis"],
                        default="analysis", help="Benchmark mode")
    parser.add_argument("--rate",     type=int, default=10000, help="Target msg/s (kafka-only, flink modes)")
    parser.add_argument("--duration", type=int, default=30,    help="Test duration in seconds")
    parser.add_argument("--workers",  type=int, default=8,     help="Parallel workers (db-only mode)")
    parser.add_argument("--devices",  type=int, default=1000,  help="Number of simulated devices")
    parser.add_argument("--producers",type=int, default=4,     help="Number of Kafka producer threads")
    args = parser.parse_args()

    global SENSOR_POOL, NUM_DEVICES
    NUM_DEVICES = args.devices
    SENSOR_POOL = [f"SIM-{i:05d}" for i in range(1, NUM_DEVICES + 1)]

    if args.mode == "analysis":
        print_scaling_analysis()
        return

    if args.mode == "kafka-only":
        if not KAFKA_AVAILABLE:
            print("[ERROR] kafka-python required"); sys.exit(1)
        bench = KafkaBenchmark(args.rate, args.duration, args.producers)
        result = bench.run()
        print_report(result)

    elif args.mode == "db-only":
        if not DB_AVAILABLE:
            print("[ERROR] psycopg2 required"); sys.exit(1)
        bench = DbBenchmark(args.duration, args.workers)
        result = bench.run()
        print_report(result)

    elif args.mode == "flink":
        if not KAFKA_AVAILABLE:
            print("[ERROR] kafka-python required"); sys.exit(1)
        if not DB_AVAILABLE:
            print("[ERROR] psycopg2 required"); sys.exit(1)
        bench = FlinkE2EBenchmark(args.rate, args.duration, args.producers)
        result = bench.run()
        print_report(result)


if __name__ == "__main__":
    main()
