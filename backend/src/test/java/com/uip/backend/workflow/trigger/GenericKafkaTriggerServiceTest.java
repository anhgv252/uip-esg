package com.uip.backend.workflow.trigger;

import com.uip.backend.common.exception.WorkflowNotFoundException;
import com.uip.backend.workflow.config.FilterEvaluator;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenericKafkaTriggerService")
class GenericKafkaTriggerServiceTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private WorkflowService workflowService;
    @Mock private FilterEvaluator filterEvaluator;
    @Mock private VariableMapper variableMapper;
    @Mock private Acknowledgment ack;
    @InjectMocks private GenericKafkaTriggerService service;

    private TriggerConfig buildConfig(String scenarioKey, String processKey, String filterJson, String mappingJson, String dedupKey) {
        return TriggerConfig.builder()
            .scenarioKey(scenarioKey).processKey(processKey)
            .triggerType("KAFKA").kafkaTopic("UIP.flink.alert.detected.v1")
            .filterConditions(filterJson).variableMapping(mappingJson)
            .deduplicationKey(dedupKey).enabled(true).build();
    }

    @Test
    @DisplayName("Matching config → starts process")
    void matchingConfig_startsProcess() throws Exception {
        TriggerConfig config = buildConfig("aiC01", "aiC01", "[{}]", "{}", null);
        when(configRepo.findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", "UIP.flink.alert.detected.v1", true))
            .thenReturn(List.of(config));
        when(filterEvaluator.matches(anyString(), anyMap())).thenReturn(true);
        when(variableMapper.map(anyString(), anyMap())).thenReturn(Map.of("scenarioKey", "aiC01"));
        when(workflowService.startProcess(eq("aiC01"), anyMap())).thenReturn(ProcessInstanceDto.builder().id("123").build());

        service.onKafkaEvent(Map.of("module", "ENVIRONMENT", "value", 175.0), ack);

        verify(workflowService).startProcess(eq("aiC01"), anyMap());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("No matching filter → skips process")
    void noMatch_skipsProcess() throws Exception {
        TriggerConfig config = buildConfig("aiC01", "aiC01", "[{}]", "{}", null);
        when(configRepo.findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", "UIP.flink.alert.detected.v1", true))
            .thenReturn(List.of(config));
        when(filterEvaluator.matches(anyString(), anyMap())).thenReturn(false);

        service.onKafkaEvent(Map.of("module", "TRAFFIC", "value", 50.0), ack);

        verify(workflowService, never()).startProcess(anyString(), anyMap());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Deduplication → skips if active process exists")
    void deduplication_skipsActiveProcess() throws Exception {
        TriggerConfig config = buildConfig("aiC01", "aiC01", "[{}]", "{}", "sensorId");
        when(configRepo.findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", "UIP.flink.alert.detected.v1", true))
            .thenReturn(List.of(config));
        when(filterEvaluator.matches(anyString(), anyMap())).thenReturn(true);
        when(variableMapper.extractValue(eq("sensorId"), anyMap())).thenReturn("AQI-001");
        when(workflowService.hasActiveProcess("aiC01", "sensorId", "AQI-001")).thenReturn(true);

        service.onKafkaEvent(Map.of("sensorId", "AQI-001", "value", 175.0), ack);

        verify(workflowService, never()).startProcess(anyString(), anyMap());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Multiple configs match → starts multiple processes")
    void multipleConfigs_startsMultiple() throws Exception {
        TriggerConfig c1 = buildConfig("aiC01", "aiC01", "[{}]", "{}", null);
        TriggerConfig c2 = buildConfig("aiM02", "aiM02", "[{}]", "{}", null);
        when(configRepo.findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", "UIP.flink.alert.detected.v1", true))
            .thenReturn(List.of(c1, c2));
        when(filterEvaluator.matches(anyString(), anyMap())).thenReturn(true);
        when(variableMapper.map(anyString(), anyMap())).thenReturn(Map.of());
        when(workflowService.startProcess(anyString(), anyMap())).thenReturn(ProcessInstanceDto.builder().id("123").build());

        service.onKafkaEvent(Map.of("module", "ENVIRONMENT", "value", 175.0), ack);

        verify(workflowService, times(2)).startProcess(anyString(), anyMap());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("WorkflowNotFoundException → acks and continues")
    void processNotFound_acks() throws Exception {
        TriggerConfig config = buildConfig("missing", "missing", "[{}]", "{}", null);
        when(configRepo.findByTriggerTypeAndKafkaTopicAndEnabled("KAFKA", "UIP.flink.alert.detected.v1", true))
            .thenReturn(List.of(config));
        when(filterEvaluator.matches(anyString(), anyMap())).thenReturn(true);
        when(variableMapper.map(anyString(), anyMap())).thenReturn(Map.of());
        when(workflowService.startProcess(eq("missing"), anyMap()))
            .thenThrow(new WorkflowNotFoundException("Process not found: missing"));

        service.onKafkaEvent(Map.of("value", 175.0), ack);

        verify(ack).acknowledge();
    }
}
