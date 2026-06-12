package com.uip.backend.bms.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uip.backend.bms.api.dto.BmsReadingEvent;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MqttPublisher} — publishSensorReading topic format and client publish invocation.
 *
 * Thread-safety (ReentrantLock) tests are in {@link MqttPublisherConcurrentTest}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MqttPublisher — publishSensorReading")
class MqttPublisherTest {

    @Mock
    private ObjectMapper objectMapper;

    private MqttProperties properties;
    private MqttPublisher mqttPublisher;

    @BeforeEach
    void setUp() {
        properties = new MqttProperties();
        properties.setBrokerUrl("tcp://localhost:1883");
        properties.setClientId("test-client-reading");
        properties.setCommandTopicPattern("bms/commands/%s/%s");
        properties.setQos(1);
        mqttPublisher = new MqttPublisher(properties, objectMapper);
    }

    @Test
    @DisplayName("publishSensorReading: topic formatted as bms/readings/{tenantId}/{deviceId}")
    void publishSensorReading_correctTopicFormat() throws Exception {
        BmsReadingEvent event = new BmsReadingEvent(
                UUID.randomUUID(), "hcm", "TEMPERATURE", 22.5, "C", Instant.now(), "BACnet");

        byte[] payload = "{\"readingType\":\"TEMPERATURE\"}".getBytes();
        when(objectMapper.writeValueAsBytes(event)).thenReturn(payload);

        try (MockedConstruction<MqttClient> mocked = mockConstruction(MqttClient.class,
                (mock, ctx) -> {
                    when(mock.isConnected()).thenReturn(true);
                    doNothing().when(mock).connect(any(MqttConnectionOptions.class));
                    doNothing().when(mock).publish(anyString(), any(MqttMessage.class));
                })) {

            mqttPublisher.connect();
            mqttPublisher.publishReading("hcm", "device-42", event);

            MqttClient mockClient = mocked.constructed().get(0);
            ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockClient).publish(topicCaptor.capture(), any(MqttMessage.class));

            assertThat(topicCaptor.getValue()).isEqualTo("bms/readings/hcm/device-42");
        }
    }

    @Test
    @DisplayName("publishSensorReading: mqttClient.publish() is called with correct payload bytes")
    void publishSensorReading_callsClientPublishWithPayload() throws Exception {
        BmsReadingEvent event = new BmsReadingEvent(
                UUID.randomUUID(), "hcm", "CO2", 450.0, "ppm", Instant.now(), "Modbus");

        byte[] expectedPayload = "{\"readingType\":\"CO2\",\"value\":450.0}".getBytes();
        when(objectMapper.writeValueAsBytes(event)).thenReturn(expectedPayload);

        try (MockedConstruction<MqttClient> mocked = mockConstruction(MqttClient.class,
                (mock, ctx) -> {
                    when(mock.isConnected()).thenReturn(true);
                    doNothing().when(mock).connect(any(MqttConnectionOptions.class));
                    doNothing().when(mock).publish(anyString(), any(MqttMessage.class));
                })) {

            mqttPublisher.connect();
            mqttPublisher.publishReading("hcm", "sensor-01", event);

            MqttClient mockClient = mocked.constructed().get(0);
            ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mockClient).publish(anyString(), msgCaptor.capture());

            assertThat(msgCaptor.getValue().getPayload()).isEqualTo(expectedPayload);
        }
    }

    @Test
    @DisplayName("publishSensorReading: serialization failure is swallowed — no exception propagated")
    void publishSensorReading_serializationFailure_swallowed() throws Exception {
        BmsReadingEvent event = new BmsReadingEvent(
                UUID.randomUUID(), "hcm", "AQI", 120.0, "AQI", Instant.now(), "sensor");
        when(objectMapper.writeValueAsBytes(event))
                .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("fail") {});

        try (MockedConstruction<MqttClient> mocked = mockConstruction(MqttClient.class,
                (mock, ctx) -> {
                    when(mock.isConnected()).thenReturn(true);
                    doNothing().when(mock).connect(any(MqttConnectionOptions.class));
                })) {

            mqttPublisher.connect();

            // publishReading swallows errors (warn log only) — must not propagate
            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> mqttPublisher.publishReading("hcm", "sensor-01", event));

            MqttClient mockClient = mocked.constructed().get(0);
            verify(mockClient, never()).publish(anyString(), any(MqttMessage.class));
        }
    }

    @Test
    @DisplayName("publishSensorReading: QoS 0 is used (fire-and-forget for readings)")
    void publishSensorReading_usesQos0() throws Exception {
        BmsReadingEvent event = new BmsReadingEvent(
                UUID.randomUUID(), "sg", "POWER", 5000.0, "W", Instant.now(), "BACnet");
        when(objectMapper.writeValueAsBytes(event)).thenReturn("{}".getBytes());

        try (MockedConstruction<MqttClient> mocked = mockConstruction(MqttClient.class,
                (mock, ctx) -> {
                    when(mock.isConnected()).thenReturn(true);
                    doNothing().when(mock).connect(any(MqttConnectionOptions.class));
                    doNothing().when(mock).publish(anyString(), any(MqttMessage.class));
                })) {

            mqttPublisher.connect();
            mqttPublisher.publishReading("sg", "bld-sensor-99", event);

            ArgumentCaptor<MqttMessage> msgCaptor = ArgumentCaptor.forClass(MqttMessage.class);
            verify(mocked.constructed().get(0)).publish(anyString(), msgCaptor.capture());
            assertThat(msgCaptor.getValue().getQos()).isEqualTo(0);
        }
    }
}
