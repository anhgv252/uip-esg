package com.uip.backend.workflow.delegate.management;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EsgAnomalyDelegate")
class EsgAnomalyDelegateTest {

    @Mock private DelegateExecution execution;
    @InjectMocks private EsgAnomalyDelegate delegate;

    @Test
    @DisplayName("aiDecision chứa SPIKE → anomalyCategory = SUDDEN_SPIKE")
    void execute_spikeDecision_suddenSpikeCategory() throws Exception {
        when(execution.getVariable("aiDecision")).thenReturn("INVESTIGATE_SPIKE");
        when(execution.getVariable("metricType")).thenReturn("energy");

        delegate.execute(execution);

        verify(execution).setVariable("anomalyCategory", "SUDDEN_SPIKE");
    }

    @Test
    @DisplayName("metricType energy, aiDecision không match → anomalyCategory = ENERGY_ANOMALY")
    void execute_energyNoMatch_energyAnomaly() throws Exception {
        when(execution.getVariable("aiDecision")).thenReturn("INVESTIGATE");
        when(execution.getVariable("metricType")).thenReturn("energy_consumption");

        delegate.execute(execution);

        verify(execution).setVariable("anomalyCategory", "ENERGY_ANOMALY");
    }

    @Test
    @DisplayName("investigationReportId là UUID")
    void execute_reportId_isUuid() throws Exception {
        delegate.execute(execution);

        ArgumentCaptor<String> reportIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("investigationReportId"), reportIdCaptor.capture());
        assertThat(reportIdCaptor.getValue()).hasSize(36);
    }

    @Test
    @DisplayName("investigationSummary chứa đủ thông tin")
    void execute_summary_containsAllInfo() throws Exception {
        when(execution.getVariable("metricType")).thenReturn("carbon");
        when(execution.getVariable("currentValue")).thenReturn(120.5);
        when(execution.getVariable("historicalAvg")).thenReturn(80.0);
        when(execution.getVariable("period")).thenReturn("2026-Q1");
        when(execution.getVariable("aiReasoning")).thenReturn("Carbon spike detected");

        delegate.execute(execution);

        ArgumentCaptor<String> summaryCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("investigationSummary"), summaryCaptor.capture());
        assertThat(summaryCaptor.getValue())
                .contains("carbon").contains("120.5").contains("80.0").contains("2026-Q1");
    }
}
