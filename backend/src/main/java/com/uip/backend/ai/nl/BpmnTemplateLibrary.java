package com.uip.backend.ai.nl;

import com.uip.backend.ai.nl.template.NlBpmnTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * BPMN 2.0 template library for 10 MVP4 workflow types.
 *
 * <p>M5-2 T03: Template grounding — instantiate BPMN from NL intent + entities.
 * M5-2 ops: DB-backed template override — operators can update templates at runtime
 * via admin API without redeployment (ai.nl.template.db-override-enabled=true).
 *
 * <p>Resolution order:
 * <ol>
 *   <li>DB override (ai.nl_bpmn_templates where is_active=true) — if db-override enabled</li>
 *   <li>Classpath template (bpmn-templates/{intent}.bpmn)</li>
 * </ol>
 *
 * <p>Templates use Mustache-like {{placeholder}} syntax for entity substitution.
 * Unknown placeholders are replaced with a safe default (empty string) — never fail on missing entities.
 * Available placeholders: {{zone}}, {{threshold}}, {{buildingId}}, {{sensorId}}, {{location}}, {{month}}
 */
@Component
@Slf4j
public class BpmnTemplateLibrary {

    private final ResourceLoader resourceLoader;

    @Nullable
    private final NlBpmnTemplateRepository templateRepository;

    @Value("${ai.nl.template.db-override-enabled:true}")
    private boolean dbOverrideEnabled;

    private final Map<String, String> classpathCache = new HashMap<>();

    private static final String TEMPLATE_PATH_PREFIX = "classpath:bpmn-templates/";
    private static final String TEMPLATE_EXTENSION = ".bpmn";

    /** All 10 supported intents — used for validation and listing. */
    public static final Set<String> SUPPORTED_INTENTS = Set.of(
        "flood_response", "aqi_alert", "traffic_signal", "building_hvac",
        "sensor_maintenance", "citizen_notification", "energy_optimization",
        "water_leak_response", "emergency_evacuation", "esg_report"
    );

    @Autowired
    public BpmnTemplateLibrary(ResourceLoader resourceLoader,
                                @Autowired(required = false) NlBpmnTemplateRepository templateRepository) {
        this.resourceLoader = resourceLoader;
        this.templateRepository = templateRepository;
    }

    @PostConstruct
    public void loadClasspathTemplates() {
        log.info("Loading {} BPMN templates from classpath", SUPPORTED_INTENTS.size());
        int loaded = 0;
        for (String intent : SUPPORTED_INTENTS) {
            String filename = intent + TEMPLATE_EXTENSION;
            try {
                Resource resource = resourceLoader.getResource(TEMPLATE_PATH_PREFIX + filename);
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                classpathCache.put(intent, content);
                loaded++;
            } catch (IOException e) {
                log.warn("Classpath BPMN template missing: {} — DB override must exist for intent={}", filename, intent);
            }
        }
        log.info("BPMN classpath templates loaded: {}/{}", loaded, SUPPORTED_INTENTS.size());
    }

    /**
     * Resolve template for intent: DB override first, then classpath.
     *
     * @param intent one of the 10 supported workflow intents
     * @return BPMN XML template with {{placeholders}}, or null if not found
     */
    @Nullable
    public String getTemplate(String intent) {
        // 1. DB override (runtime-customisable)
        if (dbOverrideEnabled && templateRepository != null) {
            var dbTemplate = templateRepository.findByIntentAndIsActiveTrue(intent);
            if (dbTemplate.isPresent()) {
                log.debug("Using DB override template for intent={}", intent);
                return dbTemplate.get().getBpmnXml();
            }
        }
        // 2. Classpath fallback
        return classpathCache.get(intent);
    }

    /**
     * Instantiate BPMN template by substituting entity placeholders.
     * Unknown placeholders are silently replaced with empty string — never throws on missing entities.
     *
     * @param intent   workflow intent
     * @param entities extracted entities map (may be empty)
     * @return instantiated BPMN XML
     * @throws IllegalArgumentException if no template exists for intent
     */
    public String instantiate(String intent, Map<String, String> entities) {
        String template = getTemplate(intent);
        if (template == null) {
            throw new IllegalArgumentException(
                "No BPMN template for intent '" + intent + "'. "
                + "Ensure classpath:bpmn-templates/" + intent + ".bpmn exists "
                + "or add a DB override via POST /api/v1/admin/nl/templates");
        }

        if (entities == null || entities.isEmpty()) {
            // Replace remaining placeholders with safe defaults
            return removePlaceholders(template, intent);
        }

        String result = template;
        for (Map.Entry<String, String> entity : entities.entrySet()) {
            String safeValue = escapeXml(entity.getValue());
            result = result.replace("{{" + entity.getKey() + "}}", safeValue);
        }
        result = removePlaceholders(result, intent);

        log.debug("BPMN template instantiated: intent={}, substituted={}", intent, entities.keySet());
        return result;
    }

    public boolean hasTemplate(String intent) {
        return getTemplate(intent) != null;
    }

    // ---- helpers ----

    /** Replace any remaining {{placeholder}} with empty string (safe default). */
    private String removePlaceholders(String xml, String intent) {
        String result = xml.replaceAll("\\{\\{[^}]+}}", "");
        if (result.contains("{{")) {
            log.warn("BPMN template still has unfilled placeholders after substitution, intent={}", intent);
        }
        return result;
    }

    /** Escape user-supplied entity values to prevent XML injection. */
    private String escapeXml(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
