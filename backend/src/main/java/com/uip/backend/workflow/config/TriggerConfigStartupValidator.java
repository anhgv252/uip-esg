package com.uip.backend.workflow.config;

import com.uip.backend.workflow.trigger.strategy.ScheduledQueryStrategyRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class TriggerConfigStartupValidator implements ApplicationRunner {

    private final TriggerConfigRepository configRepo;
    private final ScheduledQueryStrategyRegistry strategyRegistry;

    @Override
    public void run(ApplicationArguments args) {
        List<TriggerConfig> scheduledConfigs = configRepo.findByTriggerTypeAndEnabled("SCHEDULED", true);
        AtomicInteger invalidCount = new AtomicInteger(0);

        for (TriggerConfig config : scheduledConfigs) {
            if (!validateScheduledConfig(config)) {
                invalidCount.incrementAndGet();
            }
        }

        log.info("[TriggerConfigStartupValidator] Validated {} SCHEDULED configs — {} invalid",
                scheduledConfigs.size(), invalidCount.get());
    }

    private boolean validateScheduledConfig(TriggerConfig config) {
        String queryBeanRef = config.getScheduleQueryBean();

        if (queryBeanRef == null || queryBeanRef.isBlank()) {
            log.error("[TriggerConfigStartupValidator] INVALID config scenarioKey='{}' — scheduleQueryBean is null or blank. Fix in Admin Console.",
                    config.getScenarioKey());
            return false;
        }

        if (!strategyRegistry.contains(queryBeanRef)) {
            log.error("[TriggerConfigStartupValidator] INVALID config scenarioKey='{}' — no ScheduledQueryStrategy registered for '{}'. Add a @Component implementing ScheduledQueryStrategy with queryBeanRef='{}'.",
                    config.getScenarioKey(), queryBeanRef, queryBeanRef);
            return false;
        }

        return true;
    }
}
