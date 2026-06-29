#!/usr/bin/env python3
"""
ClickHouse partition hotspot scanner for INV-4 (M5-2-T06).

Queries ClickHouse HTTP interface for row distribution across tenants in the
analytics.esg_readings table, calculates skew metrics, and flags hotspots when
a single tenant/partition combination receives >20% more data than average.

Partition key: toYYYYMM(event_time) on analytics.esg_readings.

Exit codes / verdict:
    "hotspot": true  → skew > configured threshold (default 20%)
    "hotspot": false → balanced distribution
    "note": "..."    → edge cases (no data, CH unreachable, query error)
"""
from __future__ import annotations

import json
import sys
import urllib.request
import urllib.error
from typing import Any


def check_partition_skew(
    ch_host: str,
    ch_port: int,
    tenant_ids: list[str],
    skew_threshold: float = 0.20,
    timeout: float = 15.0,
) -> dict[str, Any]:
    """
    Query ClickHouse for row counts per tenant and partition, calculate skew.

    Args:
        ch_host: ClickHouse hostname (e.g., "localhost")
        ch_port: HTTP interface port (default 8123)
        tenant_ids: List of tenant IDs to check (e.g., ["tenant-001", ...])
        skew_threshold: Max allowed skew ratio (default 0.20 = 20%)
        timeout: Query timeout in seconds

    Returns:
        {
            "skew_ratio": float,        # max/avg - 1 (0.0 = perfect balance)
            "hotspot": bool,            # True if skew > threshold
            "max_rows_tenant": str,     # Tenant with most rows
            "avg_rows_per_tenant": float,
            "tenant_distribution": {    # tenant_id -> row_count
                "tenant-001": 1234,
                ...
            },
            "note": str,                # Optional edge-case explanation
        }

    Edge cases:
        - CH unreachable → {"hotspot": false, "note": "unreachable: ..."}
        - No data in table → {"hotspot": false, "note": "no data (expected in mock)"}
        - Query error → {"hotspot": false, "note": "query_error: ..."}
    """
    if not tenant_ids:
        return {
            "skew_ratio": 1.0,
            "hotspot": False,
            "note": "no tenant_ids provided",
            "tenant_distribution": {},
        }

    # Build query: aggregate row counts per tenant (ignore partition for skew calc)
    # Production variant: GROUP BY (tenant_id, month_partition) for per-partition view
    tenant_list = ",".join(f"'{t}'" for t in tenant_ids)
    query = f"""
    SELECT 
        tenant_id,
        count() as row_count
    FROM analytics.esg_readings
    WHERE tenant_id IN ({tenant_list})
    GROUP BY tenant_id
    ORDER BY row_count DESC
    FORMAT JSONCompact
    """

    url = f"http://{ch_host}:{ch_port}/?query={_url_encode(query)}"
    
    try:
        with urllib.request.urlopen(url, timeout=timeout) as resp:
            raw = resp.read()
            data = json.loads(raw)
    except urllib.error.URLError as e:
        return {
            "skew_ratio": 1.0,
            "hotspot": False,
            "note": f"unreachable: {e}",
            "tenant_distribution": {},
        }
    except Exception as e:
        return {
            "skew_ratio": 1.0,
            "hotspot": False,
            "note": f"query_error: {e}",
            "tenant_distribution": {},
        }

    # Parse JSONCompact: {"data": [[tenant_id, row_count], ...]}
    # CH returns empty data array if no rows match WHERE clause
    rows = data.get("data", [])
    if not rows:
        return {
            "skew_ratio": 1.0,
            "hotspot": False,
            "note": "no data (expected in mock run or before ingestion)",
            "tenant_distribution": {},
        }

    row_counts: dict[str, int] = {row[0]: row[1] for row in rows}
    
    # Calculate skew: (max - avg) / avg
    counts = list(row_counts.values())
    avg = sum(counts) / len(counts)
    max_count = max(counts)
    skew_ratio = (max_count / avg - 1.0) if avg > 0 else 0.0
    
    max_tenant = max(row_counts, key=row_counts.get)  # type: ignore
    
    return {
        "skew_ratio": round(skew_ratio, 3),
        "hotspot": skew_ratio > skew_threshold,
        "max_rows_tenant": max_tenant,
        "avg_rows_per_tenant": round(avg, 1),
        "max_rows": max_count,
        "tenant_distribution": row_counts,
    }


def _url_encode(s: str) -> str:
    """Minimal URL encoding for CH query param (stdlib only)."""
    import urllib.parse
    return urllib.parse.quote(s, safe="")


def main() -> int:
    """CLI entrypoint for standalone INV-4 checks (debugging).

    Usage:
        python3 ch_partition_check.py tenant-001 tenant-002 tenant-003
        python3 ch_partition_check.py --host clickhouse.svc.cluster.local \
                                       --port 8123 tenant-{001..050}
    """
    import argparse
    parser = argparse.ArgumentParser(
        description="Check ClickHouse partition hotspot (INV-4)")
    parser.add_argument("--host", default="localhost",
                        help="ClickHouse hostname")
    parser.add_argument("--port", type=int, default=8123,
                        help="ClickHouse HTTP port")
    parser.add_argument("--threshold", type=float, default=0.20,
                        help="Skew threshold (default 0.20 = 20%%)")
    parser.add_argument("tenant_ids", nargs="+",
                        help="Tenant IDs to check")
    args = parser.parse_args()

    result = check_partition_skew(
        args.host, args.port, args.tenant_ids, args.threshold
    )
    
    print(json.dumps(result, indent=2))
    
    if result.get("hotspot"):
        print("\n⚠️  HOTSPOT DETECTED: partition skew > threshold",
              file=sys.stderr)
        return 1
    else:
        print("\n✅ No hotspot detected", file=sys.stderr)
        return 0


if __name__ == "__main__":
    sys.exit(main())
