package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.domain.NLParseResult;
import com.uip.backend.ai.nl.domain.Route;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Local ViT5 stub parser for POC (keyword-based fallback).
 *
 * <p>M5-2 T02: POC implementation — keyword matching for 10 intents.
 * <p>M5-3 T05: Replace with actual ViT5 HTTP endpoint after fine-tuning.
 *
 * <p>ADR-049 §6: ViT5 SLA p95 ≤ 8s, timeout 10s, intent hit rate ≥ 80%.
 *
 * <p>Active by default (ai.nl.local-parser.enabled=true). Set to false
 * only when a real on-prem ViT5 endpoint is available and preferred.
 */
@Component
@ConditionalOnProperty(name = "ai.nl.local-parser.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class LocalNLParser {

    /**
     * Parse Vietnamese text with keyword-based intent matching (POC only).
     * 
     * @param text Vietnamese operator command
     * @param requestId for logging
     * @return parse result with intent, confidence (0.0 for UNKNOWN), entities
     */
    public NLParseResult parse(String text, String requestId) {
        log.debug("Local parser (keyword stub) invoked, requestId={}", requestId);
        
        long startMs = System.currentTimeMillis();
        
        String textLower = text.toLowerCase();
        String intent = "UNKNOWN";
        double confidence = 0.0;
        Map<String, String> entities = new HashMap<>();
        
        // Keyword patterns for 10 MVP4 intents
        if (textLower.contains("lũ") || textLower.contains("ngập") || 
            textLower.contains("flood") || textLower.contains("nước lũ")) {
            intent = "flood_response";
            confidence = 0.85;
            extractZone(text, entities);
        } 
        else if (textLower.contains("aqi") || textLower.contains("không khí") || 
                 textLower.contains("chất lượng không khí") || textLower.contains("ô nhiễm")) {
            intent = "aqi_alert";
            confidence = 0.90;
            extractZone(text, entities);
            extractThreshold(text, entities);
        }
        else if (textLower.contains("đèn giao thông") || textLower.contains("traffic") || 
                 textLower.contains("tín hiệu") || textLower.contains("ngã tư")) {
            intent = "traffic_signal";
            confidence = 0.88;
            extractLocation(text, entities);
        }
        else if (textLower.contains("điều hòa") || textLower.contains("hvac") || 
                 textLower.contains("máy lạnh") || textLower.contains("nhiệt độ tòa nhà")) {
            intent = "building_hvac";
            confidence = 0.87;
            extractBuilding(text, entities);
        }
        else if (textLower.contains("bảo trì") || textLower.contains("maintenance") || 
                 textLower.contains("cảm biến") || textLower.contains("sensor")) {
            intent = "sensor_maintenance";
            confidence = 0.82;
            extractSensorId(text, entities);
        }
        else if (textLower.contains("thông báo") || textLower.contains("notification") || 
                 textLower.contains("cư dân") || textLower.contains("gửi tin")) {
            intent = "citizen_notification";
            confidence = 0.85;
            extractZone(text, entities);
        }
        else if (textLower.contains("tối ưu") || textLower.contains("optimization") || 
                 textLower.contains("điện năng") || textLower.contains("energy")) {
            intent = "energy_optimization";
            confidence = 0.83;
            extractBuilding(text, entities);
        }
        else if (textLower.contains("rò rỉ") || textLower.contains("leak") || 
                 textLower.contains("đường ống") || textLower.contains("nước")) {
            intent = "water_leak_response";
            confidence = 0.80;
            extractZone(text, entities);
        }
        else if (textLower.contains("sơ tán") || textLower.contains("evacuation") || 
                 textLower.contains("khẩn cấp") || textLower.contains("emergency")) {
            intent = "emergency_evacuation";
            confidence = 0.92;
            extractZone(text, entities);
        }
        else if (textLower.contains("esg") || textLower.contains("báo cáo") || 
                 textLower.contains("report") || textLower.contains("môi trường")) {
            intent = "esg_report";
            confidence = 0.88;
            extractBuilding(text, entities);
            extractMonth(text, entities);
        }
        
        long latencyMs = System.currentTimeMillis() - startMs;
        
        log.info("Local parse complete (keyword): intent={}, confidence={}, latencyMs={}, requestId={}",
                intent, confidence, latencyMs, requestId);
        
        return new NLParseResult(
            intent,
            confidence,
            entities.isEmpty() ? null : entities,
            Route.LOCAL,
            latencyMs,
            null // BPMN template filled by service layer
        );
    }
    
    private void extractZone(String text, Map<String, String> entities) {
        // Extract "quận X" or "phường Y"
        if (text.matches(".*quận\\s+\\d+.*")) {
            String zone = text.replaceAll(".*quận\\s+(\\d+).*", "quận $1");
            entities.put("zone", zone);
        } else if (text.matches(".*phường\\s+[A-Za-zÀ-ỹ\\s]+.*")) {
            String zone = text.replaceAll(".*phường\\s+([A-Za-zÀ-ỹ\\s]+).*", "phường $1").trim();
            entities.put("zone", zone);
        }
    }
    
    private void extractThreshold(String text, Map<String, String> entities) {
        // Extract "AQI > 150" or "vượt 150"
        if (text.matches(".*>\\s*\\d+.*")) {
            String threshold = text.replaceAll(".*>\\s*(\\d+).*", "$1");
            entities.put("threshold", threshold);
        } else if (text.matches(".*vượt\\s+\\d+.*")) {
            String threshold = text.replaceAll(".*vượt\\s+(\\d+).*", "$1");
            entities.put("threshold", threshold);
        }
    }
    
    private void extractLocation(String text, Map<String, String> entities) {
        // Extract intersection or street name
        if (text.contains("ngã tư")) {
            String location = text.replaceAll(".*ngã tư\\s+([A-Za-zÀ-ỹ\\s-]+).*", "$1").trim();
            entities.put("location", location);
        }
    }
    
    private void extractBuilding(String text, Map<String, String> entities) {
        // Extract "tòa nhà X" or "building X"
        if (text.matches(".*tòa nhà\\s+[A-Za-z0-9]+.*")) {
            String building = text.replaceAll(".*tòa nhà\\s+([A-Za-z0-9]+).*", "$1");
            entities.put("building", building);
        }
    }
    
    private void extractSensorId(String text, Map<String, String> entities) {
        // Extract sensor ID pattern
        if (text.matches(".*[A-Z]+-\\d+.*")) {
            String sensorId = text.replaceAll(".*([A-Z]+-\\d+).*", "$1");
            entities.put("sensorId", sensorId);
        }
    }
    
    private void extractMonth(String text, Map<String, String> entities) {
        // Extract "tháng X"
        if (text.matches(".*tháng\\s+\\d+.*")) {
            String month = text.replaceAll(".*tháng\\s+(\\d+).*", "$1");
            entities.put("month", month);
        }
    }
}
