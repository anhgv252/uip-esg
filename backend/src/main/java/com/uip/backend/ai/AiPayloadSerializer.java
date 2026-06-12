package com.uip.backend.ai;

import com.uip.backend.correlation.domain.CorrelatedPayload;
import com.uip.backend.correlation.domain.CorrelatedPayload.SensorReading;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * M4-COR-02: Serializes a {@link CorrelatedPayload} into a structured prompt string
 * suitable for the Claude AI inference API.
 *
 * <p>Output format:
 * <pre>
 *   Building {id}: {N} sensors triggered. AQI={val}, Water={val}, Noise={val}.
 *   Score={score:0.2f}. Action required?
 * </pre>
 * Known measure types are emitted by their short display names; unknown types are
 * appended at the end in {@code TYPE=value} format.</p>
 */
@Component
@Slf4j
public class AiPayloadSerializer {

    // Canonical display names for well-known measure types
    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "AQI",        "AQI",
            "FLOOD",      "Water",
            "WATER_LEVEL","Water",
            "NOISE",      "Noise",
            "STRUCTURAL", "Structural",
            "HUMIDITY",   "Humidity",
            "TEMPERATURE","Temp"
    );

    /**
     * Converts a {@link CorrelatedPayload} into a compact AI prompt string.
     *
     * @param payload the correlated sensor payload to serialize
     * @return prompt string ready to pass to the AI inference service
     */
    public String toAiPrompt(CorrelatedPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("CorrelatedPayload must not be null");
        }

        String sensorSummary = payload.sensors().stream()
                .map(this::formatSensor)
                .collect(Collectors.joining(", "));

        String prompt = String.format(
                "Building %s: %d sensors triggered. %s. Score=%.2f. Action required?",
                payload.buildingId(),
                payload.sensors().size(),
                sensorSummary,
                payload.correlationScore()
        );

        log.debug("[AiPayloadSerializer] building={} prompt_length={}", payload.buildingId(), prompt.length());
        return prompt;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String formatSensor(SensorReading reading) {
        String displayName = DISPLAY_NAMES.getOrDefault(
                reading.measureType().toUpperCase(),
                reading.measureType()
        );
        return String.format("%s=%.1f", displayName, reading.value());
    }
}
