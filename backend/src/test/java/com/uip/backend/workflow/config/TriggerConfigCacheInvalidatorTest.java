package com.uip.backend.workflow.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerConfigCacheInvalidator")
class TriggerConfigCacheInvalidatorTest {

    @Mock private TriggerConfigCacheService cacheService;
    @InjectMocks private TriggerConfigCacheInvalidator invalidator;

    @Test
    @DisplayName("onConfigUpdated → evictAll được gọi với bất kỳ action nào")
    void onConfigUpdated_evictsAll() {
        invalidator.onConfigUpdated(Map.of("configId", 1L, "action", "UPDATE"));
        verify(cacheService).evictAll();
    }

    @Test
    @DisplayName("onConfigUpdated CREATE → evictAll")
    void onConfigUpdated_create_evictsAll() {
        invalidator.onConfigUpdated(Map.of("configId", 5L, "action", "CREATE"));
        verify(cacheService).evictAll();
    }

    @Test
    @DisplayName("onConfigUpdated DISABLE → evictAll")
    void onConfigUpdated_disable_evictsAll() {
        invalidator.onConfigUpdated(Map.of("configId", 9L, "action", "DISABLE"));
        verify(cacheService).evictAll();
    }
}
