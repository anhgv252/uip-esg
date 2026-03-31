"""
check_results.py  –  POC Verification Script
=============================================

Queries a single TimescaleDB instance (esg_db) with two schemas:
  esg          – esg.clean_metrics, esg.aggregate_metrics
  error_mgmt   – error_mgmt.error_records

Usage (from project root, after `make up`):
  pip install psycopg2-binary tabulate
  python scripts/check_results.py
  # or simply:
  make check

Environment variables (defaults for local host access):
  TIMESCALE_HOST  localhost
  TIMESCALE_PORT  5432
"""

import os
import sys
import time
from datetime import datetime

try:
    import psycopg2
    import psycopg2.extras
    from tabulate import tabulate
except ImportError:
    print("Missing deps. Run: pip install psycopg2-binary tabulate")
    sys.exit(1)

TSDB_CFG = {
    "host":     os.getenv("TIMESCALE_HOST", "localhost"),
    "port":     int(os.getenv("TIMESCALE_PORT", "5432")),
    "dbname":   "esg_db",
    "user":     "esg_user",
    "password": "esg_pass",
}

DIV  = "═" * 68
DIV2 = "─" * 68


def section(title: str) -> None:
    print(f"\n{DIV}\n  {title}\n{DIV2}")


def connect(cfg: dict, label: str):
    for i in range(15):
        try:
            conn = psycopg2.connect(**cfg)
            print(f"✅ Connected to {label}")
            return conn
        except Exception as e:
            if i < 14:
                print(f"⏳ Waiting for {label} ({i+1}/15): {e}")
                time.sleep(3)
            else:
                raise


def q(conn, sql: str, params=None) -> list[dict]:
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, params)
        return [dict(r) for r in cur.fetchall()]


def check_clean_metrics(conn) -> None:
    section("esg.clean_metrics  (valid records)")
    row = q(conn, """
        SELECT
            COUNT(*)                        AS total_records,
            COUNT(DISTINCT meter_id)        AS unique_meters,
            COUNT(DISTINCT site_id)         AS unique_sites,
            COUNT(DISTINCT measure_type)    AS measure_types,
            MIN(event_ts)                   AS first_event,
            MAX(event_ts)                   AS last_event,
            MIN(ingested_at)                AS first_ingested,
            MAX(ingested_at)                AS last_ingested,
            ROUND(AVG(value)::numeric, 3)   AS avg_value
        FROM esg.clean_metrics
    """)[0]
    for k, v in row.items():
        print(f"  {k:<25} {v}")

    section("esg.clean_metrics  by measure_type")
    rows = q(conn, """
        SELECT measure_type, unit,
               COUNT(*) AS records,
               ROUND(AVG(value)::numeric,3) AS avg,
               ROUND(MIN(value)::numeric,3) AS min,
               ROUND(MAX(value)::numeric,3) AS max
        FROM esg.clean_metrics
        GROUP BY measure_type, unit ORDER BY records DESC
    """)
    print(tabulate(rows, headers="keys", tablefmt="rounded_outline") if rows else "  (no data yet)")

    section("esg.clean_metrics  by site")
    rows = q(conn, """
        SELECT site_id,
               COUNT(*) AS records,
               COUNT(DISTINCT meter_id) AS meters,
               COUNT(DISTINCT measure_type) AS measure_types
        FROM esg.clean_metrics GROUP BY site_id ORDER BY records DESC
    """)
    print(tabulate(rows, headers="keys", tablefmt="rounded_outline") if rows else "  (no data yet)")

    section("esg.aggregate_metrics  (1-min event-time windows)")
    row = q(conn, """
        SELECT COUNT(*) AS total_windows,
               COUNT(DISTINCT meter_id) AS unique_meters,
               SUM(record_count) AS total_source_records,
               ROUND(AVG(avg_value)::numeric,3) AS global_avg,
               MIN(window_start) AS earliest_window,
               MAX(window_end)   AS latest_window
        FROM esg.aggregate_metrics
    """)[0]
    for k, v in row.items():
        print(f"  {k:<25} {v}")

    section("esg.aggregate_metrics  (last 10 windows)")
    rows = q(conn, """
        SELECT meter_id, site_id, measure_type, unit,
               TO_CHAR(window_start,'HH24:MI') AS win_start,
               TO_CHAR(window_end,  'HH24:MI') AS win_end,
               ROUND(total_value::numeric,3) AS total,
               ROUND(avg_value::numeric,3)   AS avg,
               record_count AS n
        FROM esg.aggregate_metrics
        ORDER BY window_start DESC LIMIT 10
    """)
    print(tabulate(rows, headers="keys", tablefmt="rounded_outline") if rows
          else "  (no aggregate windows yet – Flink needs ~1 min to close first window)")


def check_error_records(conn) -> None:
    section("error_mgmt.error_records  (invalid messages)")
    row = q(conn, """
        SELECT COUNT(*) AS total_errors,
               COUNT(DISTINCT error_type)   AS distinct_error_types,
               COUNT(DISTINCT source_id)    AS distinct_sources,
               MIN(received_at) AS first_error,
               MAX(received_at) AS last_error,
               COUNT(*) FILTER (WHERE reviewed)   AS reviewed,
               COUNT(*) FILTER (WHERE reingested) AS reingested
        FROM error_mgmt.error_records
    """)[0]
    for k, v in row.items():
        print(f"  {k:<25} {v}")

    section("error_mgmt.error_records  by error_type")
    rows = q(conn, "SELECT * FROM error_mgmt.error_summary")
    print(tabulate(rows, headers="keys", tablefmt="rounded_outline") if rows else "  (no error records yet)")

    section("error_mgmt.error_records  one sample per type")
    rows = q(conn, """
        SELECT DISTINCT ON (error_type)
            error_type, meter_id, raw_value,
            event_timestamp, unit, error_detail,
            TO_CHAR(received_at,'HH24:MI:SS') AS received
        FROM error_mgmt.error_records
        ORDER BY error_type, received_at DESC
    """)
    print(tabulate(rows, headers="keys", tablefmt="rounded_outline",
                   maxcolwidths=[22,20,15,22,8,42,10]) if rows else "  (no error records yet)")

    section("Pending operator review (first 5)")
    rows = q(conn, """
        SELECT id, error_type, meter_id, raw_value, source_id,
               TO_CHAR(received_at,'YYYY-MM-DD HH24:MI:SS') AS received_at
        FROM error_mgmt.error_records
        WHERE reviewed = FALSE ORDER BY received_at DESC LIMIT 5
    """)
    if rows:
        print(tabulate(rows, headers="keys", tablefmt="rounded_outline"))
        total = q(conn, "SELECT COUNT(*) AS n FROM error_mgmt.error_records WHERE reviewed=FALSE")[0]["n"]
        print(f"\n  … {total:,} total unreviewed records")
    else:
        print("  (all errors reviewed ✓)")


def overall_pipeline_stats(conn) -> None:
    section("OVERALL PIPELINE STATS")
    clean = q(conn, "SELECT COUNT(*) AS n FROM esg.clean_metrics")[0]["n"]
    agg   = q(conn, "SELECT COUNT(*) AS n FROM esg.aggregate_metrics")[0]["n"]
    errs  = q(conn, "SELECT COUNT(*) AS n FROM error_mgmt.error_records")[0]["n"]
    total = clean + errs
    if total == 0:
        print("  No data yet. The Flink job may still be starting up.")
        return
    print(f"  Total messages processed : {total:>10,}")
    print(f"  ✅ Clean records (esg)   : {clean:>10,}  ({clean/total*100:.1f}%)")
    print(f"  ❌ Error records (errors): {errs:>10,}  ({errs/total*100:.1f}%)")
    print(f"  📊 Aggregate windows     : {agg:>10,}")
    print(f"\n  Data quality rate        : {clean/total*100:.2f}%")


if __name__ == "__main__":
    print(f"\n{'═'*68}")
    print(f"  UIP ESG POC  •  Results Checker  •  {datetime.now():%Y-%m-%d %H:%M:%S}")
    print(f"{'═'*68}")

    conn = connect(TSDB_CFG, "TimescaleDB (esg_db)")
    try:
        check_clean_metrics(conn)
        check_error_records(conn)
        overall_pipeline_stats(conn)
    finally:
        conn.close()
    print(f"\n{'═'*68}\n")
