package com.uip.flink.structural;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uip.flink.common.NgsiLdDeserializer;
import com.uip.flink.common.NgsiLdMessage;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * S7-B02 — Structural Vibration Anomaly Detection Job (Flink CEP + Welford).
 *
 * <p>Consumes {@code ngsi_ld_environment}, filters for structural sensor types
 * (STRUCTURAL_VIBRATION, STRUCTURAL_TILT, STRUCTURAL_CRACK), and detects anomalies
 * using the Welford online standard deviation algorithm combined with Flink CEP
 * pattern matching (3 consecutive spikes within 10 seconds).</p>
 *
 * <p><strong>BR-010:</strong> All structural P0 alerts require operator review.
 * The system does NOT auto-evacuate.</p>
 *
 * <p>Thresholds per TCVN 9386:2012 + ISO 4866:</p>
 * <ul>
 *   <li>Vibration: WARNING 10 mm/s, CRITICAL 50 mm/s</li>
 *   <li>Tilt: WARNING 3 mrad, CRITICAL 10 mrad</li>
 *   <li>Crack: WARNING 0.3 mm, CRITICAL 2.0 mm</li>
 * </ul>
 */
public class VibrationAnomalyJob {

    private static final Logger LOG = LoggerFactory.getLogger(VibrationAnomalyJob.class);

    private static final String KAFKA_BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String KAFKA_SECURITY_PROTOCOL =
            System.getenv().getOrDefault("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
    private static final String KAFKA_SASL_MECHANISM =
            System.getenv().getOrDefault("KAFKA_SASL_MECHANISM", "");
    private static final String KAFKA_SASL_JAAS_CONFIG =
            System.getenv().getOrDefault("KAFKA_SASL_JAAS_CONFIG", "");

    /** Output Kafka topic — consumed by StructuralAlertConsumer in the monolith. */
    static final String SINK_TOPIC = "UIP.structural.alert.critical.v1";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        String checkpointDir = System.getenv().getOrDefault("S3_CHECKPOINT_DIR",
                "s3://uip-flink-checkpoints/checkpoints");
        env.getCheckpointConfig().setCheckpointStorage(new FileSystemCheckpointStorage(checkpointDir));

        // --- Source: reuse same topic as FloodAlertJob ---
        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics("ngsi_ld_environment")
                .setGroupId("flink-structural-anomaly-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .setProperties(kafkaSecurityProps())
                .build();

        // --- Sink ---
        KafkaSink<String> structuralSink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(SINK_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setKafkaProducerConfig(kafkaSecurityProps())
                .build();

        // --- CEP Pattern: ≥3 consecutive Welford anomalies within 10 seconds ---
        // Input to CEP is already pre-filtered by WelfordKeyedProcessFunction,
        // so no where() condition needed — every event in the stream is an anomaly.
        Pattern<NgsiLdMessage, ?> structuralPattern = Pattern
                .<NgsiLdMessage>begin("spike1")
                .timesOrMore(3)
                .consecutive()
                .within(Time.seconds(10));

        // --- Pipeline ---
        // Step 1: ingest + filter structural sensor types
        var structuralStream = env.fromSource(
                source,
                WatermarkStrategy.<NgsiLdMessage>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, ts) -> event.getObservedAtMillis()),
                "ngsi_ld_environment Source (Structural Anomaly)"
        )
        .filter(msg -> msg != null
                && msg.getDeviceIdValue() != null
                && msg.getMeta() != null
                && StructuralThreshold.isStructuralSensor(msg.getMeta().getSensorType()));

        // Step 2: per-sensor Welford anomaly filter (maintains running mean/stddev per sensorId)
        // Emits only readings that exceed mean+4σ AND exceed the TCVN minimum threshold.
        // Cold-start guard: suppresses alerts until n ≥ 1000 samples per sensor (BR-010 safe).
        var anomalyStream = structuralStream
                .keyBy(msg -> msg.getDeviceIdValue())
                .process(new WelfordKeyedProcessFunction())
                .name("Welford Per-Sensor Anomaly Filter");

        // Step 3: CEP on anomaly stream — 3 consecutive spikes within 10s → StructuralAlertEvent
        PatternStream<NgsiLdMessage> patternStream = CEP.pattern(
                anomalyStream.keyBy(msg -> msg.getDeviceIdValue()), structuralPattern);

        patternStream
                .process(new StructuralPatternProcessFunction())
                .map(event -> MAPPER.writeValueAsString(event))
                .sinkTo(structuralSink)
                .name("Kafka " + SINK_TOPIC + " Sink");

        LOG.info("Starting Structural Vibration Anomaly Detection Job");
        env.execute("Structural Vibration Anomaly Detection Job");
    }

    /**
     * Check if a message's sensor reading is a structural anomaly.
     * Uses Welford 4-sigma rule combined with TCVN minimum threshold.
     *
     * <p>NOTE: In this simplified version, Welford state is not maintained per-sensor
     * across the CEP pattern. Instead, we use the absolute threshold check here,
     * and the CEP pattern (3 consecutive) provides the statistical confirmation.
     * Full per-sensor Welford state will be added with a KeyedProcessFunction in B2-3.</p>
     */
    static boolean isStructuralAnomaly(NgsiLdMessage msg) {
        if (msg == null || msg.getMeta() == null || msg.getMeasurementValues() == null) {
            return false;
        }
        String sensorType = msg.getMeta().getSensorType();
        if (!StructuralThreshold.isStructuralSensor(sensorType)) {
            return false;
        }
        Double value = extractMeasurementValue(msg);
        if (value == null) return false;

        // Check against WARNING threshold (TCVN minimum floor)
        double warningThreshold = StructuralThreshold.getWarningThreshold(sensorType);
        return value >= warningThreshold;
    }

    /** Extract the primary measurement value from the NgsiLd message. */
    static Double extractMeasurementValue(NgsiLdMessage msg) {
        if (msg.getMeasurementValues() == null || msg.getMeasurementValues().isEmpty()) return null;
        // NgsiLdMessage stores measurements as Map<String, Double>
        return msg.getMeasurementValues().get("value");
    }

    /**
     * Creates a StructuralAlertEvent from matched CEP patterns.
     * Follows FloodAlertJob's FloodPatternProcessFunction pattern.
     */
    static class StructuralPatternProcessFunction
            extends PatternProcessFunction<NgsiLdMessage, StructuralAlertEvent> {

        @Override
        public void processMatch(Map<String, List<NgsiLdMessage>> match,
                                 Context ctx,
                                 Collector<StructuralAlertEvent> out) {
            List<NgsiLdMessage> readings = match.get("spike1");
            if (readings == null || readings.isEmpty()) return;

            // Use the most recent reading for severity classification
            NgsiLdMessage last = readings.get(readings.size() - 1);
            String sensorType = last.getMeta().getSensorType();
            Double value = extractMeasurementValue(last);
            if (value == null) return;

            String severity = StructuralThreshold.classifySeverity(sensorType, value);
            if (severity == null) {
                severity = "WARNING"; // fallback — above threshold but below WARNING? Should not happen
            }
            double threshold = "CRITICAL".equals(severity)
                    ? StructuralThreshold.getCriticalThreshold(sensorType)
                    : StructuralThreshold.getWarningThreshold(sensorType);

            StructuralAlertEvent event = new StructuralAlertEvent(
                    last.getDeviceIdValue(),
                    sensorType,
                    last.getTenantId(),
                    value,
                    0.0, // meanValue — will be populated by Welford in B2-3
                    0.0, // stdDevValue — will be populated by Welford in B2-3
                    threshold,
                    severity,
                    last.getMeta().getDistrict(),
                    last.getObservedAtMillis(),
                    readings.size()
            );

            out.collect(event);
        }
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
