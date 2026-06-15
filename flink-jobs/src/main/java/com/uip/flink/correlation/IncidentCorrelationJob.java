package com.uip.flink.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * M4-COR-01 — Incident Correlation Flink CEP job.
 *
 * <p>Consumes alert events from {@code UIP.flink.alert.detected.v1}, groups them
 * by {@code buildingId}, and detects windows where ≥ {@code minSensorTypes}
 * distinct measure types fire within {@code windowSeconds}. Emits a
 * {@link CorrelatedIncidentEvent} to {@code correlated.incidents}, consumed by
 * the backend {@code CorrelationDlqHandler} → {@code CorrelationService.processIncomingEvent}.
 *
 * <p>This is the real Flink CEP producer the docs previously claimed — before this
 * job, {@code CorrelationService.correlate()} had no caller and
 * {@code correlated.incidents} had no producer.</p>
 *
 * <p>Follows the job convention of {@code VibrationAnomalyJob}: 30 s EXACTLY_ONCE
 * checkpointing, RocksDB state backend, bounded-out-of-orderness watermarks,
 * Kafka string sink.</p>
 *
 * <p>Config via environment variables:</p>
 * <ul>
 *   <li>{@code COR_WINDOW_SECONDS} — CEP window size (default 30)</li>
 *   <li>{@code COR_MIN_SENSOR_TYPES} — min distinct measure types (default 3)</li>
 *   <li>{@code COR_MIN_SCORE} — min correlation score to emit (default 0.6)</li>
 *   <li>{@code COR_SOURCE_TOPIC} — source (default {@code UIP.flink.alert.detected.v1})</li>
 *   <li>{@code COR_OUTPUT_TOPIC} — sink (default {@code correlated.incidents})</li>
 * </ul>
 */
public class IncidentCorrelationJob {

    private static final Logger LOG = LoggerFactory.getLogger(IncidentCorrelationJob.class);

    private static final String KAFKA_BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String KAFKA_SECURITY_PROTOCOL =
            System.getenv().getOrDefault("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
    private static final String KAFKA_SASL_MECHANISM =
            System.getenv().getOrDefault("KAFKA_SASL_MECHANISM", "");
    private static final String KAFKA_SASL_JAAS_CONFIG =
            System.getenv().getOrDefault("KAFKA_SASL_JAAS_CONFIG", "");

    /** Source topic — produced by backend AlertEventKafkaConsumer's upstream Flink AlertDetectionJob. */
    static final String SOURCE_TOPIC =
            System.getenv().getOrDefault("COR_SOURCE_TOPIC", "UIP.flink.alert.detected.v1");

    /** Sink topic — matches {@code IncidentCorrelationConfig.outputTopic} in backend. */
    static final String SINK_TOPIC =
            System.getenv().getOrDefault("COR_OUTPUT_TOPIC", "correlated.incidents");

    static final int WINDOW_SECONDS =
            Integer.parseInt(System.getenv().getOrDefault("COR_WINDOW_SECONDS", "30"));
    static final int MIN_SENSOR_TYPES =
            Integer.parseInt(System.getenv().getOrDefault("COR_MIN_SENSOR_TYPES", "3"));
    static final double MIN_SCORE =
            Double.parseDouble(System.getenv().getOrDefault("COR_MIN_SCORE", "0.6"));

    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        env.getCheckpointConfig().setCheckpointStorage("file:///flink/checkpoints");

        // --- Source ---
        KafkaSource<String> source = KafkaSource.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics(SOURCE_TOPIC)
                .setGroupId("flink-incident-correlation-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
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

        // --- Deserialise JSON -> AlertEventEnvelope, drop messages without buildingId ---
        var alertStream = env.fromSource(
                source,
                WatermarkStrategy.<String>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((raw, ts) -> System.currentTimeMillis()),
                "Alert Events Source (Incident Correlation)"
        )
        .map(IncidentCorrelationJob::parseAlert)
        .filter(e -> e != null && e.getBuildingId() != null && e.getMeasureType() != null);

        // --- CEP pattern: ≥ MIN_SENSOR_TYPES alerts within WINDOW_SECONDS ---
        // The distinct-type constraint is enforced in the PatternProcessFunction
        // (CEP does not natively express "≥ N distinct field values"), so the
        // pattern matches MIN_SENSOR_TYPES alerts of any measure type in the
        // window; the process function then verifies distinctness + score.
        Pattern<AlertEventEnvelope, ?> pattern = Pattern
                .<AlertEventEnvelope>begin("alerts")
                .timesOrMore(MIN_SENSOR_TYPES)
                .within(Time.seconds(WINDOW_SECONDS));

        PatternStream<AlertEventEnvelope> patternStream = CEP.pattern(
                alertStream.keyBy(AlertEventEnvelope::getBuildingId), pattern);

        patternStream
                .process(new CorrelationPatternProcessFunction(MIN_SENSOR_TYPES, MIN_SCORE))
                .map(MAPPER::writeValueAsString)
                .sinkTo(sink)
                .name("Kafka " + SINK_TOPIC + " Sink");

        LOG.info("Starting Incident Correlation Job (window={}s, minTypes={}, minScore={}, sink={})",
                WINDOW_SECONDS, MIN_SENSOR_TYPES, MIN_SCORE, SINK_TOPIC);
        env.execute("Incident Correlation Job");
    }

    // ─── Static helpers (unit-testable without a cluster) ────────────────────

    /** Parse a raw JSON alert into an envelope; returns null on malformed input. */
    static AlertEventEnvelope parseAlert(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, AlertEventEnvelope.class);
        } catch (Exception e) {
            LOG.debug("Skipping malformed alert message: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compute the correlation score using the same formula as the backend
     * {@code CorrelationScoringService} so Flink and in-app paths agree.
     */
    static double score(int distinctTypes, int minRequired, long timeRangeSeconds, int windowSeconds) {
        double typeCoverage = (double) distinctTypes / minRequired;
        double timeSpread = 1.0 - ((double) timeRangeSeconds / windowSeconds);
        return Math.min(1.0, typeCoverage * Math.max(0.1, timeSpread));
    }

    /** Build the sorted JSON-array string of distinct measure types. */
    static String buildSensorTypesJson(List<String> distinctTypes) {
        String joined = distinctTypes.stream().map(t -> "\"" + t + "\"").collect(Collectors.joining(","));
        return "[" + joined + "]";
    }

    /**
     * Pure decision function: given the alerts matched in a CEP window, return
     * the {@link CorrelatedIncidentEvent} to emit, or {@code empty} if distinct
     * types are insufficient or the score is below threshold. Extracted so it
     * can be unit-tested without a Flink cluster or a Context stub.
     */
    static java.util.Optional<CorrelatedIncidentEvent> evaluateWindow(
            List<AlertEventEnvelope> alerts, int minSensorTypes, double minScore, int windowSeconds) {
        if (alerts == null || alerts.isEmpty()) {
            return java.util.Optional.empty();
        }

        // Distinct measure types in deterministic order
        Set<String> typeSet = new LinkedHashSet<>();
        for (AlertEventEnvelope a : alerts) {
            if (a.getMeasureType() != null) {
                typeSet.add(a.getMeasureType());
            }
        }
        if (typeSet.size() < minSensorTypes) {
            return java.util.Optional.empty();
        }
        List<String> distinctTypes = new ArrayList<>(typeSet);
        distinctTypes.sort(Comparator.naturalOrder());

        // Time spread between earliest and latest detectedAt
        List<Instant> times = alerts.stream()
                .map(IncidentCorrelationJob::parseInstant)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
        long timeRangeSeconds = times.size() >= 2
                ? times.get(times.size() - 1).getEpochSecond() - times.get(0).getEpochSecond()
                : 0L;

        double s = score(distinctTypes.size(), minSensorTypes, timeRangeSeconds, windowSeconds);
        if (s < minScore) {
            return java.util.Optional.empty();
        }

        AlertEventEnvelope first = alerts.get(0);
        return java.util.Optional.of(new CorrelatedIncidentEvent(
                first.getBuildingId(),
                buildSensorTypesJson(distinctTypes),
                s,
                alerts.size(),
                Instant.now().toString(),
                "OPEN",
                distinctTypes,
                first.getTenantId()
        ));
    }

    /**
     * Pattern process function: delegates to {@link #evaluateWindow} so the
     * decision logic is testable in isolation.
     */
    static class CorrelationPatternProcessFunction
            extends PatternProcessFunction<AlertEventEnvelope, CorrelatedIncidentEvent> {

        private final int minSensorTypes;
        private final double minScore;

        CorrelationPatternProcessFunction(int minSensorTypes, double minScore) {
            this.minSensorTypes = minSensorTypes;
            this.minScore = minScore;
        }

        @Override
        public void processMatch(Map<String, List<AlertEventEnvelope>> match,
                                 Context ctx,
                                 Collector<CorrelatedIncidentEvent> out) {
            List<AlertEventEnvelope> alerts = match.get("alerts");
            evaluateWindow(alerts, minSensorTypes, minScore, WINDOW_SECONDS)
                    .ifPresent(out::collect);
        }
    }

    private static Instant parseInstant(AlertEventEnvelope a) {
        if (a == null || a.getDetectedAt() == null) {
            return null;
        }
        try {
            return Instant.parse(a.getDetectedAt());
        } catch (Exception e) {
            return null;
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
