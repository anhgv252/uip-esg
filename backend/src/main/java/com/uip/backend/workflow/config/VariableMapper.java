package com.uip.backend.workflow.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class VariableMapper {

    private final ObjectMapper objectMapper;

    public Map<String, Object> map(String variableMappingJson, Map<String, Object> payload) {
        try {
            Map<String, MappingDef> mappings = objectMapper.readValue(
                variableMappingJson, new TypeReference<>() {});
            Map<String, Object> result = new HashMap<>();
            for (var entry : mappings.entrySet()) {
                Object value = resolveValue(entry.getValue(), payload);
                if (value != null) {
                    result.put(entry.getKey(), value);
                }
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to parse variable mapping: {}", e.getMessage());
            return Map.of();
        }
    }

    public String extractValue(String dotPath, Map<String, Object> payload) {
        Object value = getNestedValue(payload, dotPath);
        return value != null ? value.toString() : null;
    }

    private Object resolveValue(MappingDef def, Map<String, Object> payload) {
        if (def.staticVal() != null) return def.staticVal();
        if (def.source() != null) {
            String path = def.source().startsWith("payload.") ? def.source().substring(8) : def.source();
            Object value = getNestedValue(payload, path);
            if (value != null) return value;
        }
        if (def.defaultVal() != null) {
            return switch (def.defaultVal()) {
                case "NOW()" -> Instant.now().toString();
                case "UUID()" -> UUID.randomUUID().toString();
                default -> def.defaultVal();
            };
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> data, String path) {
        if (data == null || path == null) return null;
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    record MappingDef(
        String source,
        @JsonProperty("default") String defaultVal,
        @JsonProperty("static") String staticVal
    ) {}
}
