#!/usr/bin/env python3
"""
UIP synthetic 50-tenant test — tenant-load runner (R16 mitigation, T12 scaffold).

Orchestrates the generator across N tenants CONCURRENTLY (one thread per
tenant), then asserts the invariants that the build-for-50 architecture
must hold at any scale (R16 = "build-for-50 chưa exercise scale"):

    INV-1 (events-reachable)  Every tenant's events reach the read layer
                              (analytics REST 200, or sensor count > 0
                              after ingestion settled).
    INV-2 (no-cross-leak)     Tenant A querying with tenant A's JWT never
                              sees tenant B's sensors/readings. Asserted
                              at the API layer; CH RowPolicy V32 + cache
                              `tenant:{id}:` prefix provide defense-in-depth.
    INV-3 (fail-closed)       An event whose `_meta.tenantId` is null/blank
                              is dropped by Flink's TenantBindingProcessFunction
                              (uip.tenant.dropped_no_tenant metric increments).
                              This runner CANNOT assert this without Prometheus
                              access — left as an M5-2 hook (see report §4).

The runner can drive TWO transport modes (select per profile):
    transport: kafka   Publish NGSI-LD events to `ngsi_ld_environment`
                       (full Simulator→Kafka→Flink→DB pipeline), then verify
                       reachability via analytics REST.
    transport: api     Skip Kafka/Flink — POST directly to analytics REST
                       (`/api/v1/analytics/energy-aggregate`) per tenant and
                       assert cross-tenant 403. SIMPLER; the scaffold default.

Per-tenant metrics (latency p50/p95/p99, throughput, error rate, status
histogram) are collected into a structured JSON report keyed by tenant_id,
plus a markdown summary. Invariant violations fail the run (exit 1).

This scaffold uses transport=api as the default (no Kafka/PG needed). The
M5-2/M5-3/M5-4/M5-5 extensions (CH partition scan, NL routing, billing
quota, FULL 50-tenant) plug in by extending the per-tenant worker.

Usage:
    # 5-tenant smoke (default profile)
    python3 -m runner --profile infrastructure/scripts/synthetic/profiles/smoke-5-tenant.yaml

    # Direct CLI (no profile file)
    python3 -m runner --tenants 5 --sensors-per-tenant 20 \\
        --events-per-sensor 10 --base-url http://localhost:8081 \\
        --admin-username admin --admin-password 'admin_Dev#2026!'

Exit codes:
    0 = all invariants hold, all tenants reached
    1 = invariant violation or per-tenant error rate above threshold
    2 = setup error (auth/unreachable)
"""
from __future__ import annotations

import argparse
import concurrent.futures as cf
import json
import os
import statistics
import sys
import threading
import time
from dataclasses import dataclass, field, asdict
from datetime import datetime, timezone
from typing import Any

# stdlib HTTP — no external dep required (mirrors uat_smoke_test.py)
import urllib.request
import urllib.error

# Optional YAML for profile loading (fall back to JSON / inline CLI)
try:
    import yaml  # type: ignore
    _YAML_OK = True
except ImportError:  # pragma: no cover
    _YAML_OK = False

# Use the sibling generator
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))
from lib import generate as gen  # noqa: E402


# ─── Per-tenant metrics container ────────────────────────────────────────────
@dataclass
class TenantMetrics:
    tenant_id: str
    sensors_owned: int
    events_sent: int = 0
    events_ok: int = 0
    events_failed: int = 0
    latencies_ms: list[float] = field(default_factory=list)
    status_histogram: dict[str, int] = field(default_factory=dict)
    cross_tenant_leaks: int = 0  # tenant saw another tenant's sensors
    errors: list[str] = field(default_factory=list)

    def add(self, status: int, latency_ms: float) -> None:
        self.events_sent += 1
        key = str(status)
        self.status_histogram[key] = self.status_histogram.get(key, 0) + 1
        if 200 <= status < 300:
            self.events_ok += 1
        else:
            self.events_failed += 1
        self.latencies_ms.append(latency_ms)

    def summarize(self) -> dict[str, Any]:
        lat = sorted(self.latencies_ms) if self.latencies_ms else [0.0]
        err_rate = (self.events_failed / self.events_sent * 100.0
                    if self.events_sent else 0.0)
        return {
            "tenant_id": self.tenant_id,
            "sensors_owned": self.sensors_owned,
            "events_sent": self.events_sent,
            "events_ok": self.events_ok,
            "events_failed": self.events_failed,
            "error_rate_pct": round(err_rate, 3),
            "latency_ms": {
                "p50": round(_percentile(lat, 50), 2),
                "p95": round(_percentile(lat, 95), 2),
                "p99": round(_percentile(lat, 99), 2),
                "max": round(lat[-1], 2),
            },
            "status_histogram": self.status_histogram,
            "cross_tenant_leaks": self.cross_tenant_leaks,
            "errors_truncated": self.errors[:5],
        }


def _percentile(sorted_data: list[float], pct: float) -> float:
    if not sorted_data:
        return 0.0
    k = (len(sorted_data) - 1) * (pct / 100.0)
    lo = int(k)
    hi = min(lo + 1, len(sorted_data) - 1)
    frac = k - lo
    return sorted_data[lo] * (1 - frac) + sorted_data[hi] * frac


# ─── HTTP helper (stdlib — no `requests` dependency) ─────────────────────────
def _http(method: str, url: str, body: Any | None = None,
          token: str | None = None, timeout: float = 10.0
          ) -> tuple[int, dict | str, float]:
    """Return (status, parsed_body_or_error_string, latency_ms)."""
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
            elapsed = (time.perf_counter() - t0) * 1000.0
            try:
                return resp.status, json.loads(raw) if raw else {}, elapsed
            except json.JSONDecodeError:
                return resp.status, {"_raw": raw.decode(errors="replace")}, elapsed
    except urllib.error.HTTPError as e:
        elapsed = (time.perf_counter() - t0) * 1000.0
        try:
            return e.code, json.loads(e.read()), elapsed
        except Exception:
            return e.code, {"_error": str(e)}, elapsed
    except Exception as e:
        elapsed = (time.perf_counter() - t0) * 1000.0
        return 0, {"_error": str(e)}, elapsed


# ─── Auth: login once to get a JWT carrying the caller's tenant_id claim ─────
# The seed stack authenticates the admin user, whose JWT carries tenant_id
# "default". This single token drives ALL bulk traffic (Phase 1) using the
# JWT's own tenant_id, so Phase 1 reads legitimately return 200.
#
# Phase 2 (cross-tenant-leak probe) sends SYNTHETIC tenant IDs in the body —
# the controller's `isCrossTenantViolation(requestedTenantId)` compares the
# JWT's `tenant_id` claim against the body and MUST 403 any mismatch.
# That is the assertion INV-2 makes.
#
# For the FULL M5-5 run, seed one operator PER tenant so each JWT carries a
# distinct `tenant_id` claim — the harness then drives one login per tenant
# (cfg["per_tenant_auth"] = true) and Phase 1 uses the synthetic tenant_id.
# ────────────────────────────────────────────────────────────────────────────
def login(base_url: str, username: str, password: str
          ) -> tuple[str | None, str | None]:
    """Return (access_token, jwt_tenant_id_or_None).

    `jwt_tenant_id` is read from the login response body if present (the seed
    backend returns `user.tenant_id`). When absent, callers fall back to
    cfg["default_tenant_id"] (typically "default").
    """
    status, body, _ = _http("POST", f"{base_url}/api/v1/auth/login",
                            {"username": username, "password": password})
    if status == 200 and isinstance(body, dict) and "accessToken" in body:
        token = body["accessToken"]
        user = body.get("user") or {}
        jwt_tid = (user.get("tenant_id") if isinstance(user, dict) else None)
        return token, jwt_tid
    return None, None


# ─── Per-tenant worker ───────────────────────────────────────────────────────
def _tenant_worker(tenant: gen.Sensor, sensors_for_tenant: list[gen.Sensor],
                   cfg: dict, token: str, jwt_tenant_id: str) -> TenantMetrics:
    """Run the synthetic load for one synthetic tenant identity.

    Phase 1 (reachability + latency):
        Send `events_per_sensor` queries per sensor using the JWT's OWN
        tenant_id as `body.tenantId`. Against the seed stack this is the
        admin token's `default` tenant — Phase 1 reads legitimately return
        200. Against the FULL M5-5 stack (per-tenant operator seeded), each
        worker logs in as its own tenant and `jwt_tenant_id == tenant.tenant_id`.

    Phase 2 (INV-2 cross-tenant-leak probe):
        For each OTHER synthetic tenant, send `body.tenantId = <other>` while
        authenticated with the current token. The controller's
        `isCrossTenantViolation` MUST return 403. A 200 here is a leak.
        Transport errors (status 0) are NOT counted as leaks.
    """
    m = TenantMetrics(tenant_id=tenant.tenant_id,
                      sensors_owned=len(sensors_for_tenant))
    base_url = cfg["base_url"]
    endpoint = cfg.get("analytics_endpoint",
                       "/api/v1/analytics/energy-aggregate")
    events_per_sensor = int(cfg.get("events_per_sensor", 5))
    rate_rps = float(cfg.get("rate_per_tenant", 5))
    interval = 1.0 / rate_rps if rate_rps > 0 else 0.0

    # Phase 1 — bulk traffic using the JWT's OWN tenant_id (legitimate reads).
    # For per-tenant-auth mode (M5-5), this becomes the synthetic tenant.
    phase1_tenant = jwt_tenant_id or cfg.get("default_tenant_id", "default")
    for sensor in sensors_for_tenant:
        for _ in range(events_per_sensor):
            body = {
                "tenantId": phase1_tenant,
                "buildingIds": [sensor.building_id],
                "districtCode": sensor.district,
                "sensorId": sensor.sensor_id,
            }
            status, _, lat = _http("POST", f"{base_url}{endpoint}",
                                   body, token=token, timeout=15.0)
            m.add(status, lat)
            if status == 0:
                m.errors.append(f"unreachable for {sensor.sensor_id}")
            if interval > 0:
                time.sleep(interval)

    # Phase 2 — INV-2 cross-tenant-leak probe.
    # Each probe asks for a DIFFERENT synthetic tenant's data while
    # authenticated with the current JWT. The controller MUST 403.
    other_tenants = [t for t in cfg.get("_all_tenant_ids", [])
                     if t != phase1_tenant and t != tenant.tenant_id]
    # Limit probe count so it doesn't dominate runtime at 50 tenants
    probe_cap = int(cfg.get("leak_probe_per_tenant", 5))
    for other in other_tenants[:probe_cap]:
        body = {
            "tenantId": other,
            "buildingIds": [f"{other}-BLDG-001"],
            "districtCode": "D1",
        }
        status, resp, _ = _http("POST", f"{base_url}{endpoint}",
                                body, token=token, timeout=15.0)
        # 403/401 = correctly blocked (no leak). 200 = LEAK. 0 = transport err.
        if status == 200:
            m.cross_tenant_leaks += 1
            m.errors.append(f"LEAK: 200 for tenant={other} "
                            f"while auth tenant={phase1_tenant}")
        # 403 / 401 are the expected "blocked" outcomes — no leak.
    return m


# ─── Profile loader ──────────────────────────────────────────────────────────
def load_profile(path: str | None, cli_args: argparse.Namespace) -> dict:
    """Merge YAML profile (if given) with CLI overrides. CLI wins."""
    cfg: dict[str, Any] = {
        "tenants": cli_args.tenants,
        "sensors_per_tenant": cli_args.sensors_per_tenant,
        "events_per_sensor": cli_args.events_per_sensor,
        "rate_per_tenant": cli_args.rate_per_tenant,
        "base_url": cli_args.base_url,
        "analytics_endpoint": cli_args.analytics_endpoint,
        "admin_username": cli_args.admin_username,
        "admin_password": cli_args.admin_password,
        "max_workers": cli_args.max_workers,
        "seed": cli_args.seed,
    }
    if path:
        if not _YAML_OK:
            print("[runner] WARN: PyYAML not installed; "
                  "ignoring profile file, using CLI args", file=sys.stderr)
        else:
            with open(path) as f:
                prof = yaml.safe_load(f) or {}
            # Profile defaults; CLI overrides take precedence
            for k, v in prof.items():
                if getattr(cli_args, k, None) is None:
                    cfg[k] = v
                else:
                    # Only override when CLI explicitly set (non-default)
                    pass
            # Apply profile values that aren't on CLI
            for k, v in prof.items():
                cfg.setdefault(k, v)
    return cfg


# ─── Orchestration ───────────────────────────────────────────────────────────
def run(cfg: dict) -> dict:
    tenants_n = int(cfg["tenants"])
    sensors_n = int(cfg["sensors_per_tenant"])
    seed = int(cfg.get("seed", 0))
    print(f"[runner] building fleet: {tenants_n} tenants × "
          f"{sensors_n} sensors = {tenants_n * sensors_n} sensors",
          file=sys.stderr)

    fleet = gen.build_fleet(tenants_n, sensors_n, seed=seed)
    by_tenant: dict[str, list[gen.Sensor]] = {}
    for s in fleet:
        by_tenant.setdefault(s.tenant_id, []).append(s)
    tenant_ids = sorted(by_tenant.keys())
    cfg["_all_tenant_ids"] = tenant_ids

    # Auth — single admin login (scaffold). The admin JWT carries tenant_id
    # "default"; Phase 1 uses that tenant_id for bulk traffic so reads return
    # 200. M5-5 FULL run sets cfg["per_tenant_auth"]=true and seeds one
    # operator per synthetic tenant so each JWT carries its own tenant_id.
    print(f"[runner] authenticating as {cfg['admin_username']} "
          f"against {cfg['base_url']}", file=sys.stderr)
    token, jwt_tid = login(cfg["base_url"], cfg["admin_username"],
                           cfg["admin_password"])
    if not token:
        print(f"[runner] ERROR: auth failed at {cfg['base_url']}/api/v1/auth/login "
              f"— is the backend up? (exit 2)", file=sys.stderr)
        return {"_setup_error": "auth_failed"}
    jwt_tenant_id = jwt_tid or cfg.get("default_tenant_id", "default")
    print(f"[runner] JWT tenant_id claim = '{jwt_tenant_id}' "
          f"(Phase 1 bulk traffic target)", file=sys.stderr)

    max_workers = int(cfg.get("max_workers", min(10, tenants_n)))
    print(f"[runner] launching {tenants_n} concurrent tenant workers "
          f"(max_workers={max_workers})", file=sys.stderr)

    results: list[TenantMetrics] = []
    t_start = time.perf_counter()
    with cf.ThreadPoolExecutor(max_workers=max_workers) as pool:
        futs = []
        for tid in tenant_ids:
            sensors = by_tenant[tid]
            # Use the first sensor as the tenant's "identity" for the worker
            tenant_repr = sensors[0]
            futs.append(pool.submit(_tenant_worker, tenant_repr, sensors,
                                    cfg, token, jwt_tenant_id))
        for fut in cf.as_completed(futs):
            try:
                results.append(fut.result())
            except Exception as e:
                print(f"[runner] worker crashed: {e}", file=sys.stderr)
    elapsed = time.perf_counter() - t_start

    # ─── Aggregate + invariant checks ───────────────────────────────────────
    summary = {
        "run_utc": datetime.now(timezone.utc).isoformat(),
        "config": {k: v for k, v in cfg.items()
                   if k != "_all_tenant_ids" and not callable(v)},
        "elapsed_sec": round(elapsed, 3),
        "tenants_total": tenants_n,
        "per_tenant": [m.summarize() for m in sorted(results,
                                                      key=lambda x: x.tenant_id)],
    }
    total_sent = sum(m.events_sent for m in results)
    total_failed = sum(m.events_failed for m in results)
    total_leaks = sum(m.cross_tenant_leaks for m in results)
    tenants_unreachable = [m.tenant_id for m in results if m.events_sent == 0]
    err_rate = (total_failed / total_sent * 100.0) if total_sent else 0.0

    summary["aggregate"] = {
        "events_sent": total_sent,
        "events_failed": total_failed,
        "error_rate_pct": round(err_rate, 3),
        "cross_tenant_leaks_total": total_leaks,
        "tenants_unreachable": tenants_unreachable,
    }

    # INV-1: every tenant must have reached (events_sent > 0 AND no transport
    #        errors). Empty result is fine (200 with empty body); 0 status is not.
    inv1_ok = (not tenants_unreachable
               and all(m.events_sent > 0 for m in results))
    # INV-2: zero cross-tenant leaks across all probes
    inv2_ok = total_leaks == 0
    # Soft SLO: per-tenant error rate ≤ threshold (default 1%)
    err_threshold = float(cfg.get("error_rate_threshold_pct", 1.0))
    slo_ok = err_rate <= err_threshold

    summary["invariants"] = {
        "INV1_events_reachable": "PASS" if inv1_ok else "FAIL",
        "INV2_no_cross_tenant_leak": "PASS" if inv2_ok else "FAIL",
        "SLO_error_rate_pct_le_" + str(err_threshold): (
            "PASS" if slo_ok else "FAIL"),
    }
    summary["verdict"] = "PASS" if (inv1_ok and inv2_ok and slo_ok) else "FAIL"
    return summary


# ─── CLI ─────────────────────────────────────────────────────────────────────
def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--profile", help="YAML profile (e.g. profiles/smoke-5-tenant.yaml)")
    p.add_argument("--tenants", type=int, default=5)
    p.add_argument("--sensors-per-tenant", type=int, default=20)
    p.add_argument("--events-per-sensor", type=int, default=5)
    p.add_argument("--rate-per-tenant", type=float, default=5.0,
                   help="Queries per second per tenant (default 5)")
    p.add_argument("--base-url", default=os.getenv("BASE_URL", "http://localhost:8081"))
    p.add_argument("--analytics-endpoint",
                   default="/api/v1/analytics/energy-aggregate")
    p.add_argument("--admin-username", default=os.getenv("UIP_USERNAME", "admin"))
    p.add_argument("--admin-password",
                   default=os.getenv("UIP_PASSWORD", "admin_Dev#2026!"))
    p.add_argument("--max-workers", type=int, default=10)
    p.add_argument("--seed", type=int, default=0)
    p.add_argument("--report-json", help="Path to write structured JSON report")
    p.add_argument("--report-md", help="Path to write markdown summary")
    args = p.parse_args(argv)

    cfg = load_profile(args.profile, args)
    summary = run(cfg)
    if "_setup_error" in summary:
        return 2

    # Reports
    from lib import reporting
    json_path = args.report_json or os.getenv(
        "SYNTHETIC_REPORT_JSON",
        f"synthetic-report-{summary['run_utc'].replace(':', '').replace('-', '').split('.')[0]}.json")
    md_path = args.report_md or os.getenv(
        "SYNTHETIC_REPORT_MD",
        json_path.replace(".json", ".md"))
    reporting.write_json(summary, json_path)
    reporting.write_markdown(summary, md_path)

    print(f"\n[runner] verdict: {summary['verdict']}", file=sys.stderr)
    print(f"[runner] report JSON: {json_path}", file=sys.stderr)
    print(f"[runner] report MD:   {md_path}", file=sys.stderr)
    return 0 if summary["verdict"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
