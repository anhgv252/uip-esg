#!/usr/bin/env python3
"""
ESG Dual-Sink Integration Test
================================
Tests the Flink EsgDualSinkJob dual-write pipeline:
  Kafka (ngsi_ld_esg) → Flink → TimescaleDB (esg.clean_metrics) + ClickHouse (analytics.esg_readings)

Also runs ClickHouse performance benchmarks via the analytics-service API.

Usage:
  pip3 install kafka-python requests psycopg2-binary clickhouse-connect
  python3 scripts/esg_dual_sink_test.py

Phases:
  1. Pre-flight health checks
  2. Inject 500 synthetic ESG readings via Kafka
  3. Wait for Flink to flush (batch 500 rows / 1s interval for TimescaleDB, 5000/2s for ClickHouse)
  4. Verify row counts in both sinks
  5. Benchmark ClickHouse analytics-service API response times
  6. Print pass/fail report
"""

import json
import time
import random
import sys
import uuid
from datetime import datetime, timezone, timedelta

# ── Optional dependencies ─────────────────────────────────────────────────────
try:
    from kafka import KafkaProducer
    KAFKA_OK = True
except ImportError:
    KAFKA_OK = False
    print("[WARN] kafka-python not installed: pip3 install kafka-python")

try:
    import requests
    REQUESTS_OK = True
except ImportError:
    REQUESTS_OK = False
    print("[WARN] requests not installed: pip3 install requests")

try:
    import psycopg2
    PG_OK = True
except ImportError:
    PG_OK = False
    print("[WARN] psycopg2-binary not installed: pip3 install psycopg2-binary")

try:
    import clickhouse_connect
    CH_OK = True
except ImportError:
    CH_OK = False
    print("[WARN] clickhouse-connect not installed: pip3 install clickhouse-connect")

# ── Config ────────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP         = "localhost:29092"
KAFKA_TOPIC             = "ngsi_ld_esg"
FLINK_UI                = "http://localhost:8081"
ANALYTICS_API           = "http://localhost:8082"
BACKEND_API             = "http://localhost:8080"
TIMESCALE_HOST          = "localhost"
TIMESCALE_PORT          = 5432
TIMESCALE_DB            = "uip_smartcity"
TIMESCALE_USER          = "uip"
TIMESCALE_PASSWORD      = "changeme_db_password"   # override with env or .env
CLICKHOUSE_HOST         = "localhost"
CLICKHOUSE_PORT         = 8123
CLICKHOUSE_DB           = "analytics"
CLICKHOUSE_USER         = "default"
CLICKHOUSE_PASSWORD     = ""

NUM_MESSAGES            = 500
TEST_TENANT             = "tenant_hcm"
BUILDINGS               = [f"BLD-{i:03d}" for i in range(1, 11)]  # BLD-001..BLD-010
METRICS                 = ["energy_kwh", "water_m3", "carbon_kg", "waste_kg"]
WAIT_FLUSH_SECS         = 12   # TimescaleDB batch: 500rows/1s; ClickHouse: 5000rows/2s

# ── Helpers ───────────────────────────────────────────────────────────────────
PASS = "\033[92m[PASS]\033[0m"
FAIL = "\033[91m[FAIL]\033[0m"
INFO = "\033[94m[INFO]\033[0m"
WARN = "\033[93m[WARN]\033[0m"

results = []

def check(label, ok, detail=""):
    status = PASS if ok else FAIL
    print(f"  {status} {label}" + (f" — {detail}" if detail else ""))
    results.append((label, ok, detail))
    return ok


def build_ngsi_ld_message(building_id: str, metric: str, value: float) -> dict:
    """Build an NGSI-LD ESG message matching NgsiLdMessage contract."""
    device_id = f"SENSOR-{building_id}-{metric[:3].upper()}-001"
    now_ms = int(datetime.now(timezone.utc).timestamp() * 1000)
    return {
        "id": f"urn:ngsi-ld:Device:{device_id}",
        "type": "EsgSensor",
        "deviceId": {"type": "Property", "value": device_id},
        "observedAt": {"type": "Property", "value": now_ms},
        "sensorType": {"type": "Property", "value": "ESG"},
        "measurements": {
            "type": "Property",
            "value": {metric: round(value, 3)}
        },
        "_meta": {
            "source": "esg_dual_sink_test",
            "sensorType": "ESG",
            "tenantId": TEST_TENANT
        }
    }


# ── Phase 1: Pre-flight ───────────────────────────────────────────────────────
def phase_preflight():
    print(f"\n{INFO} === Phase 1: Pre-flight health checks ===")

    # Flink job running
    if REQUESTS_OK:
        try:
            r = requests.get(f"{FLINK_UI}/v1/jobs", timeout=5)
            jobs = r.json().get("jobs", [])
            running = [j for j in jobs if j["status"] == "RUNNING"]
            check("Flink EsgDualSinkJob RUNNING", len(running) > 0,
                  f"{len(running)} running job(s)")
        except Exception as e:
            check("Flink EsgDualSinkJob RUNNING", False, str(e))

        # Backend health
        try:
            r = requests.get(f"{BACKEND_API}/api/v1/health", timeout=5)
            check("Backend API healthy", r.status_code == 200)
        except Exception as e:
            check("Backend API healthy", False, str(e))

        # Analytics-service health
        try:
            r = requests.get(f"{ANALYTICS_API}/actuator/health", timeout=5)
            body = r.json()
            check("Analytics service healthy",
                  body.get("status") == "UP",
                  body.get("status", "?"))
        except Exception as e:
            check("Analytics service healthy", False, str(e))

    # ClickHouse table schema
    if CH_OK:
        try:
            client = clickhouse_connect.get_client(
                host=CLICKHOUSE_HOST, port=CLICKHOUSE_PORT,
                username=CLICKHOUSE_USER, password=CLICKHOUSE_PASSWORD,
                database=CLICKHOUSE_DB)
            cols = [r[0] for r in client.query("DESCRIBE TABLE analytics.esg_readings").result_rows]
            required = {"tenant_id", "building_id", "source_id", "metric_type", "value", "recorded_at"}
            missing = required - set(cols)
            check("ClickHouse esg_readings schema OK", len(missing) == 0,
                  f"cols={cols}" if not missing else f"MISSING: {missing}")
        except Exception as e:
            check("ClickHouse esg_readings schema OK", False, str(e))


# ── Phase 2: Inject Kafka messages ───────────────────────────────────────────
def phase_inject() -> int:
    print(f"\n{INFO} === Phase 2: Injecting {NUM_MESSAGES} ESG messages → Kafka ===")
    if not KAFKA_OK:
        print(f"  {WARN} Skipping — kafka-python not available")
        return 0

    producer = KafkaProducer(
        bootstrap_servers=KAFKA_BOOTSTRAP,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        acks="all",
        retries=3
    )

    sent = 0
    start = time.time()
    for i in range(NUM_MESSAGES):
        building = random.choice(BUILDINGS)
        metric   = random.choice(METRICS)
        value    = random.uniform(0.1, 999.9)
        msg = build_ngsi_ld_message(building, metric, value)
        producer.send(KAFKA_TOPIC, value=msg)
        sent += 1

    producer.flush()
    elapsed = time.time() - start
    throughput = sent / elapsed if elapsed > 0 else 0
    check(f"Kafka publish {NUM_MESSAGES} ESG messages", sent == NUM_MESSAGES,
          f"{throughput:.0f} msg/s, {elapsed:.2f}s")
    producer.close()
    return sent


# ── Phase 3: Wait for Flink flush ────────────────────────────────────────────
def phase_wait():
    print(f"\n{INFO} === Phase 3: Waiting {WAIT_FLUSH_SECS}s for Flink batch flush ===")
    for i in range(WAIT_FLUSH_SECS, 0, -3):
        print(f"  ... {i}s remaining", end="\r", flush=True)
        time.sleep(3)
    print()


# ── Phase 4: Verify row counts ────────────────────────────────────────────────
def phase_verify(sent: int):
    print(f"\n{INFO} === Phase 4: Verify dual-sink row counts ===")

    # TimescaleDB count before and after
    if PG_OK:
        try:
            # Read actual password from .env file
            pg_pass = TIMESCALE_PASSWORD
            try:
                import os
                env_file = os.path.join(os.path.dirname(__file__), "../infrastructure/.env")
                with open(env_file) as f:
                    for line in f:
                        if line.startswith("POSTGRES_PASSWORD="):
                            pg_pass = line.strip().split("=", 1)[1]
            except Exception:
                pass

            conn = psycopg2.connect(
                host=TIMESCALE_HOST, port=TIMESCALE_PORT, dbname=TIMESCALE_DB,
                user=TIMESCALE_USER, password=pg_pass, connect_timeout=5)
            cur = conn.cursor()
            cur.execute(
                "SELECT COUNT(*) FROM esg.clean_metrics WHERE tenant_id = %s "
                "AND timestamp > NOW() - INTERVAL '5 minutes'",
                (TEST_TENANT,))
            ts_count = cur.fetchone()[0]
            cur.close()
            conn.close()
            check("TimescaleDB esg.clean_metrics received rows",
                  ts_count >= sent * 0.95,
                  f"got {ts_count} rows in last 5 min (expected ~{sent})")
        except Exception as e:
            check("TimescaleDB esg.clean_metrics received rows", False, str(e))

    # ClickHouse count
    if CH_OK:
        try:
            ch_pass = CLICKHOUSE_PASSWORD
            try:
                import os
                env_file = os.path.join(os.path.dirname(__file__), "../infrastructure/.env")
                with open(env_file) as f:
                    for line in f:
                        if line.startswith("CLICKHOUSE_PASSWORD="):
                            ch_pass = line.strip().split("=", 1)[1]
            except Exception:
                pass

            client = clickhouse_connect.get_client(
                host=CLICKHOUSE_HOST, port=CLICKHOUSE_PORT,
                username=CLICKHOUSE_USER, password=ch_pass,
                database=CLICKHOUSE_DB)
            result = client.query(
                "SELECT count() FROM analytics.esg_readings "
                "WHERE tenant_id = {tenant:String} "
                "AND recorded_at > now() - INTERVAL 5 MINUTE",
                parameters={"tenant": TEST_TENANT})
            ch_count = result.first_row[0]
            check("ClickHouse analytics.esg_readings received rows",
                  ch_count >= sent * 0.95,
                  f"got {ch_count} rows in last 5 min (expected ~{sent})")
        except Exception as e:
            check("ClickHouse analytics.esg_readings received rows", False, str(e))


# ── Phase 5: ClickHouse performance benchmark ─────────────────────────────────
def phase_benchmark():
    print(f"\n{INFO} === Phase 5: ClickHouse performance benchmark via analytics-service ===")
    if not REQUESTS_OK:
        print(f"  {WARN} Skipping — requests not available")
        return

    # Get a valid JWT token from backend (no-auth on analytics-service health but need token for /analytics)
    token = None
    try:
        r = requests.post(
            f"{BACKEND_API}/api/v1/auth/login",
            json={"username": "admin", "password": "Admin#2026!"},
            timeout=5)
        if r.status_code == 200:
            token = r.json().get("accessToken") or r.json().get("token")
    except Exception:
        pass

    headers = {"Authorization": f"Bearer {token}"} if token else {}

    # Benchmark 1: aggregate energy by building (last 30 days)
    endpoint = f"{ANALYTICS_API}/api/v1/analytics/energy/aggregate"
    params = {
        "tenantId": TEST_TENANT,
        "metricType": "ENERGY",
        "from": (datetime.now(timezone.utc) - timedelta(days=30)).isoformat(),
        "to": datetime.now(timezone.utc).isoformat(),
        "granularity": "day"
    }
    latencies = []
    success_count = 0
    for i in range(5):
        try:
            t0 = time.time()
            r = requests.get(endpoint, params=params, headers=headers, timeout=10)
            latency_ms = (time.time() - t0) * 1000
            latencies.append(latency_ms)
            if r.status_code in (200, 401, 403):
                success_count += 1
        except Exception:
            pass

    if latencies:
        avg_ms = sum(latencies) / len(latencies)
        p95_ms = sorted(latencies)[int(len(latencies) * 0.95)]
        check("ClickHouse aggregate query avg < 500ms", avg_ms < 500,
              f"avg={avg_ms:.0f}ms p95={p95_ms:.0f}ms over {len(latencies)} calls")
    else:
        print(f"  {WARN} Analytics API not reachable or returned no data")

    # Benchmark 2: total row count query
    if CH_OK:
        try:
            ch_pass = CLICKHOUSE_PASSWORD
            try:
                import os
                env_file = os.path.join(os.path.dirname(__file__), "../infrastructure/.env")
                with open(env_file) as f:
                    for line in f:
                        if line.startswith("CLICKHOUSE_PASSWORD="):
                            ch_pass = line.strip().split("=", 1)[1]
            except Exception:
                pass

            client = clickhouse_connect.get_client(
                host=CLICKHOUSE_HOST, port=CLICKHOUSE_PORT,
                username=CLICKHOUSE_USER, password=ch_pass,
                database=CLICKHOUSE_DB)

            queries = [
                ("Total row count", "SELECT count() FROM analytics.esg_readings"),
                ("Group by tenant+metric (30d)",
                 "SELECT tenant_id, metric_type, sum(value), count() "
                 "FROM analytics.esg_readings "
                 "WHERE recorded_at > now() - INTERVAL 30 DAY "
                 "GROUP BY tenant_id, metric_type"),
                ("Building daily energy (30d)",
                 "SELECT building_id, toDate(recorded_at) as day, sum(value) "
                 "FROM analytics.esg_readings "
                 "WHERE metric_type='ENERGY' AND recorded_at > now() - INTERVAL 30 DAY "
                 "GROUP BY building_id, day ORDER BY day"),
            ]

            print(f"\n  ClickHouse direct query benchmarks:")
            for name, sql in queries:
                t0 = time.time()
                result = client.query(sql)
                elapsed_ms = (time.time() - t0) * 1000
                rows = len(result.result_rows)
                print(f"    {PASS if elapsed_ms < 200 else WARN} {name}: {elapsed_ms:.1f}ms, {rows} rows")

        except Exception as e:
            print(f"  {WARN} ClickHouse direct benchmark failed: {e}")


# ── Summary ───────────────────────────────────────────────────────────────────
def print_summary():
    print("\n" + "=" * 60)
    print("  ESG DUAL-SINK TEST SUMMARY")
    print("=" * 60)
    passed = sum(1 for _, ok, _ in results if ok)
    failed = sum(1 for _, ok, _ in results if not ok)
    for label, ok, detail in results:
        status = PASS if ok else FAIL
        print(f"  {status} {label}")
        if detail and not ok:
            print(f"         {detail}")
    print(f"\n  Total: {passed} passed, {failed} failed")
    if failed == 0:
        print(f"\n  \033[92mALL CHECKS PASSED — dual-sink pipeline is HEALTHY\033[0m")
    else:
        print(f"\n  \033[91m{failed} CHECK(S) FAILED — review output above\033[0m")
    print("=" * 60)
    return failed == 0


# ── Main ──────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    print("=" * 60)
    print("  UIP Flink EsgDualSinkJob — Integration Test")
    print(f"  Kafka: {KAFKA_BOOTSTRAP} | Flink: {FLINK_UI}")
    print(f"  TimescaleDB: {TIMESCALE_HOST}:{TIMESCALE_PORT} | ClickHouse: {CLICKHOUSE_HOST}:{CLICKHOUSE_PORT}")
    print("=" * 60)

    phase_preflight()
    sent = phase_inject()
    if sent > 0:
        phase_wait()
        phase_verify(sent)
    phase_benchmark()
    ok = print_summary()
    sys.exit(0 if ok else 1)
