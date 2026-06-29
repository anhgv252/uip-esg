package com.uip.backend.ai.nl.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression suite for {@link BpmnCustomValidator} — M5-3 T02.
 *
 * <p>20 parameterized test cases covering all 10 rules (base + safety):
 * <ul>
 *   <li>10 valid BPMN scenarios that must PASS
 *   <li>10 invalid scenarios that must FAIL (5 for new Rules 6–10, 5 cross-rule combos)
 * </ul>
 *
 * <p>Design notes:
 * <ul>
 *   <li>All test BPMN is minimal — only what is needed to trigger the rule under test.
 *   <li>Valid cases include a {@code userTask} to satisfy Rule 10 (BR-010).
 *   <li>Error code prefix (SAFETY/COMPLEXITY/ORDERING/BR-010) is asserted to prevent
 *       message-wording drift from masking real regressions.
 * </ul>
 */
@DisplayName("BpmnCustomValidator — regression suite (Rules 1–10)")
class BpmnValidatorRegressionTest {

    private BpmnCustomValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BpmnCustomValidator();
    }

    // =========================================================
    // Valid scenarios (must PASS)
    // =========================================================

    static Stream<Arguments> validBpmnScenarios() {
        return Stream.of(
            Arguments.of(
                "VALID-01: air quality check with notification — correct order",
                bpmn(
                    serviceTask("Task_1", "Check AQI Sensor Reading") +
                    userTask("Task_2", "Operator Approve Alert") +
                    serviceTask("Task_3", "Send Citizen Notification")
                )
            ),
            Arguments.of(
                "VALID-02: flood response with gateway before pump",
                bpmn(
                    serviceTask("Task_1", "Read Water Level Sensor") +
                    exclusiveGateway("GW_1") +
                    userTask("Task_2", "Operator Review") +
                    serviceTask("Task_3", "Activate Pump Station")
                )
            ),
            Arguments.of(
                "VALID-03: fire response with evacuation — has exclusive gateway",
                bpmn(
                    serviceTask("Task_1", "Detect Fire Sensor") +
                    exclusiveGateway("GW_1") +
                    userTask("Task_2", "Operator Decision") +
                    serviceTask("Task_3", "Activate Sprinkler System") +
                    serviceTask("Task_4", "Initiate Evacuation")
                )
            ),
            Arguments.of(
                "VALID-04: minimal workflow with userTask — satisfies BR-010",
                bpmn(
                    serviceTask("Task_1", "Read Sensor Data") +
                    userTask("Task_2", "Operator Approval")
                )
            ),
            Arguments.of(
                "VALID-05: parallel workflow under 3 parallel gateways",
                bpmn(
                    parallelGateway("PGW_1") +
                    serviceTask("Task_1", "Process Stream A") +
                    serviceTask("Task_2", "Process Stream B") +
                    parallelGateway("PGW_2") +
                    userTask("Task_3", "Operator Review")
                )
            ),
            Arguments.of(
                "VALID-06: bơm (Vietnamese) pump with gateway — Rule 6 satisfied",
                bpmn(
                    serviceTask("Task_1", "Đọc cảm biến mực nước") +
                    exclusiveGateway("GW_1") +
                    userTask("Task_2", "Phê duyệt vận hành") +
                    serviceTask("Task_3", "Kích hoạt bơm thoát nước")
                )
            ),
            Arguments.of(
                "VALID-07: fire keyword but no evacuation — Rule 7 does not trigger",
                bpmn(
                    serviceTask("Task_1", "Detect fire alarm") +
                    userTask("Task_2", "Operator review fire alert") +
                    serviceTask("Task_3", "Notify fire department")
                )
            ),
            Arguments.of(
                "VALID-08: evacuation keyword but no sprinkler/fire — Rule 7 does not trigger",
                bpmn(
                    serviceTask("Task_1", "Assess structural damage") +
                    userTask("Task_2", "Operator approve evacuation") +
                    serviceTask("Task_3", "Execute evacuation plan")
                )
            ),
            Arguments.of(
                "VALID-09: exactly 3 parallel gateways — boundary, must PASS",
                bpmn(
                    parallelGateway("PGW_1") +
                    serviceTask("Task_1", "Stream A") +
                    serviceTask("Task_2", "Stream B") +
                    parallelGateway("PGW_2") +
                    serviceTask("Task_3", "Stream C") +
                    parallelGateway("PGW_3") +
                    userTask("Task_4", "Merge and Review")
                )
            ),
            Arguments.of(
                "VALID-10: notification task is third task — Rule 8 does not trigger",
                bpmn(
                    serviceTask("Task_1", "Assess air quality index") +
                    serviceTask("Task_2", "Check historical baseline") +
                    userTask("Task_3", "Operator Approve") +
                    serviceTask("Task_4", "Send citizen notification alert")
                )
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validBpmnScenarios")
    @DisplayName("Valid BPMN scenarios must pass all rules")
    void validScenarios_mustPass(String description, String bpmnXml) {
        ValidationResult result = validator.validate(bpmnXml);
        assertThat(result.isValid())
            .as("Expected VALID but got errors for [%s]: %s", description, result.errors())
            .isTrue();
        assertThat(result.errors()).isEmpty();
    }

    // =========================================================
    // Invalid scenarios (must FAIL)
    // =========================================================

    static Stream<Arguments> invalidBpmnScenarios() {
        return Stream.of(
            // Rule 6: pump without gateway
            Arguments.of(
                "INVALID-01: pump task without any gateway — Rule 6 SAFETY",
                bpmn(
                    serviceTask("Task_1", "Read Water Level") +
                    userTask("Task_2", "Operator Review") +
                    serviceTask("Task_3", "Activate Pump Station")
                ),
                "SAFETY:",
                "pump"
            ),
            // Rule 6: Vietnamese bơm without gateway
            Arguments.of(
                "INVALID-02: bơm (Vietnamese) without gateway — Rule 6 SAFETY",
                bpmn(
                    serviceTask("Task_1", "Read sensor") +
                    userTask("Task_2", "Review") +
                    serviceTask("Task_3", "Kích hoạt bơm thoát nước")
                ),
                "SAFETY:",
                "pump"
            ),
            // Rule 7: sprinkler + evacuation without exclusive gateway
            Arguments.of(
                "INVALID-03: sprinkler and evacuation without decision gateway — Rule 7 SAFETY",
                bpmn(
                    serviceTask("Task_1", "Detect fire") +
                    userTask("Task_2", "Operator review") +
                    serviceTask("Task_3", "Activate sprinkler system") +
                    serviceTask("Task_4", "Start evacuation")
                ),
                "SAFETY:",
                "sprinkler"
            ),
            // Rule 7: fire + evacuation without exclusive gateway
            Arguments.of(
                "INVALID-04: fire response with evacuation without decision gateway — Rule 7 SAFETY",
                bpmn(
                    serviceTask("Task_1", "Detect fire sensor trigger") +
                    userTask("Task_2", "Operator decision") +
                    serviceTask("Task_3", "Execute evacuation procedure")
                ),
                "SAFETY:",
                "sprinkler"
            ),
            // Rule 8: notification as first task
            Arguments.of(
                "INVALID-05: citizen notification is first task — Rule 8 ORDERING",
                bpmn(
                    serviceTask("Task_1", "Send citizen notification") +
                    serviceTask("Task_2", "Check AQI baseline") +
                    userTask("Task_3", "Operator review")
                ),
                "ORDERING:",
                "notification"
            ),
            // Rule 9: 4 parallel gateways exceeds max
            Arguments.of(
                "INVALID-06: 4 parallel gateways exceeds complexity limit — Rule 9 COMPLEXITY",
                bpmn(
                    parallelGateway("PGW_1") +
                    serviceTask("Task_1", "Path A") +
                    parallelGateway("PGW_2") +
                    serviceTask("Task_2", "Path B") +
                    parallelGateway("PGW_3") +
                    serviceTask("Task_3", "Path C") +
                    parallelGateway("PGW_4") +
                    userTask("Task_4", "Review")
                ),
                "COMPLEXITY:",
                "parallel"
            ),
            // Rule 10: missing userTask
            Arguments.of(
                "INVALID-07: no userTask present — Rule 10 BR-010",
                bpmn(
                    serviceTask("Task_1", "Assess sensor data") +
                    serviceTask("Task_2", "Generate alert") +
                    serviceTask("Task_3", "Execute response")
                ),
                "BR-010:",
                "usertask"
            ),
            // Rule 10: missing userTask — minimal BPMN
            Arguments.of(
                "INVALID-08: minimal BPMN with only serviceTask — Rule 10 BR-010",
                bpmn(serviceTask("Task_1", "Automated Response")),
                "BR-010:",
                "usertask"
            ),
            // Combined: pump without gateway AND missing userTask (two violations)
            Arguments.of(
                "INVALID-09: pump without gateway AND no userTask — Rules 6 + 10",
                bpmn(
                    serviceTask("Task_1", "Detect flooding") +
                    serviceTask("Task_2", "Activate pump immediately")
                ),
                "SAFETY:",
                "pump"
            ),
            // Combined: sprinkler+evacuation without gateway AND missing userTask
            Arguments.of(
                "INVALID-10: sprinkler+evacuation without gateway AND no userTask — Rules 7 + 10",
                bpmn(
                    serviceTask("Task_1", "Fire sensor trigger") +
                    serviceTask("Task_2", "Activate sprinkler") +
                    serviceTask("Task_3", "Execute evacuation now")
                ),
                "SAFETY:",
                "sprinkler"
            )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBpmnScenarios")
    @DisplayName("Invalid BPMN scenarios must fail with expected error code")
    void invalidScenarios_mustFailWithExpectedCode(
            String description, String bpmnXml, String expectedErrorPrefix, String expectedKeyword) {
        ValidationResult result = validator.validate(bpmnXml);
        assertThat(result.isValid())
            .as("Expected INVALID but got valid for [%s]", description)
            .isFalse();
        assertThat(result.errors())
            .as("Expected error with prefix [%s] containing [%s] for [%s]",
                expectedErrorPrefix, expectedKeyword, description)
            .anyMatch(e -> e.startsWith(expectedErrorPrefix) && e.toLowerCase().contains(expectedKeyword));
    }

    // =========================================================
    // BPMN XML builder helpers
    // =========================================================

    /** Wraps body elements in a minimal valid BPMN definitions + process envelope. */
    private static String bpmn(String bodyElements) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              targetNamespace="http://uip.smartcity/workflows" id="reg">
              <bpmn:process id="P_1" isExecutable="true">
                <bpmn:startEvent id="Start" name="Start"/>
            %s
                <bpmn:endEvent id="End" name="End"/>
              </bpmn:process>
            </bpmn:definitions>
            """.formatted(bodyElements);
    }

    private static String serviceTask(String id, String name) {
        return "    <bpmn:serviceTask id=\"" + id + "\" name=\"" + name + "\"/>\n";
    }

    private static String userTask(String id, String name) {
        return "    <bpmn:userTask id=\"" + id + "\" name=\"" + name + "\"/>\n";
    }

    private static String exclusiveGateway(String id) {
        return "    <bpmn:exclusiveGateway id=\"" + id + "\" name=\"Decision\"/>\n";
    }

    private static String parallelGateway(String id) {
        return "    <bpmn:parallelGateway id=\"" + id + "\" name=\"Split\"/>\n";
    }
}
