package com.uip.backend.bms.mqtt;

import com.uip.backend.bms.api.dto.BmsCommand;
import com.uip.backend.bms.api.dto.BmsReadingEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@RequiredArgsConstructor
public class MqttPublisher {

    private final MqttProperties properties;
    private final ObjectMapper objectMapper;
    private MqttClient client;
    private final ReentrantLock connectionLock = new ReentrantLock();

    public void connect() throws MqttException {
        connectionLock.lock();
        try {
            if (client != null && client.isConnected()) return;

            client = new MqttClient(properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            MqttConnectionOptions opts = new MqttConnectionOptions();
            opts.setAutomaticReconnect(true);
            opts.setCleanStart(true);
            if (properties.getUsername() != null) {
                opts.setUserName(properties.getUsername());
                opts.setPassword(properties.getPassword().getBytes());
            }
            client.connect(opts);
            log.info("MQTT connected to {}", properties.getBrokerUrl());
        } finally {
            connectionLock.unlock();
        }
    }

    public void publishCommand(String tenantId, String deviceId, BmsCommand command) {
        connectionLock.lock();
        try {
            if (client == null || !client.isConnected()) {
                // ReentrantLock is reentrant — connect() will re-acquire the same lock
                connect();
            }

            String topic = String.format(properties.getCommandTopicPattern(), tenantId, deviceId);
            byte[] payload = objectMapper.writeValueAsBytes(command);
            MqttMessage msg = new MqttMessage(payload);
            msg.setQos(properties.getQos());
            client.publish(topic, msg);
            log.info("MQTT command published: topic={} qos={}", topic, properties.getQos());
        } catch (Exception e) {
            throw new RuntimeException("MQTT publish failed: " + e.getMessage(), e);
        } finally {
            connectionLock.unlock();
        }
    }

    public void publishReading(String tenantId, String deviceId, BmsReadingEvent event) {
        connectionLock.lock();
        try {
            if (client == null || !client.isConnected()) {
                connect();
            }

            String topic = String.format("bms/readings/%s/%s", tenantId, deviceId);
            byte[] payload = objectMapper.writeValueAsBytes(event);
            MqttMessage msg = new MqttMessage(payload);
            msg.setQos(0);
            client.publish(topic, msg);
        } catch (Exception e) {
            log.warn("MQTT reading publish failed: {}", e.getMessage());
        } finally {
            connectionLock.unlock();
        }
    }

    @PreDestroy
    public void disconnect() {
        connectionLock.lock();
        try {
            if (client != null && client.isConnected()) {
                try {
                    client.disconnect();
                } catch (MqttException e) {
                    log.warn("MQTT disconnect error: {}", e.getMessage());
                }
            }
        } finally {
            connectionLock.unlock();
        }
    }
}
