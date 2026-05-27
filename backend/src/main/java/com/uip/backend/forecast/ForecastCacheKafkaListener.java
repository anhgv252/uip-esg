package com.uip.backend.forecast;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Kafka listener for forecast cache eviction events (S4-17).
 *
 * Listens to ESG metric events and evicts forecast cache when
 * new energy data is ingested, ensuring forecast results stay fresh.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastCacheKafkaListener {

    private static final Set<String> FORECAST_TYPES = Set.of("ENERGY", "WATER");

    private final ForecastCacheStatsService cacheStatsService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${uip.kafka.topic-esg-metrics:uip.esg.metrics.v1}",
            groupId = "forecast-cache-eviction",
            autoStartup = "${uip.kafka.cache-eviction-enabled:true}"
    )
    public void onEsgMetricEvent(String message) {
        if (message == null) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(message);
            String type = node.has("type") ? node.get("type").asText("") : "";
            if (FORECAST_TYPES.contains(type)) {
                log.debug("ESG metric event received (type={}), evicting forecast cache", type);
                cacheStatsService.evictAll();
            }
        } catch (Exception e) {
            log.warn("Failed to parse ESG metric event for cache eviction: {}", e.getMessage());
        }
    }
}
