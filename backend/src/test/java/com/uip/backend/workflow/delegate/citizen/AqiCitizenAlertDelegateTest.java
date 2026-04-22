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
@DisplayName("AqiCitizenAlertDelegate")
class AqiCitizenAlertDelegateTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private DelegateExecution   execution;
    @InjectMocks private AqiCitizenAlertDelegate delegate;

    // ─── Case 1 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("NOTIFY_CITIZENS → publish Redis, notificationSent = true")
    void execute_notifyCitizens_publishesRedisAndSetsTrue() throws Exception {
        when(execution.getVariable("aiDecision")).thenReturn("NOTIFY_CITIZENS");
        when(execution.getVariable("sensorId")).thenReturn("AQI-001");
        when(execution.getVariable("aqiValue")).thenReturn(175.0);
        when(execution.getVariable("districtCode")).thenReturn("D7");
        when(execution.getVariable("measuredAt")).thenReturn("2026-04-14T10:00:00Z");
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        delegate.execute(execution);

        verify(redisTemplate, times(1))
                .convertAndSend(eq(NotificationService.ALERT_CHANNEL), anyString());
        verify(execution).setVariable("notificationSent", true);
    }

    // ─── Case 2 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MONITOR_ONLY → không publish Redis, notificationSent = false")
    void execute_monitorOnly_doesNotPublishRedis() throws Exception {
        when(execution.getVariable("aiDecision")).thenReturn("MONITOR_ONLY");
        when(execution.getVariable("sensorId")).thenReturn("AQI-002");
        when(execution.getVariable("aqiValue")).thenReturn(120.0);
        when(execution.getVariable("districtCode")).thenReturn("D1");
        when(execution.getVariable("measuredAt")).thenReturn("2026-04-14T10:00:00Z");

        delegate.execute(execution);

        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
        verify(execution).setVariable("notificationSent", false);
        verify(execution).setVariable("citizensNotified", 0);
    }

    // ─── Case 3 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("aiDecision = null → không throw exception")
    void execute_nullDecision_doesNotThrow() {
        when(execution.getVariable("aiDecision")).thenReturn(null);
        when(execution.getVariable("sensorId")).thenReturn("AQI-003");
        when(execution.getVariable("aqiValue")).thenReturn(100.0);
        when(execution.getVariable("districtCode")).thenReturn("D3");
        when(execution.getVariable("measuredAt")).thenReturn("2026-04-14T10:00:00Z");

        assertThatCode(() -> delegate.execute(execution)).doesNotThrowAnyException();
        verify(redisTemplate, never()).convertAndSend(anyString(), anyString());
    }

    // ─── Case 4 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("NOTIFY_CITIZENS → Redis message chứa đúng JSON fields")
    void execute_notifyCitizens_messageContainsRequiredFields() throws Exception {
        when(execution.getVariable("aiDecision")).thenReturn("NOTIFY_CITIZENS");
        when(execution.getVariable("sensorId")).thenReturn("AQI-001");
        when(execution.getVariable("aqiValue")).thenReturn(200.0);
        when(execution.getVariable("districtCode")).thenReturn("D9");
        when(execution.getVariable("measuredAt")).thenReturn("2026-04-14T10:00:00Z");
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

        delegate.execute(execution);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(anyString(), messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message).contains("aqi_alert");
        assertThat(message).contains("AQI-001");
        assertThat(message).contains("D9");
    }
}
