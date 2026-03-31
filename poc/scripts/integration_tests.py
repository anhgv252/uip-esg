"""
integration_tests.py  –  UIP ESG POC End-to-End Test Suite
===========================================================

23 test cases covering:
  • TimescaleDB data integrity  (esg.clean_metrics + esg.aggregate_metrics)
  • Error records               (error_mgmt.error_records – classification, completeness)
  • Pipeline stats              (valid ratio, throughput)
  • ESG API endpoints           (all 10 endpoints)

Single TimescaleDB connection (esg_db) for all DB tests.
Run after `make up` once the producer has finished
and Flink has processed all 100K messages (~3-5 min).

Usage:
  pip install psycopg2-binary requests
  python scripts/integration_tests.py
  # or: make test

Exit code: 0 = all passed | 1 = failures
"""

from __future__ import annotations

import os
import sys
import time
import traceback
import logging
from typing import Callable

import psycopg2
import psycopg2.extras
import requests

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("integration-tests")

TSDB_CFG = {
    "host":     os.getenv("TIMESCALE_HOST", "localhost"),
    "port":     int(os.getenv("TIMESCALE_PORT", "5432")),
    "dbname":   "esg_db",
    "user":     "esg_user",
    "password": "esg_pass",
}
ESG_API  = os.getenv("ESG_API_URL", "http://localhost:8000")
MIN_MSGS = int(os.getenv("MIN_EXPECTED_MSGS", "90000"))

# ── Test runner ───────────────────────────────────────────────────
_results: list[tuple[str, bool, str]] = []


def test(name: str):
    def decorator(fn: Callable):
        def wrapper(*args, **kwargs):
            try:
                fn(*args, **kwargs)
                _results.append((name, True, ""))
                log.info(f"  ✅ {name}")
            except AssertionError as e:
                _results.append((name, False, str(e)))
                log.error(f"  ❌ {name}: {e}")
            except Exception as e:
                tb = traceback.format_exc(limit=3)
                _results.append((name, False, tb))
                log.error(f"  💥 {name}: {e}")
        wrapper()
        return wrapper
    return decorator


def q(conn, sql, params=None):
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, params or [])
        return [dict(r) for r in cur.fetchall()]


def scalar(conn, sql, params=None):
    return list(q(conn, sql, params)[0].values())[0]


def connect_retry(cfg: dict, label: str, retries=15):
    for i in range(retries):
        try:
            conn = psycopg2.connect(**cfg)
            log.info(f"  ✅ Connected to {label}")
            return conn
        except Exception as e:
            log.warning(f"  ⏳ Waiting for {label} ({i+1}/{retries}): {e}")
            time.sleep(3)
    raise RuntimeError(f"Cannot connect to {label}")


def wait_for_data(conn, expected=MIN_MSGS, timeout=300):
    log.info(f"⏳ Waiting for ≥{expected:,} total processed records…")
    deadline = time.time() + timeout
    while time.time() < deadline:
        clean = scalar(conn, "SELECT COUNT(*) FROM esg.clean_metrics")
        errs  = scalar(conn, "SELECT COUNT(*) FROM error_mgmt.error_records")
        total = (clean or 0) + (errs or 0)
        log.info(f"   clean={clean:,}  errors={errs:,}  total={total:,}")
        if total >= expected:
            return
        time.sleep(10)
    log.warning("⚠️  Timeout – proceeding with partial data")


# ═══════════════════════════════════════════════════════════════════
#  DB TESTS
# ═══════════════════════════════════════════════════════════════════

def run_db_tests(conn):
    log.info("\n── esg.clean_metrics Tests ──────────────────────────")

    @test("TS: esg.clean_metrics has records")
    def _():
        n = scalar(conn, "SELECT COUNT(*) FROM esg.clean_metrics")
        assert n > 0, f"Expected > 0 clean records, got {n}"

    @test("TS: all 5 measure_types present")
    def _():
        types = {r["measure_type"] for r in
                 q(conn, "SELECT DISTINCT measure_type FROM esg.clean_metrics")}
        expected = {"electric_kwh", "water_m3", "temp_celsius", "co2_ppm", "humidity_pct"}
        missing = expected - types
        assert not missing, f"Missing measure_types: {missing}"

    @test("TS: all 3 sites present")
    def _():
        sites = {r["site_id"] for r in
                 q(conn, "SELECT DISTINCT site_id FROM esg.clean_metrics")}
        assert len(sites) >= 3, f"Expected ≥3 sites, got {sites}"

    @test("TS: quality_flag is always OK")
    def _():
        bad = scalar(conn,
            "SELECT COUNT(*) FROM esg.clean_metrics WHERE quality_flag != 'OK'")
        assert bad == 0, f"{bad} records with quality_flag != OK"

    @test("TS: no negative values in clean_metrics")
    def _():
        neg = scalar(conn, "SELECT COUNT(*) FROM esg.clean_metrics WHERE value < 0")
        assert neg == 0, f"{neg} negative values found"

    @test("TS: no values above 10 000 in clean_metrics")
    def _():
        hi = scalar(conn, "SELECT COUNT(*) FROM esg.clean_metrics WHERE value > 10000")
        assert hi == 0, f"{hi} values above threshold"

    @test("TS: meter_id never NULL in clean_metrics")
    def _():
        null_m = scalar(conn,
            "SELECT COUNT(*) FROM esg.clean_metrics WHERE meter_id IS NULL OR meter_id = ''")
        assert null_m == 0, f"{null_m} records with null meter_id"

    @test("TS: event_ts is always non-null in clean_metrics")
    def _():
        null_ts = scalar(conn,
            "SELECT COUNT(*) FROM esg.clean_metrics WHERE event_ts IS NULL")
        assert null_ts == 0, f"{null_ts} records with null event_ts"

    @test("TS: esg.aggregate_metrics table exists and is queryable")
    def _():
        n = scalar(conn, "SELECT COUNT(*) FROM esg.aggregate_metrics")
        assert n >= 0

    log.info("\n── error_mgmt.error_records Tests ───────────────────")

    @test("ERR: error_mgmt.error_records has records")
    def _():
        n = scalar(conn, "SELECT COUNT(*) FROM error_mgmt.error_records")
        assert n > 0, f"Expected > 0 error records, got {n}"

    @test("ERR: at least 5 distinct error_types present")
    def _():
        types = {r["error_type"] for r in
                 q(conn, "SELECT DISTINCT error_type FROM error_mgmt.error_records")}
        expected = {
            "MISSING_METER_ID", "MISSING_VALUE", "INVALID_VALUE_FORMAT",
            "OUT_OF_RANGE_NEGATIVE", "OUT_OF_RANGE_HIGH", "MISSING_UNIT", "MISSING_TIMESTAMP",
        }
        found = len(types & expected)
        assert found >= 5, f"Only {found} expected error types found: {types}"

    @test("ERR: all error records have error_type")
    def _():
        blank = scalar(conn,
            "SELECT COUNT(*) FROM error_mgmt.error_records "
            "WHERE error_type IS NULL OR error_type = ''")
        assert blank == 0, f"{blank} records missing error_type"

    @test("ERR: all error records have error_detail")
    def _():
        no_detail = scalar(conn,
            "SELECT COUNT(*) FROM error_mgmt.error_records WHERE error_detail IS NULL")
        assert no_detail == 0, f"{no_detail} records missing error_detail"

    @test("ERR: reviewed defaults to FALSE for new records")
    def _():
        unreviewed = scalar(conn,
            "SELECT COUNT(*) FROM error_mgmt.error_records WHERE reviewed = FALSE")
        assert unreviewed > 0, "Expected some unreviewed records"

    @test("ERR: dedup_key is always non-null (idempotency key present)")
    def _():
        null_key = scalar(conn,
            "SELECT COUNT(*) FROM error_mgmt.error_records WHERE dedup_key IS NULL")
        assert null_key == 0, f"{null_key} error records missing dedup_key"

    log.info("\n── Pipeline Stats Tests ─────────────────────────────")

    @test("PIPELINE: valid ratio within expected bounds [55%–85%]")
    def _():
        clean = scalar(conn, "SELECT COUNT(*) FROM esg.clean_metrics")
        errs  = scalar(conn, "SELECT COUNT(*) FROM error_mgmt.error_records")
        total = clean + errs
        assert total > 0, "No records at all"
        ratio = clean / total
        assert 0.55 <= ratio <= 0.85, f"Valid ratio {ratio:.2%} out of range"
        log.info(f"       valid ratio = {ratio:.2%}  ({clean:,} clean / {total:,} total)")

    @test("PIPELINE: total processed ≥ 80% of 100K messages sent")
    def _():
        clean = scalar(conn, "SELECT COUNT(*) FROM esg.clean_metrics")
        errs  = scalar(conn, "SELECT COUNT(*) FROM error_mgmt.error_records")
        total = clean + errs
        assert total >= MIN_MSGS * 0.80, \
            f"Only {total:,} records processed, expected ≥{int(MIN_MSGS*0.80):,}"


# ═══════════════════════════════════════════════════════════════════
#  API TESTS
# ═══════════════════════════════════════════════════════════════════

def run_api_tests():
    log.info("\n── API Tests ────────────────────────────────────────")

    def get(path: str, **params) -> dict:
        resp = requests.get(f"{ESG_API}{path}", params=params, timeout=10)
        assert resp.status_code == 200, \
            f"GET {path} → HTTP {resp.status_code}: {resp.text[:200]}"
        return resp.json()

    def post(path: str, body: dict):
        return requests.post(f"{ESG_API}{path}", json=body, timeout=10)

    @test("API: /health returns ok=true and esg_db up")
    def _():
        data = get("/health")
        assert data.get("ok") is True, f"health not ok: {data}"
        assert data["databases"].get("esg_db") == "up"

    @test("API: /esg/overview returns KPI data with records")
    def _():
        data = get("/esg/overview")
        assert data["ok"] is True
        assert len(data["data"]["kpis"]) > 0, "No KPIs returned"

    @test("API: /esg/overview scope filter isolates site-a")
    def _():
        data = get("/esg/overview", scope="site-a")
        assert data["ok"] is True
        for entry in data["data"]["site_summary"]:
            assert entry["site_id"] == "site-a", f"Unexpected site_id: {entry['site_id']}"

    @test("API: /esg/sites/{site_id} returns buildings breakdown")
    def _():
        data = get("/esg/sites/site-a")
        assert data["ok"] is True
        assert len(data["data"]["buildings"]) > 0, "No buildings returned"

    @test("API: /esg/aggregates returns data")
    def _():
        data = get("/esg/aggregates")
        assert data["ok"] is True

    @test("API: /esg/reports/summary daily")
    def _():
        data = get("/esg/reports/summary", period="daily")
        assert data["ok"] is True

    @test("API: /esg/data-quality returns quality stats")
    def _():
        data = get("/esg/data-quality")
        assert data["ok"] is True
        summary = data["data"]["summary"]
        assert summary["total_processed"] > 0
        log.info(f"       quality_rate = {summary['quality_rate_pct']}%")

    @test("API: /esg/data-quality/errors returns paginated list")
    def _():
        data = get("/esg/data-quality/errors", limit=10)
        assert data["ok"] is True
        assert isinstance(data["data"], list)

    @test("API: /esg/data-quality/errors error_type filter works")
    def _():
        data = get("/esg/data-quality/errors", error_type="MISSING_UNIT", limit=5)
        assert data["ok"] is True
        for row in data["data"]:
            assert row["error_type"] == "MISSING_UNIT"

    @test("API: POST review single error record")
    def _():
        errors = get("/esg/data-quality/errors", reviewed=False, limit=1)
        if not errors["data"]:
            log.info("       (no unreviewed records – skipping)")
            return
        rec_id = errors["data"][0]["id"]
        resp = post(f"/esg/data-quality/errors/{rec_id}/review",
                    {"reviewed_by": "test-runner", "notes": "Integration test"})
        assert resp.status_code == 200, f"Review failed: {resp.status_code}"
        assert resp.json()["data"]["reviewed"] is True

    @test("API: POST bulk-review by error_type")
    def _():
        resp = post("/esg/data-quality/errors/bulk-review", {
            "error_type":  "MISSING_TIMESTAMP",
            "reviewed_by": "test-runner",
            "notes":       "Bulk review – integration test",
        })
        assert resp.status_code == 200, f"Bulk review failed: {resp.status_code}"
        log.info(f"       bulk-reviewed {resp.json()['data']['updated']} records")

    @test("API: unknown meter returns HTTP 404")
    def _():
        resp = requests.get(
            f"{ESG_API}/esg/meters/no-such-meter-xyz-abc/timeseries", timeout=10)
        assert resp.status_code == 404, f"Expected 404, got {resp.status_code}"

    @test("API: bulk-review without filter returns HTTP 400")
    def _():
        resp = post("/esg/data-quality/errors/bulk-review", {"reviewed_by": "test"})
        assert resp.status_code == 400, f"Expected 400, got {resp.status_code}"


# ═══════════════════════════════════════════════════════════════════
#  MAIN
# ═══════════════════════════════════════════════════════════════════

def main():
    log.info("═" * 60)
    log.info("  UIP ESG POC  •  Integration Test Suite")
    log.info(f"  TimescaleDB : {TSDB_CFG['host']}:{TSDB_CFG['port']} (esg_db)")
    log.info(f"  ESG API     : {ESG_API}")
    log.info("═" * 60)

    conn = connect_retry(TSDB_CFG, "TimescaleDB (esg + error_mgmt schemas)")
    wait_for_data(conn)

    run_db_tests(conn)

    try:
        requests.get(f"{ESG_API}/health", timeout=5)
        run_api_tests()
    except Exception:
        log.warning("⚠️  ESG API not reachable – API tests skipped")

    conn.close()

    passed = sum(1 for _, ok, _ in _results if ok)
    failed = sum(1 for _, ok, _ in _results if not ok)

    log.info("")
    log.info("═" * 60)
    log.info(f"  RESULTS: {passed} passed  /  {failed} failed  /  {len(_results)} total")
    log.info("═" * 60)
    for name, ok_, msg in _results:
        log.info(f"  {'✅' if ok_ else '❌'} {name}")
        if not ok_ and msg:
            for line in msg.strip().splitlines()[:3]:
                log.info(f"       {line}")
    log.info("═" * 60)
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
