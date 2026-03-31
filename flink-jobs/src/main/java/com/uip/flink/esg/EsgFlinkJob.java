package com.uip.flink.esg;

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
import java.time.Instant;
import java.util.Map;

/**
 * S1-08 — EsgFlinkJob
 * Consumes NGSI-LD messages from ngsi_ld_esg → writes to esg.clean_metrics
 */
public class EsgFlinkJob {

    private static final Logger LOG = LoggerFactory.getLogger(EsgFlinkJob.class);

    private static final String KAFKA_BOOTSTRAP = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String DB_URL = System.getenv().getOrDefault("DB_URL",
            "jdbc:postgresql://timescaledb:5432/uip_smartcity");
    private static final String DB_USER = System.getenv().getOrDefault("DB_USER", "uip");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    // Known metric types mapped from measurement keys
    private static final Map<String, String> METRIC_TYPE_MAP = Map.of(
            "energy_kwh", "ENERGY",
            "water_m3",   "WATER",
            "carbon_kg",  "CARBON",
            "waste_kg",   "WASTE"
    );

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        env.getCheckpointConfig().setCheckpointStorage("file:///flink/checkpoints");

        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics("ngsi_ld_esg")
                .setGroupId("flink-esg-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .build();

        SinkFunction<Object[]> jdbcSink = JdbcSink.<Object[]>sink(
                """
                INSERT INTO esg.clean_metrics
                    (source_id, metric_type, timestamp, value, unit, raw_payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                (stmt, row) -> {
                    stmt.setString(1, row[0].toString());
                    stmt.setString(2, row[1].toString());
                    stmt.setObject(3, java.sql.Timestamp.from((Instant) row[2]));
                    stmt.setDouble(4, (Double) row[3]);
                    stmt.setString(5, row[4].toString());
                    stmt.setString(6, row[5].toString());
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
                "ngsi_ld_esg Source"
        )
        .filter(msg -> msg != null && msg.getDeviceIdValue() != null)
        .flatMap((NgsiLdMessage msg, org.apache.flink.util.Collector<Object[]> out) -> {
            // Emit one row per measurement key
            String rawJson = MAPPER.writeValueAsString(msg);
            Instant ts = Instant.ofEpochMilli(msg.getObservedAtMillis());
            msg.getMeasurementValues().forEach((key, value) -> {
                String metricType = METRIC_TYPE_MAP.getOrDefault(key, key.toUpperCase());
                String unit = getUnit(key);
                out.collect(new Object[]{msg.getDeviceIdValue(), metricType, ts, value, unit, rawJson});
            });
        })
        .returns(Object[].class)
        .addSink(jdbcSink)
        .name("TimescaleDB esg.clean_metrics Sink");

        LOG.info("Starting EsgFlinkJob — Kafka={}", KAFKA_BOOTSTRAP);
        env.execute("EsgFlinkJob");
    }

    private static String getUnit(String key) {
        return switch (key) {
            case "energy_kwh" -> "kWh";
            case "water_m3"   -> "m3";
            case "carbon_kg"  -> "kg";
            case "waste_kg"   -> "kg";
            default           -> "";
        };
    }
}
