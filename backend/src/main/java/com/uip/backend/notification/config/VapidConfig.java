package com.uip.backend.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "uip.push.vapid")
public class VapidConfig {

    private String publicKey;
    private String privateKey;
    private String subject;
}
