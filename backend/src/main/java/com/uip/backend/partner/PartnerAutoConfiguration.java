package com.uip.backend.partner;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PartnerConfig.class)
@ConditionalOnProperty(name = "uip.partner.enabled", havingValue = "true")
public class PartnerAutoConfiguration {
}
