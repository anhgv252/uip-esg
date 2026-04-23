#!/usr/bin/env python3
"""
UIP Smart City — UAT Smoke Test
================================
Chạy 10 smoke test cases cơ bản để verify UAT stack hoạt động.
Không cần bất kỳ secret nào — dùng credentials đã được seed sẵn.

Usage:
  make uat-test
  # hoặc trực tiếp:
  BASE_URL=http://localhost:8080 python3 scripts/uat_smoke_test.py
"""

from __future__ import annotations

import os
import sys
import json
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("uat-smoke")

try:
    import urllib.request
    import urllib.error
except ImportError:
    pass  # stdlib, always available

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")

_results: list[tuple[str, bool, str]] = []


def _req(method: str, path: str, body=None, token: str | None = None) -> tuple[int, dict]:
    url = BASE_URL + path
    data = json.dumps(body).encode() if body else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status, json.loads(resp.read())
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, {}
    except Exception as e:
        return 0, {"error": str(e)}


def _check(name: str, condition: bool, msg: str = "") -> None:
    status = "PASS" if condition else "FAIL"
    _results.append((name, condition, msg))
    if condition:
        log.info("  %-4s  %s", status, name)
    else:
        log.error("  %-4s  %s — %s", status, name, msg)


def run() -> None:
    log.info("=== UAT Smoke Tests: %s ===", BASE_URL)
    log.info("")

    # T01 — Health endpoint
    status, body = _req("GET", "/api/v1/health")
    _check("T01 Health endpoint reachable", status == 200, f"HTTP {status}")

    # T02 — Admin login
    status, body = _req("POST", "/api/v1/auth/login",
                        {"username": "admin", "password": "admin_Dev#2026!"})
    admin_ok = status == 200 and "accessToken" in body
    _check("T02 Admin login returns JWT", admin_ok, f"HTTP {status} — {body.get('message','')}")
    admin_token = body.get("accessToken", "")

    # T03 — Operator login
    status, body = _req("POST", "/api/v1/auth/login",
                        {"username": "operator", "password": "operator_Dev#2026!"})
    _check("T03 Operator login returns JWT",
           status == 200 and "accessToken" in body, f"HTTP {status}")

    # T04 — Citizen login
    status, body = _req("POST", "/api/v1/auth/login",
                        {"username": "citizen1", "password": "citizen_Dev#2026!"})
    citizen_ok = status == 200 and "accessToken" in body
    _check("T04 Citizen1 login returns JWT", citizen_ok, f"HTTP {status}")
    citizen_token = body.get("accessToken", "")

    if not admin_token:
        log.warning("Cannot proceed with authenticated tests — admin login failed")
        _print_summary()
        return

    # T05 — Sensors list
    status, body = _req("GET", "/api/v1/environment/sensors", token=admin_token)
    sensor_count = len(body) if isinstance(body, list) else body.get("total", 0)
    _check("T05 Sensors list returns ≥8 sensors",
           status == 200 and sensor_count >= 8, f"HTTP {status}, count={sensor_count}")

    # T06 — ESG metrics endpoint
    status, body = _req("GET", "/api/v1/esg/metrics?period=QUARTERLY", token=admin_token)
    _check("T06 ESG metrics endpoint accessible", status in (200, 204),
           f"HTTP {status}")

    # T07 — Traffic data accessible
    status, body = _req("GET", "/api/v1/traffic/counts", token=admin_token)
    _check("T07 Traffic endpoint accessible", status in (200, 204),
           f"HTTP {status}")

    # T08 — Alert events accessible
    status, body = _req("GET", "/api/v1/alerts/events", token=admin_token)
    _check("T08 Alert events accessible", status in (200, 204),
           f"HTTP {status}")

    # T09 — Citizen invoices (requires citizen token)
    if citizen_token:
        status, body = _req("GET", "/api/v1/citizen/invoices", token=citizen_token)
        _check("T09 Citizen invoices accessible", status in (200, 204),
               f"HTTP {status}")
    else:
        _check("T09 Citizen invoices accessible", False, "skipped — no citizen token")

    # T10 — Unauthorized access rejected
    status, _ = _req("GET", "/api/v1/environment/sensors")
    _check("T10 Unauthenticated request returns 401", status == 401, f"HTTP {status}")

    _print_summary()


def _print_summary() -> None:
    total  = len(_results)
    passed = sum(1 for _, ok, _ in _results if ok)
    failed = total - passed

    log.info("")
    log.info("─" * 45)
    log.info("SMOKE TEST RESULTS: %d/%d passed", passed, total)
    if failed:
        log.info("FAILED:")
        for name, ok, msg in _results:
            if not ok:
                log.info("  ✗ %s — %s", name, msg)
    log.info("─" * 45)

    if failed > 0:
        sys.exit(1)


if __name__ == "__main__":
    run()
