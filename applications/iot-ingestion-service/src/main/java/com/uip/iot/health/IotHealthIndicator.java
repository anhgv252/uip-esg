package com.uip.iot.health;

import com.uip.iot.config.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IotHealthIndicator implements HealthIndicator {

    private final IngestionService ingestionService;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("mode", ingestionService.getMode())
                .build();
    }
}
