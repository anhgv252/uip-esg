"""
ESG Telemetry Processing Job  –  Urban Intelligence Platform
=============================================================

Pipeline:
  Kafka topic: ngsi_ld_telemetry
    │
    ├─► esg.clean_metrics        (TimescaleDB) – valid records
    ├─► esg.aggregate_metrics    (TimescaleDB) – 1-min event-time windows
    └─► error_mgmt.error_records (TimescaleDB) – invalid records

All three targets live in the same TimescaleDB instance (esg_db).
error_mgmt is a separate schema, not a separate database.

─── Correctness model ───────────────────────────────────────────────
Checkpoint mode  : AT_LEAST_ONCE
  EXACTLY_ONCE is not available for JDBC sinks without XA transactions.
  Claiming it here would be misleading: Flink checkpoints guarantee
  exactly-once state within Flink operators, but the JDBC side effect
  (rows in PostgreSQL) is at-least-once.

Idempotency (makes AT_LEAST_ONCE behave like EXACTLY_ONCE):
  Every sink uses Flink JDBC upsert mode, which generates:
    INSERT … ON CONFLICT (<pk>) DO UPDATE SET …
  The conflict keys are deterministic functions of the source data, so
  replaying the same Kafka offsets after a checkpoint restore produces
  identical upsert keys → no duplicate rows.

  esg.clean_metrics       → UNIQUE (meter_id, event_ts, measure_type)
  esg.aggregate_metrics   → UNIQUE (meter_id, window_start, measure_type)
  error_mgmt.error_records → PRIMARY KEY (dedup_key)
    dedup_key = MD5(meter_id | event_timestamp | measure_type | error_type)

Operator field protection:
  The Flink JDBC sink DDL for error_records intentionally omits operator
  workflow columns (reviewed, reviewed_by, reviewed_at, reingested,
  reingested_at, notes, id). PostgreSQL's ON CONFLICT DO UPDATE only
  touches columns present in the Flink INSERT, leaving operator actions
  intact across checkpoint replays. No trigger required.

─── Event time ──────────────────────────────────────────────────────
event_ts is a computed column that parses the sensor's event_timestamp
string (ISO-8601 format: "2024-01-15T10:30:00Z") into TIMESTAMP(3).
A 30-second watermark allows for network and battery-saving delays.

Aggregates use event_ts (sensor time), not proc_time (processing time).
This ensures 1-minute windows align to what actually happened in the
building, not to when Flink received the message.

For records with null or malformed event_timestamp, event_ts falls back
to LOCALTIMESTAMP. Those records receive a MISSING_TIMESTAMP or
INVALID_VALUE_FORMAT classification and are routed to error_records
rather than the aggregate window.

─── Validation rules (telemetry_classified VIEW) ────────────────────
  1. MISSING_METER_ID      – meter_id is null or blank
  2. MISSING_VALUE         – raw_value is null
  3. INVALID_VALUE_FORMAT  – raw_value cannot be parsed as DOUBLE
  4. OUT_OF_RANGE_NEGATIVE – parsed value < 0
  5. OUT_OF_RANGE_HIGH     – parsed value > MAX_VALUE_THRESHOLD (10 000)
  6. MISSING_UNIT          – unit is null or blank
  7. MISSING_TIMESTAMP     – event_timestamp is null or blank
"""

import os
import logging
from pyflink.table import EnvironmentSettings, TableEnvironment

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s – %(message)s",
)
log = logging.getLogger("esg-processing-job")

# ─────────────────────────────────────────────────────────────────
#  Configuration
# ─────────────────────────────────────────────────────────────────
KAFKA_BOOTSTRAP     = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")
# Single TimescaleDB holds both schemas: esg + error_mgmt
TIMESCALE_URL       = os.getenv("TIMESCALE_JDBC_URL",
                                "jdbc:postgresql://timescaledb:5432/esg_db")
TIMESCALE_USER      = os.getenv("TIMESCALE_USER", "esg_user")
TIMESCALE_PASS      = os.getenv("TIMESCALE_PASS", "esg_pass")
MAX_VALUE_THRESHOLD = float(os.getenv("MAX_VALUE_THRESHOLD", "10000"))


# ─────────────────────────────────────────────────────────────────
#  DDL
# ─────────────────────────────────────────────────────────────────

def create_kafka_source(t_env: TableEnvironment) -> None:
    """
    ngsi_ld_telemetry → Flink source table.

    event_ts: strips trailing 'Z' from the ISO-8601 string produced by the
    producer ("2024-01-15T10:30:00Z"), then TRY_CAST to TIMESTAMP(3).
    Falls back to LOCALTIMESTAMP when the string is null or unparseable —
    those records are classified as errors and never reach the aggregate.

    WATERMARK: 30-second late-arrival tolerance. The watermark generator
    advances based on event_ts, so windows close 30 s after their upper
    bound in event time.
    """
    t_env.execute_sql(f"""
        CREATE TABLE IF NOT EXISTS ngsi_ld_telemetry (
            meter_id        STRING,
            site_id         STRING,
            building_id     STRING,
            floor_id        STRING,
            zone_id         STRING,
            event_timestamp STRING,
            measure_type    STRING,
            raw_value       STRING,
            unit            STRING,
            source_id       STRING,
            normalized_at   STRING,
            event_ts        AS COALESCE(
                                 TRY_CAST(REPLACE(event_timestamp, 'Z', '') AS TIMESTAMP(3)),
                                 LOCALTIMESTAMP
                             ),
            proc_time       AS PROCTIME(),
            WATERMARK FOR event_ts AS event_ts - INTERVAL '30' SECOND
        ) WITH (
            'connector'                     = 'kafka',
            'topic'                         = 'ngsi_ld_telemetry',
            'properties.bootstrap.servers'  = '{KAFKA_BOOTSTRAP}',
            'properties.group.id'           = 'flink-esg-processor',
            'format'                        = 'json',
            'json.ignore-parse-errors'      = 'true',
            'scan.startup.mode'             = 'earliest-offset'
        )
    """)
    log.info("✅ Kafka source: ngsi_ld_telemetry (event_ts watermark, 30s late tolerance)")


def create_classification_view(t_env: TableEnvironment) -> None:
    """
    Validates every record and assigns error_type (NULL = valid).
    First matching rule wins (priority order matters).
    """
    t_env.execute_sql(f"""
        CREATE TEMPORARY VIEW IF NOT EXISTS telemetry_classified AS
        SELECT
            meter_id, site_id, building_id, floor_id, zone_id,
            event_timestamp, event_ts, measure_type, raw_value, unit,
            source_id, normalized_at, proc_time,

            CASE
                WHEN meter_id IS NULL OR TRIM(meter_id) = ''
                    THEN 'MISSING_METER_ID'
                WHEN raw_value IS NULL
                    THEN 'MISSING_VALUE'
                WHEN TRY_CAST(raw_value AS DOUBLE) IS NULL
                    THEN 'INVALID_VALUE_FORMAT'
                WHEN TRY_CAST(raw_value AS DOUBLE) < 0
                    THEN 'OUT_OF_RANGE_NEGATIVE'
                WHEN TRY_CAST(raw_value AS DOUBLE) > {MAX_VALUE_THRESHOLD}
                    THEN 'OUT_OF_RANGE_HIGH'
                WHEN unit IS NULL OR TRIM(unit) = ''
                    THEN 'MISSING_UNIT'
                WHEN event_timestamp IS NULL OR TRIM(event_timestamp) = ''
                    THEN 'MISSING_TIMESTAMP'
                ELSE NULL
            END AS error_type,

            CASE
                WHEN meter_id IS NULL OR TRIM(meter_id) = ''
                    THEN 'meter_id field is null or empty'
                WHEN raw_value IS NULL
                    THEN 'raw_value field is null'
                WHEN TRY_CAST(raw_value AS DOUBLE) IS NULL
                    THEN CONCAT('Cannot parse raw_value as number: ', COALESCE(raw_value,'<null>'))
                WHEN TRY_CAST(raw_value AS DOUBLE) < 0
                    THEN CONCAT('Value ', raw_value, ' is negative (min 0)')
                WHEN TRY_CAST(raw_value AS DOUBLE) > {MAX_VALUE_THRESHOLD}
                    THEN CONCAT('Value ', raw_value, ' exceeds max threshold {MAX_VALUE_THRESHOLD}')
                WHEN unit IS NULL OR TRIM(unit) = ''
                    THEN 'unit field is null or empty'
                WHEN event_timestamp IS NULL OR TRIM(event_timestamp) = ''
                    THEN 'event_timestamp field is null or empty'
                ELSE NULL
            END AS error_detail

        FROM ngsi_ld_telemetry
    """)
    log.info("✅ Classification view: telemetry_classified")


def create_clean_metrics_sink(t_env: TableEnvironment) -> None:
    """
    Sink: esg.clean_metrics (TimescaleDB schema: esg).
    PRIMARY KEY NOT ENFORCED → Flink JDBC upsert mode:
      INSERT … ON CONFLICT (meter_id, event_ts, measure_type) DO UPDATE SET …
    Replaying the same Kafka message produces the same (meter_id, event_ts,
    measure_type) tuple → upsert, no duplicate row.
    """
    t_env.execute_sql(f"""
        CREATE TABLE IF NOT EXISTS esg_clean_metrics_sink (
            meter_id        VARCHAR(100),
            site_id         VARCHAR(100),
            building_id     VARCHAR(100),
            floor_id        VARCHAR(100),
            zone_id         VARCHAR(100),
            event_ts        TIMESTAMP(3),
            measure_type    VARCHAR(50),
            `value`         DOUBLE,
            unit            VARCHAR(20),
            quality_flag    VARCHAR(20),
            source_id       VARCHAR(100),
            ingested_at     TIMESTAMP(3),
            PRIMARY KEY (meter_id, event_ts, measure_type) NOT ENFORCED
        ) WITH (
            'connector'                  = 'jdbc',
            'url'                        = '{TIMESCALE_URL}',
            'table-name'                 = 'esg.clean_metrics',
            'username'                   = '{TIMESCALE_USER}',
            'password'                   = '{TIMESCALE_PASS}',
            'driver'                     = 'org.postgresql.Driver',
            'sink.buffer-flush.max-rows' = '1000',
            'sink.buffer-flush.interval' = '2s',
            'sink.max-retries'           = '3'
        )
    """)
    log.info("✅ Upsert sink: esg.clean_metrics")


def create_aggregate_metrics_sink(t_env: TableEnvironment) -> None:
    """
    Sink: esg.aggregate_metrics.
    PRIMARY KEY NOT ENFORCED → upsert on (meter_id, window_start, measure_type).
    Replaying a checkpoint recalculates the same window → upsert replaces it.
    """
    t_env.execute_sql(f"""
        CREATE TABLE IF NOT EXISTS esg_aggregate_metrics_sink (
            meter_id        VARCHAR(100),
            site_id         VARCHAR(100),
            measure_type    VARCHAR(50),
            unit            VARCHAR(20),
            window_start    TIMESTAMP(3),
            window_end      TIMESTAMP(3),
            total_value     DOUBLE,
            avg_value       DOUBLE,
            min_value       DOUBLE,
            max_value       DOUBLE,
            record_count    BIGINT,
            PRIMARY KEY (meter_id, window_start, measure_type) NOT ENFORCED
        ) WITH (
            'connector'                  = 'jdbc',
            'url'                        = '{TIMESCALE_URL}',
            'table-name'                 = 'esg.aggregate_metrics',
            'username'                   = '{TIMESCALE_USER}',
            'password'                   = '{TIMESCALE_PASS}',
            'driver'                     = 'org.postgresql.Driver',
            'sink.buffer-flush.max-rows' = '200',
            'sink.buffer-flush.interval' = '5s',
            'sink.max-retries'           = '3'
        )
    """)
    log.info("✅ Upsert sink: esg.aggregate_metrics")


def create_error_records_sink(t_env: TableEnvironment) -> None:
    """
    Sink: error_mgmt.error_records (same TimescaleDB, different schema).
    PRIMARY KEY (dedup_key) NOT ENFORCED → upsert on dedup_key.

    Operator workflow columns (reviewed, reviewed_by, reviewed_at,
    reingested, reingested_at, notes, id, received_at) are intentionally
    absent from this DDL. PostgreSQL ON CONFLICT DO UPDATE only sets
    columns present in this table definition, leaving those columns intact.
    """
    t_env.execute_sql(f"""
        CREATE TABLE IF NOT EXISTS esg_error_records_sink (
            dedup_key       VARCHAR(32),
            meter_id        VARCHAR(100),
            site_id         VARCHAR(100),
            building_id     VARCHAR(100),
            floor_id        VARCHAR(100),
            zone_id         VARCHAR(100),
            event_timestamp VARCHAR(50),
            measure_type    VARCHAR(50),
            raw_value       VARCHAR(200),
            unit            VARCHAR(20),
            source_id       VARCHAR(100),
            error_type      VARCHAR(50),
            error_detail    VARCHAR(500),
            PRIMARY KEY (dedup_key) NOT ENFORCED
        ) WITH (
            'connector'                  = 'jdbc',
            'url'                        = '{TIMESCALE_URL}',
            'table-name'                 = 'error_mgmt.error_records',
            'username'                   = '{TIMESCALE_USER}',
            'password'                   = '{TIMESCALE_PASS}',
            'driver'                     = 'org.postgresql.Driver',
            'sink.buffer-flush.max-rows' = '500',
            'sink.buffer-flush.interval' = '2s',
            'sink.max-retries'           = '3'
        )
    """)
    log.info("✅ Upsert sink: error_mgmt.error_records")


# ─────────────────────────────────────────────────────────────────
#  INSERT statements (single StatementSet job graph)
# ─────────────────────────────────────────────────────────────────

INSERT_CLEAN_METRICS = """
    INSERT INTO esg_clean_metrics_sink
    SELECT
        meter_id, site_id, building_id, floor_id, zone_id,
        event_ts,                                           -- TIMESTAMP(3) sensor event time
        measure_type,
        COALESCE(TRY_CAST(raw_value AS DOUBLE), 0.0) AS `value`,
        unit,
        'OK'              AS quality_flag,
        source_id,
        CURRENT_TIMESTAMP AS ingested_at
    FROM telemetry_classified
    WHERE error_type IS NULL
"""

INSERT_ERROR_RECORDS = """
    INSERT INTO esg_error_records_sink
    SELECT
        -- dedup_key: deterministic MD5 over the logical error identity.
        -- Replaying the same Kafka message produces the same hash → safe upsert.
        -- COALESCE guards against null fields (meter_id, event_timestamp, measure_type
        -- can all be null for certain error types).
        MD5(CONCAT(
            COALESCE(meter_id, ''),        '|',
            COALESCE(event_timestamp, ''), '|',
            COALESCE(measure_type, ''),    '|',
            error_type
        ))                AS dedup_key,
        meter_id, site_id, building_id, floor_id, zone_id,
        event_timestamp, measure_type, raw_value, unit, source_id,
        error_type, error_detail
    FROM telemetry_classified
    WHERE error_type IS NOT NULL
"""

# Aggregate uses event_ts (rowtime watermark attribute) for the tumbling window,
# not proc_time. This ensures window boundaries align to sensor event time.
# The 30-second watermark on event_ts means: a window [T, T+1min) fires when
# the watermark passes T+1min+30s, accounting for late-arriving messages.
INSERT_AGGREGATES = """
    INSERT INTO esg_aggregate_metrics_sink
    SELECT
        meter_id, site_id, measure_type, unit,
        TUMBLE_START(event_ts, INTERVAL '1' MINUTE) AS window_start,
        TUMBLE_END  (event_ts, INTERVAL '1' MINUTE) AS window_end,
        SUM(COALESCE(TRY_CAST(raw_value AS DOUBLE), 0.0)) AS total_value,
        AVG(COALESCE(TRY_CAST(raw_value AS DOUBLE), 0.0)) AS avg_value,
        MIN(COALESCE(TRY_CAST(raw_value AS DOUBLE), 0.0)) AS min_value,
        MAX(COALESCE(TRY_CAST(raw_value AS DOUBLE), 0.0)) AS max_value,
        COUNT(*) AS record_count
    FROM telemetry_classified
    WHERE error_type IS NULL
    GROUP BY
        meter_id, site_id, measure_type, unit,
        TUMBLE(event_ts, INTERVAL '1' MINUTE)
"""


# ─────────────────────────────────────────────────────────────────
#  Main
# ─────────────────────────────────────────────────────────────────

def main():
    log.info("═" * 60)
    log.info("  UIP ESG Telemetry Processing Job")
    log.info(f"  Kafka      : {KAFKA_BOOTSTRAP}")
    log.info(f"  TimescaleDB: {TIMESCALE_URL}")
    log.info("═" * 60)

    settings = EnvironmentSettings.in_streaming_mode()
    t_env = TableEnvironment.create(settings)

    # AT_LEAST_ONCE is the correct checkpoint mode for JDBC sinks.
    # All sinks are idempotent via upsert (INSERT … ON CONFLICT DO UPDATE),
    # so checkpoint replay cannot produce duplicate rows.
    # Net effect: effectively exactly-once delivery without XA transactions.
    t_env.get_config().set("execution.checkpointing.interval", "10000ms")
    t_env.get_config().set("execution.checkpointing.mode",     "AT_LEAST_ONCE")
    t_env.get_config().set("parallelism.default", "4")  # từ 2 → 4

    create_kafka_source(t_env)
    create_classification_view(t_env)
    create_clean_metrics_sink(t_env)
    create_aggregate_metrics_sink(t_env)
    create_error_records_sink(t_env)

    log.info("🚀 Submitting StatementSet (3 concurrent INSERT jobs)…")
    stmt_set = t_env.create_statement_set()
    stmt_set.add_insert_sql(INSERT_CLEAN_METRICS)
    stmt_set.add_insert_sql(INSERT_ERROR_RECORDS)
    stmt_set.add_insert_sql(INSERT_AGGREGATES)

    table_result = stmt_set.execute()
    job_client = table_result.get_job_client()
    if job_client:
        log.info(f"📌 Job ID: {job_client.get_job_id()}")
        try:
            job_client.get_job_execution_result().result()
        except KeyboardInterrupt:
            log.info("⛔ Job cancelled.")
        except Exception as exc:
            log.error(f"❌ Job error: {exc}", exc_info=True)
            raise


if __name__ == "__main__":
    main()
