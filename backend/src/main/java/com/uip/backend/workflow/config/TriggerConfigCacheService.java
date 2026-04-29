package com.uip.backend.workflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.kafka.KafkaException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerConfigCacheService {

    static final String CACHE_NAME = "trigger-configs";

    private final TriggerConfigRepository configRepo;

    @Cacheable(value = CACHE_NAME, key = "#topic")
    public List<TriggerConfig> findActiveKafkaConfigs(String topic) {
        log.debug("[CACHE] MISS trigger-configs for topic={}", topic);
        return configRepo.findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", topic, true);
    }

    @Retryable(retryFor = KafkaException.class, maxAttempts = 3, backoff = @Backoff(delay = 200))
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAll() {
        log.info("[CACHE] Evicted all trigger-config cache entries");
    }
}
