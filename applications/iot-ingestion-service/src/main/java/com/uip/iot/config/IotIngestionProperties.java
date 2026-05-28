package com.uip.iot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "iot.ingestion")
public class IotIngestionProperties {

    private Mode mode = Mode.DISABLED;

    public enum Mode {
        SHADOW,
        PRIMARY,
        DISABLED
    }
}
