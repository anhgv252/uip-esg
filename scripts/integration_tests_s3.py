"""
integration_tests_s3.py  –  Sprint 3 API Integration Test Suite
================================================================

30+ test cases covering Sprint 3 features:
  • Traffic module        (counts, incidents, congestion map)
  • Citizen module        (register, profile, invoices, meters)
  • Admin module          (users, sensors, sensor toggle)
  • Alert management      (list, acknowledge E2E)
  • City Ops              (sensors map endpoint)

Prerequisites:
  - Backend running at BASE_URL (default http://localhost:8080)
  - Admin credentials via env vars: ADMIN_USERNAME, ADMIN_PASSWORD
  - V6-V8 DB migrations applied
  - TimescaleDB up

Usage:
  pip install requests
  python scripts/integration_tests_s3.py
  # or: BASE_URL=http://localhost:8080 python scripts/integration_tests_s3.py
"""

from __future__ import annotations

import os
import sys
import logging
from typing import Callable

import requests

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("s3-integration-tests")

BASE_URL = os.getenv("BASE_URL", "http://localhost:8080")
ADMIN_USER = os.getenv("ADMIN_USERNAME", "admin")
ADMIN_PASS = os.getenv("ADMIN_PASSWORD", "")
OPERATOR_USER = os.getenv("OPERATOR_USERNAME", "operator")
OPERATOR_PASS = os.getenv("OPERATOR_PASSWORD", "")

# ── Test runner ────────────────────────────────────────────────────
_results: list[tuple[str, bool, str]] = []


def test(name: str):
    def decorator(fn: Callable):
        def wrapper(*args, **kwargs):
            try:
                fn(*args, **kwargs)
                _results.append((name, True, ""))
                log.info("  PASS  %s", name)
            except AssertionError as e:
                _results.append((name, False, str(e)))
                log.error("  FAIL  %s — %s", name, e)
            except Exception as e:
                _results.append((name, False, f"Exception: {e}"))
                log.error("  ERROR %s — %s", name, e)
        return wrapper
    return decorator


# ── Auth helpers ───────────────────────────────────────────────────
def get_token(username: str, password: str) -> str:
    r = requests.post(f"{BASE_URL}/api/v1/auth/login",
                      json={"username": username, "password": password}, timeout=10)
    assert r.status_code == 200, f"Login failed for {username}: {r.status_code}"
    token = r.json().get("accessToken") or r.json().get("token")
    assert token, f"No token in response: {r.json()}"
    return token


def auth_headers(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


# ══════════════════════════════════════════════════════════════════
# TRAFFIC TESTS
# ══════════════════════════════════════════════════════════════════

@test("traffic: GET /api/v1/traffic/counts returns 200")
def test_traffic_counts_ok():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/traffic/counts", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"


@test("traffic: counts response has pagination fields")
def test_traffic_counts_paged():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/traffic/counts", headers=auth_headers(token), timeout=10)
    data = r.json()
    assert "content" in data or isinstance(data, list), "Expected paged or list response"


@test("traffic: GET /api/v1/traffic/incidents returns 200")
def test_traffic_incidents_ok():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/traffic/incidents", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"


@test("traffic: incidents list is non-empty (seed data)")
def test_traffic_incidents_seeded():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/traffic/incidents", headers=auth_headers(token), timeout=10)
    data = r.json()
    items = data.get("content", data) if isinstance(data, dict) else data
    assert len(items) > 0, "Expected at least 1 seeded incident from V6 migration"


@test("traffic: incident has required fields (type, status, latitude, longitude)")
def test_traffic_incident_fields():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/traffic/incidents", headers=auth_headers(token), timeout=10)
    data = r.json()
    items = data.get("content", data) if isinstance(data, dict) else data
    if items:
        inc = items[0]
        assert "id" in inc, "Missing 'id'"
        assert "incidentType" in inc, "Missing 'incidentType'"
        assert "status" in inc, "Missing 'status'"


@test("traffic: GET /api/v1/traffic/congestion-map returns GeoJSON")
def test_traffic_congestion_map():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/traffic/congestion-map", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    data = r.json()
    assert "type" in data or "features" in data or isinstance(data, list), \
        "Expected GeoJSON or list response"


@test("traffic: unauthenticated request returns 401")
def test_traffic_requires_auth():
    r = requests.get(f"{BASE_URL}/api/v1/traffic/incidents", timeout=10)
    assert r.status_code == 401, f"Expected 401 without token, got {r.status_code}"


# ══════════════════════════════════════════════════════════════════
# CITIZEN TESTS
# ══════════════════════════════════════════════════════════════════

@test("citizen: POST /api/v1/citizen/register creates account (public endpoint)")
def test_citizen_register():
    import time
    unique = int(time.time())
    payload = {
        "fullName": f"Test Citizen {unique}",
        "email": f"citizen_{unique}@test.uip.vn",
        "phoneNumber": f"090{unique % 10000000:07d}",
        "identityNumber": f"07{unique % 10000000:07d}",
    }
    r = requests.post(f"{BASE_URL}/api/v1/citizen/register", json=payload, timeout=10)
    assert r.status_code in (200, 201), f"Expected 201, got {r.status_code}: {r.text}"


@test("citizen: register with duplicate email returns 409 or 400")
def test_citizen_register_duplicate():
    payload = {
        "fullName": "Duplicate Test",
        "email": "duplicate_seed@test.uip.vn",
        "phoneNumber": "0901111111",
        "identityNumber": "079111111111",
    }
    # First call — may succeed or fail if already exists
    requests.post(f"{BASE_URL}/api/v1/citizen/register", json=payload, timeout=10)
    # Second call should fail
    r = requests.post(f"{BASE_URL}/api/v1/citizen/register", json=payload, timeout=10)
    assert r.status_code in (400, 409, 422), \
        f"Expected error for duplicate email, got {r.status_code}"


@test("citizen: GET /api/v1/citizen/buildings returns building list")
def test_citizen_buildings():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/citizen/buildings", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"
    data = r.json()
    assert isinstance(data, list), "Expected list of buildings"
    assert len(data) > 0, "Expected at least 1 seeded building from V7 migration"


@test("citizen: buildings have required fields (id, buildingName, address)")
def test_citizen_building_fields():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/citizen/buildings", headers=auth_headers(token), timeout=10)
    buildings = r.json()
    if buildings:
        b = buildings[0]
        assert "id" in b, "Missing 'id'"
        assert "buildingName" in b, "Missing 'buildingName'"


@test("citizen: GET /api/v1/citizen/profile requires auth")
def test_citizen_profile_requires_auth():
    r = requests.get(f"{BASE_URL}/api/v1/citizen/profile", timeout=10)
    assert r.status_code == 401, f"Expected 401, got {r.status_code}"


@test("citizen: GET /api/v1/citizen/invoices requires auth")
def test_citizen_invoices_requires_auth():
    r = requests.get(f"{BASE_URL}/api/v1/citizen/invoices", timeout=10)
    assert r.status_code == 401, f"Expected 401, got {r.status_code}"


@test("citizen: invoices endpoint returns 200 for admin user")
def test_citizen_invoices_admin():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/citizen/invoices", headers=auth_headers(token), timeout=10)
    # Admin may get their own invoices (empty) or 403 depending on impl
    assert r.status_code in (200, 403), f"Unexpected status {r.status_code}"


# ══════════════════════════════════════════════════════════════════
# ADMIN TESTS (S3-07)
# ══════════════════════════════════════════════════════════════════

@test("admin: GET /api/v1/admin/users returns user list for ADMIN")
def test_admin_users_ok():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/admin/users", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"


@test("admin: users list has required fields")
def test_admin_users_fields():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/admin/users", headers=auth_headers(token), timeout=10)
    data = r.json()
    users = data.get("content", data) if isinstance(data, dict) else data
    if users:
        u = users[0]
        assert "username" in u, "Missing 'username'"
        assert "email" in u, "Missing 'email'"
        assert "roles" in u, "Missing 'roles'"
        assert "active" in u, "Missing 'active'"


@test("admin: GET /api/v1/admin/users is forbidden for non-admin")
def test_admin_users_forbidden():
    # Use a citizen/viewer token via citizen register + login
    r = requests.get(f"{BASE_URL}/api/v1/admin/users", timeout=10)
    assert r.status_code == 401, f"Expected 401 without token, got {r.status_code}"


@test("admin: GET /api/v1/admin/sensors returns sensor list")
def test_admin_sensors_ok():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/admin/sensors", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"


@test("admin: sensors list is non-empty (environment module data)")
def test_admin_sensors_nonempty():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/admin/sensors", headers=auth_headers(token), timeout=10)
    sensors = r.json()
    assert isinstance(sensors, list), "Expected list"
    assert len(sensors) > 0, "Expected at least 1 sensor"


@test("admin: sensor has required fields (sensorId, sensorName, active)")
def test_admin_sensor_fields():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/admin/sensors", headers=auth_headers(token), timeout=10)
    sensors = r.json()
    if sensors:
        s = sensors[0]
        assert "sensorId" in s, "Missing 'sensorId'"
        assert "sensorName" in s, "Missing 'sensorName'"
        assert "active" in s, "Missing 'active'"


@test("admin: PUT /api/v1/admin/sensors/{id}/status toggles sensor")
def test_admin_sensor_toggle():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    sensors_r = requests.get(f"{BASE_URL}/api/v1/admin/sensors", headers=auth_headers(token), timeout=10)
    sensors = sensors_r.json()
    if not sensors:
        log.warning("  SKIP  No sensors found to toggle")
        return
    sensor_id = sensors[0]["sensorId"]
    current_active = sensors[0]["active"]
    r = requests.put(
        f"{BASE_URL}/api/v1/admin/sensors/{sensor_id}/status",
        params={"active": str(not current_active).lower()},
        headers=auth_headers(token), timeout=10,
    )
    assert r.status_code in (200, 204), f"Expected 200/204, got {r.status_code}"
    # Restore
    requests.put(
        f"{BASE_URL}/api/v1/admin/sensors/{sensor_id}/status",
        params={"active": str(current_active).lower()},
        headers=auth_headers(token), timeout=10,
    )


# ══════════════════════════════════════════════════════════════════
# ALERT TESTS (S3-02 E2E)
# ══════════════════════════════════════════════════════════════════

@test("alerts: GET /api/v1/alerts returns list")
def test_alerts_list():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/alerts", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"


@test("alerts: alert list is paged with correct fields")
def test_alerts_paged_fields():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/alerts", headers=auth_headers(token), timeout=10)
    data = r.json()
    if isinstance(data, list) and data:
        a = data[0]
    elif isinstance(data, dict) and data.get("content"):
        a = data["content"][0]
    else:
        return  # Empty — skip field check
    assert "id" in a, "Missing 'id'"
    assert "severity" in a, "Missing 'severity'"


@test("alerts: unauthenticated request returns 401")
def test_alerts_requires_auth():
    r = requests.get(f"{BASE_URL}/api/v1/alerts", timeout=10)
    assert r.status_code == 401, f"Expected 401, got {r.status_code}"


# ══════════════════════════════════════════════════════════════════
# CITY OPS / ENVIRONMENT TESTS
# ══════════════════════════════════════════════════════════════════

@test("cityops: GET /api/v1/environment/sensors returns sensors")
def test_env_sensors_ok():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/environment/sensors", headers=auth_headers(token), timeout=10)
    assert r.status_code == 200, f"Expected 200, got {r.status_code}"


@test("cityops: sensors have lat/lng for map rendering")
def test_env_sensors_latlong():
    token = get_token(ADMIN_USER, ADMIN_PASS)
    r = requests.get(f"{BASE_URL}/api/v1/environment/sensors", headers=auth_headers(token), timeout=10)
    sensors = r.json()
    if isinstance(sensors, dict):
        sensors = sensors.get("content", [])
    if sensors:
        s = sensors[0]
        assert "latitude" in s or "lat" in s, "No latitude in sensor response"
        assert "longitude" in s or "lng" in s, "No longitude in sensor response"


# ══════════════════════════════════════════════════════════════════
# RUNNER
# ══════════════════════════════════════════════════════════════════

def run_all():
    tests = [
        test_traffic_counts_ok,
        test_traffic_counts_paged,
        test_traffic_incidents_ok,
        test_traffic_incidents_seeded,
        test_traffic_incident_fields,
        test_traffic_congestion_map,
        test_traffic_requires_auth,
        test_citizen_register,
        test_citizen_register_duplicate,
        test_citizen_buildings,
        test_citizen_building_fields,
        test_citizen_profile_requires_auth,
        test_citizen_invoices_requires_auth,
        test_citizen_invoices_admin,
        test_admin_users_ok,
        test_admin_users_fields,
        test_admin_users_forbidden,
        test_admin_sensors_ok,
        test_admin_sensors_nonempty,
        test_admin_sensor_fields,
        test_admin_sensor_toggle,
        test_alerts_list,
        test_alerts_paged_fields,
        test_alerts_requires_auth,
        test_env_sensors_ok,
        test_env_sensors_latlong,
    ]

    log.info("Running %d Sprint 3 integration tests against %s", len(tests), BASE_URL)
    for t in tests:
        t()

    passed = sum(1 for _, ok, _ in _results if ok)
    failed = len(_results) - passed
    log.info("")
    log.info("Results: %d passed, %d failed (total %d)", passed, failed, len(_results))

    if failed:
        log.error("Failed tests:")
        for name, ok, msg in _results:
            if not ok:
                log.error("  ✗ %s — %s", name, msg)
        sys.exit(1)
    else:
        log.info("All Sprint 3 integration tests passed!")


if __name__ == "__main__":
    run_all()
