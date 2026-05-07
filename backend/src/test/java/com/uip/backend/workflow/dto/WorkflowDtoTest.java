package com.uip.backend.workflow.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Workflow DTOs")
class WorkflowDtoTest {

    @Nested
    @DisplayName("AIDecision")
    class AiDecisionTest {
        @Test
        void gettersAndSetters() {
            AIDecision d = new AIDecision();
            d.setDecision("ESCALATE");
            d.setReasoning("AQI critical");
            d.setConfidence(0.95);
            d.setRecommendedActions(List.of("notify", "evacuate"));
            d.setSeverity("HIGH");

            assertThat(d.getDecision()).isEqualTo("ESCALATE");
            assertThat(d.getReasoning()).isEqualTo("AQI critical");
            assertThat(d.getConfidence()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.001));
            assertThat(d.getRecommendedActions()).containsExactly("notify", "evacuate");
            assertThat(d.getSeverity()).isEqualTo("HIGH");
        }

        @Test
        void nullFields() {
            AIDecision d = new AIDecision();
            assertThat(d.getDecision()).isNull();
            assertThat(d.getRecommendedActions()).isNull();
        }
    }

    @Nested
    @DisplayName("ProcessDefinitionDto")
    class ProcessDefinitionDtoTest {
        @Test
        void builder() {
            ProcessDefinitionDto dto = ProcessDefinitionDto.builder()
                    .id("pd-1").key("aiC01").name("AQI Alert")
                    .tenantId("default").version(1)
                    .deploymentId("dep-1").suspended(false)
                    .build();

            assertThat(dto.getId()).isEqualTo("pd-1");
            assertThat(dto.getKey()).isEqualTo("aiC01");
            assertThat(dto.getName()).isEqualTo("AQI Alert");
            assertThat(dto.getTenantId()).isEqualTo("default");
            assertThat(dto.getVersion()).isEqualTo(1);
            assertThat(dto.isSuspended()).isFalse();
        }
    }

    @Nested
    @DisplayName("ProcessInstanceDto")
    class ProcessInstanceDtoTest {
        @Test
        void builder() {
            LocalDateTime now = LocalDateTime.now();
            ProcessInstanceDto dto = ProcessInstanceDto.builder()
                    .id("pi-1").processDefinitionId("pd-1")
                    .processDefinitionKey("aiC01").businessKey("BK-001")
                    .state("ACTIVE").startTime(now)
                    .variables(Map.of("aqi", 150))
                    .build();

            assertThat(dto.getId()).isEqualTo("pi-1");
            assertThat(dto.getProcessDefinitionKey()).isEqualTo("aiC01");
            assertThat(dto.getState()).isEqualTo("ACTIVE");
            assertThat(dto.getStartTime()).isEqualTo(now);
            assertThat(dto.getVariables()).containsEntry("aqi", 150);
        }

        @Test
        void nullVariables() {
            ProcessInstanceDto dto = ProcessInstanceDto.builder().build();
            assertThat(dto.getVariables()).isNull();
        }
    }

    @Nested
    @DisplayName("ClaudeApiResponse")
    class ClaudeApiResponseTest {
        @Test
        void contentParsing() {
            ClaudeApiResponse.Content content = new ClaudeApiResponse.Content();
            content.setType("text");
            content.setText("Decision: ESCALATE");

            assertThat(content.getType()).isEqualTo("text");
            assertThat(content.getText()).isEqualTo("Decision: ESCALATE");
        }

        @Test
        void responseWithContent() {
            ClaudeApiResponse response = new ClaudeApiResponse();
            response.setId("resp-1");
            response.setType("message");
            response.setRole("assistant");
            response.setModel("claude-3");
            response.setStopReason("end_turn");

            ClaudeApiResponse.Content c1 = new ClaudeApiResponse.Content();
            c1.setType("text");
            c1.setText("hello");
            response.setContent(List.of(c1));

            assertThat(response.getId()).isEqualTo("resp-1");
            assertThat(response.getRole()).isEqualTo("assistant");
            assertThat(response.getContent()).hasSize(1);
            assertThat(response.getContent().get(0).getText()).isEqualTo("hello");
            assertThat(response.getStopReason()).isEqualTo("end_turn");
        }
    }

    @Nested
    @DisplayName("ClaudeApiRequest")
    class ClaudeApiRequestTest {
        @Test
        void builder() {
            ClaudeApiRequest request = ClaudeApiRequest.builder()
                    .model("claude-3")
                    .maxTokens(1024)
                    .messages(List.of(
                            new ClaudeApiRequest.Message("user", "Analyze AQI")
                    ))
                    .build();

            assertThat(request.getModel()).isEqualTo("claude-3");
            assertThat(request.getMaxTokens()).isEqualTo(1024);
            assertThat(request.getMessages()).hasSize(1);
            assertThat(request.getMessages().get(0).getRole()).isEqualTo("user");
        }

        @Test
        void messageConstructor() {
            ClaudeApiRequest.Message msg = new ClaudeApiRequest.Message("system", "You are helpful");
            assertThat(msg.getRole()).isEqualTo("system");
            assertThat(msg.getContent()).isEqualTo("You are helpful");
        }
    }
}
