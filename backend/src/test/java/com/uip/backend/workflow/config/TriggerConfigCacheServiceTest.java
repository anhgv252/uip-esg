package com.uip.backend.workflow.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GAP-04: Unit tests for TriggerConfigCacheService.
 * NOTE: @Cacheable/@CacheEvict are Spring AOP proxies invisible to Mockito — these tests verify
 * the underlying repository delegation only. Spring Cache proxy behaviour is covered by
 * TriggerConfigCacheServiceSpringIT (Testcontainers, CI only).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerConfigCacheService — unit")
class TriggerConfigCacheServiceTest {

    @Mock private TriggerConfigRepository configRepo;
    @InjectMocks private TriggerConfigCacheService cacheService;

    @Test
    @DisplayName("findActiveKafkaConfigs — delegates to repository with correct params")
    void findActiveKafkaConfigs_delegatesToRepository() {
        TriggerConfig config = TriggerConfig.builder()
            .id(1L).scenarioKey("aiC01").triggerType("KAFKA").enabled(true)
            .kafkaTopic("UIP.environment.aqi.v1").variableMapping("{}").build();
        when(configRepo.findByTriggerTypeAndKafkaTopicAndEnabled(
            "KAFKA", "UIP.environment.aqi.v1", true))
            .thenReturn(List.of(config));

        List<TriggerConfig> result = cacheService.findActiveKafkaConfigs("UIP.environment.aqi.v1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScenarioKey()).isEqualTo("aiC01");
        verify(configRepo).findByTriggerTypeAndKafkaTopicAndEnabled(
            "KAFKA", "UIP.environment.aqi.v1", true);
    }

    @Test
    @DisplayName("findActiveKafkaConfigs — returns empty list when no active configs")
    void findActiveKafkaConfigs_noActiveConfigs_returnsEmptyList() {
        when(configRepo.findByTriggerTypeAndKafkaTopicAndEnabled(any(), any(), anyBoolean()))
            .thenReturn(List.of());

        List<TriggerConfig> result = cacheService.findActiveKafkaConfigs("UNKNOWN.topic");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("evictAll — does not throw, invokes no repo operations")
    void evictAll_doesNotInteractWithRepo() {
        cacheService.evictAll();
        verifyNoInteractions(configRepo);
    }
}
