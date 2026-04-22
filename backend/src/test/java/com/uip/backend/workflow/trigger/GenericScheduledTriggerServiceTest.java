package com.uip.backend.workflow.trigger;

import com.uip.backend.esg.dto.EsgAnomalyDto;
import com.uip.backend.esg.service.EsgService;
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
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenericScheduledTriggerService")
class GenericScheduledTriggerServiceTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private WorkflowService workflowService;
    @Mock private VariableMapper variableMapper;
    @Mock private ApplicationContext applicationContext;
    @InjectMocks private GenericScheduledTriggerService service;

    private TriggerConfig buildScheduledConfig(String scenarioKey, String processKey, String queryBean, String dedupKey) {
        return TriggerConfig.builder()
            .scenarioKey(scenarioKey).processKey(processKey)
            .triggerType("SCHEDULED").scheduleQueryBean(queryBean)
            .variableMapping("{\"scenarioKey\":{\"static\":\"" + scenarioKey + "\"}}")
            .deduplicationKey(dedupKey).enabled(true).build();
    }

    @Test
    @DisplayName("Matching anomaly → starts process")
    void matchingAnomaly_startsProcess() {
        EsgService mockEsg = mock(EsgService.class);
        TriggerConfig config = buildScheduledConfig("aiM03", "aiM03", "esgService.detectUtilityAnomalies", null);
        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(applicationContext.getBean("esgService")).thenReturn(mockEsg);
        EsgAnomalyDto anomaly = new EsgAnomalyDto("energy", 150.0, 100.0, "BLD-001", null);
        when(mockEsg.detectUtilityAnomalies()).thenReturn(List.of(anomaly));
        when(variableMapper.map(anyString(), anyMap())).thenReturn(Map.of("scenarioKey", "aiM03"));
        when(workflowService.startProcess(eq("aiM03"), anyMap())).thenReturn(ProcessInstanceDto.builder().id("123").build());

        service.checkScheduledTriggers();

        verify(workflowService).startProcess(eq("aiM03"), anyMap());
    }

    @Test
    @DisplayName("No anomalies → no process started")
    void noAnomalies_noProcessStarted() {
        EsgService mockEsg = mock(EsgService.class);
        TriggerConfig config = buildScheduledConfig("aiM04", "aiM04", "esgService.detectEsgAnomalies", null);
        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(applicationContext.getBean("esgService")).thenReturn(mockEsg);
        when(mockEsg.detectEsgAnomalies()).thenReturn(List.of());

        service.checkScheduledTriggers();

        verify(workflowService, never()).startProcess(anyString(), anyMap());
    }

    @Test
    @DisplayName("Deduplication → skips if active process exists")
    void deduplication_skipsActiveProcess() {
        EsgService mockEsg = mock(EsgService.class);
        TriggerConfig config = buildScheduledConfig("aiM03", "aiM03", "esgService.detectUtilityAnomalies", "buildingId");
        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(applicationContext.getBean("esgService")).thenReturn(mockEsg);
        EsgAnomalyDto anomaly = new EsgAnomalyDto("energy", 150.0, 100.0, "BLD-001", null);
        when(mockEsg.detectUtilityAnomalies()).thenReturn(List.of(anomaly));
        when(variableMapper.extractValue(anyString(), anyMap())).thenReturn("BLD-001");
        when(workflowService.hasActiveProcess("aiM03", "buildingId", "BLD-001")).thenReturn(true);

        service.checkScheduledTriggers();

        verify(workflowService, never()).startProcess(anyString(), anyMap());
    }

    @Test
    @DisplayName("Reflection failure → logged, other configs continue")
    void reflectionFailure_loggedAndContinues() {
        TriggerConfig badConfig = buildScheduledConfig("bad", "bad", "nonExistent.nonMethod", null);
        TriggerConfig goodConfig = buildScheduledConfig("aiM04", "aiM04", "esgService.detectEsgAnomalies", null);
        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(badConfig, goodConfig));
        when(applicationContext.getBean("nonExistent")).thenThrow(new RuntimeException("No bean"));

        service.checkScheduledTriggers();

        // Bad config failed silently, good config attempted
        verify(workflowService, never()).startProcess(anyString(), anyMap());
    }
}
