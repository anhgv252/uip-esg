package com.uip.backend.esg.kafka;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * ADR-014 — Consumes telemetry validation errors from UIP.esg.telemetry.error.v1.
 * Logs structured warnings and increments metrics for monitoring.
 *
 * Topic registry: docs/deployment/kafka-topic-registry.xlsx
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TelemetryErrorConsumer {

    static final String TOPIC = "UIP.esg.telemetry.error.v1";
    private static final String GROUP_ID = "uip-backend-telemetry-errors";

    private final MeterRegistry meterRegistry;

    private Counter errorCounter;

    @jakarta.annotation.PostConstruct
    void init() {
        errorCounter = Counter.builder("uip.esg.telemetry.errors")
                .description("Count of telemetry messages with validation errors")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP_ID,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTelemetryError(TelemetryErrorDto error, Acknowledgment ack) {
        log.warn("Telemetry validation error: code={} sensor={} msg={} detectedAt={}",
                error.errorCode(), error.sensorId(), error.message(), error.detectedAt());

        errorCounter.increment();

        ack.acknowledge();
    }
}
