package com.uip.backend.ai.routing;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * M4-AI-02: Routes AI inference requests to Haiku or Sonnet based on
 * estimated token count and request priority.
 *
 * <p>Routing policy:
 * <ul>
 *   <li>LOW priority → always Haiku (cost-optimized)</li>
 *   <li>Estimated tokens ≤ 500 → Haiku (sufficient for short completions)</li>
 *   <li>Otherwise → Sonnet (higher capability for complex, high-priority requests)</li>
 * </ul>
 * </p>
 */
@Component
@Slf4j
public class ModelRouter {

    private static final int    HAIKU_MAX_TOKENS  = 500;
    private static final String MODEL_HAIKU       = "claude-haiku-4-5-20251001";
    private static final String MODEL_SONNET      = "claude-sonnet-4-6";

    /**
     * Select the optimal model for an inference request.
     *
     * @param estimatedTokens approximate prompt+completion token count
     * @param priority        request priority: "LOW", "MEDIUM", "HIGH"
     * @return model identifier string
     */
    public String selectModel(int estimatedTokens, String priority) {
        boolean useHaiku = "LOW".equals(priority) || estimatedTokens <= HAIKU_MAX_TOKENS;
        String model = useHaiku ? MODEL_HAIKU : MODEL_SONNET;
        log.debug("[ModelRouter] tokens={} priority={} → model={}", estimatedTokens, priority, model);
        return model;
    }
}
