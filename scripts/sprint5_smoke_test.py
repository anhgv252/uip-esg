#!/usr/bin/env python3
"""
Sprint 5 Demo Smoke Test Suite
Runs all P0 smoke tests before demo starts
Expected: All 9 tests pass in < 2 minutes
"""

import requests
import subprocess
import sys
import time
from typing import Tuple

# Color codes for output
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
RESET = '\033[0m'

def print_test(name: str, status: str, details: str = ""):
    status_color = GREEN if status == "PASS" else RED
    print(f"  [{status_color}{status:4}{RESET}] {name}")
    if details:
        print(f"         {details}")

def check_backend_health() -> Tuple[bool, str]:
    """SMOKE-01: Backend health endpoint"""
    urls = [
        "http://localhost:8080/actuator/health",
        "http://localhost:8080/api/v1/health",
    ]
    details = []
    for url in urls:
        try:
            resp = requests.get(url, timeout=5)
            if resp.status_code != 200:
                details.append(f"{url} -> HTTP {resp.status_code}")
                continue

            body = resp.json()
            status = str(body.get("status", "")).upper()
            if status and status != "UP":
                details.append(f"{url} -> status={status}")
                continue

            components = body.get("components", {})
            if isinstance(components, dict):
                down_components = [k for k, v in components.items() if isinstance(v, dict) and v.get("status") != "UP"]
                if down_components:
                    details.append(f"{url} -> components down: {', '.join(down_components)}")
                    continue

            return True, f"Backend healthy via {url}"
        except Exception as e:
            details.append(f"{url} -> {str(e)}")

    return False, " | ".join(details)

def check_frontend() -> Tuple[bool, str]:
    """SMOKE-02: Frontend loads"""
    try:
        resp = requests.get("http://localhost:3000", timeout=5)
        if resp.status_code == 200:
            return True, "Frontend accessible"
        return False, f"HTTP {resp.status_code}"
    except Exception as e:
        return False, f"Connection failed: {str(e)}"

def check_rls_policies() -> Tuple[bool, str]:
    """SMOKE-03: TimescaleDB RLS policies active"""
    try:
        result = subprocess.run([
            "docker", "exec", "uip-timescaledb",
            "psql", "-U", "uip", "-d", "uip_smartcity",
            "-c", "SELECT COUNT(*) FROM pg_policies;"
        ], capture_output=True, text=True, timeout=10)  # counts all schemas

        if result.returncode == 0:
            lines = result.stdout.strip().split('\n')
            for line in lines:
                line = line.strip()
                if line.isdigit():
                    count = int(line)
                    if count >= 5:
                        return True, f"{count} RLS policies active"
                    return False, f"Only {count} policies found (expected ≥5)"
        return False, f"psql failed: {result.stderr.strip()[:200]}"
    except Exception as e:
        return False, f"Query failed: {str(e)}"

def check_redis() -> Tuple[bool, str]:
    """SMOKE-04: Redis connectivity"""
    try:
        result = subprocess.run([
            "docker", "exec", "uip-redis",
            "redis-cli", "-a", "changeme_redis_password", "--no-auth-warning", "PING"
        ], capture_output=True, text=True, timeout=5)

        if result.returncode == 0 and "PONG" in result.stdout:
            return True, "Redis PONG received"
        return False, f"Redis not responding: {result.stderr.strip()[:200]}"
    except Exception as e:
        return False, f"Connection failed: {str(e)}"

def check_kafka_consumers() -> Tuple[bool, str]:
    """SMOKE-05: Kafka consumer groups active"""
    try:
        result = subprocess.run([
            "docker", "exec", "uip-kafka",
            "kafka-consumer-groups", "--bootstrap-server", "localhost:9092", "--list"
        ], capture_output=True, text=True, timeout=15)

        if result.returncode == 0:
            groups = [g for g in result.stdout.strip().split('\n') if g.strip()]
            if len(groups) >= 3:
                return True, f"{len(groups)} consumer groups active"
            return False, f"Only {len(groups)} groups found (expected ≥3)"
        return False, f"kafka-consumer-groups failed: {result.stderr.strip()[:200]}"
    except Exception as e:
        return False, f"Check failed: {str(e)}"

def check_login(username: str, password: str, expected_redirect: str) -> Tuple[bool, str]:
    """Generic login test"""
    try:
        resp = requests.post("http://localhost:8080/api/v1/auth/login", json={
            "username": username,
            "password": password
        }, timeout=5)

        if resp.status_code == 200:
            data = resp.json()
            if "accessToken" in data or "token" in data:
                return True, "Login successful, JWT received"
            return False, f"No token in response: {list(data.keys())}"
        return False, f"HTTP {resp.status_code}: {resp.text[:100]}"
    except Exception as e:
        return False, f"Login failed: {str(e)}"

def check_admin_login() -> Tuple[bool, str]:
    """SMOKE-06: Admin login"""
    return check_login("admin", "admin_Dev#2026!", "/dashboard")

def check_tenant_admin_login() -> Tuple[bool, str]:
    """SMOKE-07: Tenant Admin login"""
    return check_login("tadmin", "admin_Dev#2026!", "/tenant-admin")

def check_citizen_login() -> Tuple[bool, str]:
    """SMOKE-08: Citizen login"""
    return check_login("citizen", "citizen_Dev#2026!", "/citizen/bills")

def _extract_scalar_values_from_psql(output: str) -> list[str]:
    values = []
    for raw in output.splitlines():
        line = raw.strip()
        if not line or line.startswith("(") or line.startswith("-"):
            continue
        if "|" in line:
            values.append(line.split("|", 1)[0].strip())
        else:
            values.append(line)
    return values

def check_forecast_seed_alignment() -> Tuple[bool, str]:
    """SMOKE-09: Active tenants must have ENERGY rows for forecast in ClickHouse"""
    try:
        pg = subprocess.run([
            "docker", "exec", "uip-timescaledb",
            "psql", "-U", "uip", "-d", "uip_smartcity", "-At",
            "-c", "SELECT tenant_id FROM tenants WHERE is_active = true ORDER BY tenant_id;"
        ], capture_output=True, text=True, timeout=10)

        if pg.returncode != 0:
            return False, f"Cannot read active tenants: {pg.stderr.strip()[:200]}"

        active_tenants = set(_extract_scalar_values_from_psql(pg.stdout))
        if not active_tenants:
            return False, "No active tenants found in Postgres"

        ch = subprocess.run([
            "docker", "exec", "uip-clickhouse", "clickhouse-client",
            "--query",
            "SELECT DISTINCT tenant_id FROM analytics.esg_readings WHERE metric_type = 'ENERGY'"
        ], capture_output=True, text=True, timeout=10)

        if ch.returncode != 0:
            return False, f"Cannot read ClickHouse tenants: {ch.stderr.strip()[:200]}"

        seeded_tenants = {line.strip() for line in ch.stdout.splitlines() if line.strip()}
        missing = sorted(active_tenants - seeded_tenants)
        if missing:
            return False, f"Missing ENERGY seed for active tenants: {', '.join(missing)}"

        return True, f"All active tenants seeded for ENERGY: {', '.join(sorted(active_tenants))}"
    except Exception as e:
        return False, f"Tenant seed check failed: {str(e)}"

def main():
    print(f"\n{YELLOW}{'='*60}")
    print("Sprint 5 Demo Smoke Test Suite")
    print(f"{'='*60}{RESET}\n")
    
    start_time = time.time()
    
    tests = [
        ("SMOKE-01: Backend health endpoint", check_backend_health),
        ("SMOKE-02: Frontend loads", check_frontend),
        ("SMOKE-03: TimescaleDB RLS policies", check_rls_policies),
        ("SMOKE-04: Redis connectivity", check_redis),
        ("SMOKE-05: Kafka consumer groups", check_kafka_consumers),
        ("SMOKE-06: Admin login", check_admin_login),
        ("SMOKE-07: Tenant Admin login", check_tenant_admin_login),
        ("SMOKE-08: Citizen login", check_citizen_login),
        ("SMOKE-09: Forecast tenant seed alignment", check_forecast_seed_alignment),
    ]
    
    results = []
    for test_name, test_func in tests:
        passed, details = test_func()
        results.append(passed)
        status = "PASS" if passed else "FAIL"
        print_test(test_name, status, details)
    
    elapsed = time.time() - start_time
    passed_count = sum(results)
    total_count = len(results)
    
    print(f"\n{YELLOW}{'='*60}{RESET}")
    print(f"Results: {passed_count}/{total_count} passed in {elapsed:.1f}s")
    
    if passed_count == total_count:
        print(f"{GREEN}✅ SMOKE TESTS PASS — Demo ready to start{RESET}\n")
        sys.exit(0)
    else:
        print(f"{RED}❌ SMOKE TESTS FAIL — Demo NOT ready{RESET}")
        print(f"{RED}Fix failing tests before PO arrives{RESET}\n")
        sys.exit(1)

if __name__ == "__main__":
    main()
