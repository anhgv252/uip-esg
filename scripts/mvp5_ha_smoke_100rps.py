#!/usr/bin/env python3
"""
UIP Smart City — MVP5 Sprint 1 (T02) HA Smoke Test — 100 RPS for 60 s
=====================================================================

Drives a read endpoint on the UIP backend (typically through Kong on :8000,
which fans out to the ClickHouse 2-node cluster + analytics-service) at a
constant target rate for a fixed duration, then asserts:

    p95 latency  ≤ 500 ms
    error rate   ≤ 0.01 %
    p50 latency  ≤ 200 ms (advisory)

Authenticates once with seed admin credentials (admin / admin_Dev#2026! by
default — same as scripts/uat_smoke_test.py). Uses only Python stdlib so it
runs anywhere without `pip install`.

Usage:
    python3 scripts/mvp5_ha_smoke_100rps.py \\
        --base-url http://localhost:8000 \\
        --endpoint /api/v1/environment/sensors/ENV-001/readings \\
        --rps 100 --duration 60 \\
        --username admin --password 'admin_Dev#2026!'

    # or via env vars (CI-friendly):
    HA_SMOKE_BASE_URL=http://localhost:8000 \\
    HA_SMOKE_RPS=100 HA_SMOKE_DURATION=60 \\
    HA_SMOKE_USERNAME=admin HA_SMOKE_PASSWORD='admin_Dev#2026!' \\
    python3 scripts/mvp5_ha_smoke_100rps.py

Exit code:
    0 = all thresholds met
    1 = one or more thresholds violated (details printed + JSON saved)
    2 = setup error (auth/login/unreachable host)

References:
    - docs/mvp5/runbooks/mvp5-sprint1-compose-ha-runbook.md §8
    - docs/mvp5/adr/ADR-048-compose-ha-test-topology.md
    - scripts/uat_smoke_test.py (credential pattern reused)
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed

LOG = logging.getLogger("ha-smoke")

DEFAULTS = {
    "base_url":  os.getenv("HA_SMOKE_BASE_URL",  "http://localhost:8000"),
    "endpoint":  os.getenv("HA_SMOKE_ENDPOINT",  "/api/v1/environment/sensors/ENV-001/readings"),
    "rps":       int(os.getenv("HA_SMOKE_RPS",       "100")),
    "duration":  int(os.getenv("HA_SMOKE_DURATION",  "60")),
    "username":  os.getenv("HA_SMOKE_USERNAME",  "admin"),
    "password":  os.getenv("HA_SMOKE_PASSWORD",  "admin_Dev#2026!"),
    "timeout":   float(os.getenv("HA_SMOKE_TIMEOUT", "10")),
    "out":       os.getenv("HA_SMOKE_OUTPUT", "g5-rerun-ha-smoke.json"),
}

# ---- Thresholds (T02 acceptance) ------------------------------------------------
P95_MS_LIMIT        = 500.0
P50_MS_ADVISORY     = 200.0
ERROR_RATE_LIMIT    = 0.0001   # 0.01 %
COMPLETION_FRACTION = 0.95     # ≥95% of planned requests must complete


# --------------------------------------------------------------------------------
def _http(method: str, url: str, token: str | None, timeout: float, body=None):
    """Single HTTP request → (status, latency_ms, error_str_or_None)."""
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            _ = resp.read()
            return resp.status, (time.perf_counter() - t0) * 1000.0, None
    except urllib.error.HTTPError as e:
        return e.code, (time.perf_counter() - t0) * 1000.0, f"HTTP {e.code}"
    except Exception as e:  # noqa: BLE001 — we report all failures
        return 0, (time.perf_counter() - t0) * 1000.0, repr(e)


def login(base_url: str, username: str, password: str, timeout: float) -> str:
    """Reuse the same /api/v1/auth/login contract as scripts/uat_smoke_test.py."""
    status, _, err = _http(
        "POST", f"{base_url}/api/v1/auth/login", token=None, timeout=timeout,
        body={"username": username, "password": password},
    )
    if status != 200:
        raise RuntimeError(f"login failed: status={status} err={err}")
    # re-issue to read body
    req = urllib.request.Request(
        f"{base_url}/api/v1/auth/login",
        data=json.dumps({"username": username, "password": password}).encode(),
        headers={"Content-Type": "application/json"}, method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = json.loads(resp.read())
    token = body.get("accessToken") or body.get("access_token")
    if not token:
        raise RuntimeError(f"login 200 but no accessToken in body: {body!r}")
    return token


# --------------------------------------------------------------------------------
def drive_load(base_url, endpoint, token, rps, duration, timeout):
    """
    Constant-rate scheduler: distributes `rps` requests per wall-second across
    a thread pool. Returns list of (status, latency_ms, error).
    """
    total_planned = rps * duration
    interval = 1.0 / rps
    results: list[tuple[int, float, str | None]] = []
    lock = threading.Lock()
    stop_at = time.perf_counter() + duration

    LOG.info("Driving %d RPS for %ds (planned=%d, interval=%.3fms) ...",
             rps, duration, total_planned, interval * 1000)

    # One worker per outstanding request; bounded by a pool sized to rps.
    pool = ThreadPoolExecutor(max_workers=min(rps, 256))

    def one_request(_):
        url = base_url + endpoint
        return _http("GET", url, token=token, timeout=timeout)

    next_dispatch = time.perf_counter()
    dispatched = 0
    futures = []
    try:
        while time.perf_counter() < stop_at and dispatched < total_planned:
            now = time.perf_counter()
            if now >= next_dispatch:
                futures.append(pool.submit(one_request, dispatched))
                dispatched += 1
                next_dispatch += interval
                # if we fell behind (slow scheduling), catch up in burst
                if next_dispatch < now:
                    next_dispatch = now + interval
            else:
                time.sleep(min(0.005, next_dispatch - now))
    finally:
        LOG.info("Dispatched %d/%d requests, awaiting completion ...", dispatched, total_planned)

    for fut in as_completed(futures):
        try:
            status, lat, err = fut.result()
        except Exception as e:  # noqa: BLE001
            status, lat, err = 0, 0.0, f"pool-error: {e!r}"
        with lock:
            results.append((status, lat, err))
    pool.shutdown(wait=True)
    return results


# --------------------------------------------------------------------------------
def summarize(results, rps, duration):
    n = len(results)
    planned = rps * duration
    latencies = sorted(r[1] for r in results if r[0] != 0)
    statuses = Counter(r[0] for r in results)
    errors = [r for r in results if r[2] is not None]
    err_count = sum(1 for r in results if r[2] is not None or r[0] >= 400)

    def percentile(sorted_xs, p):
        if not sorted_xs:
            return float("nan")
        k = max(0, min(len(sorted_xs) - 1, int(round((p / 100.0) * (len(sorted_xs) - 1)))))
        return sorted_xs[k]

    summary = {
        "planned_requests":     planned,
        "completed_requests":   n,
        "completion_fraction":  (n / planned) if planned else 0.0,
        "status_breakdown":     dict(statuses),
        "error_count":          err_count,
        "error_rate":           (err_count / n) if n else 1.0,
        "latency_ms": {
            "p50": percentile(latencies, 50),
            "p90": percentile(latencies, 90),
            "p95": percentile(latencies, 95),
            "p99": percentile(latencies, 99),
            "max": latencies[-1] if latencies else float("nan"),
            "mean": statistics.fmean(latencies) if latencies else float("nan"),
        },
        "thresholds": {
            "p95_ms_limit":        P95_MS_LIMIT,
            "p50_ms_advisory":     P50_MS_ADVISORY,
            "error_rate_limit":    ERROR_RATE_LIMIT,
            "completion_fraction": COMPLETION_FRACTION,
        },
        "sample_errors":        [e[2] for e in errors[:5]],
    }

    checks = [
        ("completion",
         summary["completion_fraction"] >= COMPLETION_FRACTION,
         f"completed {n}/{planned} ({summary['completion_fraction']:.1%}) "
         f"≥ {COMPLETION_FRACTION:.0%}"),
        ("p95_ms",
         summary["latency_ms"]["p95"] <= P95_MS_LIMIT,
         f"p95={summary['latency_ms']['p95']:.1f}ms ≤ {P95_MS_LIMIT:.0f}ms"),
        ("error_rate",
         summary["error_rate"] <= ERROR_RATE_LIMIT,
         f"error_rate={summary['error_rate']:.4%} ≤ {ERROR_RATE_LIMIT:.4%}"),
        ("p50_advisory",
         summary["latency_ms"]["p50"] <= P50_MS_ADVISORY,
         f"p50={summary['latency_ms']['p50']:.1f}ms ≤ {P50_MS_ADVISORY:.0f}ms (advisory)"),
    ]
    summary["checks"] = [{"name": n, "pass": p, "msg": m} for n, p, m in checks]
    summary["overall_pass"] = all(c[1] for c in checks)
    return summary


# --------------------------------------------------------------------------------
def main(argv=None):
    p = argparse.ArgumentParser(description="UIP HA 100 RPS smoke (T02)")
    p.add_argument("--base-url",  default=DEFAULTS["base_url"])
    p.add_argument("--endpoint",  default=DEFAULTS["endpoint"])
    p.add_argument("--rps",       type=int, default=DEFAULTS["rps"])
    p.add_argument("--duration",  type=int, default=DEFAULTS["duration"])
    p.add_argument("--username",  default=DEFAULTS["username"])
    p.add_argument("--password",  default=DEFAULTS["password"])
    p.add_argument("--timeout",   type=float, default=DEFAULTS["timeout"])
    p.add_argument("--out",       default=DEFAULTS["out"])
    p.add_argument("--skip-auth", action="store_true",
                   help="Do not login; use HA_SMOKE_TOKEN env var instead")
    args = p.parse_args(argv)

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )

    LOG.info("=== UIP HA Smoke (T02) — %s ===", args.base_url)
    LOG.info("endpoint=%s rps=%s duration=%ss timeout=%ss",
             args.endpoint, args.rps, args.duration, args.timeout)

    # 1) Pre-flight: reachability + auth
    LOG.info("Pre-flight: health probe ...")
    status, _, err = _http("GET", f"{args.base_url}/api/v1/health",
                           token=None, timeout=args.timeout)
    if status != 200:
        LOG.error("Pre-flight health failed: status=%s err=%s — abort.", status, err)
        return 2

    if args.skip_auth:
        token = os.getenv("HA_SMOKE_TOKEN", "")
        if not token:
            LOG.error("--skip-auth but HA_SMOKE_TOKEN not set")
            return 2
    else:
        try:
            token = login(args.base_url, args.username, args.password, args.timeout)
        except Exception as e:  # noqa: BLE001
            LOG.error("Login failed: %s", e)
            return 2
    LOG.info("Authenticated as %s.", args.username)

    # 2) Drive the load
    t0 = time.perf_counter()
    results = drive_load(args.base_url, args.endpoint, token,
                         args.rps, args.duration, args.timeout)
    wall = time.perf_counter() - t0
    LOG.info("Wall time %.1fs for %d completed requests.", wall, len(results))

    # 3) Summarize + assert
    summary = summarize(results, args.rps, args.duration)
    summary["config"] = {
        "base_url": args.base_url, "endpoint": args.endpoint,
        "rps": args.rps, "duration": args.duration, "wall_s": wall,
    }

    print()
    print("─" * 60)
    print(f"HA SMOKE RESULTS — {args.base_url}{args.endpoint}")
    print(f"  requests:    {summary['completed_requests']}/{summary['planned_requests']}"
          f" ({summary['completion_fraction']:.1%})")
    print(f"  status:      {summary['status_breakdown']}")
    print(f"  errors:      {summary['error_count']} ({summary['error_rate']:.4%})")
    print(f"  latency p50: {summary['latency_ms']['p50']:.1f} ms")
    print(f"  latency p95: {summary['latency_ms']['p95']:.1f} ms  (limit {P95_MS_LIMIT:.0f} ms)")
    print(f"  latency p99: {summary['latency_ms']['p99']:.1f} ms")
    print("─" * 60)
    for c in summary["checks"]:
        tag = "PASS" if c["pass"] else "FAIL"
        print(f"  [{tag}] {c['name']:14s} {c['msg']}")
    print("─" * 60)

    try:
        with open(args.out, "w") as f:
            json.dump(summary, f, indent=2)
        LOG.info("Saved JSON results to %s", args.out)
    except OSError as e:
        LOG.warning("Could not write %s: %s", args.out, e)

    return 0 if summary["overall_pass"] else 1


if __name__ == "__main__":
    sys.exit(main())
