#!/usr/bin/env python3
"""
UIP Smart City — E2E / UI Regression Tests
===========================================
Chạy toàn bộ Playwright spec files, nhóm kết quả theo module, hiển thị
pass/fail/skip có màu — tương tự api_regression_test.py nhưng ở tầng UI.

Usage:
  python3 scripts/e2e_regression_test.py                  # tất cả specs
  python3 scripts/e2e_regression_test.py --group auth     # 1 group
  python3 scripts/e2e_regression_test.py --headed         # mở browser
  python3 scripts/e2e_regression_test.py --fail-fast      # dừng khi fail đầu tiên
  python3 scripts/e2e_regression_test.py --verbose        # hiện error detail
  python3 scripts/e2e_regression_test.py --url http://localhost:3000

Yêu cầu:
  - Frontend đang chạy tại --url (mặc định http://localhost:3000)
  - Backend đang chạy tại http://localhost:8080 (cho các spec cần real API)
  - Node.js + npm install đã chạy trong frontend/
"""

import subprocess
import json
import sys
import os
import argparse
import tempfile
import urllib.request

# ─── Colors ───────────────────────────────────────────────────────────────────

if sys.stdout.isatty():
    GREEN  = "\033[32m"
    RED    = "\033[31m"
    YELLOW = "\033[33m"
    CYAN   = "\033[36m"
    DIM    = "\033[2m"
    BOLD   = "\033[1m"
    RESET  = "\033[0m"
else:
    GREEN = RED = YELLOW = CYAN = DIM = BOLD = RESET = ""

# ─── Spec groups ──────────────────────────────────────────────────────────────
# Format: (group_key, display_label, spec_filename)

SPEC_GROUPS = [
    ("auth",             "Authentication",              "auth.spec.ts"),
    ("dashboard",        "City Operations Dashboard",   "dashboard.spec.ts"),
    ("environment",      "Environment Monitoring",      "environment.spec.ts"),
    ("esg-metrics",      "ESG Metrics Dashboard",       "esg-metrics.spec.ts"),
    ("esg-reports",      "ESG Report Generation",       "esg-reports.spec.ts"),
    ("alerts",           "Alert Management",            "alerts.spec.ts"),
    ("alert-pipeline",   "Alert Pipeline E2E",          "alert-pipeline.spec.ts"),
    ("traffic",          "Traffic Management",          "traffic.spec.ts"),
    ("citizen",          "Citizen Portal RBAC",         "citizen-rbac.spec.ts"),
    ("citizen-register", "Citizen Registration",        "citizen-register.spec.ts"),
    ("ai-workflow",      "AI Workflow Dashboard",       "ai-workflow.spec.ts"),
    ("workflow-config",  "Workflow Trigger Config",     "workflow-config.spec.ts"),
    ("multi-tenancy",    "Multi-Tenancy (Sprint 2)",    "sprint2-multi-tenancy.spec.ts"),
]

# ─── CLI ──────────────────────────────────────────────────────────────────────

def parse_args():
    p = argparse.ArgumentParser(
        description="UIP E2E Regression Tests — Playwright wrapper",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("--group",     "-g", metavar="KEY",
                   help=f"Run one group. Keys: {', '.join(k for k,_,_ in SPEC_GROUPS)}")
    p.add_argument("--headed",    action="store_true",
                   help="Open browser window during test run")
    p.add_argument("--fail-fast", "-f", action="store_true",
                   help="Stop reporting after first failing group")
    p.add_argument("--verbose",   "-v", action="store_true",
                   help="Print error messages for failed tests")
    p.add_argument("--url",       "-u", default="http://localhost:3000",
                   metavar="URL", help="Frontend base URL (default: http://localhost:3000)")
    return p.parse_args()

# ─── Helpers ──────────────────────────────────────────────────────────────────

def check_url(url: str, timeout: int = 3) -> bool:
    try:
        urllib.request.urlopen(url, timeout=timeout)
        return True
    except Exception:
        return False


def wait_for_url(url: str, label: str, timeout_sec: int = 30) -> bool:
    """Poll url every 2s until HTTP 200 or timeout. Returns True when up."""
    import time
    elapsed = 0
    print(f"  {DIM}Waiting for {label} ({url}){RESET}", end="", flush=True)
    while elapsed < timeout_sec:
        if check_url(url):
            print(f"  {GREEN}✓ UP{RESET}  {DIM}({elapsed}s){RESET}")
            return True
        print(".", end="", flush=True)
        time.sleep(2)
        elapsed += 2
    print()
    return False


def collect_tests(suite_node: dict) -> list[dict]:
    """Recursively flatten all test specs from a Playwright JSON suite node."""
    results = []
    for spec in suite_node.get("specs", []):
        for test in spec.get("tests", []):
            status = test.get("status", "unknown")
            # "expected" = passed, "unexpected" = failed, "skipped", "flaky"
            test_results = test.get("results", [])
            error_msg = None
            if test_results:
                last = test_results[-1]
                err = last.get("error")
                if err:
                    if isinstance(err, dict):
                        error_msg = err.get("message", "")
                    else:
                        error_msg = str(err)
            results.append({
                "title": spec["title"],
                "status": status,
                "error": error_msg,
            })
    for child in suite_node.get("suites", []):
        results.extend(collect_tests(child))
    return results


def run_playwright(
    spec_files: list[str],
    frontend_dir: str,
    headed: bool = False,
    frontend_url: str = "http://localhost:3000",
) -> dict | None:
    """Run `npx playwright test` with JSON reporter; return parsed results dict."""

    out_file = tempfile.mktemp(suffix="_pw_regression.json")

    cmd = [
        "npx", "playwright", "test",
        "--project=chromium",
        "--reporter=json",
        "--",
    ]
    if headed:
        cmd.insert(cmd.index("--"), "--headed")

    cmd = [
        "npx", "playwright", "test",
        "--project=chromium",
        "--reporter=json",
    ]
    if headed:
        cmd.append("--headed")
    for spec in spec_files:
        cmd.append(f"e2e/{spec}")

    env = os.environ.copy()
    env["PLAYWRIGHT_JSON_OUTPUT_NAME"] = out_file
    env["BASE_URL"] = frontend_url

    try:
        subprocess.run(
            cmd,
            cwd=frontend_dir,
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
    except FileNotFoundError:
        print(f"{RED}Error: npx not found. Install Node.js and run `npm install` in frontend/{RESET}")
        return None

    if not os.path.exists(out_file):
        print(f"{RED}Playwright did not produce JSON output. "
              f"Check that playwright is installed in frontend/.{RESET}")
        return None

    try:
        with open(out_file) as f:
            data = json.load(f)
    except json.JSONDecodeError as e:
        print(f"{RED}Failed to parse Playwright JSON output: {e}{RESET}")
        return None
    finally:
        if os.path.exists(out_file):
            os.unlink(out_file)

    return data

# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    args = parse_args()

    script_dir  = os.path.dirname(os.path.abspath(__file__))
    root_dir    = os.path.dirname(script_dir)
    frontend_dir = os.path.join(root_dir, "frontend")

    # ── Resolve groups ────────────────────────────────────────────────────────
    groups = SPEC_GROUPS
    if args.group:
        groups = [(k, l, s) for k, l, s in SPEC_GROUPS if k == args.group]
        if not groups:
            valid = ", ".join(k for k, _, _ in SPEC_GROUPS)
            print(f"{RED}Unknown group '{args.group}'. Valid keys: {valid}{RESET}")
            sys.exit(1)

    # ── Header ────────────────────────────────────────────────────────────────
    print(f"\n{BOLD}UIP Smart City — E2E / UI Regression Tests{RESET}")
    print(f"{DIM}Frontend: {args.url}{RESET}\n")

    # ── Pre-flight: frontend must be UP before any test runs ──────────────────
    # Tests will NOT start until the service is confirmed reachable.
    print(f"{DIM}  Checking frontend (tests will not start until service is UP)…{RESET}")
    if not wait_for_url(args.url, "Frontend", timeout_sec=30):
        print(f"\n{RED}✗ Frontend not reachable at {args.url} after 30s{RESET}")
        print(f"{DIM}  Start it:  cd frontend && npm run dev -- --host 0.0.0.0{RESET}")
        print(f"{RED}{BOLD}  ABORTED — frontend not running{RESET}\n")
        sys.exit(1)
    print()  # blank line after UP confirmation

    # ── Pre-flight: playwright installed ─────────────────────────────────────
    pw_bin = os.path.join(frontend_dir, "node_modules", ".bin", "playwright")
    if not os.path.exists(pw_bin):
        print(f"{RED}✗ Playwright not installed. Run `npm install` in frontend/{RESET}")
        sys.exit(1)

    # ── Run playwright ────────────────────────────────────────────────────────
    spec_files = [s for _, _, s in groups]
    print(f"{DIM}  Running {len(spec_files)} spec file(s) via Playwright…{RESET}\n")

    data = run_playwright(spec_files, frontend_dir, headed=args.headed, frontend_url=args.url)
    if data is None:
        sys.exit(1)

    # ── Build spec → tests map ────────────────────────────────────────────────
    # Top-level suites in Playwright JSON: one per spec file
    spec_test_map: dict[str, list[dict]] = {}
    for top_suite in data.get("suites", []):
        file_path = top_suite.get("file", top_suite.get("title", ""))
        spec_name = os.path.basename(file_path)
        spec_test_map.setdefault(spec_name, [])
        spec_test_map[spec_name].extend(collect_tests(top_suite))

    # ── Print per-group results ────────────────────────────────────────────────
    group_results: list[tuple] = []
    total_pass = total_fail = total_skip = 0
    overall_fail = False
    stop = False

    for key, label, spec in groups:
        if stop:
            break

        tests = spec_test_map.get(spec, [])
        print(f"{CYAN}▶ {label}{RESET}")

        if not tests:
            print(f"  {YELLOW}⚠ No tests found (spec may be skipped or spec file missing){RESET}")
            group_results.append((label, 0, 0, 0))
            continue

        g_pass = g_fail = g_skip = 0
        for t in tests:
            status = t["status"]
            title  = t["title"]

            if status == "expected":
                print(f"  {GREEN}✓ PASS{RESET}  {title}")
                g_pass += 1
            elif status in ("skipped", "fixme"):
                print(f"  {YELLOW}⊘ SKIP{RESET}  {DIM}{title}{RESET}")
                g_skip += 1
            else:  # "unexpected", "flaky"
                print(f"  {RED}✗ FAIL{RESET}  {title}")
                if args.verbose and t["error"]:
                    lines = t["error"].split("\n")[:6]
                    for line in lines:
                        print(f"         {DIM}{line}{RESET}")
                g_fail += 1
                overall_fail = True

        group_results.append((label, g_pass, g_fail, g_skip))
        total_pass += g_pass
        total_fail += g_fail
        total_skip += g_skip

        if args.fail_fast and g_fail > 0:
            print(f"\n{RED}  FAIL FAST — stopping after first failing group{RESET}")
            stop = True

        print()

    # ── Summary ───────────────────────────────────────────────────────────────
    sep = "─" * 57
    print(sep)
    print(f"{BOLD}E2E REGRESSION TEST SUMMARY{RESET}")
    print(sep)

    for (label, g_pass, g_fail, g_skip) in group_results:
        if g_fail > 0:
            icon = f"{RED}✗{RESET}"
        elif g_pass == 0 and g_skip > 0:
            icon = f"{YELLOW}⊘{RESET}"
        else:
            icon = f"{GREEN}✓{RESET}"

        detail = f"{g_pass} pass"
        if g_skip:
            detail += f", {g_skip} skip"
        if g_fail:
            detail += f", {RED}{g_fail} fail{RESET}"

        print(f"  {icon}  {label:<36} {detail}")

    print(sep)
    total = total_pass + total_fail + total_skip
    pass_col  = GREEN if total_fail == 0 else ""
    fail_col  = RED   if total_fail  > 0 else ""
    print(
        f"  Total: {total} tests  |  "
        f"{pass_col}{total_pass} passed{RESET}  |  "
        f"{total_skip} skipped  |  "
        f"{fail_col}{total_fail} failed{RESET}"
    )
    print(sep)

    if overall_fail:
        print(f"\n{RED}{BOLD}✗ UI REGRESSION DETECTED — {total_fail} test(s) failed{RESET}\n")
        sys.exit(1)
    else:
        print(f"\n{GREEN}{BOLD}✓ ALL E2E TESTS PASSED — no UI regression detected{RESET}\n")
        sys.exit(0)


if __name__ == "__main__":
    main()
