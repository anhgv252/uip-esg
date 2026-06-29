package com.uip.backend.ai.nl.validation;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Custom semantic/constraint rules for AI-generated BPMN.
 *
 * <p>Runs after XSD validation. Catches constraints that XSD cannot express:
 * node count limits, structural completeness, injection/URL risks, and
 * city-operations safety rules.
 *
 * <p>M5-2 T10: Base ruleset (Rules 1–5).
 * <p>M5-3 T02: Semantic safety rules (Rules 6–10) — flood/fire safety, operator gate.
 */
@Component
public class BpmnCustomValidator {

    static final int MAX_NODES = 20;
    static final int MAX_PARALLEL_GATEWAYS = 3;

    private static final Pattern SCRIPT_TASK_PATTERN =
            Pattern.compile("<(bpmn:)?scriptTask[\\s>/]");

    // Matches href attribute values pointing to external http/https URLs.
    // Intentionally excludes namespace URIs (xmlns:... declarations).
    private static final Pattern EXTERNAL_HREF_PATTERN =
            Pattern.compile("href\\s*=\\s*[\"']https?://", Pattern.CASE_INSENSITIVE);

    // Counts all BPMN flow-node opening tags (tasks, events, gateways).
    private static final Pattern FLOW_NODE_PATTERN = Pattern.compile(
            "<(bpmn:)?(task|serviceTask|userTask|manualTask|businessRuleTask|sendTask|receiveTask" +
            "|startEvent|endEvent|exclusiveGateway|parallelGateway|inclusiveGateway" +
            "|eventBasedGateway|subProcess)[\\s>/]");

    private static final Pattern START_EVENT_PATTERN =
            Pattern.compile("<(bpmn:)?startEvent[\\s>/]");

    private static final Pattern END_EVENT_PATTERN =
            Pattern.compile("<(bpmn:)?endEvent[\\s>/]");

    // Rule 6 / Rule 7: gateway patterns
    private static final Pattern ANY_GATEWAY_PATTERN =
            Pattern.compile("<(bpmn:)?(exclusiveGateway|parallelGateway)[\\s>/]");

    private static final Pattern EXCLUSIVE_GATEWAY_PATTERN =
            Pattern.compile("<(bpmn:)?exclusiveGateway[\\s>/]");

    private static final Pattern PARALLEL_GATEWAY_PATTERN =
            Pattern.compile("<(bpmn:)?parallelGateway[\\s>/]");

    // Rule 10: operator userTask requirement (BR-010)
    private static final Pattern USER_TASK_PATTERN =
            Pattern.compile("<(bpmn:)?userTask[\\s>/]");

    /**
     * Validate a BPMN XML string against all custom rules.
     *
     * @param bpmnXml raw BPMN XML (may be null or blank — returns error)
     * @return {@link ValidationResult} with all violations collected
     */
    public ValidationResult validate(String bpmnXml) {
        List<String> errors = new ArrayList<>();

        if (bpmnXml == null || bpmnXml.isBlank()) {
            errors.add("STRUCTURE: BPMN XML must not be empty");
            return new ValidationResult(false, errors);
        }

        // Rule 1: No scriptTask (arbitrary code execution risk)
        if (SCRIPT_TASK_PATTERN.matcher(bpmnXml).find()) {
            errors.add("SECURITY: scriptTask not allowed in generated BPMN");
        }

        // Rule 2: No external URLs in href attributes
        if (EXTERNAL_HREF_PATTERN.matcher(bpmnXml).find()) {
            errors.add("SECURITY: external URLs not allowed in href attributes");
        }

        // Rule 3: Max node count (prevent runaway graph generation)
        long nodeCount = FLOW_NODE_PATTERN.matcher(bpmnXml).results().count();
        if (nodeCount > MAX_NODES) {
            errors.add("CONSTRAINT: max " + MAX_NODES + " nodes exceeded (" + nodeCount + " found)");
        }

        // Rule 4: Must have at least 1 startEvent
        if (!START_EVENT_PATTERN.matcher(bpmnXml).find()) {
            errors.add("STRUCTURE: missing startEvent");
        }

        // Rule 5: Must have at least 1 endEvent
        if (!END_EVENT_PATTERN.matcher(bpmnXml).find()) {
            errors.add("STRUCTURE: missing endEvent");
        }

        // Rule 6: Flood safety — pump/bơm activation requires a confirmation gateway
        // Prevents false pump activation: sensor confirmation gate is mandatory before pump tasks.
        String bpmnXmlLower = bpmnXml.toLowerCase();
        if (bpmnXmlLower.contains("pump") || bpmnXmlLower.contains("bơm")) {
            long gatewayCount = ANY_GATEWAY_PATTERN.matcher(bpmnXml).results().count();
            if (gatewayCount == 0) {
                errors.add("SAFETY: flood pump activation without confirmation gateway (sensor confirmation required)");
            }
        }

        // Rule 7: Fire/sprinkler safety — sprinkler and evacuation must not co-occur without decision gateway
        if ((bpmnXmlLower.contains("sprinkler") || bpmnXmlLower.contains("fire"))
                && bpmnXmlLower.contains("evacuation")) {
            long exclusiveGatewayCount = EXCLUSIVE_GATEWAY_PATTERN.matcher(bpmnXml).results().count();
            if (exclusiveGatewayCount == 0) {
                errors.add("SAFETY: sprinkler and evacuation in same process without decision gateway");
            }
        }

        // Rule 8: Citizen notification ordering — notification task must not be the first substantive task
        // Heuristic: count serviceTask/manualTask/businessRuleTask elements before the first task whose
        // name contains "notification" or "notify" or "thông báo". At least 1 preceding task is required.
        applyNotificationOrderingRule(bpmnXml, bpmnXmlLower, errors);

        // Rule 9: Maximum parallel paths — more than 3 parallel gateways is too complex for operator review
        long parallelGateways = PARALLEL_GATEWAY_PATTERN.matcher(bpmnXml).results().count();
        if (parallelGateways > MAX_PARALLEL_GATEWAYS) {
            errors.add("COMPLEXITY: too many parallel gateways (" + parallelGateways
                    + " > " + MAX_PARALLEL_GATEWAYS + " max) — simplify for operator review");
        }

        // Rule 10: BR-010 — all AI-generated workflows require at least 1 userTask (operator approval gate)
        if (!USER_TASK_PATTERN.matcher(bpmnXml).find()) {
            errors.add("BR-010: missing operator review userTask — all AI-generated workflows require human approval before execution");
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Rule 8 implementation: citizen notification must not be the first substantive task.
     *
     * <p>Approach: scan task name attributes in document order. The first task whose name
     * (case-insensitive) contains "notification", "notify", or "thông báo" must be preceded
     * by at least one other task name in the document. This is a text-order heuristic —
     * not a full graph traversal — but sufficient for LLM-generated XML which is emitted
     * in topological order.
     */
    private void applyNotificationOrderingRule(String bpmnXml, String bpmnXmlLower,
                                                List<String> errors) {
        // Extract all task name= attributes in document order (serviceTask, manualTask, userTask, etc.)
        Pattern taskNamePattern = Pattern.compile(
                "<(bpmn:)?(serviceTask|manualTask|userTask|businessRuleTask|sendTask)[^>]*name\\s*=\\s*[\"']([^\"']*)[\"']",
                Pattern.CASE_INSENSITIVE);
        var matcher = taskNamePattern.matcher(bpmnXml);
        int taskIndex = 0;
        while (matcher.find()) {
            String taskName = matcher.group(3).toLowerCase();
            boolean isNotification = taskName.contains("notification")
                    || taskName.contains("notify")
                    || taskName.contains("thông báo");
            if (isNotification && taskIndex == 0) {
                errors.add("ORDERING: citizen notification task appears before any check/assess task — AQI verification must precede notification");
                break;
            }
            taskIndex++;
        }
    }
}
