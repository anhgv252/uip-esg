"""
esg-service-api  –  Urban Intelligence Platform
================================================

FastAPI serving layer for the ESG & Telemetry Analytics module.
Reads from a single TimescaleDB instance (esg_db) with two schemas:
  esg          – esg.clean_metrics, esg.aggregate_metrics
  error_mgmt   – error_mgmt.error_records, views

Connection pool: psycopg2.pool.ThreadedConnectionPool (2–20 connections).
Each request borrows a connection from the pool and returns it on completion.

Endpoints
─────────
  GET  /health
  GET  /esg/overview
  GET  /esg/sites/{site_id}
  GET  /esg/meters/{meter_id}/timeseries
  GET  /esg/aggregates
  GET  /esg/reports/summary
  GET  /esg/data-quality
  GET  /esg/data-quality/errors
  POST /esg/data-quality/errors/{error_id}/review
  POST /esg/data-quality/errors/bulk-review

Swagger UI : http://localhost:8000/docs
ReDoc      : http://localhost:8000/redoc
"""

from __future__ import annotations

import os
import logging
from contextlib import asynccontextmanager, contextmanager
from datetime import datetime, timedelta, timezone
from typing import Any, Optional

import psycopg2
import psycopg2.extras
from psycopg2 import pool
from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s – %(message)s",
)
log = logging.getLogger("esg-api")

# ── Connection string ─────────────────────────────────────────────
# Single TimescaleDB holds both schemas: esg + error_mgmt
TSDB_DSN = (
    f"host={os.getenv('TIMESCALE_HOST', 'timescaledb')} "
    f"port={os.getenv('TIMESCALE_PORT', '5432')} "
    f"dbname=esg_db user=esg_user password=esg_pass"
)

# Global connection pool (initialised in lifespan)
POOL: pool.ThreadedConnectionPool | None = None


# ── Pool helpers ──────────────────────────────────────────────────

@contextmanager
def get_conn():
    """
    Borrow a connection from the pool, set autocommit=True for
    simplicity (each statement commits immediately), yield it, then
    return it to the pool.
    """
    conn = POOL.getconn()
    conn.autocommit = True
    try:
        yield conn
    finally:
        conn.autocommit = False   # reset to default before returning
        POOL.putconn(conn)


def query(conn, sql: str, params=None) -> list[dict]:
    with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute(sql, params or [])
        return [dict(r) for r in cur.fetchall()]


def execute(conn, sql: str, params=None) -> int:
    with conn.cursor() as cur:
        cur.execute(sql, params or [])
        return cur.rowcount


# ── Response envelope ─────────────────────────────────────────────
def ok(data: Any, meta: dict | None = None) -> dict:
    return {"ok": True, "data": data, "meta": meta or {}}


# ── Pydantic request models ───────────────────────────────────────
class ReviewRequest(BaseModel):
    reviewed_by: str
    notes: Optional[str] = None


class BulkReviewRequest(BaseModel):
    error_type: Optional[str] = None
    source_id:  Optional[str] = None
    reviewed_by: str
    notes: Optional[str] = None


# ── App lifecycle ─────────────────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    global POOL
    log.info("🚀 ESG API starting up…")
    POOL = pool.ThreadedConnectionPool(minconn=2, maxconn=20, dsn=TSDB_DSN)
    try:
        with get_conn() as conn:
            # verify both schemas are accessible
            query(conn, "SELECT 1 FROM esg.clean_metrics LIMIT 0")
            query(conn, "SELECT 1 FROM error_mgmt.error_records LIMIT 0")
        log.info("  ✅ TimescaleDB (esg + error_mgmt schemas) OK")
    except Exception as e:
        log.warning(f"  ⚠️  TimescaleDB not fully ready yet: {e}")
    yield
    POOL.closeall()
    log.info("🛑 ESG API shutting down.")


app = FastAPI(
    title="UIP ESG Service API",
    version="0.2.0-poc",
    description=(
        "ESG & Telemetry Analytics serving layer for Urban Intelligence Platform.\n\n"
        "Reads from a single **TimescaleDB** instance (`esg_db`) with two schemas:\n"
        "- `esg` – clean metrics + aggregates\n"
        "- `error_mgmt` – error records + operator workflow"
    ),
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ═══════════════════════════════════════════════════════════════════
#  HEALTH
# ═══════════════════════════════════════════════════════════════════

@app.get("/health", tags=["ops"], summary="Liveness + DB connectivity check")
def health():
    try:
        with get_conn() as conn:
            query(conn, "SELECT 1 FROM esg.clean_metrics LIMIT 0")
            query(conn, "SELECT 1 FROM error_mgmt.error_records LIMIT 0")
        db_status = "up"
    except Exception:
        db_status = "down"
    ok_flag = db_status == "up"
    return {"ok": ok_flag, "databases": {"esg_db": db_status}}


# ═══════════════════════════════════════════════════════════════════
#  ESG OVERVIEW DASHBOARD
# ═══════════════════════════════════════════════════════════════════

@app.get(
    "/esg/overview",
    tags=["dashboard"],
    summary="KPI tiles + series data for the ESG Overview dashboard",
)
def esg_overview(
    from_dt: Optional[str] = Query(None, description="ISO-8601 start (default: -24h)"),
    to_dt:   Optional[str] = Query(None, description="ISO-8601 end   (default: now)"),
    scope:   Optional[str] = Query(None, description="Filter by site_id"),
):
    now = datetime.now(timezone.utc)
    frm = from_dt or (now - timedelta(hours=24)).isoformat()
    to  = to_dt   or now.isoformat()
    scope_filter = "AND site_id = %(scope)s" if scope else ""
    params = {"frm": frm, "to": to, "scope": scope}

    with get_conn() as conn:
        kpis = query(conn, f"""
            SELECT
                measure_type,
                unit,
                COUNT(*)                          AS record_count,
                ROUND(SUM(value)::numeric,  3)    AS total,
                ROUND(AVG(value)::numeric,  3)    AS avg,
                ROUND(MIN(value)::numeric,  3)    AS min,
                ROUND(MAX(value)::numeric,  3)    AS max,
                COUNT(DISTINCT meter_id)           AS meters,
                COUNT(DISTINCT site_id)            AS sites
            FROM esg.clean_metrics
            WHERE event_ts BETWEEN %(frm)s AND %(to)s
            {scope_filter}
            GROUP BY measure_type, unit
            ORDER BY record_count DESC
        """, params)

        site_summary = query(conn, f"""
            SELECT
                site_id, measure_type,
                ROUND(SUM(value)::numeric, 3) AS total,
                COUNT(DISTINCT meter_id)       AS meters,
                COUNT(*)                       AS records
            FROM esg.clean_metrics
            WHERE event_ts BETWEEN %(frm)s AND %(to)s
            {scope_filter}
            GROUP BY site_id, measure_type
            ORDER BY site_id, total DESC
        """, params)

        # 5-minute bucket time series
        series = query(conn, f"""
            SELECT
                date_trunc('hour', event_ts)
                    + INTERVAL '5 min' * (
                        EXTRACT(MINUTE FROM event_ts)::int / 5
                    )                                          AS bucket,
                measure_type,
                ROUND(AVG(value)::numeric, 3)                  AS avg_value
            FROM esg.clean_metrics
            WHERE event_ts BETWEEN %(frm)s AND %(to)s
            {scope_filter}
            GROUP BY bucket, measure_type
            ORDER BY bucket
        """, params)

    return ok({
        "period": {"from": frm, "to": to},
        "scope": scope,
        "kpis": kpis,
        "site_summary": site_summary,
        "series": series,
    })


# ═══════════════════════════════════════════════════════════════════
#  SITE DETAIL
# ═══════════════════════════════════════════════════════════════════

@app.get(
    "/esg/sites/{site_id}",
    tags=["dashboard"],
    summary="Detailed ESG view for one site – drill-down to building/zone/meter",
)
def site_detail(
    site_id: str,
    from_dt:     Optional[str] = Query(None),
    to_dt:       Optional[str] = Query(None),
    granularity: str           = Query("1h", description="15m | 1h | 1d"),
):
    now = datetime.now(timezone.utc)
    frm = from_dt or (now - timedelta(hours=24)).isoformat()
    to  = to_dt   or now.isoformat()
    gran_map = {"15m": "15 minutes", "1h": "1 hour", "1d": "1 day"}
    gran_sql = gran_map.get(granularity, "1 hour")
    params   = {"site_id": site_id, "frm": frm, "to": to}

    with get_conn() as conn:
        buildings = query(conn, """
            SELECT
                building_id, measure_type, unit,
                ROUND(SUM(value)::numeric, 3) AS total,
                COUNT(*)                       AS records,
                COUNT(DISTINCT meter_id)        AS meters
            FROM esg.clean_metrics
            WHERE site_id = %(site_id)s
              AND event_ts BETWEEN %(frm)s AND %(to)s
            GROUP BY building_id, measure_type, unit
            ORDER BY building_id, total DESC
        """, params)

        series = query(conn, f"""
            SELECT
                date_trunc('hour', event_ts)
                    + INTERVAL '{gran_sql}'
                        * (EXTRACT(EPOCH FROM event_ts -
                                   date_trunc('hour', event_ts))::int
                           / EXTRACT(EPOCH FROM INTERVAL '{gran_sql}')::int
                          )                                          AS bucket,
                meter_id, measure_type,
                ROUND(SUM(value)::numeric, 3) AS total,
                ROUND(AVG(value)::numeric, 3) AS avg
            FROM esg.clean_metrics
            WHERE site_id = %(site_id)s
              AND event_ts BETWEEN %(frm)s AND %(to)s
              AND meter_id IN (
                  SELECT meter_id FROM esg.clean_metrics
                  WHERE site_id = %(site_id)s
                  GROUP BY meter_id ORDER BY COUNT(*) DESC LIMIT 10
              )
            GROUP BY bucket, meter_id, measure_type
            ORDER BY bucket, meter_id
        """, params)

    return ok({
        "site_id": site_id,
        "period": {"from": frm, "to": to},
        "granularity": granularity,
        "buildings": buildings,
        "series": series,
    })


# ═══════════════════════════════════════════════════════════════════
#  METER TIME-SERIES  (drill-down leaf)
# ═══════════════════════════════════════════════════════════════════

@app.get(
    "/esg/meters/{meter_id}/timeseries",
    tags=["dashboard"],
    summary="Raw time-series for a specific meter",
)
def meter_timeseries(
    meter_id: str,
    from_dt:  Optional[str] = Query(None),
    to_dt:    Optional[str] = Query(None),
    limit:    int           = Query(500, le=5000),
):
    now = datetime.now(timezone.utc)
    frm = from_dt or (now - timedelta(hours=6)).isoformat()
    to  = to_dt   or now.isoformat()

    with get_conn() as conn:
        rows = query(conn, """
            SELECT
                event_ts, measure_type, value, unit, quality_flag,
                zone_id, floor_id, building_id, ingested_at
            FROM esg.clean_metrics
            WHERE meter_id = %(meter_id)s
              AND event_ts BETWEEN %(frm)s AND %(to)s
            ORDER BY event_ts DESC
            LIMIT %(limit)s
        """, {"meter_id": meter_id, "frm": frm, "to": to, "limit": limit})

    if not rows:
        raise HTTPException(404, f"No data found for meter '{meter_id}'")

    return ok(
        {"meter_id": meter_id, "records": rows},
        meta={"count": len(rows), "period": {"from": frm, "to": to}},
    )


# ═══════════════════════════════════════════════════════════════════
#  PRE-COMPUTED AGGREGATES  (Flink 1-min event-time windows)
# ═══════════════════════════════════════════════════════════════════

@app.get(
    "/esg/aggregates",
    tags=["dashboard"],
    summary="Query pre-computed 1-min Flink event-time window aggregates",
)
def get_aggregates(
    site_id:      Optional[str] = Query(None),
    meter_id:     Optional[str] = Query(None),
    measure_type: Optional[str] = Query(None),
    from_dt:      Optional[str] = Query(None),
    to_dt:        Optional[str] = Query(None),
    limit:        int           = Query(200, le=2000),
):
    now = datetime.now(timezone.utc)
    frm = from_dt or (now - timedelta(hours=2)).isoformat()
    to  = to_dt   or now.isoformat()

    filters = ["window_start BETWEEN %(frm)s AND %(to)s"]
    params: dict = {"frm": frm, "to": to, "limit": limit}
    if site_id:
        filters.append("site_id = %(site_id)s");           params["site_id"] = site_id
    if meter_id:
        filters.append("meter_id = %(meter_id)s");         params["meter_id"] = meter_id
    if measure_type:
        filters.append("measure_type = %(measure_type)s"); params["measure_type"] = measure_type

    where = " AND ".join(filters)
    with get_conn() as conn:
        rows = query(conn, f"""
            SELECT
                meter_id, site_id, measure_type, unit,
                window_start, window_end,
                ROUND(total_value::numeric, 3) AS total_value,
                ROUND(avg_value::numeric,   3) AS avg_value,
                ROUND(min_value::numeric,   3) AS min_value,
                ROUND(max_value::numeric,   3) AS max_value,
                record_count
            FROM esg.aggregate_metrics
            WHERE {where}
            ORDER BY window_start DESC
            LIMIT %(limit)s
        """, params)

    return ok(rows, meta={"count": len(rows)})


# ═══════════════════════════════════════════════════════════════════
#  ESG REPORTS
# ═══════════════════════════════════════════════════════════════════

@app.get(
    "/esg/reports/summary",
    tags=["reports"],
    summary="ESG summary per period (daily | weekly | monthly)",
)
def report_summary(
    site_id: Optional[str] = Query(None),
    period:  str           = Query("daily", description="daily | weekly | monthly"),
):
    trunc = {"daily": "day", "weekly": "week", "monthly": "month"}.get(period, "day")
    site_filter = "WHERE site_id = %(site_id)s" if site_id else ""

    with get_conn() as conn:
        rows = query(conn, f"""
            SELECT
                date_trunc('{trunc}', event_ts) AS period_start,
                site_id, measure_type, unit,
                ROUND(SUM(value)::numeric, 3)    AS total,
                ROUND(AVG(value)::numeric, 3)    AS avg,
                COUNT(*)                          AS records,
                COUNT(DISTINCT meter_id)          AS meters
            FROM esg.clean_metrics
            {site_filter}
            GROUP BY period_start, site_id, measure_type, unit
            ORDER BY period_start DESC, site_id, total DESC
            LIMIT 500
        """, {"site_id": site_id} if site_id else {})

    return ok(rows, meta={"period": period, "site_id": site_id})


# ═══════════════════════════════════════════════════════════════════
#  DATA QUALITY
# ═══════════════════════════════════════════════════════════════════

@app.get(
    "/esg/data-quality",
    tags=["data-quality"],
    summary="Data Quality dashboard: totals, error rates, error-type breakdown",
)
def data_quality_overview():
    with get_conn() as conn:
        clean = query(conn, """
            SELECT
                COUNT(*) AS clean_count,
                COUNT(DISTINCT meter_id) AS unique_meters,
                MIN(event_ts) AS first_event,
                MAX(event_ts) AS last_event
            FROM esg.clean_metrics
        """)[0]

        errors = query(conn, """
            SELECT
                COUNT(*)                                AS error_count,
                COUNT(*) FILTER (WHERE reviewed)        AS reviewed_count,
                COUNT(*) FILTER (WHERE reingested)      AS reingested_count
            FROM error_mgmt.error_records
        """)[0]

        error_breakdown = query(conn, "SELECT * FROM error_mgmt.error_summary")
        error_by_source = query(conn, "SELECT * FROM error_mgmt.error_by_source")

    total = (clean["clean_count"] or 0) + (errors["error_count"] or 0)
    quality_rate = round(clean["clean_count"] / total * 100, 2) if total else 0

    return ok({
        "summary": {
            "total_processed":  total,
            "clean_records":    clean["clean_count"],
            "error_records":    errors["error_count"],
            "reviewed":         errors["reviewed_count"],
            "reingested":       errors["reingested_count"],
            "quality_rate_pct": quality_rate,
        },
        "clean_stats":      clean,
        "error_breakdown":  error_breakdown,
        "error_by_source":  error_by_source,
    })


@app.get(
    "/esg/data-quality/errors",
    tags=["data-quality"],
    summary="Paginated list of error records with optional filters",
)
def list_errors(
    error_type: Optional[str]  = Query(None),
    reviewed:   Optional[bool] = Query(None),
    source_id:  Optional[str]  = Query(None),
    limit:      int            = Query(50, le=500),
    offset:     int            = Query(0),
):
    filters = ["1=1"]
    params: dict = {"limit": limit, "offset": offset}
    if error_type:
        filters.append("error_type = %(error_type)s"); params["error_type"] = error_type
    if reviewed is not None:
        filters.append("reviewed = %(reviewed)s");     params["reviewed"] = reviewed
    if source_id:
        filters.append("source_id = %(source_id)s");   params["source_id"] = source_id

    where = " AND ".join(filters)
    with get_conn() as conn:
        total = query(conn,
            f"SELECT COUNT(*) AS n FROM error_mgmt.error_records WHERE {where}",
            params)[0]["n"]
        rows = query(conn, f"""
            SELECT
                id, meter_id, site_id, event_timestamp,
                measure_type, raw_value, unit,
                error_type, error_detail, source_id,
                received_at, reviewed, reviewed_by, reviewed_at,
                reingested, reingested_at, notes
            FROM error_mgmt.error_records
            WHERE {where}
            ORDER BY received_at DESC
            LIMIT %(limit)s OFFSET %(offset)s
        """, params)

    return ok(rows, meta={"total": total, "limit": limit, "offset": offset})


@app.post(
    "/esg/data-quality/errors/{error_id}/review",
    tags=["data-quality"],
    summary="Mark a single error record as reviewed (operator action)",
)
def review_error(error_id: int, body: ReviewRequest):
    with get_conn() as conn:
        affected = execute(conn, """
            UPDATE error_mgmt.error_records
            SET reviewed    = TRUE,
                reviewed_by = %(by)s,
                reviewed_at = NOW(),
                notes       = %(notes)s
            WHERE id = %(id)s AND reviewed = FALSE
        """, {"id": error_id, "by": body.reviewed_by, "notes": body.notes})

    if affected == 0:
        raise HTTPException(404, "Record not found or already reviewed")
    return ok({"id": error_id, "reviewed": True, "reviewed_by": body.reviewed_by})


@app.post(
    "/esg/data-quality/errors/bulk-review",
    tags=["data-quality"],
    summary="Bulk-mark errors as reviewed – filter by error_type and/or source_id",
)
def bulk_review(body: BulkReviewRequest):
    if not body.error_type and not body.source_id:
        raise HTTPException(400, "Provide at least one filter: error_type or source_id")

    filters = ["reviewed = FALSE"]
    params: dict = {"by": body.reviewed_by, "notes": body.notes}
    if body.error_type:
        filters.append("error_type = %(error_type)s"); params["error_type"] = body.error_type
    if body.source_id:
        filters.append("source_id = %(source_id)s");   params["source_id"] = body.source_id

    where = " AND ".join(filters)
    with get_conn() as conn:
        affected = execute(conn, f"""
            UPDATE error_mgmt.error_records
            SET reviewed    = TRUE,
                reviewed_by = %(by)s,
                reviewed_at = NOW(),
                notes       = %(notes)s
            WHERE {where}
        """, params)

    return ok({
        "updated":     affected,
        "error_type":  body.error_type,
        "source_id":   body.source_id,
        "reviewed_by": body.reviewed_by,
    })
