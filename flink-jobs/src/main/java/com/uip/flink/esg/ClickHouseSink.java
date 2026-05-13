package com.uip.flink.esg;

import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Factory for the ClickHouse JDBC sink used by EsgDualSinkJob.
 *
 * Row contract (Object[7]):
 *   [0] tenant_id   String
 *   [1] building_id String
 *   [2] source_id   String
 *   [3] metric_type String
 *   [4] ts          Instant
 *   [5] value       Double
 *   [6] unit        String
 *
 * Target table: analytics.esg_readings (ADR-026)
 * Batch: 5,000 rows or 2s interval — avoids too-many-small-inserts anti-pattern.
 */
public final class ClickHouseSink {

    static final String DRIVER  = "com.clickhouse.jdbc.ClickHouseDriver";
    static final String INSERT_SQL = """
            INSERT INTO analytics.esg_readings
                (tenant_id, building_id, source_id, metric_type, value, unit, recorded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    static final int    BATCH_SIZE     = 5_000;
    static final long   BATCH_INTERVAL = 2_000L;

    private ClickHouseSink() {}

    public static SinkFunction<Object[]> create(String url, String user, String password) {
        return JdbcSink.sink(
                INSERT_SQL,
                (stmt, row) -> {
                    stmt.setString(1, row[0] != null ? (String) row[0] : "");
                    stmt.setString(2, row[1] != null ? (String) row[1] : "");
                    stmt.setString(3, row[2] != null ? (String) row[2] : "");
                    stmt.setString(4, (String) row[3]);
                    stmt.setDouble(5, (Double) row[5]);
                    stmt.setString(6, (String) row[6]);
                    stmt.setTimestamp(7, Timestamp.from((Instant) row[4]));
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(BATCH_SIZE)
                        .withBatchIntervalMs(BATCH_INTERVAL)
                        .withMaxRetries(3)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(url)
                        .withDriverName(DRIVER)
                        .withUsername(user)
                        .withPassword(password)
                        .build()
        );
    }
}
