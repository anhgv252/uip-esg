package com.uip.backend.workflow.trigger;

import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenericRestTriggerController")
class GenericRestTriggerControllerTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private WorkflowService workflowService;
    @Mock private VariableMapper variableMapper;
    @Mock private UserDetails userDetails;
    @InjectMocks private GenericRestTriggerController controller;

    private TriggerConfig buildRestConfig(String scenarioKey, boolean enabled) {
        return TriggerConfig.builder()
            .scenarioKey(scenarioKey).processKey(scenarioKey)
            .triggerType("REST").enabled(enabled)
            .variableMapping("{\"scenarioKey\":{\"static\":\"" + scenarioKey + "\"}}")
            .build();
    }

    @Test
    @DisplayName("Valid REST config → process started, returns 202")
    void validConfig_returns202() {
        TriggerConfig config = buildRestConfig("aiC02_citizenServiceRequest", true);
        when(configRepo.findByScenarioKey("aiC02_citizenServiceRequest")).thenReturn(Optional.of(config));
        when(userDetails.getUsername()).thenReturn("citizen-001");
        when(variableMapper.map(anyString(), anyMap())).thenReturn(Map.of("scenarioKey", "aiC02_citizenServiceRequest"));
        when(workflowService.startProcess(eq("aiC02_citizenServiceRequest"), anyMap()))
            .thenReturn(ProcessInstanceDto.builder().id("proc-123").build());

        var response = controller.triggerWorkflow("aiC02_citizenServiceRequest", Map.of("requestType", "ENVIRONMENT"), userDetails);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("processInstanceId", "proc-123");
    }

    @Test
    @DisplayName("Unknown scenario key → throws IllegalArgumentException")
    void unknownScenario_throwsException() {
        when(configRepo.findByScenarioKey("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.triggerWorkflow("unknown", Map.of(), userDetails))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No enabled REST trigger");
    }

    @Test
    @DisplayName("Disabled config → throws IllegalArgumentException")
    void disabledConfig_throwsException() {
        TriggerConfig config = buildRestConfig("aiC02_citizenServiceRequest", false);
        when(configRepo.findByScenarioKey("aiC02_citizenServiceRequest")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> controller.triggerWorkflow("aiC02_citizenServiceRequest", Map.of(), userDetails))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Non-REST config → throws IllegalArgumentException")
    void nonRestConfig_throwsException() {
        TriggerConfig config = TriggerConfig.builder()
            .scenarioKey("aiC01").triggerType("KAFKA").enabled(true).build();
        when(configRepo.findByScenarioKey("aiC01")).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> controller.triggerWorkflow("aiC01", Map.of(), userDetails))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
