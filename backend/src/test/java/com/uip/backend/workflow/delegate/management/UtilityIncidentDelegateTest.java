package com.uip.backend.workflow.delegate.management;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UtilityIncidentDelegate")
class UtilityIncidentDelegateTest {

    @Mock private DelegateExecution execution;
    @InjectMocks private UtilityIncidentDelegate delegate;

    @Test
    @DisplayName("metricType electricity → assignedTeam = ELECTRICAL_TEAM")
    void execute_electricity_electricalTeam() throws Exception {
        when(execution.getVariable("metricType")).thenReturn("electricity");

        delegate.execute(execution);

        verify(execution).setVariable("assignedTeam", "ELECTRICAL_TEAM");
    }

    @Test
    @DisplayName("metricType water → assignedTeam = PLUMBING_TEAM")
    void execute_water_plumbingTeam() throws Exception {
        when(execution.getVariable("metricType")).thenReturn("water");

        delegate.execute(execution);

        verify(execution).setVariable("assignedTeam", "PLUMBING_TEAM");
    }

    @Test
    @DisplayName("metricType không match → assignedTeam = GENERAL_MAINTENANCE")
    void execute_unknownMetric_generalMaintenance() throws Exception {
        when(execution.getVariable("metricType")).thenReturn("UNKNOWN_METRIC");

        delegate.execute(execution);

        verify(execution).setVariable("assignedTeam", "GENERAL_MAINTENANCE");
    }

    @Test
    @DisplayName("maintenanceTicketId là UUID và diagnosisReport chứa buildingId")
    void execute_ticketIdUuid_reportContainsBuildingId() throws Exception {
        when(execution.getVariable("buildingId")).thenReturn("BLDG-001");
        when(execution.getVariable("metricType")).thenReturn("electricity");
        when(execution.getVariable("anomalyValue")).thenReturn(450.0);

        delegate.execute(execution);

        ArgumentCaptor<String> ticketCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("maintenanceTicketId"), ticketCaptor.capture());
        assertThat(ticketCaptor.getValue()).hasSize(36);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        verify(execution).setVariable(eq("diagnosisReport"), reportCaptor.capture());
        assertThat(reportCaptor.getValue()).contains("BLDG-001");
    }
}
