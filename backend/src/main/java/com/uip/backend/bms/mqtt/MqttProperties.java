package com.uip.backend.bms.mqtt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "bms.mqtt")
public class MqttProperties {
    private String brokerUrl = "tcp://localhost:1883";
    private String clientId = "uip-bms-backend";
    private String username;
    private String password;
    private String commandTopicPattern = "bms/commands/%s/%s";
    private int qos = 1;
}
