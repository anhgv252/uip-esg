package com.uip.backend.alert.service;

import com.uip.backend.alert.domain.AlertEvent;
import com.uip.backend.alert.domain.AlertRule;
import com.uip.backend.alert.repository.AlertEventRepository;
import com.uip.backend.alert.repository.AlertRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AlertEngine")
class AlertEngineTest {

    @Mock private AlertRuleRepository  alertRuleRepository;
    @Mock private AlertEventRepository alertEventRepository;
    @Mock private StringRedisTemplate  redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private AlertEngine alertEngine;

    @Captor private ArgumentCaptor<AlertEvent> eventCaptor;

    private AlertRule makeRule(String operator, double threshold) {
        AlertRule rule = new AlertRule();
        rule.setModule("environment");
        rule.setMeasureType("pm25");
        rule.setOperator(operator);
        rule.setThreshold(threshold);
        rule.setSeverity("HIGH");
        rule.setCooldownMinutes(10);
        return rule;
    }

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("evaluate: exceeds threshold fires alert and persists event")
    void evaluate_exceedsThreshold_savesAlert() {
        AlertRule rule = makeRule(">", 100.0);
        when(alertRuleRepository.findByModuleAndActiveTrue("environment")).thenReturn(List.of(rule));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertEngine.evaluate("environment", "sensor-1", "pm25", 150.0);

        verify(alertEventRepository).save(eventCaptor.capture());
        AlertEvent saved = eventCaptor.getValue();
        assertThat(saved.getValue()).isEqualTo(150.0);
        assertThat(saved.getThreshold()).isEqualTo(100.0);
        assertThat(saved.getSeverity()).isEqualTo("HIGH");
        assertThat(saved.getSensorId()).isEqualTo("sensor-1");
    }

    @Test
    @DisplayName("evaluate: value at threshold boundary (op=>) does NOT fire")
    void evaluate_atThresholdExactWithGreaterOp_noAlert() {
        AlertRule rule = makeRule(">", 100.0);
        when(alertRuleRepository.findByModuleAndActiveTrue("environment")).thenReturn(List.of(rule));
        // valueOps.setIfAbsent is never called because condition is not met
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        alertEngine.evaluate("environment", "sensor-1", "pm25", 100.0);

        verify(alertEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("evaluate: value at threshold boundary (op=>=) fires alert")
    void evaluate_atThresholdWithGteOp_firesAlert() {
        AlertRule rule = makeRule(">=", 100.0);
        when(alertRuleRepository.findByModuleAndActiveTrue("environment")).thenReturn(List.of(rule));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertEngine.evaluate("environment", "sensor-1", "pm25", 100.0);

        verify(alertEventRepository).save(any());
    }

    @Test
    @DisplayName("evaluate: Redis dedup prevents duplicate alert within cooldown")
    void evaluate_dedupViaRedis_skipsSecondFire() {
        AlertRule rule = makeRule(">", 100.0);
        when(alertRuleRepository.findByModuleAndActiveTrue("environment")).thenReturn(List.of(rule));
        // First call: key not present → returns true (new)
        // Second call: key exists → returns false (duplicate)
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true).thenReturn(false);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertEngine.evaluate("environment", "sensor-1", "pm25", 150.0);
        alertEngine.evaluate("environment", "sensor-1", "pm25", 160.0);

        // Only one save despite two calls
        verify(alertEventRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("evaluate: mismatched measureType is skipped")
    void evaluate_wrongMeasureType_skipped() {
        AlertRule rule = makeRule(">", 100.0); // measureType = pm25
        when(alertRuleRepository.findByModuleAndActiveTrue("environment")).thenReturn(List.of(rule));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        alertEngine.evaluate("environment", "sensor-1", "no2", 999.0);

        verify(alertEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("evaluate: no active rules in module → no action")
    void evaluate_noActiveRules_noAction() {
        when(alertRuleRepository.findByModuleAndActiveTrue("environment")).thenReturn(List.of());

        alertEngine.evaluate("environment", "sensor-1", "pm25", 999.0);

        // No interactions with alertEventRepository or Redis
        verify(alertEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("evaluate: Redis dedup key includes ruleId, sensorId, measureType")
    void evaluate_dedupKeyFormat() {
        AlertRule rule = makeRule(">", 50.0);
        UUID ruleId = UUID.randomUUID();
        rule.setModule("environment");

        // Use reflection-free setter — AlertRule uses Lombok @Setter
        // We set a known UUID via another AlertRule approach (no setter for id in AlertRule?)
        // Re-check: AlertRule has @GeneratedValue but no explicit setter. Use spy.
        AlertRule spy = Mockito.spy(rule);
        doReturn(ruleId).when(spy).getId();

        when(alertRuleRepository.findByModuleAndActiveTrue("environment")).thenReturn(List.of(spy));
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(alertEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        alertEngine.evaluate("environment", "sensor-42", "pm25", 100.0);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(keyCaptor.capture(), eq("1"), any());
        String key = keyCaptor.getValue();
        assertThat(key).contains("sensor-42").contains("pm25").contains(ruleId.toString());
    }
}
