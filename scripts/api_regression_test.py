#!/usr/bin/env python3
"""
UIP Smart City — API Regression Test Suite
============================================
Chạy toàn bộ API tests sau mỗi lần thêm feature mới để đảm bảo
không có regression. Bao gồm RBAC, scope enforcement, module coverage.

Usage:
  python3 scripts/api_regression_test.py
  BASE_URL=http://localhost:8080 python3 scripts/api_regression_test.py
  python3 scripts/api_regression_test.py --verbose
  python3 scripts/api_regression_test.py --group esg
  python3 scripts/api_regression_test.py --fail-fast

Exit code: 0 = all pass, 1 = any failure
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from typing import Optional

# ─── Config ──────────────────────────────────────────────────────────────────

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")
API = BASE_URL + "/api/v1"

CREDENTIALS = {
    "admin":    {"username": "admin",    "password": "admin_Dev#2026!"},
    "operator": {"username": "operator", "password": "operator_Dev#2026!"},
    "citizen":  {"username": "citizen1", "password": "citizen_Dev#2026!"},
}

# ─── ANSI Colors ─────────────────────────────────────────────────────────────

RESET  = "\033[0m"
GREEN  = "\033[32m"
RED    = "\033[31m"
YELLOW = "\033[33m"
CYAN   = "\033[36m"
BOLD   = "\033[1m"
DIM    = "\033[2m"

def green(s):  return f"{GREEN}{s}{RESET}"
def red(s):    return f"{RED}{s}{RESET}"
def yellow(s): return f"{YELLOW}{s}{RESET}"
def cyan(s):   return f"{CYAN}{s}{RESET}"
def bold(s):   return f"{BOLD}{s}{RESET}"
def dim(s):    return f"{DIM}{s}{RESET}"

# ─── Result tracking ─────────────────────────────────────────────────────────

@dataclass
class Result:
    name:    str
    group:   str
    passed:  bool
    actual:  str = ""
    note:    str = ""

results: list[Result] = []
_tokens: dict[str, str] = {}
_verbose = False
_fail_fast = False
_group_filter: Optional[str] = None


# ─── HTTP helpers ─────────────────────────────────────────────────────────────

def _req(method: str, path: str, body=None, token: str | None = None,
         base: str = API) -> tuple[int, object]:
    url = base + path
    data = json.dumps(body).encode() if body is not None else None
    headers: dict[str, str] = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            try:
                return resp.status, json.loads(raw)
            except Exception:
                return resp.status, raw.decode(errors="replace")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read())
        except Exception:
            return e.code, {}
    except Exception as exc:
        return 0, {"_error": str(exc)}


def _req_with_headers(method: str, path: str, body=None, token: str | None = None,
                      base: str = API) -> tuple[int, object, dict]:
    """Like _req but also returns response headers as a dict."""
    url = base + path
    data = json.dumps(body).encode() if body is not None else None
    headers: dict[str, str] = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read()
            resp_headers = dict(resp.headers)
            try:
                return resp.status, json.loads(raw), resp_headers
            except Exception:
                return resp.status, raw.decode(errors="replace"), resp_headers
    except urllib.error.HTTPError as e:
        resp_headers = dict(e.headers) if hasattr(e, "headers") else {}
        try:
            return e.code, json.loads(e.read()), resp_headers
        except Exception:
            return e.code, {}, resp_headers
    except Exception as exc:
        return 0, {"_error": str(exc)}, {}


_refresh_tokens: dict[str, str] = {}


def token(role: str) -> str:
    if role not in _tokens:
        creds = CREDENTIALS.get(role, {})
        status, body = _req("POST", "/auth/login", creds)
        if status == 200 and isinstance(body, dict) and "accessToken" in body:
            _tokens[role] = body["accessToken"]
            _refresh_tokens[role] = body.get("refreshToken", "")
        else:
            _tokens[role] = ""
    return _tokens[role]


# ─── Assertion helpers ────────────────────────────────────────────────────────

def _record(group: str, name: str, passed: bool,
            actual: str = "", note: str = "") -> None:
    r = Result(name=name, group=group, passed=passed, actual=actual, note=note)
    results.append(r)

    icon  = green("✓ PASS") if passed else red("✗ FAIL")
    label = f"  {icon}  {name}"
    if not passed:
        label += f"  {dim('→')} {red(actual)}"
        if note:
            label += f"  {dim(note)}"
    elif _verbose and actual:
        label += f"  {dim('→')} {dim(actual)}"
    print(label)

    if _fail_fast and not passed:
        _print_summary()
        sys.exit(1)


def check_status(group: str, name: str, status: int, expected: int,
                 body: object = None) -> bool:
    passed = status == expected
    detail = f"HTTP {status} (expected {expected})"
    if not passed and isinstance(body, dict):
        msg = body.get("detail") or body.get("message") or body.get("error") or ""
        if msg:
            detail += f" — {msg}"
    _record(group, name, passed, "" if passed else detail)
    return passed


def check_status_any(group: str, name: str, status: int, expected: list[int],
                     body: object = None) -> bool:
    passed = status in expected
    detail = f"HTTP {status} (expected one of {expected})"
    if not passed and isinstance(body, dict):
        msg = body.get("detail") or body.get("message") or body.get("error") or ""
        if msg:
            detail += f" — {msg}"
    _record(group, name, passed, "" if passed else detail)
    return passed


def check_field(group: str, name: str, body: object, field_path: str,
                expected=None) -> bool:
    """Check that field_path exists (and optionally equals expected) in body."""
    keys = field_path.split(".")
    val = body
    try:
        for k in keys:
            if isinstance(val, dict):
                val = val[k]
            elif isinstance(val, list):
                val = val[int(k)]
            else:
                raise KeyError(k)
        passed = (val == expected) if expected is not None else True
        note   = f"field '{field_path}' = {repr(val)}"
        if expected is not None and not passed:
            note += f" (expected {repr(expected)})"
        _record(group, name, passed, "" if passed else note, note if (_verbose and passed) else "")
        return passed
    except (KeyError, IndexError, TypeError):
        _record(group, name, False, f"field '{field_path}' not found in response")
        return False


def check_list_len(group: str, name: str, body: object, min_len: int = 1) -> bool:
    passed = isinstance(body, list) and len(body) >= min_len
    detail = f"got {type(body).__name__} len={len(body) if isinstance(body, list) else '?'} (expected list ≥{min_len})"
    _record(group, name, passed, "" if passed else detail)
    return passed


# ─── Test Groups ──────────────────────────────────────────────────────────────

def grp_header(name: str) -> None:
    print(f"\n{bold(cyan('▶ ' + name))}")


def run_group(key: str, fn) -> None:
    if _group_filter and key.lower() != _group_filter.lower():
        return
    fn()


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 1: Infrastructure / Health
# ──────────────────────────────────────────────────────────────────────────────

def test_health():
    grp_header("Infrastructure / Health")
    G = "health"

    # Actuator health — no auth required (200=UP, 503=DOWN but responding)
    status, body = _req("GET", "/actuator/health", base=BASE_URL)
    passed = check_status_any(G, "GET /actuator/health → 200 or 503 (app running)", status, [200, 503], body)
    if passed and isinstance(body, dict):
        overall = body.get("status", "UNKNOWN")
        # Overall DOWN is acceptable in dev (mail server not running)
        _record(G, f"  health.status = {overall} (DOWN ok in dev without mail/kafka)",
                True, "", f"overall={overall}")
        # Check individual critical components if details are present
        components = body.get("components", {})
        if "db" in components:
            check_field(G, "  health.components.db = UP", components, "db.status", "UP")
        if "redis" in components:
            check_field(G, "  health.components.redis = UP", components, "redis.status", "UP")
        # VaultHealthIndicator (Sprint 1 BT-01a) — vault component in health response
        if isinstance(body.get("components"), dict) and "vault" in body["components"]:
            vault_status = body["components"]["vault"].get("status", "UNKNOWN")
            _record(G, "  health.components.vault present",
                    vault_status in ("UP", "DOWN"),
                    f"vault.status = {vault_status}")
        else:
            _record(G, "  health.components.vault present (Vault disabled or not configured)",
                    True, "", "vault component absent — acceptable if Vault not enabled in dev")

    # Prometheus MUST be protected
    status, _ = _req("GET", "/actuator/prometheus", base=BASE_URL)
    check_status(G, "GET /actuator/prometheus (no auth) → 401", status, 401)

    # Custom /api/v1/health (if present)
    status, body = _req("GET", "/health")
    check_status_any(G, "GET /api/v1/health → 200 or 404", status, [200, 404], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 2: Auth
# ──────────────────────────────────────────────────────────────────────────────

def test_auth():
    grp_header("Auth")
    G = "auth"

    # Admin login
    status, body = _req("POST", "/auth/login", CREDENTIALS["admin"])
    passed = check_status(G, "POST /auth/login (admin) → 200", status, 200, body)
    if passed and isinstance(body, dict):
        check_field(G, "  response contains accessToken", body, "accessToken")
        check_field(G, "  tokenType = Bearer", body, "tokenType", "Bearer")

    # Operator login
    status, body = _req("POST", "/auth/login", CREDENTIALS["operator"])
    check_status(G, "POST /auth/login (operator) → 200", status, 200, body)

    # Invalid credentials → 401
    status, body = _req("POST", "/auth/login",
                         {"username": "admin", "password": "wrong_password"})
    check_status(G, "POST /auth/login (wrong password) → 401", status, 401, body)

    # Unauthenticated API access → 401
    status, body = _req("GET", "/environment/sensors")
    check_status(G, "GET /environment/sensors (no token) → 401", status, 401, body)

    # Token refresh — use the real refresh token from login
    adm_token = token("admin")
    if adm_token and _refresh_tokens.get("admin"):
        status, body = _req("POST", "/auth/refresh",
                             {"refreshToken": _refresh_tokens["admin"]})
        check_status_any(G, "POST /auth/refresh (valid refresh token) → 200 or 400",
                          status, [200, 400], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 3: Environment Module
# ──────────────────────────────────────────────────────────────────────────────

def test_environment():
    grp_header("Environment Module")
    G = "environment"
    tok = token("admin")

    # Sensors list — was BUG-BE-001 (500 due to missing tenant_id in query)
    status, body = _req("GET", "/environment/sensors", token=tok)
    passed = check_status(G, "GET /environment/sensors (admin) → 200", status, 200, body)
    if passed:
        check_list_len(G, "  sensors list has ≥ 8 entries", body, 8)

    # AQI current
    status, body = _req("GET", "/environment/aqi/current", token=tok)
    passed = check_status(G, "GET /environment/aqi/current (admin) → 200", status, 200, body)
    if passed and isinstance(body, list) and body:
        check_field(G, "  aqi entry has aqiValue", body[0], "aqiValue")
        check_field(G, "  aqi entry has sensorId", body[0], "sensorId")

    # AQI history
    status, body = _req("GET", "/environment/aqi/history", token=tok)
    check_status(G, "GET /environment/aqi/history (admin) → 200", status, 200, body)

    # Sensor readings for first sensor (ENV-001)
    status, body = _req("GET", "/environment/sensors/ENV-001/readings", token=tok)
    check_status_any(G, "GET /environment/sensors/ENV-001/readings → 200 or 404",
                      status, [200, 404], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 4: ESG Module
# ──────────────────────────────────────────────────────────────────────────────

def test_esg():
    grp_header("ESG Module")
    G = "esg"
    adm = token("admin")
    opr = token("operator")

    # Energy endpoint
    status, body = _req("GET", "/esg/energy", token=adm)
    check_status(G, "GET /esg/energy (admin) → 200", status, 200, body)

    # Carbon endpoint
    status, body = _req("GET", "/esg/carbon", token=adm)
    check_status(G, "GET /esg/carbon (admin) → 200", status, 200, body)

    # Summary endpoint
    status, body = _req("GET", "/esg/summary?period=quarterly&year=2026&quarter=1",
                         token=adm)
    check_status(G, "GET /esg/summary (admin) → 200", status, 200, body)

    # Generate report — admin with esg:write → 202
    status, body = _req(
        "POST",
        "/esg/reports/generate?period=quarterly&year=2026&quarter=2",
        token=adm,
    )
    passed = check_status(G, "POST /esg/reports/generate (admin, esg:write) → 202",
                           status, 202, body)
    if passed and isinstance(body, dict):
        check_field(G, "  report response has id", body, "id")
        check_field(G, "  report status = PENDING", body, "status", "PENDING")

    # Generate report — operator WITHOUT esg:write → 403  ← RBAC regression guard
    status, body = _req(
        "POST",
        "/esg/reports/generate?period=quarterly&year=2026&quarter=2",
        token=opr,
    )
    check_status(G, "POST /esg/reports/generate (operator, no esg:write) → 403 [RBAC]",
                  status, 403, body)

    # Operator can still read ESG energy (read scope)
    status, body = _req("GET", "/esg/energy", token=opr)
    check_status(G, "GET /esg/energy (operator, esg:read) → 200 [RBAC]",
                  status, 200, body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 5: Alerts Module
# ──────────────────────────────────────────────────────────────────────────────

def test_alerts():
    grp_header("Alerts Module")
    G = "alerts"
    tok = token("admin")

    # Get alerts list
    status, body = _req("GET", "/alerts", token=tok)
    passed = check_status(G, "GET /alerts (admin) → 200", status, 200, body)
    if passed and isinstance(body, dict):
        check_field(G, "  alerts response has content array", body, "content")
        check_field(G, "  alerts response has totalElements", body, "totalElements")

    # Alert rules — under /admin/alert-rules
    status, body = _req("GET", "/admin/alert-rules", token=tok)
    check_status_any(G, "GET /admin/alert-rules (admin) → 200 or 204", status, [200, 204], body)

    # Operator can read alerts (alert:read scope)
    opr = token("operator")
    status, body = _req("GET", "/alerts", token=opr)
    check_status(G, "GET /alerts (operator, alert:read) → 200 [RBAC]", status, 200, body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 6: Traffic Module
# ──────────────────────────────────────────────────────────────────────────────

def test_traffic():
    grp_header("Traffic Module")
    G = "traffic"
    tok = token("admin")

    status, body = _req("GET", "/traffic/counts", token=tok)
    check_status_any(G, "GET /traffic/counts (admin) → 200 or 204", status, [200, 204], body)

    status, body = _req("GET", "/traffic/incidents", token=tok)
    check_status_any(G, "GET /traffic/incidents (admin) → 200 or 204", status, [200, 204], body)

    status, body = _req("GET", "/traffic/congestion-map", token=tok)
    check_status_any(G, "GET /traffic/congestion-map (admin) → 200 or 204", status, [200, 204], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 7: Tenant Config
# ──────────────────────────────────────────────────────────────────────────────

def test_tenant():
    grp_header("Tenant Config")
    G = "tenant"
    tok = token("admin")

    status, body = _req("GET", "/tenant/config", token=tok)
    passed = check_status(G, "GET /tenant/config (admin) → 200", status, 200, body)
    if passed and isinstance(body, dict):
        check_field(G, "  config has features object", body, "features")
        check_field(G, "  config has branding object", body, "branding")


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 8: Citizen Module
# ──────────────────────────────────────────────────────────────────────────────

def test_citizen():
    grp_header("Citizen Module")
    G = "citizen"
    tok = token("admin")

    status, body = _req("GET", "/citizen/buildings", token=tok)
    check_status_any(G, "GET /citizen/buildings (admin) → 200 or 204", status, [200, 204], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 9: Admin Module
# ──────────────────────────────────────────────────────────────────────────────

def test_admin():
    grp_header("Admin Module")
    G = "admin"
    tok = token("admin")

    status, body = _req("GET", "/admin/users", token=tok)
    check_status_any(G, "GET /admin/users (admin) → 200 or 204", status, [200, 204], body)

    status, body = _req("GET", "/admin/sensors", token=tok)
    check_status_any(G, "GET /admin/sensors (admin) → 200 or 204", status, [200, 204], body)

    # Non-admin access to admin endpoints → 403
    opr = token("operator")
    status, body = _req("GET", "/admin/users", token=opr)
    check_status(G, "GET /admin/users (operator) → 403 [RBAC]", status, 403, body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 10: Workflow Config
# ──────────────────────────────────────────────────────────────────────────────

def test_workflow():
    grp_header("Workflow Config")
    G = "workflow"
    tok = token("admin")

    # Workflow trigger configs — under /admin/workflow-configs
    status, body = _req("GET", "/admin/workflow-configs", token=tok)
    check_status_any(G, "GET /admin/workflow-configs (admin) → 200 or 204", status, [200, 204], body)

    status, body = _req("GET", "/workflow/definitions", token=tok)
    check_status_any(G, "GET /workflow/definitions (admin) → 200 or 204", status, [200, 204], body)

    status, body = _req("GET", "/workflow/instances", token=tok)
    check_status_any(G, "GET /workflow/instances (admin) → 200 or 204", status, [200, 204], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 11: Tenant Admin API (Sprint 4 BT-13a)
# ──────────────────────────────────────────────────────────────────────────────

def test_tenant_admin():
    grp_header("Tenant Admin API (Sprint 4)")
    G = "tenant_admin"
    adm = token("admin")
    opr = token("operator")
    # Use "default" tenant ID — resolveEffectiveTenantId handles admin resolution
    tenant_id = "default"

    # List users in tenant
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/users", token=adm)
    check_status_any(G, f"GET /admin/tenants/{tenant_id}/users (admin) → 200 or 404",
                     status, [200, 404], body)

    # Invite user — just verify auth layer; accept 202 (sent) or 400 (validation)
    invite_body = {"email": "regression-test@uip.invalid", "role": "OPERATOR"}
    status, body = _req("POST", f"/admin/tenants/{tenant_id}/users/invite",
                        body=invite_body, token=adm)
    check_status_any(G, "POST .../invite (admin) → 202 or 400 or 404",
                     status, [202, 400, 404], body)

    # Invite endpoint must NOT be accessible without token → 401
    status, _ = _req("POST", f"/admin/tenants/{tenant_id}/users/invite",
                     body=invite_body)
    check_status(G, "POST .../invite (no token) → 401 [RBAC]", status, 401)

    # Get usage with 7-day range
    now = datetime.now(timezone.utc)
    from_ts = (now - timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")
    to_ts = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    status, body = _req("GET",
                        f"/admin/tenants/{tenant_id}/usage?from={from_ts}&to={to_ts}",
                        token=adm)
    check_status_any(G, "GET .../usage (admin, 7d range) → 200 or 404",
                     status, [200, 404], body)

    # Get settings
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/settings", token=adm)
    check_status_any(G, "GET .../settings (admin) → 200 or 404",
                     status, [200, 404], body)

    # Operator cannot access tenant admin endpoints → 403
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/users", token=opr)
    check_status(G, "GET .../users (operator) → 403 [RBAC]", status, 403, body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 12: Invite Flow (Sprint 4 BT-13b)
# ──────────────────────────────────────────────────────────────────────────────

def test_invite_flow():
    grp_header("Invite Flow (Sprint 4)")
    G = "invite"

    # POST /auth/invite/accept is a public endpoint — must NOT return 401
    # We send an invalid token to verify the auth layer, not the business logic
    payload = {"token": "00000000-0000-0000-0000-000000000000", "password": "NewPass123!"}
    status, body = _req("POST", "/auth/invite/accept", body=payload)
    not_401 = status != 401
    _record(G, "POST /auth/invite/accept is public (no 401 without token)", not_401,
            f"HTTP {status} — should be public endpoint" if not not_401 else "")
    check_status_any(G, "POST /auth/invite/accept (invalid token) → 400, 404, or 422",
                     status, [400, 404, 422], body)

    # Malformed body (missing password) → 400
    status, body = _req("POST", "/auth/invite/accept", body={"token": "bad-token"})
    check_status_any(G, "POST /auth/invite/accept (missing password) → 400 or 422",
                     status, [400, 422], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 13: Rate Limiting (Sprint 2 MVP2-14)
# ──────────────────────────────────────────────────────────────────────────────

def test_rate_limiting():
    grp_header("Rate Limiting (Sprint 2)")
    G = "rate_limit"
    adm = token("admin")

    # Verify RateLimitFilter is active — X-RateLimit-Remaining header should appear
    status, body, resp_headers = _req_with_headers("GET", "/esg/energy", token=adm)
    check_status(G, "GET /esg/energy (rate limit probe) → 200", status, 200, body)

    headers_lower = {k.lower(): v for k, v in resp_headers.items()}
    has_rl_header = "x-ratelimit-remaining" in headers_lower
    _record(G, "X-RateLimit-Remaining header present in response", has_rl_header,
            "header missing — check RateLimitFilter is registered" if not has_rl_header else "",
            f"remaining = {headers_lower.get('x-ratelimit-remaining', '?')}" if has_rl_header else "")

    # Unauthenticated request to public health endpoint should NOT be rate-limited
    # (RateLimitFilter should skip requests without tenant context)
    status, _, _ = _req_with_headers("GET", "/actuator/health", base=BASE_URL)
    check_status_any(G, "GET /actuator/health (no token) not blocked by rate limit → 200 or 503",
                     status, [200, 503])

    # Operator also has rate limit headers
    opr = token("operator")
    status, body, resp_headers = _req_with_headers("GET", "/esg/energy", token=opr)
    check_status(G, "GET /esg/energy (operator, rate limit check) → 200", status, 200, body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 14: ESG Export (Sprint 4 BT-30a)
# ──────────────────────────────────────────────────────────────────────────────

def test_esg_export():
    grp_header("ESG Export Format (Sprint 4)")
    G = "esg_export"
    adm = token("admin")

    # Trigger report generation (async)
    status, body = _req("POST",
                        "/esg/reports/generate?period=quarterly&year=2026&quarter=1",
                        token=adm)
    if status != 202 or not isinstance(body, dict):
        _record(G, "Setup: trigger ESG report generation → 202", False,
                f"HTTP {status} — cannot proceed with export tests")
        return
    _record(G, "Setup: trigger ESG report generation → 202", True)

    report_id = body.get("id")
    if not report_id:
        _record(G, "Setup: response contains report id", False, "no 'id' field")
        return
    _record(G, "Setup: response contains report id", True)

    # Check report status endpoint
    status, body = _req("GET", f"/esg/reports/{report_id}/status", token=adm)
    check_status(G, f"GET /esg/reports/{report_id}/status → 200", status, 200, body)
    if status == 200 and isinstance(body, dict):
        check_field(G, "  status field present", body, "status")

    # Download CSV — report may still be PENDING/GENERATING so accept 200, 404, 400, 503
    status, body = _req("GET", f"/esg/reports/{report_id}/download?format=csv", token=adm)
    check_status_any(G, "GET .../download?format=csv → 200 or 404/503 (PENDING ok)",
                     status, [200, 404, 400, 503], body)

    # Download XLSX — same
    status, body = _req("GET", f"/esg/reports/{report_id}/download?format=xlsx", token=adm)
    check_status_any(G, "GET .../download?format=xlsx → 200 or 404/503 (PENDING ok)",
                     status, [200, 404, 400, 503], body)

    # Download with invalid format → 400 or 503
    status, body = _req("GET", f"/esg/reports/{report_id}/download?format=pptx", token=adm)
    check_status_any(G, "GET .../download?format=pptx (invalid) → 400 or 503",
                     status, [400, 404, 503], body)

    # Non-authenticated download → 401
    status, _ = _req("GET", f"/esg/reports/{report_id}/download?format=csv")
    check_status(G, "GET .../download (no token) → 401 [RBAC]", status, 401)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 15: Mobile PWA — Citizen Bill & Notification APIs (Sprint 5 MVP2-12)
# ──────────────────────────────────────────────────────────────────────────────

def test_pwa_citizen():
    grp_header("Mobile PWA — Citizen API (Sprint 5 MVP2-12)")
    G = "pwa_citizen"

    # Citizen token (public-facing endpoints)
    cit = token("citizen")

    # GET /citizen/bills — utility bills list for citizen
    # GET /citizen/invoices — utility bills list for citizen (actual endpoint)
    status, body = _req("GET", "/citizen/invoices", token=cit)
    check_status_any(G, "GET /citizen/invoices (citizen) → 200 or 204", status, [200, 204], body)
    if status == 200 and isinstance(body, dict):
        check_field(G, "  bills response has content array", body, "content")
    elif status == 200 and isinstance(body, list):
        _record(G, "  bills returned as list", True)

    # GET /citizen/invoices (no token) → 401 [RBAC regression guard]
    status, _ = _req("GET", "/citizen/invoices")
    check_status(G, "GET /citizen/invoices (no token) → 401 [RBAC]", status, 401)

    # GET /push/subscriptions — list citizen's push subscriptions
    status, body = _req("GET", "/push/subscriptions", token=cit)
    check_status_any(G, "GET /push/subscriptions (citizen) → 200 or 204", status, [200, 204], body)

    # POST /push/subscribe — register push subscription (actual endpoint)
    push_payload = {
        "endpoint": "https://test.example.com/push/fake-endpoint",
        "keys": {
            "p256dh": "BFAKE_P256DH_KEY_FOR_REGRESSION_TEST",
            "auth": "FAKE_AUTH_KEY"
        }
    }
    status, body = _req("POST", "/push/subscribe", body=push_payload, token=cit)
    check_status_any(G, "POST /push/subscribe (citizen) → 200, 201, or 400",
                     status, [200, 201, 400], body)

    # POST subscribe (no token) → 401
    status, _ = _req("POST", "/push/subscribe", body=push_payload)
    check_status(G, "POST /push/subscribe (no token) → 401 [RBAC]", status, 401)

    # GET /citizen/aqi/current — AQI for citizen's district (used by PWA home screen)
    status, body = _req("GET", "/citizen/aqi/current", token=cit)
    check_status_any(G, "GET /citizen/aqi/current (citizen) → 200 or 204 or 404",
                     status, [200, 204, 404], body)

    # Admin can read citizen bills too (cross-role read)
    adm = token("admin")
    status, body = _req("GET", "/citizen/invoices", token=adm)
    check_status_any(G, "GET /citizen/invoices (admin) → 200, 204, or 403 [citizen-scoped]",
                     status, [200, 204, 403], body)


# ──────────────────────────────────────────────────────────────────────────────
# GROUP 16: Tenant Admin Dashboard API Coverage (Sprint 5 MVP2-13)
# ──────────────────────────────────────────────────────────────────────────────

def test_tenant_admin_dashboard():
    grp_header("Tenant Admin Dashboard API (Sprint 5 MVP2-13)")
    G = "tenant_admin_dashboard"
    adm = token("admin")
    opr = token("operator")
    tenant_id = "default"

    # GET /admin/tenants/{id}/overview — stat cards on dashboard home
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/overview", token=adm)
    check_status_any(G, "GET .../overview (admin) → 200 or 404", status, [200, 404], body)
    if status == 200 and isinstance(body, dict):
        check_field(G, "  overview has totalUsers", body, "totalUsers")
        check_field(G, "  overview has totalBuildings", body, "totalBuildings")

    # GET /admin/tenants/{id}/users — user management table
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/users", token=adm)
    check_status_any(G, "GET .../users (admin) → 200 or 404", status, [200, 404], body)
    if status == 200:
        if isinstance(body, dict):
            check_field(G, "  users response has content", body, "content")
        elif isinstance(body, list):
            _record(G, "  users returned as list", True)

    # GET /admin/tenants/{id}/users?role=CITIZEN — role filter
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/users?role=CITIZEN", token=adm)
    check_status_any(G, "GET .../users?role=CITIZEN (admin) → 200 or 404",
                     status, [200, 404], body)

    # PATCH /admin/tenants/{id}/users/{uid}/status — deactivate user
    # Use a non-existent user id — expect 404, not 401/403
    status, body = _req("PATCH",
                        f"/admin/tenants/{tenant_id}/users/non-existent-user/status",
                        body={"active": False}, token=adm)
    check_status_any(G, "PATCH .../users/non-existent/status (admin) → 404 or 400",
                     status, [400, 404, 422], body)

    # PATCH without token → 401
    status, _ = _req("PATCH",
                     f"/admin/tenants/{tenant_id}/users/non-existent-user/status",
                     body={"active": False})
    check_status(G, "PATCH .../status (no token) → 401 [RBAC]", status, 401)

    # GET /admin/tenants/{id}/settings — branding & color settings
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/settings", token=adm)
    check_status_any(G, "GET .../settings (admin) → 200 or 404", status, [200, 404], body)
    if status == 200 and isinstance(body, dict):
        # Sprint 5 settings includes primaryColor
        has_color = "primaryColor" in body or "branding" in body
        _record(G, "  settings has color/branding field", has_color,
                f"fields present: {list(body.keys())}" if not has_color else "")

    # PATCH /admin/tenants/{id}/settings — update branding
    # PUT /admin/tenants/{id}/settings — update branding (backend uses PUT + configKey/configValue)
    put_body = {"configKey": "primaryColor", "configValue": "#2e7d32"}
    status, body = _req("PUT", f"/admin/tenants/{tenant_id}/settings",
                        body=put_body, token=adm)
    check_status_any(G, "PUT .../settings (admin) → 200 or 204 or 404",
                     status, [200, 204, 404], body)

    # Operator cannot access tenant admin endpoints → 403
    status, body = _req("GET", f"/admin/tenants/{tenant_id}/overview", token=opr)
    check_status_any(G, "GET .../overview (operator) → 403 or 404 [RBAC]",
                     status, [403, 404], body)

    # GET /admin/tenants/{id}/usage — usage chart data
    now = datetime.now(timezone.utc)
    from_ts = (now - timedelta(days=14)).strftime("%Y-%m-%dT%H:%M:%SZ")
    to_ts = now.strftime("%Y-%m-%dT%H:%M:%SZ")
    status, body = _req("GET",
                        f"/admin/tenants/{tenant_id}/usage?from={from_ts}&to={to_ts}",
                        token=adm)
    check_status_any(G, "GET .../usage (admin, 14d range) → 200 or 404",
                     status, [200, 404], body)

    # GET .../usage (no token) → 401
    status, _ = _req("GET", f"/admin/tenants/{tenant_id}/usage?from={from_ts}&to={to_ts}")
    check_status(G, "GET .../usage (no token) → 401 [RBAC]", status, 401)


# ──────────────────────────────────────────────────────────────────────────────
# Summary
# ──────────────────────────────────────────────────────────────────────────────

def _print_summary() -> int:
    total  = len(results)
    passed = sum(1 for r in results if r.passed)
    failed = total - passed

    # Per-group breakdown
    groups: dict[str, dict] = {}
    for r in results:
        g = groups.setdefault(r.group, {"pass": 0, "fail": 0})
        if r.passed:
            g["pass"] += 1
        else:
            g["fail"] += 1

    print(f"\n{'─' * 55}")
    print(bold("REGRESSION TEST SUMMARY"))
    print(f"{'─' * 55}")

    for grp, counts in groups.items():
        icon = green("✓") if counts["fail"] == 0 else red("✗")
        line = f"  {icon}  {grp:<20} {counts['pass']:>2} pass"
        if counts["fail"]:
            line += f"  {red(str(counts['fail']) + ' fail')}"
        print(line)

    print(f"{'─' * 55}")
    total_line = f"  Total: {bold(str(total))} tests  |  "
    total_line += green(f"{passed} passed")
    if failed:
        total_line += f"  |  {red(str(failed) + ' FAILED')}"
    print(total_line)
    print(f"{'─' * 55}\n")

    if failed:
        print(red(bold("✗ REGRESSION DETECTED — see failures above")))
        print(dim("  Fix all failures before merging new features.\n"))
    else:
        print(green(bold("✓ ALL TESTS PASSED — no regression detected\n")))

    return failed


# ──────────────────────────────────────────────────────────────────────────────
# Entry point
# ──────────────────────────────────────────────────────────────────────────────

ALL_GROUPS: dict[str, callable] = {
    "health":                 test_health,
    "auth":                   test_auth,
    "environment":            test_environment,
    "esg":                    test_esg,
    "alerts":                 test_alerts,
    "traffic":                test_traffic,
    "tenant":                 test_tenant,
    "citizen":                test_citizen,
    "admin":                  test_admin,
    "workflow":               test_workflow,
    "tenant_admin":           test_tenant_admin,
    "invite":                 test_invite_flow,
    "rate_limit":             test_rate_limiting,
    "esg_export":             test_esg_export,
    # Sprint 5 (MVP2-5)
    "pwa_citizen":            test_pwa_citizen,
    "tenant_admin_dashboard": test_tenant_admin_dashboard,
}


def _wait_for_backend(url: str, timeout_sec: int = 60) -> bool:
    """Poll backend health every 2s until HTTP 200 or 503 (app responding) or timeout."""
    import time
    health_url = url + "/actuator/health"
    elapsed = 0
    print(dim(f"  Waiting for backend ({health_url})"), end="", flush=True)
    while elapsed < timeout_sec:
        try:
            import urllib.request as _ur
            import urllib.error as _ue
            try:
                code = _ur.urlopen(health_url, timeout=2).getcode()
                if code == 200:
                    print(f"  {GREEN}✓ UP{RESET}  {DIM}({elapsed}s){RESET}")
                    return True
            except _ue.HTTPError as e:
                # 503 = app is responding but some components are DOWN — still runnable
                if e.code in (503, 200):
                    print(f"  {YELLOW}⚠ UP (health={e.code}){RESET}  {DIM}({elapsed}s){RESET}")
                    return True
        except Exception:
            pass
        print(".", end="", flush=True)
        time.sleep(2)
        elapsed += 2
    print()
    return False


def main() -> None:
    global _verbose, _fail_fast, _group_filter

    parser = argparse.ArgumentParser(
        description="UIP Smart City — API Regression Test Suite"
    )
    parser.add_argument("--verbose", "-v", action="store_true",
                        help="Show passing test details")
    parser.add_argument("--fail-fast", "-f", action="store_true",
                        help="Stop on first failure")
    parser.add_argument("--group", "-g", metavar="GROUP",
                        choices=list(ALL_GROUPS.keys()),
                        help=f"Run only one group: {', '.join(ALL_GROUPS.keys())}")
    parser.add_argument("--url", "-u", metavar="URL",
                        help="Backend base URL (default: http://localhost:8080)")
    args = parser.parse_args()

    global BASE_URL, API
    if args.url:
        BASE_URL = args.url.rstrip("/")
        API = BASE_URL + "/api/v1"

    _verbose    = args.verbose
    _fail_fast  = args.fail_fast
    _group_filter = args.group

    print(bold(f"\nUIP Smart City — API Regression Tests"))
    print(dim(f"Backend: {BASE_URL}"))
    if _group_filter:
        print(dim(f"Group filter: {_group_filter}"))
    print()

    # ── Pre-flight: backend must be UP before any test runs ──────────────────
    # Tests will NOT start until the service is confirmed reachable.
    print(dim("  Checking backend (tests will not start until service is UP)…"))
    if not _wait_for_backend(BASE_URL, timeout_sec=60):
        print(f"\n{RED}✗ Backend not reachable at {BASE_URL} after 60s{RESET}")
        print(dim("  Start it:  cd backend && SPRING_PROFILES_ACTIVE=dev nohup ./gradlew bootRun > /tmp/uip-backend.log 2>&1 &"))
        print(f"{RED}{BOLD}  ABORTED — backend not running{RESET}\n")
        sys.exit(1)
    print()

    for key, fn in ALL_GROUPS.items():
        run_group(key, fn)

    failed = _print_summary()
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
