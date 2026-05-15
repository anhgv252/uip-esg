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
import org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * EsgDualSinkJob — dual-write ESG readings to TimescaleDB AND ClickHouse (ADR-026).
 *
 * Row format (Object[7]) used by both sinks:
 *   [0] tenant_id   String  — from NgsiLdMessage._meta.tenantId
 *   [1] building_id String  — extracted from deviceId prefix (empty if not parseable)
 *   [2] source_id   String  — deviceId value
 *   [3] metric_type String  — mapped from measurement key
 *   [4] ts          Instant — observedAt epoch millis
 *   [5] value       Double  — measurement value
 *   [6] unit        String  — kWh / m3 / kg / ""
 *
 * UID assignment (.uid()) on every sink is mandatory for checkpoint restore (ADR-026).
 */
public class EsgDualSinkJob {

    private static final Logger LOG = LoggerFactory.getLogger(EsgDualSinkJob.class);

    private static final String KAFKA_BOOTSTRAP = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String DB_URL          = System.getenv().getOrDefault("DB_URL",
            "jdbc:postgresql://timescaledb:5432/uip_smartcity");
    private static final String DB_USER         = System.getenv().getOrDefault("DB_USER", "uip");
    private static final String DB_PASSWORD     = System.getenv("DB_PASSWORD");
    private static final String CH_URL          = System.getenv().getOrDefault("CLICKHOUSE_URL",
            "jdbc:clickhouse://clickhouse:8123/analytics");
    private static final String CH_USER         = System.getenv().getOrDefault("CLICKHOUSE_USER", "uip_analytics");
    private static final String CH_PASSWORD     = System.getenv().getOrDefault("CLICKHOUSE_PASSWORD", "uip_analytics_pwd");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

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

        String checkpointDir = System.getenv().getOrDefault("S3_CHECKPOINT_DIR",
                "s3://uip-flink-checkpoints/checkpoints");
        env.getCheckpointConfig().setCheckpointStorage(new FileSystemCheckpointStorage(checkpointDir));

        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics("ngsi_ld_esg")
                .setGroupId("flink-esg-dual-sink-job")
                // committedOffsets(EARLIEST): first deploy reads from beginning, restart resumes from committed
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .build();

        SingleOutputStreamOperator<Object[]> stream = env
                .fromSource(
                        source,
                        WatermarkStrategy.<NgsiLdMessage>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                                .withTimestampAssigner((event, ts) -> event.getObservedAtMillis()),
                        "ngsi_ld_esg Source"
                )
                .filter(msg -> msg != null && msg.getDeviceIdValue() != null)
                .process(new TenantIdValidator())
                .flatMap((NgsiLdMessage msg, org.apache.flink.util.Collector<Object[]> out) -> {
                    String tenantId   = msg.getTenantId() != null ? msg.getTenantId() : "";
                    String deviceId   = msg.getDeviceIdValue();
                    String buildingId = extractBuildingId(deviceId);
                    Instant ts        = Instant.ofEpochMilli(msg.getObservedAtMillis());

                    msg.getMeasurementValues().forEach((key, value) -> {
                        String metricType = METRIC_TYPE_MAP.getOrDefault(key, key.toUpperCase());
                        String unit       = getUnit(key);
                        out.collect(new Object[]{tenantId, buildingId, deviceId, metricType, ts, value, unit});
                    });
                })
                .returns(Object[].class);

        // TimescaleDB sink — uid bắt buộc cho checkpoint restore
        stream.addSink(buildTimescaleSink())
              .uid("timescaledb-esg-sink")
              .name("TimescaleDB esg.clean_metrics Sink");

        // ClickHouse sink — uid bắt buộc cho checkpoint restore
        stream.addSink(ClickHouseSink.create(CH_URL, CH_USER, CH_PASSWORD))
              .uid("clickhouse-esg-sink")
              .name("ClickHouse analytics.esg_readings Sink");

        LOG.info("Starting EsgDualSinkJob — Kafka={} CH={}", KAFKA_BOOTSTRAP, CH_URL);
        env.execute("EsgDualSinkJob");
    }

    private static org.apache.flink.streaming.api.functions.sink.SinkFunction<Object[]> buildTimescaleSink() {
        return JdbcSink.sink(
                """
                INSERT INTO esg.clean_metrics
                    (source_id, metric_type, timestamp, value, unit, tenant_id, building_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                (stmt, row) -> {
                    stmt.setString(1, (String) row[2]);
                    stmt.setString(2, (String) row[3]);
                    stmt.setObject(3, java.sql.Timestamp.from((Instant) row[4]));
                    stmt.setDouble(4, (Double) row[5]);
                    stmt.setString(5, (String) row[6]);
                    stmt.setString(6, (String) row[0]);
                    stmt.setString(7, (String) row[1]);
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(500)
                        .withBatchIntervalMs(1_000)
                        .withMaxRetries(3)
                        .build(),
                new JdbcConnectionOptions.JdbcConnectionOptionsBuilder()
                        .withUrl(DB_URL)
                        .withDriverName("org.postgresql.Driver")
                        .withUsername(DB_USER)
                        .withPassword(DB_PASSWORD)
                        .build()
        );
    }

    /**
     * Extracts building_id from deviceId patterns like "BLD-001-SENSOR-01" or "SENSOR-BLD-001-001".
     * Returns empty string if no pattern matches — downstream analytics handles empty building_id.
     */
    static String extractBuildingId(String deviceId) {
        if (deviceId == null) return "";
        if (deviceId.startsWith("SENSOR-")) {
            String rest = deviceId.substring("SENSOR-".length());
            int lastDash = rest.lastIndexOf('-');
            if (lastDash > 0) return rest.substring(0, lastDash);
        }
        int sensorIdx = deviceId.indexOf("-SENSOR-");
        if (sensorIdx > 0) return deviceId.substring(0, sensorIdx);
        LOG.warn("Could not extract building_id from deviceId={}", deviceId);
        return "";
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
