package com.uip.flink.flood;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uip.flink.common.NgsiLdDeserializer;
import com.uip.flink.common.NgsiLdMessage;
import com.uip.flink.common.tenant.TenantKeyedProcessFunctionDelegate;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.cep.CEP;
import org.apache.flink.cep.PatternStream;
import org.apache.flink.cep.functions.PatternProcessFunction;
import org.apache.flink.cep.pattern.Pattern;
import org.apache.flink.cep.pattern.conditions.SimpleCondition;
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
import java.util.List;
import java.util.Map;

/**
 * S6-FL01 — Flood Alert Detection Job (Flink CEP).
 *
 * Consumes {@code ngsi_ld_environment}, filters for flood-relevant sensor types
 * (RAINFALL, WATER_LEVEL, SOIL_MOISTURE), and detects 3 consecutive readings
 * above the P2 advisory threshold within 10 minutes using Flink CEP.
 *
 * Emits {@link FloodAlertEvent} to Kafka topic {@code UIP.flink.alert.flood.v1}
 * with severity per TCVN 9386:2012.
 */
public class FloodAlertJob {

    private static final Logger LOG = LoggerFactory.getLogger(FloodAlertJob.class);

    private static final String KAFKA_BOOTSTRAP =
            System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");
    private static final String KAFKA_SECURITY_PROTOCOL =
            System.getenv().getOrDefault("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT");
    private static final String KAFKA_SASL_MECHANISM =
            System.getenv().getOrDefault("KAFKA_SASL_MECHANISM", "");
    private static final String KAFKA_SASL_JAAS_CONFIG =
            System.getenv().getOrDefault("KAFKA_SASL_JAAS_CONFIG", "");

    /** Output Kafka topic — must match FloodAlertConsumer in the monolith. */
    static final String SINK_TOPIC = "UIP.flink.alert.flood.v1";

    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        String checkpointDir = System.getenv().getOrDefault(
                "S3_CHECKPOINT_DIR", "s3://uip-flink-checkpoints/checkpoints");
        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        env.getCheckpointConfig().setCheckpointStorage(checkpointDir);

        // --- Source ---
        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics("ngsi_ld_environment")
                .setGroupId("flink-flood-alert-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .setProperties(kafkaSecurityProps())
                .build();

        // --- Sink ---
        KafkaSink<String> floodSink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(SINK_TOPIC)
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .setKafkaProducerConfig(kafkaSecurityProps())
                .build();

        // --- CEP Pattern: ≥3 consecutive readings above P2 threshold within 10 min ---
        Pattern<NgsiLdMessage, ?> floodPattern = Pattern
                .<NgsiLdMessage>begin("reading1")
                .where(SimpleCondition.of(msg -> isAboveP2Threshold(msg)))
                .timesOrMore(3)
                .consecutive()
                .within(Time.minutes(10));

        // --- Pipeline ---
        var stream = env.fromSource(
                source,
                WatermarkStrategy.<NgsiLdMessage>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, ts) -> event.getObservedAtMillis()),
                "ngsi_ld_environment Source (Flood Alert)"
        )
        .filter(msg -> msg != null
                && msg.getDeviceIdValue() != null
                && msg.getMeta() != null
                && ThresholdCondition.isFloodSensor(msg.getMeta().getSensorType()));

        PatternStream<NgsiLdMessage> patternStream = CEP.pattern(
                stream.keyBy(msg -> msg.getDeviceIdValue()), floodPattern);

        patternStream
                .process(new FloodPatternProcessFunction())
                .map(event -> MAPPER.writeValueAsString(event))
                .sinkTo(floodSink)
                .name("Kafka " + SINK_TOPIC + " Sink");

        LOG.info("Starting Flood Alert Detection Job");
        env.execute("Flood Alert Detection Job");
    }

    /**
     * Check if a message's sensor reading is above the P2 advisory threshold.
     */
    static boolean isAboveP2Threshold(NgsiLdMessage msg) {
        if (msg == null || msg.getMeta() == null || msg.getMeasurementValues() == null) {
            return false;
        }
        String sensorType = msg.getMeta().getSensorType();
        ThresholdCondition.FloodReading reading =
                ThresholdCondition.extractFloodReading(msg.getMeasurementValues(), sensorType);
        if (reading == null) return false;
        double p2 = ThresholdCondition.getP2Threshold(sensorType);
        return p2 > 0 && reading.value >= p2;
    }

    /**
     * Creates a FloodAlertEvent from matched CEP patterns.
     */
    static class FloodPatternProcessFunction
            extends PatternProcessFunction<NgsiLdMessage, FloodAlertEvent> {

        // ADR-047 §1.3 — alert built under a bound TenantContext (fail-closed drop if no tenant).
        // Initialised in open() (transient fields are not restored on the task manager).
        private transient TenantKeyedProcessFunctionDelegate<NgsiLdMessage, FloodAlertEvent> tenantGuard;

        @Override
        public void open(org.apache.flink.configuration.Configuration parameters) {
            tenantGuard = TenantKeyedProcessFunctionDelegate.forFn(NgsiLdMessage::getTenantId);
        }

        @Override
        public void processMatch(Map<String, List<NgsiLdMessage>> match,
                                 Context ctx,
                                 Collector<FloodAlertEvent> out) {
            List<NgsiLdMessage> readings = match.get("reading1");
            if (readings == null || readings.isEmpty()) return;

            // Use the most recent reading for severity classification + as the tenant source.
            NgsiLdMessage last = readings.get(readings.size() - 1);
            final int spikeCount = readings.size();

            tenantGuard.run(last, (rec, emit) -> buildAlert(rec, spikeCount, emit), out::collect);
        }

        private static void buildAlert(NgsiLdMessage last, int spikeCount,
                                       java.util.function.Consumer<FloodAlertEvent> emit) {
            String sensorType = last.getMeta().getSensorType();
            ThresholdCondition.FloodReading floodReading =
                    ThresholdCondition.extractFloodReading(last.getMeasurementValues(), sensorType);
            if (floodReading == null) return;

            String severity = ThresholdCondition.classifySeverity(sensorType, floodReading.value);
            if (severity == null) {
                severity = "P2_ADVISORY"; // fallback — should not happen
            }
            double threshold = ThresholdCondition.getThreshold(sensorType, severity);

            emit.accept(new FloodAlertEvent(
                    last.getDeviceIdValue(),
                    sensorType,
                    last.getTenantId(),
                    floodReading.value,
                    threshold,
                    severity,
                    last.getMeta().getDistrict(),
                    last.getObservedAtMillis(),
                    spikeCount
            ));
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
