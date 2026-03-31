package com.uip.flink.traffic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uip.flink.common.NgsiLdDeserializer;
import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Duration;

/**
 * S2-08 — TrafficFlinkJob
 * Consumes NGSI-LD messages from ngsi_ld_traffic → writes to traffic.traffic_counts
 */
public class TrafficFlinkJob {

    private static final Logger LOG = LoggerFactory.getLogger(TrafficFlinkJob.class);

    private static final String KAFKA_BOOTSTRAP = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String DB_URL = System.getenv().getOrDefault("DB_URL",
            "jdbc:postgresql://timescaledb:5432/uip_smartcity");
    private static final String DB_USER     = System.getenv().getOrDefault("DB_USER", "uip");
    private static final String DB_PASSWORD = System.getenv("DB_PASSWORD");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        env.getCheckpointConfig().setCheckpointStorage("file:///flink/checkpoints");

        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics("ngsi_ld_traffic")
                .setGroupId("flink-traffic-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .build();

        SinkFunction<TrafficCount> jdbcSink = JdbcSink.sink(
                """
                INSERT INTO traffic.traffic_counts
                    (intersection_id, timestamp, vehicle_count, avg_speed_kmh, congestion_level, raw_payload)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                (stmt, tc) -> {
                    stmt.setString(1, tc.getIntersectionId());
                    stmt.setTimestamp(2, Timestamp.from(tc.getTimestamp()));
                    stmt.setInt(3, tc.getVehicleCount() != null ? tc.getVehicleCount() : 0);
                    if (tc.getAvgSpeedKmh() != null) stmt.setDouble(4, tc.getAvgSpeedKmh());
                    else stmt.setNull(4, java.sql.Types.DOUBLE);
                    stmt.setString(5, tc.getCongestionLevel());
                    stmt.setString(6, tc.getRawPayload());
                },
                JdbcExecutionOptions.builder()
                        .withBatchSize(200)
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

        env.fromSource(source, WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(5)),
                "ngsi_ld_traffic")
                .map(msg -> {
                    String rawJson = MAPPER.writeValueAsString(msg);
                    return TrafficCount.from(msg, rawJson);
                })
                .addSink(jdbcSink)
                .name("traffic-counts-jdbc-sink");

        LOG.info("TrafficFlinkJob starting, consuming from ngsi_ld_traffic");
        env.execute("UIP — TrafficFlinkJob");
    }
}
