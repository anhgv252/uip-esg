package com.uip.backend.esg.kafka;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TelemetryErrorConsumerTest {

    private TelemetryErrorConsumer consumer;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        consumer = new TelemetryErrorConsumer(meterRegistry);
        consumer.init();
    }

    @Test
    void topic_constant() {
        assertThat(TelemetryErrorConsumer.TOPIC).isEqualTo("UIP.esg.telemetry.error.v1");
    }

    @Test
    void onTelemetryError_acknowledgesAndIncrementsCounter() {
        TelemetryErrorDto error = new TelemetryErrorDto(
                "VALIDATION_ERROR", "sensor-01", "Value out of range",
                Instant.now()
        );
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onTelemetryError(error, ack);

        verify(ack).acknowledge();
        Counter counter = meterRegistry.find("uip.esg.telemetry.errors").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void onTelemetryError_multipleErrors_incrementsEachTime() {
        Acknowledgment ack = mock(Acknowledgment.class);
        TelemetryErrorDto error = new TelemetryErrorDto(
                "MISSING_FIELD", "sensor-02", "Missing required field",
                Instant.now()
        );

        consumer.onTelemetryError(error, ack);
        consumer.onTelemetryError(error, ack);
        consumer.onTelemetryError(error, ack);

        Counter counter = meterRegistry.find("uip.esg.telemetry.errors").counter();
        assertThat(counter.count()).isEqualTo(3.0);
        verify(ack, times(3)).acknowledge();
    }

    @Test
    void init_registersCounter() {
        Counter counter = meterRegistry.find("uip.esg.telemetry.errors").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.getId().getDescription())
                .isEqualTo("Count of telemetry messages with validation errors");
    }
}
