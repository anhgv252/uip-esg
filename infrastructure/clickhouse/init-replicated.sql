-- ClickHouse HA init — Replicated tables for 2-node cluster (ADR-036)
-- Runs automatically when HA containers start for the first time.
-- Uses ReplicatedReplacingMergeTree to auto-deduplicate rows.

CREATE DATABASE IF NOT EXISTS analytics;

-- Replicated version of analytics.esg_readings
-- ReplacingMergeTree uses `ingested_at` as version column for dedup.
-- ZooKeeper path: /clickhouse/tables/{shard}/esg_readings
-- Replica name from CLICKHOUSE_REPLICA_NAME env var (macros config).
CREATE TABLE IF NOT EXISTS analytics.esg_readings
(
    tenant_id    String,
    building_id  String,
    source_id    String DEFAULT '',
    metric_type  LowCardinality(String),
    value        Float64,
    unit         LowCardinality(String) DEFAULT '',
    recorded_at  DateTime CODEC(DoubleDelta, ZSTD(3)),
    ingested_at  DateTime DEFAULT now(),
    building_name String DEFAULT '',
    district     String DEFAULT ''
)
ENGINE = ReplicatedReplacingMergeTree(
    '/clickhouse/tables/{shard}/analytics/esg_readings',
    '{replica}',
    ingested_at
)
PARTITION BY toYYYYMM(recorded_at)
ORDER BY (tenant_id, building_id, source_id, metric_type, recorded_at)
TTL recorded_at + toIntervalYear(2)
SETTINGS index_granularity = 8192;

-- ─── M5-2 mTLS auth user (T09 follow-up) ─────────────────────────────────
-- JDBC consumers (analytics-service, backend) connect over HTTPS 8443 with
-- mutual-TLS. The transport layer (openSSL caConfig strict in tls-config.xml)
-- guarantees only ca-signed clients can open a connection. This user carries a
-- password because the ClickHouse JDBC driver always sends a credential on
-- connect and CH 23.8 rejects "no password sent" for plaintext/no_password
-- users over the HTTP interface (516). mTLS transport remains the primary
-- trust gate; this password is a secondary auth over the already-encrypted
-- channel. CH 23.8 HTTP iface does not forward client-cert CN to SQL auth,
-- so ssl_certificate CN->user mapping also fails — hence the dedicated user.
CREATE USER IF NOT EXISTS uip_jdbc IDENTIFIED WITH sha256_password BY 'uip_jdbc_mtls_2026' DEFAULT DATABASE analytics;
GRANT SELECT ON analytics.* TO uip_jdbc;
