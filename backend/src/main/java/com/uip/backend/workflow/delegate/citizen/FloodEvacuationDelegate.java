package com.uip.backend.workflow.delegate.citizen;

import com.uip.backend.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * AI-C03: Flood Emergency & Evacuation Delegate
 * Triggers mass evacuation alerts based on flood severity
 */
@Component("floodEvacuationDelegate")
@RequiredArgsConstructor
@Slf4j
public class FloodEvacuationDelegate implements JavaDelegate {

    private final StringRedisTemplate redisTemplate;

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        Object waterLevel = execution.getVariable("waterLevel");
        String sensorLocation = (String) execution.getVariable("sensorLocation");
        String warningZones = (String) execution.getVariable("warningZones");
        String aiSeverity = (String) execution.getVariable("aiSeverity");
        String aiReasoning = (String) execution.getVariable("aiReasoning");

        log.info("Flood Evacuation for location {}: waterLevel={}, severity={}", 
                sensorLocation, waterLevel, aiSeverity);

        boolean massSmsTriggered = false;
        String evacuationZones = warningZones;
        String evacuationGuide = "Monitor water levels and follow official instructions.";

        if ("CRITICAL".equalsIgnoreCase(aiSeverity)) {
            // Publish mass evacuation SSE notification
            String message = String.format(
                    "{\"type\":\"flood_evacuation\",\"location\":\"%s\",\"waterLevel\":%s,\"zones\":\"%s\",\"severity\":\"CRITICAL\"}",
                    sensorLocation, waterLevel, evacuationZones
            );
            redisTemplate.convertAndSend(NotificationService.ALERT_CHANNEL, message);
            massSmsTriggered = true;
            evacuationGuide = "CRITICAL: Evacuate immediately to designated safe zones. " + aiReasoning;
            log.warn("CRITICAL flood alert! Mass evacuation triggered for: {}", evacuationZones);
        }

        execution.setVariable("evacuationZones", evacuationZones);
        execution.setVariable("evacuationGuide", evacuationGuide);
        execution.setVariable("massSmsTriggered", massSmsTriggered);
    }
}
