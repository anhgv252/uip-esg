package com.uip.backend.workflow.trigger;

import com.uip.backend.esg.dto.EsgAnomalyDto;
import com.uip.backend.workflow.config.TriggerConfig;
import com.uip.backend.workflow.config.TriggerConfigRepository;
import com.uip.backend.workflow.config.VariableMapper;
import com.uip.backend.workflow.dto.ProcessInstanceDto;
import com.uip.backend.workflow.service.WorkflowService;
import com.uip.backend.workflow.trigger.strategy.ScheduledQueryStrategy;
import com.uip.backend.workflow.trigger.strategy.ScheduledQueryStrategyRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenericScheduledTriggerService")
class GenericScheduledTriggerServiceTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private WorkflowService workflowService;
    @Mock private VariableMapper variableMapper;
    @Mock private ScheduledQueryStrategyRegistry strategyRegistry;
    @InjectMocks private GenericScheduledTriggerService service;

    private TriggerConfig buildScheduledConfig(String scenarioKey, String processKey, String queryBeanRef, String dedupKey) {
        return TriggerConfig.builder()
            .scenarioKey(scenarioKey).processKey(processKey)
            .triggerType("SCHEDULED").scheduleQueryBean(queryBeanRef)
            .variableMapping("{\"scenarioKey\":{\"static\":\"" + scenarioKey + "\"}}")
            .deduplicationKey(dedupKey).enabled(true).build();
    }

    @Test
    @DisplayName("Matching anomaly → starts process")
    void matchingAnomaly_startsProcess() {
        TriggerConfig config = buildScheduledConfig("aiM03", "aiM03", "esgService.detectUtilityAnomalies", null);
        ScheduledQueryStrategy strategy = mock(ScheduledQueryStrategy.class);
        EsgAnomalyDto anomaly = new EsgAnomalyDto("energy", 150.0, 100.0, "BLD-001", null);

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(strategyRegistry.find("esgService.detectUtilityAnomalies")).thenReturn(Optional.of(strategy));
        doReturn(List.of(anomaly)).when(strategy).execute();
        when(variableMapper.map(anyString(), anyMap())).thenReturn(Map.of("scenarioKey", "aiM03"));
        when(workflowService.startProcess(eq("aiM03"), anyMap()))
            .thenReturn(ProcessInstanceDto.builder().id("123").build());

        service.checkScheduledTriggers();

        verify(workflowService).startProcess(eq("aiM03"), anyMap());
    }

    @Test
    @DisplayName("No anomalies → no process started")
    void noAnomalies_noProcessStarted() {
        TriggerConfig config = buildScheduledConfig("aiM04", "aiM04", "esgService.detectEsgAnomalies", null);
        ScheduledQueryStrategy strategy = mock(ScheduledQueryStrategy.class);

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(strategyRegistry.find("esgService.detectEsgAnomalies")).thenReturn(Optional.of(strategy));
        when(strategy.execute()).thenReturn(List.of());

        service.checkScheduledTriggers();

        verify(workflowService, never()).startProcess(anyString(), anyMap());
    }

    @Test
    @DisplayName("Deduplication → skips if active process exists")
    void deduplication_skipsActiveProcess() {
        TriggerConfig config = buildScheduledConfig("aiM03", "aiM03", "esgService.detectUtilityAnomalies", "buildingId");
        ScheduledQueryStrategy strategy = mock(ScheduledQueryStrategy.class);
        EsgAnomalyDto anomaly = new EsgAnomalyDto("energy", 150.0, 100.0, "BLD-001", null);

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(strategyRegistry.find("esgService.detectUtilityAnomalies")).thenReturn(Optional.of(strategy));
        doReturn(List.of(anomaly)).when(strategy).execute();
        when(variableMapper.extractValue(anyString(), anyMap())).thenReturn("BLD-001");
        when(workflowService.hasActiveProcess("aiM03", "buildingId", "BLD-001")).thenReturn(true);

        service.checkScheduledTriggers();

        verify(workflowService, never()).startProcess(anyString(), anyMap());
    }

    @Test
    @DisplayName("Unknown queryBeanRef → logged as error, other configs continue")
    void unknownQueryBeanRef_loggedAndContinues() {
        TriggerConfig badConfig = buildScheduledConfig("bad", "bad", "unknown.strategy", null);
        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(badConfig));
        when(strategyRegistry.find("unknown.strategy")).thenReturn(Optional.empty());

        service.checkScheduledTriggers();

        verify(workflowService, never()).startProcess(anyString(), anyMap());
    }
}
