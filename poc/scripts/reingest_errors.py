"""
reingest_errors.py  –  Error Re-ingestion Operator Tool
========================================================

Reads esg_error_records from PostgreSQL that have been marked
reviewed=TRUE (but reingested=FALSE), applies automatic corrections
where possible, and re-publishes the fixed messages back to the
raw_telemetry Kafka topic so they traverse the full pipeline again.

Auto-correctable error types:
  MISSING_UNIT       → fill unit from measure_type default map
  MISSING_TIMESTAMP  → fill timestamp from received_at

Skipped (ambiguous – operator must fix manually and re-run):
  MISSING_METER_ID, MISSING_VALUE, INVALID_VALUE_FORMAT,
  OUT_OF_RANGE_NEGATIVE, OUT_OF_RANGE_HIGH

Usage:
  python scripts/reingest_errors.py [options]

Options:
  --dry-run       Print what would be re-ingested, send nothing
  --error-type T  Only process records of this error_type
  --limit N       Max records to fetch (default 1000)
  --all           Include unreviewed records (use with caution)
"""

from __future__ import annotations

import os
import sys
import json
import argparse
import logging
import time
from typing import Optional

import psycopg2
import psycopg2.extras

try:
    from confluent_kafka import Producer
except ImportError:
    print("Missing dep: pip install confluent-kafka")
    sys.exit(1)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("reingest")

KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:29092")
# Error records now live in error_mgmt schema of the single TimescaleDB
PG_CFG = {
    "host":     os.getenv("TIMESCALE_HOST", "localhost"),
    "port":     int(os.getenv("TIMESCALE_PORT", "5432")),
    "dbname":   "esg_db",
    "user":     "esg_user",
    "password": "esg_pass",
}
RAW_TOPIC = "raw_telemetry"

DEFAULT_UNITS = {
    "electric_kwh": "kWh",
    "water_m3":     "m³",
    "temp_celsius": "°C",
    "co2_ppm":      "ppm",
    "humidity_pct": "%",
}
AUTOCORRECTABLE = {"MISSING_UNIT", "MISSING_TIMESTAMP"}
SKIP_TYPES = {
    "MISSING_METER_ID", "MISSING_VALUE",
    "INVALID_VALUE_FORMAT", "OUT_OF_RANGE_NEGATIVE", "OUT_OF_RANGE_HIGH",
}

_delivery_errors: list[str] = []


def _on_delivery(err, msg) -> None:
    if err:
        _delivery_errors.append(str(err))


def fetch_errors(pg_conn, error_type: Optional[str], limit: int, include_unreviewed: bool) -> list[dict]:
    filters = ["reingested = FALSE"]
    params: dict = {"limit": limit}
    if not include_unreviewed:
        filters.append("reviewed = TRUE")
    if error_type:
        filters.append("error_type = %(error_type)s")
        params["error_type"] = error_type
    else:
        placeholders = ",".join(f"'{t}'" for t in AUTOCORRECTABLE)
        filters.append(f"error_type IN ({placeholders})")

    where = " AND ".join(filters)
    with pg_conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(
            f"SELECT * FROM error_mgmt.error_records WHERE {where} ORDER BY received_at LIMIT %(limit)s",
            params,
        )
        return [dict(r) for r in cur.fetchall()]


def mark_reingested(pg_conn, record_id: int) -> None:
    with pg_conn.cursor() as cur:
        cur.execute(
            "UPDATE error_mgmt.error_records SET reingested=TRUE, reingested_at=NOW() WHERE id=%s",
            (record_id,),
        )
    pg_conn.commit()


def mark_skipped(pg_conn, record_id: int, reason: str) -> None:
    with pg_conn.cursor() as cur:
        cur.execute(
            "UPDATE error_mgmt.error_records SET notes = COALESCE(notes,'') || %s WHERE id=%s",
            (f" [AUTO-SKIP: {reason}]", record_id),
        )
    pg_conn.commit()


def attempt_correction(record: dict) -> tuple[Optional[dict], str]:
    """Return (corrected_payload | None, status_message)."""
    err_type = record["error_type"]
    if err_type in SKIP_TYPES:
        return None, f"skipped – {err_type} requires manual correction"

    payload = {
        "deviceId":     record.get("meter_id"),
        "siteCode":     record.get("site_id"),
        "buildingCode": record.get("building_id"),
        "floorCode":    record.get("floor_id"),
        "zoneCode":     record.get("zone_id"),
        "ts":           record.get("event_timestamp"),
        "type":         record.get("measure_type"),
        "v":            record.get("raw_value"),
        "u":            record.get("unit"),
    }

    if err_type == "MISSING_UNIT":
        measure = record.get("measure_type") or ""
        default_unit = DEFAULT_UNITS.get(measure)
        if not default_unit:
            return None, f"skipped – no default unit for measure_type '{measure}'"
        payload["u"] = default_unit
        return payload, f"corrected unit → '{default_unit}'"

    if err_type == "MISSING_TIMESTAMP":
        received = record.get("received_at")
        if received:
            ts_str = (
                received.strftime("%Y-%m-%dT%H:%M:%SZ")
                if hasattr(received, "strftime")
                else str(received)
            )
            payload["ts"] = ts_str
            return payload, f"corrected timestamp → '{ts_str}'"
        return None, "skipped – received_at also missing"

    return None, f"skipped – unhandled error_type '{err_type}'"


def main() -> None:
    parser = argparse.ArgumentParser(description="Re-ingest reviewed ESG error records to Kafka")
    parser.add_argument("--dry-run",    action="store_true")
    parser.add_argument("--error-type", default=None)
    parser.add_argument("--limit",      type=int, default=1000)
    parser.add_argument("--all",        action="store_true")
    args = parser.parse_args()

    log.info("═" * 60)
    log.info("  ESG Error Re-ingestion Tool")
    log.info(f"  Kafka  : {KAFKA_BOOTSTRAP}  topic: {RAW_TOPIC}")
    log.info(f"  DryRun : {args.dry_run}")
    log.info(f"  Type   : {args.error_type or '(autocorrectable types only)'}")
    log.info(f"  Limit  : {args.limit}")
    log.info("═" * 60)

    pg_conn  = psycopg2.connect(**PG_CFG)
    producer = None if args.dry_run else Producer({
        "bootstrap.servers": KAFKA_BOOTSTRAP,
        "linger.ms": 5,
        "acks": "1",
    })

    records = fetch_errors(pg_conn, args.error_type, args.limit, args.all)
    log.info(f"📋 Fetched {len(records)} candidate records")

    stats = {"reingested": 0, "skipped": 0, "kafka_errors": 0}

    for rec in records:
        corrected, msg = attempt_correction(rec)

        if corrected is None:
            log.debug(f"  [{rec['id']}] {msg}")
            stats["skipped"] += 1
            if not args.dry_run:
                mark_skipped(pg_conn, rec["id"], msg)
            continue

        log.info(f"  [{rec['id']}] {rec['error_type']} – {msg}")

        if args.dry_run:
            print(f"    DRY-RUN payload: {json.dumps(corrected)}")
        else:
            try:
                producer.produce(
                    topic=RAW_TOPIC,
                    key=(corrected.get("deviceId") or "").encode(),
                    value=json.dumps(corrected).encode(),
                    on_delivery=_on_delivery,
                )
                producer.poll(0)
                mark_reingested(pg_conn, rec["id"])
                stats["reingested"] += 1
            except Exception as exc:
                log.error(f"  ❌ Kafka produce failed for id={rec['id']}: {exc}")
                stats["kafka_errors"] += 1

    if not args.dry_run and producer:
        producer.flush(timeout=15)
        stats["kafka_errors"] += len(_delivery_errors)

    pg_conn.close()

    log.info("")
    log.info("═" * 60)
    log.info("  RE-INGESTION SUMMARY")
    log.info("═" * 60)
    log.info(f"  Fetched        : {len(records):>6,}")
    log.info(f"  Re-ingested    : {stats['reingested']:>6,}")
    log.info(f"  Skipped        : {stats['skipped']:>6,}")
    log.info(f"  Kafka errors   : {stats['kafka_errors']:>6,}")
    if args.dry_run:
        log.info("  (DRY-RUN – nothing sent to Kafka)")
    log.info("═" * 60)


if __name__ == "__main__":
    main()
