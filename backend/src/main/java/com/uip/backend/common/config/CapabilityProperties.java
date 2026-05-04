package com.uip.backend.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "uip.capabilities")
public class CapabilityProperties {

    private boolean multiTenancy = false;
    private boolean redisCache = false;
    private boolean clickhouse = false;
    private boolean kongGateway = false;
    private boolean keycloak = false;
    private boolean edgeComputing = false;
    private boolean multiRegion = false;
    private boolean iotIngestionExternal = false;
    private boolean alertExternal = false;
    private boolean analyticsExternal = false;
}
