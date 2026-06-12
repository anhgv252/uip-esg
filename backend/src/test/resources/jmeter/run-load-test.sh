#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# UIP Smart City — JMeter 1000 VU Load Test Runner
# v3.1-08: 1000 VU, ramp-up 60s, hold 5min
# Targets: p95 < 500ms, error rate < 1%, throughput > 500 RPS
# ──────────────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JMX_FILE="${SCRIPT_DIR}/uip-1000vu-load-test.jmx"
RESULTS_DIR="${SCRIPT_DIR}/results"

# Default values (override via env vars or CLI args)
BASE_URL="${1:-http://localhost:8080}"
AUTH_USER="${2:-admin}"
AUTH_PASS="${3:-admin_Dev#2026!}"

# Check JMeter
if ! command -v jmeter &> /dev/null; then
    echo "ERROR: jmeter not found in PATH. Install Apache JMeter 5.6+"
    echo "  brew install jmeter  (macOS)"
    echo "  Or download from https://jmeter.apache.org/"
    exit 1
fi

# Create results directory
mkdir -p "${RESULTS_DIR}"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
JTL_FILE="${RESULTS_DIR}/uip-1000vu-${TIMESTAMP}.jtl"
HTML_REPORT="${RESULTS_DIR}/report-${TIMESTAMP}"

echo "=============================================="
echo "UIP Smart City — 1000 VU Load Test"
echo "=============================================="
echo "Target:     ${BASE_URL}"
echo "Thread:     1000 VU"
echo "Ramp-up:    60s"
echo "Duration:   300s (5 min)"
echo "Output:     ${JTL_FILE}"
echo "Report:     ${HTML_REPORT}"
echo "=============================================="

# Run JMeter in non-GUI mode
jmeter -n \
    -t "${JMX_FILE}" \
    -l "${JTL_FILE}" \
    -e -o "${HTML_REPORT}" \
    -Jbase-url="${BASE_URL}" \
    -Jauth-user="${AUTH_USER}" \
    -Jauth-pass="${AUTH_PASS}" \
    -Jjmeter.save.saveservice.connect_time=true \
    -Jjmeter.save.saveservice.bytes=true

echo ""
echo "=============================================="
echo "Load test completed. Generating summary..."
echo "=============================================="

# Parse results and check SLA
if command -v python3 &> /dev/null; then
    python3 - "${JTL_FILE}" <<'PYEOF'
import sys, csv, statistics

jtl_file = sys.argv[1]
with open(jtl_file, 'r') as f:
    reader = csv.DictReader(f)
    samples = list(reader)

total = len(samples)
if total == 0:
    print("No samples found!")
    sys.exit(1)

errors = [s for s in samples if s['success'] != 'true']
latencies = [int(s['elapsed']) for s in samples]

p50 = statistics.median(latencies)
p90 = sorted(latencies)[int(total * 0.90)]
p95 = sorted(latencies)[int(total * 0.95)]
p99 = sorted(latencies)[int(total * 0.99)]
avg = statistics.mean(latencies)
error_rate = len(errors) / total * 100
throughput = total / 300.0  # 5 min duration

print(f"\n{'='*50}")
print(f"  RESULTS SUMMARY")
print(f"{'='*50}")
print(f"  Total requests:  {total}")
print(f"  Errors:          {len(errors)} ({error_rate:.2f}%)")
print(f"  Throughput:      {throughput:.1f} RPS")
print(f"  Avg latency:     {avg:.0f} ms")
print(f"  p50:             {p50} ms")
print(f"  p90:             {p90} ms")
print(f"  p95:             {p95} ms")
print(f"  p99:             {p99} ms")
print(f"{'='*50}")

# SLA checks
sla_pass = True
if p95 >= 500:
    print(f"  FAIL: p95 ({p95}ms) >= 500ms SLA target")
    sla_pass = False
else:
    print(f"  PASS: p95 ({p95}ms) < 500ms SLA target")

if error_rate >= 1.0:
    print(f"  FAIL: Error rate ({error_rate:.2f}%) >= 1% SLA target")
    sla_pass = False
else:
    print(f"  PASS: Error rate ({error_rate:.2f}%) < 1% SLA target")

if throughput < 500:
    print(f"  FAIL: Throughput ({throughput:.1f} RPS) < 500 RPS target")
    sla_pass = False
else:
    print(f"  PASS: Throughput ({throughput:.1f} RPS) >= 500 RPS target")

print(f"{'='*50}")
if sla_pass:
    print("  OVERALL: SLA PASSED")
else:
    print("  OVERALL: SLA FAILED")
    sys.exit(1)
PYEOF
fi

echo ""
echo "HTML report available at: ${HTML_REPORT}/index.html"
echo "Raw results: ${JTL_FILE}"
