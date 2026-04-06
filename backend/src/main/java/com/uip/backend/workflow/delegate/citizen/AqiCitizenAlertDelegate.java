package com.uip.backend.workflow.delegate.citizen;

import com.uip.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * AI-C01: AQI Citizen Alert Delegate
 * Notifies citizens when AQI exceeds threshold based on AI decision
 */
@Component("aqiCitizenAlertDelegate")
@RequiredArgsConstructor
@Slf4j
public class AqiCitizenAlertDelegate implements JavaDelegate {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        String sensorId = (String) execution.getVariable("sensorId");
        Object aqiValue = execution.getVariable("aqiValue");
        String districtCode = (String) execution.getVariable("districtCode");
        String measuredAt = (String) execution.getVariable("measuredAt");
        String aiDecision = (String) execution.getVariable("aiDecision");

        log.info("AQI Citizen Alert for sensor {} in district {}: AQI={}, decision={}", 
                sensorId, districtCode, aqiValue, aiDecision);

        if ("NOTIFY_CITIZENS".equals(aiDecision)) {
            // Publish SSE notification via Redis
            String message = String.format(
                    "{\"type\":\"aqi_alert\",\"sensorId\":\"%s\",\"district\":\"%s\",\"aqi\":%s,\"measuredAt\":\"%s\"}",
                    sensorId, districtCode, aqiValue, measuredAt
            );
            redisTemplate.convertAndSend(NotificationService.ALERT_CHANNEL, message);
            
            execution.setVariable("notificationSent", true);
            execution.setVariable("citizensNotified", 1500); // Mock count
            log.info("AQI alert notification sent for district: {}", districtCode);
        } else {
            execution.setVariable("notificationSent", false);
            execution.setVariable("citizensNotified", 0);
            log.info("No notification sent based on AI decision: {}", aiDecision);
        }
    }
}
