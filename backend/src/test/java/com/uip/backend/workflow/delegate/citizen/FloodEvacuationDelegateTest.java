package com.uip.backend.workflow.delegate.citizen;

import com.uip.backend.notification.service.NotificationService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FloodEvacuationDelegate")
class FloodEvacuationDelegateTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private DelegateExecution   execution;
    @InjectMocks private FloodEvacuationDelegate delegate;

    // ─── Case 1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiSeverity = CRITICAL → publish Redis, massSmsTriggered = true")
    void execute_criticalSeverity_triggersEvacuation() throws Exception {
        setupExecution(4.2, "CANAL-D8", "D8,D9", "CRITICAL", "Water level rising fast");
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        delegate.execute(execution);

        verify(redisTemplate, times(1))
                .convertAndSend(eq(NotificationService.ALERT_CHANNEL), anyString());
        verify(execution).setVariable("massSmsTriggered", true);
    }

    // ─── Case 2 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiSeverity = HIGH (non-critical) → không publish Redis, massSmsTriggered = false")
    void execute_highButNotCritical_doesNotTriggerEvacuation() throws Exception {
        setupExecution(3.8, "CANAL-D3", "D3", "HIGH", "Water level elevated");

        delegate.execute(execution);

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        verify(execution).setVariable("massSmsTriggered", false);
    }

    // ─── Case 3 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CRITICAL → Redis message chứa flood_evacuation + CRITICAL")
    void execute_criticalSeverity_messageContainsRequiredFields() throws Exception {
        setupExecution(4.5, "CANAL-D8", "D8,D9,D10", "CRITICAL", "Flood imminent");
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        delegate.execute(execution);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message).contains("flood_evacuation");
        assertThat(message).contains("CRITICAL");
        assertThat(message).contains("CANAL-D8");
    }

    // ─── Case 4 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CRITICAL → evacuationGuide chứa aiReasoning")
    void execute_criticalSeverity_evacuationGuideIncludesReasoning() throws Exception {
        setupExecution(4.2, "CANAL-D7", "D7", "CRITICAL", "Water level rising fast in district 7");
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        delegate.execute(execution);

        ArgumentCaptor<String> guideCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("evacuationGuide"), guideCaptor.capture());
        assertThat(guideCaptor.getValue()).contains("Water level rising fast in district 7");
    }

    // ─── Case 5 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiSeverity = null → không throw exception, massSmsTriggered = false")
    void execute_nullSeverity_doesNotThrow() {
        setupExecution(3.0, "CANAL-D1", "D1", null, null);

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void setupExecution(double waterLevel, String location, String zones,
                                String severity, String reasoning) {
        when(execution.getVariable("waterLevel")).thenReturn(waterLevel);
        when(execution.getVariable("sensorLocation")).thenReturn(location);
        when(execution.getVariable("warningZones")).thenReturn(zones);
        when(execution.getVariable("aiSeverity")).thenReturn(severity);
        when(execution.getVariable("aiReasoning")).thenReturn(reasoning);
    }
}
