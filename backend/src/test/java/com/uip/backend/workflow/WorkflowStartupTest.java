package com.uip.backend.workflow;

import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that all 7 BPMN process definitions are auto-deployed on startup.
 * This test requires a running Camunda engine (embedded via Spring Boot starter).
 * Run with Testcontainers profile or local Postgres.
 */
@SpringBootTest
@DisplayName("Workflow Startup — 7 BPMN processes deployed")
class WorkflowStartupTest {

    @Autowired
    private RepositoryService repositoryService;

    @Test
    @DisplayName("All seven AI scenario processes are deployed")
    void allSevenProcessesDeployed() {
        List<String> deployedKeys = repositoryService
            .createProcessDefinitionQuery()
            .latestVersion()
            .list()
            .stream()
            .map(ProcessDefinition::getKey)
            .toList();

        assertThat(deployedKeys).containsExactlyInAnyOrder(
            "aiC01_aqiCitizenAlert",
            "aiC02_citizenServiceRequest",
            "aiC03_floodEmergencyEvacuation",
            "aiM01_floodResponseCoordination",
            "aiM02_aqiTrafficControl",
            "aiM03_utilityIncidentCoordination",
            "aiM04_esgAnomalyInvestigation"
        );
    }
}
