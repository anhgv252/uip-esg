package com.uip.backend.workflow.config;

import com.uip.backend.workflow.trigger.strategy.ScheduledQueryStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerConfigStartupValidator")
class TriggerConfigStartupValidatorTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private ScheduledQueryStrategyRegistry strategyRegistry;

    private TriggerConfigStartupValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TriggerConfigStartupValidator(configRepo, strategyRegistry);
    }

    @Test
    @DisplayName("Valid config — strategy registered — no exception thrown")
    void validConfig_doesNotThrow() {
        TriggerConfig config = TriggerConfig.builder()
                .scenarioKey("aiM03_utilityIncidentCoordination")
                .triggerType("SCHEDULED")
                .scheduleQueryBean("esgService.detectUtilityAnomalies")
                .enabled(true)
                .build();

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(strategyRegistry.contains("esgService.detectUtilityAnomalies")).thenReturn(true);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("Unknown queryBeanRef — no strategy registered — no exception (only logs error)")
    void unknownQueryBeanRef_doesNotThrow() {
        TriggerConfig config = TriggerConfig.builder()
                .scenarioKey("aiM03_utilityIncidentCoordination")
                .triggerType("SCHEDULED")
                .scheduleQueryBean("unknownService.someMethod")
                .enabled(true)
                .build();

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(strategyRegistry.contains("unknownService.someMethod")).thenReturn(false);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("Null scheduleQueryBean — no exception thrown (only logs error)")
    void nullQueryBean_doesNotThrow() {
        TriggerConfig config = TriggerConfig.builder()
                .scenarioKey("aiM04_esgAnomalyInvestigation")
                .triggerType("SCHEDULED")
                .scheduleQueryBean(null)
                .enabled(true)
                .build();

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("No SCHEDULED configs — runs without error")
    void noScheduledConfigs_doesNotThrow() {
        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of());

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }
}
