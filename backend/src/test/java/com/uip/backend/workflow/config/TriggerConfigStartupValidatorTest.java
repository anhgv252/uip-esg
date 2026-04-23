package com.uip.backend.workflow.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TriggerConfigStartupValidator")
class TriggerConfigStartupValidatorTest {

    @Mock private TriggerConfigRepository configRepo;
    @Mock private ApplicationContext applicationContext;

    private TriggerConfigStartupValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TriggerConfigStartupValidator(configRepo, applicationContext);
    }

    @Test
    @DisplayName("Valid config — no exception thrown")
    void validConfig_doesNotThrow() throws Exception {
        TriggerConfig config = TriggerConfig.builder()
                .scenarioKey("aiM03_utilityIncidentCoordination")
                .triggerType("SCHEDULED")
                .scheduleQueryBean("esgService.detectUtilityAnomalies")
                .enabled(true)
                .build();

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(applicationContext.containsBean("esgService")).thenReturn(true);

        // Stub the bean to have the method
        Object mockBean = new Object() {
            public List<?> detectUtilityAnomalies() { return List.of(); }
        };
        when(applicationContext.getBean("esgService")).thenReturn(mockBean);

        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    @DisplayName("Missing bean — no exception thrown (only logs error)")
    void missingBean_doesNotThrow() {
        TriggerConfig config = TriggerConfig.builder()
                .scenarioKey("aiM03_utilityIncidentCoordination")
                .triggerType("SCHEDULED")
                .scheduleQueryBean("unknownBean.someMethod")
                .enabled(true)
                .build();

        when(configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true)).thenReturn(List.of(config));
        when(applicationContext.containsBean("unknownBean")).thenReturn(false);

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
