package com.uip.backend.workflow.controller;

import com.uip.backend.workflow.config.FilterEvaluator;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowConfigController")
class WorkflowConfigControllerTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private FilterEvaluator filterEvaluator;
    @Mock private VariableMapper variableMapper;
    @InjectMocks private WorkflowConfigController controller;

    private TriggerConfig buildConfig(Long id, String key) {
        return TriggerConfig.builder()
            .id(id).scenarioKey(key).processKey(key)
            .triggerType("KAFKA").enabled(true)
            .filterConditions("[{}]")
            .variableMapping("{\"scenarioKey\":{\"static\":\"" + key + "\"}}")
            .build();
    }

    @Test
    @DisplayName("GET / — returns all configs")
    void listConfigs_returnsAll() {
        when(configRepo.findAll()).thenReturn(List.of(buildConfig(1L, "aiC01"), buildConfig(2L, "aiC02")));

        var result = controller.listConfigs();

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("GET /{id} — found")
    void getConfig_found() {
        when(configRepo.findById(1L)).thenReturn(Optional.of(buildConfig(1L, "aiC01")));

        var response = controller.getConfig(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getScenarioKey()).isEqualTo("aiC01");
    }

    @Test
    @DisplayName("GET /{id} — not found")
    void getConfig_notFound() {
        when(configRepo.findById(99L)).thenReturn(Optional.empty());

        var response = controller.getConfig(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("POST / — creates config")
    void createConfig_saves() {
        TriggerConfig input = buildConfig(null, "newScenario");
        when(configRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TriggerConfig result = controller.createConfig(input);

        assertThat(result.getScenarioKey()).isEqualTo("newScenario");
        verify(configRepo).save(input);
    }

    @Test
    @DisplayName("DELETE /{id} — disables config")
    void disableConfig_setsEnabledFalse() {
        TriggerConfig config = buildConfig(1L, "aiC01");
        when(configRepo.findById(1L)).thenReturn(Optional.of(config));
        when(configRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.disableConfig(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(configRepo).save(argThat(c -> !c.getEnabled()));
    }

    @Test
    @DisplayName("POST /{id}/test — returns filter match + mapped vars")
    void testTrigger_dryRun() {
        TriggerConfig config = buildConfig(1L, "aiC01");
        when(configRepo.findById(1L)).thenReturn(Optional.of(config));
        when(filterEvaluator.matches(anyString(), anyMap())).thenReturn(true);
        when(variableMapper.map(anyString(), anyMap())).thenReturn(Map.of("sensorId", "AQI-001"));

        var response = controller.testTrigger(1L, Map.of("module", "ENVIRONMENT", "value", 175.0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("filterMatch", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) response.getBody().get("mappedVariables");
        assertThat(vars).containsEntry("sensorId", "AQI-001");
    }
}
