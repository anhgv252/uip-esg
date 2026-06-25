package com.uip.flink.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uip.flink.common.NgsiLdDeserializer;
import com.uip.flink.common.NgsiLdMessage;
import com.uip.flink.common.tenant.TenantBindingProcessFunction;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * M4-AI-01 — District-level Flink batching job.
 *
 * <p>Consumes {@code ngsi_ld_environment}, groups readings by
 * {@code (tenantId, districtCode, sensorType)} and emits one
 * {@link DistrictAggregation} per tumbling window to the
 * {@code ai.district.aggregations} topic. Replaces per-reading AI calls
 * (≈600K calls/min at 10K sensors) with per-window calls (≈50 calls/min),
 * the core of the AI cost optimisation required by G1
 * (AI cost &lt; $1/day @ 10K sensors).</p>
 *
 * <p>Follows the job convention established by {@code VibrationAnomalyJob}:
 * Kafka source with {@link NgsiLdDeserializer}, 30 s EXACTLY_ONCE
 * checkpointing, RocksDB state backend, bounded-out-of-orderness watermarks
 * on {@code observedAt}, Kafka string sink.</p>
 *
 * <p>Config via environment variables (Flink jobs run outside Spring):</p>
 * <ul>
 *   <li>{@code AI_DISTRICT_WINDOW_SECONDS} — tumbling window size (default 60)</li>
 *   <li>{@code AI_DISTRICT_MAX_SENSORS} — cap on retained sensor snapshots (default 500)</li>
 *   <li>{@code AI_DISTRICT_SOURCE_TOPIC} — source topic (default {@code ngsi_ld_environment})</li>
 *   <li>{@code AI_DISTRICT_OUTPUT_TOPIC} — sink topic (default {@code ai.district.aggregations})</li>
 *   <li>{@code KAFKA_BOOTSTRAP} / {@code KAFKA_SECURITY_PROTOCOL} (+ SASL) — Kafka auth</li>
 * </ul>
 */
public class DistrictAggregationJob {

    private static final Logger LOG = LoggerFactory.getLogger(DistrictAggregationJob.class);

    private static final String KAFKA_BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String KAFKA_SECURITY_PROTOCOL =
            System.getenv().getOrDefault("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
    private static final String KAFKA_SASL_MECHANISM =
            System.getenv().getOrDefault("KAFKA_SASL_MECHANISM", "");
    private static final String KAFKA_SASL_JAAS_CONFIG =
            System.getenv().getOrDefault("KAFKA_SASL_JAAS_CONFIG", "");

    /** Source topic — same NGSI-LD stream the other Flink jobs consume. */
    static final String SOURCE_TOPIC =
            System.getenv().getOrDefault("AI_DISTRICT_SOURCE_TOPIC", "ngsi_ld_environment");

    /** Output topic — matches {@code DistrictAggregationConfig.outputTopic} in backend. */
    static final String SINK_TOPIC =
            System.getenv().getOrDefault("AI_DISTRICT_OUTPUT_TOPIC", "ai.district.aggregations");

    /** Tumbling window size in seconds. */
    static final int WINDOW_SECONDS =
            Integer.parseInt(System.getenv().getOrDefault("AI_DISTRICT_WINDOW_SECONDS", "60"));

    /** Cap on retained sensor snapshots per window (bounds state). */
    static final int MAX_SENSORS =
            Integer.parseInt(System.getenv().getOrDefault("AI_DISTRICT_MAX_SENSORS", "500"));

    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        String checkpointDir = System.getenv().getOrDefault("S3_CHECKPOINT_DIR",
                "s3://uip-flink-checkpoints/checkpoints");
        env.getCheckpointConfig().setCheckpointStorage(new FileSystemCheckpointStorage(checkpointDir));

        // --- Source ---
        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics(SOURCE_TOPIC)
                .setGroupId("flink-district-aggregation-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .setProperties(kafkaSecurityProps())
                .build();

        // --- Sink ---
        KafkaSink<String> sink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(SINK_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setKafkaProducerConfig(kafkaSecurityProps())
                .build();

        // --- Pipeline ---
        env.fromSource(
                source,
                WatermarkStrategy.<NgsiLdMessage>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, ts) -> event.getObservedAtMillis())
                        .withIdleness(Duration.ofSeconds(30)),
                "ngsi_ld_environment Source (District Aggregation)"
        )
        // Drop malformed messages + readings missing the district key
        .filter(DistrictAggregationJob::hasDistrictAndValue)
        // ADR-047 §1.3 — bind TenantContext + fail-closed drop records with no tenant.
        // Inserted BEFORE keyBy so every record reaching the window has a tenant bound;
        // does NOT alter the composite key (tenantId is still IN the key) and does NOT
        // touch the window/aggregate logic (G1 window-batching preserved).
        .process(new TenantBindingProcessFunction<>(NgsiLdMessage::getTenantId))
                .name("Tenant Binding (District Aggregation)")
        .keyBy(DistrictAggregationJob::extractKey)
        .window(TumblingEventTimeWindows.of(Time.seconds(WINDOW_SECONDS)))
        .aggregate(
                new DistrictAggregationFunction(MAX_SENSORS),
                new DistrictAggregationFunction.WindowFinalizer())
        .map(agg -> MAPPER.writeValueAsString(agg))
        .sinkTo(sink)
        .name("Kafka " + SINK_TOPIC + " Sink");

        LOG.info("Starting District Aggregation Job (window={}s, maxSensors={}, sink={})",
                WINDOW_SECONDS, MAX_SENSORS, SINK_TOPIC);
        env.execute("District Aggregation Job");
    }

    // ─── Static helpers (unit-testable without a cluster) ────────────────────

    /** A message is processable if it has a district code and a numeric "value". */
    static boolean hasDistrictAndValue(NgsiLdMessage msg) {
        if (msg == null
                || msg.getMeta() == null
                || msg.getMeta().getDistrict() == null
                || msg.getMeta().getDistrict().isBlank()) {
            return false;
        }
        return DistrictAggregationFunction.extractMeasurementValue(msg) != null;
    }

    /** Extracts the composite {@link DistrictKey} used for {@code keyBy}. */
    static DistrictKey extractKey(NgsiLdMessage msg) {
        String sensorType = msg.getMeta() != null ? msg.getMeta().getSensorType() : null;
        return new DistrictKey(
                msg.getTenantId(),
                msg.getMeta().getDistrict(),
                sensorType != null ? sensorType : "UNKNOWN");
    }

    /** Builds the key as a Tuple3 for callers that prefer the Flink tuple form. */
    static Tuple3<String, String, String> extractTupleKey(NgsiLdMessage msg) {
        return Tuple3.of(
                msg.getTenantId(),
                msg.getMeta().getDistrict(),
                msg.getMeta() != null && msg.getMeta().getSensorType() != null
                        ? msg.getMeta().getSensorType() : "UNKNOWN");
    }

    private static java.util.Properties kafkaSecurityProps() {
        java.util.Properties props = new java.util.Properties();
        props.setProperty("security.protocol", KAFKA_SECURITY_PROTOCOL);
        if (!KAFKA_SASL_MECHANISM.isEmpty()) {
            props.setProperty("sasl.mechanism", KAFKA_SASL_MECHANISM);
        }
        if (!KAFKA_SASL_JAAS_CONFIG.isEmpty()) {
            props.setProperty("sasl.jaas.config", KAFKA_SASL_JAAS_CONFIG);
        }
        return props;
    }
}
