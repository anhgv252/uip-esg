package com.uip.backend.performance;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.alert.repository.AlertRuleRepository;
import com.uip.backend.alert.service.AlertEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * GAP-026: Sensor-to-alert latency test.
 *
 * <p>Verifies the end-to-end latency from sensor reading injection
 * to alert event creation. Uses the inline AlertEngine path (no Flink).</p>
 *
 * <p><b>Thresholds:</b>
 * <ul>
 *   <li>Single reading → alert created in &lt; 100ms (unit-level benchmark)</li>
 *   <li>Concurrent 100 readings → all alerts created in &lt; 5s</li>
 *   <li>Deduplication: only 1 alert per rule per sensor within cooldown</li>
 * </ul>
 *
 * <p>This tests the synchronous path through AlertEngine.evaluate().
 * The full async path (Kafka → Flink → Alert) is covered by integration tests
 * with target &lt; 30s end-to-end.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("performance")
@DisplayName("GAP-026: Sensor-to-Alert Latency Test")
class SensorToAlertLatencyTest {

    @Mock private AlertRuleRepository  alertRuleRepository;
    @Mock private AlertEventRepository alertEventRepository;
    @Mock private StringRedisTemplate  redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private ObjectMapper         objectMapper;

    @InjectMocks private AlertEngine alertEngine;

    @Captor private ArgumentCaptor<AlertEvent> eventCaptor;

    private AlertRule aqiThresholdRule;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        aqiThresholdRule = new AlertRule();
        aqiThresholdRule.setModule("environment");
        aqiThresholdRule.setMeasureType("AQI");
        aqiThresholdRule.setOperator(">");
        aqiThresholdRule.setThreshold(200.0);
        aqiThresholdRule.setSeverity("CRITICAL");
        aqiThresholdRule.setCooldownMinutes(10);
        aqiThresholdRule.setActive(true);

        when(alertRuleRepository.findByModuleAndActiveTrue("environment"))
            .thenReturn(List.of(aqiThresholdRule));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
            .thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> {
            AlertEvent e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Single reading latency
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Single sensor reading → alert created within 100ms (synchronous path)")
    void singleReading_alertCreatedWithin100ms() {
        Instant before = Instant.now();

        alertEngine.evaluate("environment", "SENSOR-AIR-EMERGENCY", "AQI", 350.0);

        verify(alertEventRepository, timeout(100)).save(eventCaptor.capture());

        AlertEvent alert = eventCaptor.getValue();
        Instant after = Instant.now();

        Duration latency = Duration.between(before, after);
        assertThat(latency.toMillis())
            .as("Synchronous alert creation should complete within 100ms, was %dms", latency.toMillis())
            .isLessThan(100);

        assertThat(alert.getSensorId()).isEqualTo("SENSOR-AIR-EMERGENCY");
        assertThat(alert.getValue()).isEqualTo(350.0);
        assertThat(alert.getThreshold()).isEqualTo(200.0);
        assertThat(alert.getModule()).isEqualTo("environment");
        assertThat(alert.getMeasureType()).isEqualTo("AQI");
        assertThat(alert.getDetectedAt()).isNotNull();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Concurrent readings — 100 sensors trigger alerts simultaneously
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("100 concurrent sensor readings → all alerts created within 5s")
    void concurrentReadings_allAlertsCreatedWithin5s() throws Exception {
        int sensorCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(sensorCount);
        AtomicInteger successCount = new AtomicInteger(0);

        Instant start = Instant.now();

        for (int i = 0; i < sensorCount; i++) {
            final String sensorId = "SENSOR-AIR-" + String.format("%03d", i);
            final double value = 250.0 + i;
            executor.submit(() -> {
                try {
                    alertEngine.evaluate("environment", sensorId, "AQI", value);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        Instant end = Instant.now();

        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();

        assertThat(completed)
            .as("All %d readings should be processed within 5s", sensorCount)
            .isTrue();
        assertThat(successCount.get())
            .as("All %d readings should succeed", sensorCount)
            .isEqualTo(sensorCount);

        Duration totalLatency = Duration.between(start, end);
        assertThat(totalLatency.toSeconds())
            .as("Total processing time should be under 5s, was %ds", totalLatency.toSeconds())
            .isLessThan(5);

        verify(alertEventRepository, times(sensorCount)).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Deduplication within cooldown — latency not affected by dupes
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Duplicate reading within cooldown → dedup key set, only 1 alert persisted")
    void duplicateReadingWithinCooldown_onlyOneAlertPersisted() {
        // First reading: alert created
        alertEngine.evaluate("environment", "SENSOR-AIR-EMERGENCY", "AQI", 350.0);
        verify(alertEventRepository, times(1)).save(any());

        // Second reading within cooldown: Redis returns false → dedup
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
            .thenReturn(false);

        alertEngine.evaluate("environment", "SENSOR-AIR-EMERGENCY", "AQI", 355.0);

        // Still only 1 alert — dedup worked
        verify(alertEventRepository, times(1)).save(any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  E2E timing: inject reading → verify alert has detectedAt timestamp
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Alert detectedAt is within 1s of reading injection time")
    void alertDetectedAt_within1sOfInjection() {
        Instant injectionTime = Instant.now();

        alertEngine.evaluate("environment", "SENSOR-AIR-001", "AQI", 300.0);

        verify(alertEventRepository).save(eventCaptor.capture());

        AlertEvent alert = eventCaptor.getValue();
        Duration timeDiff = Duration.between(injectionTime, alert.getDetectedAt()).abs();

        assertThat(timeDiff.toSeconds())
            .as("detectedAt should be within 1s of injection time, was %dms off",
                timeDiff.toMillis())
            .isLessThanOrEqualTo(1);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Multiple thresholds — different severity levels
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Multiple rules match → multiple alerts created with correct severity")
    void multipleRulesMatch_allAlertsCreated() {
        AlertRule warningRule = new AlertRule();
        warningRule.setModule("environment");
        warningRule.setMeasureType("AQI");
        warningRule.setOperator(">");
        warningRule.setThreshold(100.0);
        warningRule.setSeverity("WARNING");
        warningRule.setCooldownMinutes(5);
        warningRule.setActive(true);

        AlertRule criticalRule = aqiThresholdRule; // threshold=200, CRITICAL

        when(alertRuleRepository.findByModuleAndActiveTrue("environment"))
            .thenReturn(List.of(warningRule, criticalRule));

        alertEngine.evaluate("environment", "SENSOR-AIR-001", "AQI", 250.0);

        // Both rules match, so 2 saves
        verify(alertEventRepository, times(2)).save(eventCaptor.capture());

        List<AlertEvent> events = eventCaptor.getAllValues();
        assertThat(events).hasSize(2);
        assertThat(events.stream().map(AlertEvent::getSeverity).toList())
            .containsExactlyInAnyOrder("WARNING", "CRITICAL");
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  No alert for values below threshold
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Reading below threshold → no alert created (latency = 0)")
    void readingBelowThreshold_noAlertCreated() {
        alertEngine.evaluate("environment", "SENSOR-AIR-GOOD", "AQI", 45.0);

        verify(alertEventRepository, never()).save(any());
    }
}
