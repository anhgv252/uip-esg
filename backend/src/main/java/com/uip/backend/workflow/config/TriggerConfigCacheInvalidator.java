package com.uip.backend.workflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TriggerConfigCacheInvalidator {

    public static final String TOPIC = "UIP.admin.trigger-config.updated.v1";

    private final TriggerConfigCacheService cacheService;

    @KafkaListener(
        topics           = TOPIC,
        groupId          = "uip-trigger-config-cache-invalidator",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onConfigUpdated(Map<String, Object> event) {
        Object configId = event.get("configId");
        Object action   = event.get("action");
        log.info("[CACHE] Received config-updated event: configId={} action={} — evicting all", configId, action);
        cacheService.evictAll();
    }
}
