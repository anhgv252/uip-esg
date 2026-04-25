package com.uip.backend.workflow.controller;

import com.uip.backend.workflow.config.FilterEvaluator;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigAudit;
import com.uip.backend.workflow.config.TriggerConfigAuditService;
import com.uip.backend.workflow.config.TriggerConfigCacheInvalidator;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowConfigController")
class WorkflowConfigControllerTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private FilterEvaluator filterEvaluator;
    @Mock private VariableMapper variableMapper;
    @Mock private TriggerConfigAuditService auditService;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private Authentication auth;
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

        when(auth.getName()).thenReturn("admin");
        TriggerConfig result = controller.createConfig(input, auth);

        assertThat(result.getScenarioKey()).isEqualTo("newScenario");
        verify(configRepo).save(input);
    }

    @Test
    @DisplayName("DELETE /{id} — disables config")
    void disableConfig_setsEnabledFalse() {
        TriggerConfig config = buildConfig(1L, "aiC01");
        when(configRepo.findById(1L)).thenReturn(Optional.of(config));
        when(configRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.disableConfig(1L, auth);

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

    // ─── GAP-03: GET /{id}/audit ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /{id}/audit — config not found → 404")
    void getAuditHistory_configNotFound_returns404() {
        when(configRepo.existsById(99L)).thenReturn(false);

        var response = controller.getAuditHistory(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verifyNoInteractions(auditService);
    }

    @Test
    @DisplayName("GET /{id}/audit — config exists → 200 with audit history")
    void getAuditHistory_configExists_returnsHistory() {
        when(configRepo.existsById(1L)).thenReturn(true);
        TriggerConfigAudit entry = TriggerConfigAudit.builder()
            .id(10L).configId(1L).action("CREATE")
            .changedBy("admin").snapshot("{}").build();
        when(auditService.getHistory(1L)).thenReturn(List.of(entry));

        var response = controller.getAuditHistory(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var body = (List<TriggerConfigAudit>) response.getBody();
        assertThat(body).hasSize(1);
        assertThat(body.get(0).getAction()).isEqualTo("CREATE");
    }

    // ─── GAP-05: Kafka publish ───────────────────────────────────────────────

    @Test
    @DisplayName("POST / — publishes config-updated Kafka event after create")
    void createConfig_publishesKafkaEvent() {
        TriggerConfig input = buildConfig(null, "newScenario");
        when(configRepo.save(any())).thenAnswer(inv -> {
            TriggerConfig c = inv.getArgument(0);
            return TriggerConfig.builder().id(42L).scenarioKey(c.getScenarioKey())
                .processKey(c.getScenarioKey()).triggerType("KAFKA").enabled(true)
                .filterConditions("[{}]").variableMapping("{}").build();
        });
        when(auth.getName()).thenReturn("admin");

        controller.createConfig(input, auth);

        verify(kafkaTemplate).send(
            eq(TriggerConfigCacheInvalidator.TOPIC),
            argThat((Map<String, Object> m) ->
                Long.valueOf(42L).equals(m.get("configId")) && "CREATE".equals(m.get("action")))
        );
    }

    @Test
    @DisplayName("DELETE /{id} — publishes config-updated Kafka event after disable")
    void disableConfig_publishesKafkaEvent() {
        TriggerConfig config = buildConfig(1L, "aiC01");
        when(configRepo.findById(1L)).thenReturn(Optional.of(config));
        when(configRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.disableConfig(1L, auth);

        verify(kafkaTemplate).send(
            eq(TriggerConfigCacheInvalidator.TOPIC),
            argThat((Map<String, Object> m) ->
                Long.valueOf(1L).equals(m.get("configId")) && "DISABLE".equals(m.get("action")))
        );
    }

    // ─── GAP-11: Kafka failure non-fatal ────────────────────────────────────

    @Test
    @DisplayName("kafkaTemplate.send() throws → controller does not propagate exception")
    void createConfig_kafkaFails_doesNotThrow() {
        TriggerConfig input = buildConfig(null, "resilientScenario");
        when(configRepo.save(any())).thenAnswer(inv -> {
            TriggerConfig c = inv.getArgument(0);
            return TriggerConfig.builder().id(99L).scenarioKey(c.getScenarioKey())
                .processKey(c.getScenarioKey()).triggerType("KAFKA").enabled(true)
                .filterConditions("[{}]").variableMapping("{}").build();
        });
        when(auth.getName()).thenReturn("admin");
        when(kafkaTemplate.send(anyString(), any())).thenThrow(new RuntimeException("Kafka broker down"));

        assertThatCode(() -> controller.createConfig(input, auth)).doesNotThrowAnyException();
    }
}
