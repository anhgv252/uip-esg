package com.uip.backend.ai.nl.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BpmnCustomValidator}.
 *
 * <p>M5-2 T10: 20 test cases covering all 5 custom rules.
 * XSD structural validation is covered by {@link BpmnValidationServiceTest}.
 */
@DisplayName("BpmnCustomValidator — custom rule tests")
class BpmnCustomValidatorTest {

    private BpmnCustomValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BpmnCustomValidator();
    }

    // =========================================================
    // Happy path
    // =========================================================

    @Test
    @DisplayName("Valid minimal BPMN passes all rules")
    void validMinimalBpmn_passes() {
        String bpmn = minimalBpmn("Verify Sensor Reading");
        assertThat(validator.validate(bpmn).isValid()).isTrue();
    }

    @Test
    @DisplayName("Valid BPMN with multiple service tasks passes")
    void validMultiTaskBpmn_passes() {
        String bpmn = bpmnWithTasks(3, "Process Task");
        assertThat(validator.validate(bpmn).isValid()).isTrue();
    }

    @Test
    @DisplayName("Exactly 20 nodes passes (boundary)")
    void exactlyMaxNodes_passes() {
        // 2 events + 17 serviceTasks + 1 userTask (injected by bpmnWithTasks) = 20 nodes
        String bpmn = bpmnWithTasks(17, "Step");
        long nodeCount = countNodes(bpmn);
        assertThat(nodeCount).isEqualTo(20);
        assertThat(validator.validate(bpmn).isValid()).isTrue();
    }

    // =========================================================
    // Rule 1: No scriptTask
    // =========================================================

    @Test
    @DisplayName("scriptTask with bpmn: prefix is rejected")
    void scriptTaskWithBpmnPrefix_fails() {
        String bpmn = minimalBpmn("x").replace("</bpmn:process>",
            "<bpmn:scriptTask id=\"t1\" name=\"run\"><script>rm -rf /</script></bpmn:scriptTask></bpmn:process>");
        assertThat(validator.validate(bpmn).isValid()).isFalse();
        assertThat(validator.validate(bpmn).errors()).anyMatch(e -> e.contains("scriptTask"));
    }

    @Test
    @DisplayName("scriptTask without namespace prefix is rejected")
    void scriptTaskWithoutPrefix_fails() {
        String bpmn = minimalBpmn("x").replace("</bpmn:process>",
            "<scriptTask id=\"t1\" name=\"run\"></scriptTask></bpmn:process>");
        assertThat(validator.validate(bpmn).isValid()).isFalse();
        assertThat(validator.validate(bpmn).errors()).anyMatch(e -> e.contains("scriptTask"));
    }

    @Test
    @DisplayName("scriptTask error message starts with SECURITY")
    void scriptTask_errorCodeIsSecurity() {
        String bpmn = minimalBpmn("x").replace("</bpmn:process>",
            "<bpmn:scriptTask id=\"t1\" name=\"hack\"/></bpmn:process>");
        assertThat(validator.validate(bpmn).errors())
            .anyMatch(e -> e.startsWith("SECURITY:") && e.contains("scriptTask"));
    }

    // =========================================================
    // Rule 2: No external URLs in href
    // =========================================================

    @Test
    @DisplayName("href with https URL is rejected")
    void externalHrefHttps_fails() {
        String bpmn = minimalBpmn("x").replace("</bpmn:process>",
            "<bpmn:serviceTask id=\"t1\" name=\"Call\" href=\"https://evil.example.com/payload\"/></bpmn:process>");
        assertThat(validator.validate(bpmn).isValid()).isFalse();
        assertThat(validator.validate(bpmn).errors()).anyMatch(e -> e.contains("external URLs"));
    }

    @Test
    @DisplayName("href with http URL is rejected")
    void externalHrefHttp_fails() {
        String bpmn = minimalBpmn("x").replace("</bpmn:process>",
            "<bpmn:serviceTask id=\"t1\" name=\"Call\" href=\"http://external.host/resource\"/></bpmn:process>");
        assertThat(validator.validate(bpmn).isValid()).isFalse();
        assertThat(validator.validate(bpmn).errors()).anyMatch(e -> e.contains("external URLs"));
    }

    @Test
    @DisplayName("xmlns namespace URI is NOT flagged as external href")
    void xmlnsNamespaceUri_notFlaggedAsHref() {
        // The bpmn namespace declaration contains http:// but is not an href attribute
        String bpmn = minimalBpmn("Verify Reading");
        assertThat(bpmn).contains("http://www.omg.org");      // sanity: URI is present
        assertThat(validator.validate(bpmn).isValid()).isTrue(); // but not a href
    }

    // =========================================================
    // Rule 3: Max node count
    // =========================================================

    @Test
    @DisplayName("21 nodes exceeds limit and is rejected")
    void twentyOneNodes_fails() {
        // 2 events + 18 serviceTasks + 1 userTask (injected by bpmnWithTasks) = 21 nodes
        String bpmn = bpmnWithTasks(18, "Extra Step");
        assertThat(validator.validate(bpmn).isValid()).isFalse();
        assertThat(validator.validate(bpmn).errors()).anyMatch(e -> e.contains("max 20 nodes exceeded"));
    }

    @Test
    @DisplayName("Node count error reports actual count")
    void nodeCountError_reportsActualCount() {
        String bpmn = bpmnWithTasks(18, "Step");
        String errorMsg = validator.validate(bpmn).errors().stream()
            .filter(e -> e.contains("max 20 nodes exceeded"))
            .findFirst().orElse("");
        assertThat(errorMsg).contains("21 found");
    }

    // =========================================================
    // Rule 4: Must have startEvent
    // =========================================================

    @Test
    @DisplayName("Missing startEvent is rejected")
    void missingStartEvent_fails() {
        String bpmn = minimalBpmn("task").replace(
            "    <bpmn:startEvent id=\"Start\" name=\"Start\"><bpmn:outgoing>Flow_start</bpmn:outgoing></bpmn:startEvent>\n", "");
        assertThat(validator.validate(bpmn).isValid()).isFalse();
        assertThat(validator.validate(bpmn).errors()).anyMatch(e -> e.contains("startEvent"));
    }

    @Test
    @DisplayName("startEvent rule error starts with STRUCTURE")
    void startEventError_codeIsStructure() {
        String bpmn = minimalBpmn("task").replace(
            "    <bpmn:startEvent id=\"Start\" name=\"Start\"><bpmn:outgoing>Flow_start</bpmn:outgoing></bpmn:startEvent>\n", "");
        assertThat(validator.validate(bpmn).errors())
            .anyMatch(e -> e.startsWith("STRUCTURE:") && e.contains("startEvent"));
    }

    // =========================================================
    // Rule 5: Must have endEvent
    // =========================================================

    @Test
    @DisplayName("Missing endEvent is rejected")
    void missingEndEvent_fails() {
        String bpmn = minimalBpmn("task").replace(
            "    <bpmn:endEvent id=\"End\" name=\"End\"><bpmn:incoming>Flow_end</bpmn:incoming></bpmn:endEvent>\n", "");
        assertThat(validator.validate(bpmn).isValid()).isFalse();
        assertThat(validator.validate(bpmn).errors()).anyMatch(e -> e.contains("endEvent"));
    }

    @Test
    @DisplayName("endEvent rule error starts with STRUCTURE")
    void endEventError_codeIsStructure() {
        String bpmn = minimalBpmn("task").replace(
            "    <bpmn:endEvent id=\"End\" name=\"End\"><bpmn:incoming>Flow_end</bpmn:incoming></bpmn:endEvent>\n", "");
        assertThat(validator.validate(bpmn).errors())
            .anyMatch(e -> e.startsWith("STRUCTURE:") && e.contains("endEvent"));
    }

    // =========================================================
    // Edge cases
    // =========================================================

    @Test
    @DisplayName("Null input returns error (not NPE)")
    void nullInput_returnsError() {
        ValidationResult result = validator.validate(null);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    @DisplayName("Blank input returns error (not NPE)")
    void blankInput_returnsError() {
        ValidationResult result = validator.validate("   ");
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    @DisplayName("Multiple violations are all reported")
    void multipleViolations_allReported() {
        // Both scriptTask + missing start event
        String bpmn = minimalBpmn("x")
            .replace("    <bpmn:startEvent id=\"Start\" name=\"Start\"><bpmn:outgoing>Flow_start</bpmn:outgoing></bpmn:startEvent>\n", "")
            .replace("</bpmn:process>", "<bpmn:scriptTask id=\"t1\" name=\"run\"/></bpmn:process>");
        ValidationResult result = validator.validate(bpmn);
        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(2);
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Minimal valid BPMN with 1 serviceTask + 1 userTask (required by Rule 10 BR-010).
     * Elements on single lines for easy string replacement in individual rule tests.
     */
    private static String minimalBpmn(String taskName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\""
            + " targetNamespace=\"http://uip.smartcity/workflows\" id=\"tw\">\n"
            + "  <bpmn:process id=\"Process_1\" isExecutable=\"true\">\n"
            + "    <bpmn:startEvent id=\"Start\" name=\"Start\"><bpmn:outgoing>Flow_start</bpmn:outgoing></bpmn:startEvent>\n"
            + "    <bpmn:serviceTask id=\"Task_1\" name=\"" + taskName + "\">"
            + "<bpmn:incoming>Flow_start</bpmn:incoming><bpmn:outgoing>Flow_mid</bpmn:outgoing>"
            + "</bpmn:serviceTask>\n"
            + "    <bpmn:userTask id=\"Review_1\" name=\"Operator Review\">"
            + "<bpmn:incoming>Flow_mid</bpmn:incoming><bpmn:outgoing>Flow_end</bpmn:outgoing>"
            + "</bpmn:userTask>\n"
            + "    <bpmn:endEvent id=\"End\" name=\"End\"><bpmn:incoming>Flow_end</bpmn:incoming></bpmn:endEvent>\n"
            + "  </bpmn:process>\n"
            + "</bpmn:definitions>";
    }

    /**
     * Minimal BPMN with {@code taskCount} service tasks plus 1 userTask (Rule 10 BR-010).
     * Node count: 2 events + taskCount serviceTasks + 1 userTask = taskCount + 3.
     */
    private static String bpmnWithTasks(int taskCount, String namePrefix) {
        String tasks = IntStream.rangeClosed(1, taskCount)
            .mapToObj(i -> "<bpmn:serviceTask id=\"Task_" + i + "\" name=\"" + namePrefix + " " + i + "\"/>")
            .collect(Collectors.joining("\n    "));
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              targetNamespace="http://uip.smartcity/workflows" id="t">
              <bpmn:process id="P" isExecutable="true">
                <bpmn:startEvent id="Start" name="Start"/>
                %s
                <bpmn:userTask id="Review_1" name="Operator Review"/>
                <bpmn:endEvent id="End" name="End"/>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(tasks);
    }

    private static long countNodes(String bpmn) {
        return new BpmnCustomValidator().validate(bpmn).errors().stream()
            .filter(e -> e.contains("found)"))
            .findFirst()
            .map(e -> Long.parseLong(e.replaceAll(".*\\((\\d+) found\\).*", "$1")))
            .orElseGet(() -> {
                // Count manually for valid BPMN
                long count = 0;
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<(bpmn:)?(task|serviceTask|userTask|startEvent|endEvent|exclusiveGateway|parallelGateway|inclusiveGateway)[\\s>/]")
                    .matcher(bpmn);
                while (m.find()) count++;
                return count;
            });
    }
}
