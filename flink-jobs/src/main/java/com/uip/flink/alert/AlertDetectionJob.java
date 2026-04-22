package com.uip.flink.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.uip.flink.environment.EnvironmentReading;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.connector.jdbc.JdbcConnectionOptions;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.JdbcSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.contrib.streaming.state.EmbeddedRocksDBStateBackend;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import com.uip.flink.common.NgsiLdDeserializer;
import com.uip.flink.common.NgsiLdMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * S1-08 — AlertDetectionJob
 * Reads environment.sensor_readings via TimescaleDB polling
 * OR processes the ngsi_ld_environment stream with a 5-min sliding window.
 * Emits AlertEvent to Kafka topic UIP.flink.alert.detected.v1.
 */
public class AlertDetectionJob {

    private static final Logger LOG = LoggerFactory.getLogger(AlertDetectionJob.class);

    private static final String KAFKA_BOOTSTRAP = System.getenv().getOrDefault("KAFKA_BOOTSTRAP", "kafka:9092");

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        env.enableCheckpointing(30_000, CheckpointingMode.EXACTLY_ONCE);
        env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
        env.getCheckpointConfig().setCheckpointStorage("file:///flink/checkpoints");

        // Source: same ngsi_ld_environment stream as EnvironmentFlinkJob
        KafkaSource<NgsiLdMessage> source = KafkaSource.<NgsiLdMessage>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setTopics("ngsi_ld_environment")
                .setGroupId("flink-alert-detection-job")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new NgsiLdDeserializer())
                .build();

        // Sink: publish AlertEvent JSON to Kafka — must match AlertEventKafkaConsumer.TOPIC
        KafkaSink<String> alertSink = KafkaSink.<String>builder()
                .setBootstrapServers(KAFKA_BOOTSTRAP)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("UIP.flink.alert.detected.v1")
                        .setValueSerializationSchema(new SimpleStringSchema())
                        .build())
                .build();

        env.fromSource(
                source,
                WatermarkStrategy.<NgsiLdMessage>forBoundedOutOfOrderness(Duration.ofSeconds(10))
                        .withTimestampAssigner((event, ts) -> event.getObservedAtMillis()),
                "ngsi_ld_environment Source (Alert)"
        )
        .filter(msg -> msg != null && msg.getDeviceIdValue() != null)
        .map(msg -> EnvironmentReading.from(msg, ""))
        .keyBy(EnvironmentReading::getSensorId)
        // 5-minute sliding window, slide every 1 minute
        .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
        .process(new AlertDetectionFunction())
        .map(alert -> MAPPER.writeValueAsString(alert))
        .sinkTo(alertSink)
        .name("Kafka UIP.flink.alert.detected.v1 Sink");

        LOG.info("Starting AlertDetectionJob");
        env.execute("AlertDetectionJob");
    }
}
