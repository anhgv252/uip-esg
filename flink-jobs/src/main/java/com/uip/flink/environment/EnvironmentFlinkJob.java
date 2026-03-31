package com.uip.flink.environment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uip.flink.common.NgsiLdDeserializer;
import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * S1-03 — EnvironmentFlinkJob
 * Consumes NGSI-LD messages from ngsi_ld_environment → writes to environment.sensor_readings
 */
public class EnvironmentFlinkJob {

    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentFlinkJob.class);

    private static final String KAFKA_BOOTSTRAP = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String DB_URL = System.getenv().getOrDefault("DB_URL",
            "jdbc:postgresql://timescaledb:5432/uip_smartcity");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "uip");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Checkpointing every 30s with RocksDB
        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        env.getCheckpointConfig().setCheckpointStorage("file:///flink/checkpoints");
        env.getCheckpointConfig().setMinPauseBetweenCheckpoints(10_000);
        env.getCheckpointConfig().setCheckpointTimeout(120_000);
        env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);

        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics("ngsi_ld_environment")
                .setGroupId("flink-environment-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .build();

        SinkFunction<EnvironmentReading> jdbcSink = JdbcSink.<EnvironmentReading>sink(
                """
                INSERT INTO environment.sensor_readings
                    (sensor_id, timestamp, aqi, pm25, pm10, o3, no2, so2, co, temperature, humidity, raw_payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT DO NOTHING
                """,
                (stmt, r) -> {
                    stmt.setString(1, r.getSensorId());
                    stmt.setObject(2, java.sql.Timestamp.from(r.getTimestamp()));
                    setNullableDouble(stmt, 3, r.getAqi());
                    setNullableDouble(stmt, 4, r.getPm25());
                    setNullableDouble(stmt, 5, r.getPm10());
                    setNullableDouble(stmt, 6, r.getO3());
                    setNullableDouble(stmt, 7, r.getNo2());
                    setNullableDouble(stmt, 8, r.getSo2());
                    setNullableDouble(stmt, 9, r.getCo());
                    setNullableDouble(stmt, 10, r.getTemperature());
                    setNullableDouble(stmt, 11, r.getHumidity());
                    stmt.setString(12, r.getRawPayload());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(500)
                        .withBatchIntervalMs(1000)
                        .withMaxRetries(3)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(DB_URL)
                        .withDriverName("org.postgresql.Driver")
                        .withUsername(DB_USER)
                        .withPassword(DB_PASSWORD)
                        .build()
        );

        env.fromSource(
                source,
                WatermarkStrategy.<NgsiLdMessage>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, ts) -> event.getObservedAtMillis()),
                "ngsi_ld_environment Source"
        )
        .filter(msg -> msg != null && msg.getDeviceIdValue() != null)
        .map(msg -> {
            String rawJson = MAPPER.writeValueAsString(msg);
            return EnvironmentReading.from(msg, rawJson);
        })
        .addSink(jdbcSink)
        .name("TimescaleDB environment.sensor_readings Sink");

        LOG.info("Starting EnvironmentFlinkJob — Kafka={} DB={}", KAFKA_BOOTSTRAP, DB_URL);
        env.execute("EnvironmentFlinkJob");
    }

    private static void setNullableDouble(java.sql.PreparedStatement stmt, int idx, Double val)
            throws java.sql.SQLException {
        if (val != null) {
            stmt.setDouble(idx, val);
        } else {
            stmt.setNull(idx, java.sql.Types.DOUBLE);
        }
    }
}
