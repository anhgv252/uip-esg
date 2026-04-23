package com.uip.backend.workflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class TriggerConfigStartupValidator implements ApplicationRunner {

    private final TriggerConfigRepository configRepo;
    private final ApplicationContext applicationContext;

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
        String beanMethodRef = config.getScheduleQueryBean();

        if (beanMethodRef == null || beanMethodRef.isBlank()) {
            log.error("[TriggerConfigStartupValidator] INVALID config scenarioKey='{}' — scheduleQueryBean is null or blank. Fix in Admin Console.",
                    config.getScenarioKey());
            return false;
        }

        String[] parts = beanMethodRef.split("\\.");
        if (parts.length != 2) {
            log.error("[TriggerConfigStartupValidator] INVALID config scenarioKey='{}' — scheduleQueryBean '{}' must be 'beanName.methodName' format.",
                    config.getScenarioKey(), beanMethodRef);
            return false;
        }

        String beanName = parts[0];
        String methodName = parts[1];

        if (!applicationContext.containsBean(beanName)) {
            log.error("[TriggerConfigStartupValidator] INVALID config scenarioKey='{}' — bean '{}' method '{}' not found. Fix in Admin Console or DB.",
                    config.getScenarioKey(), beanName, methodName);
            return false;
        }

        try {
            applicationContext.getBean(beanName).getClass().getMethod(methodName);
        } catch (NoSuchMethodException e) {
            log.error("[TriggerConfigStartupValidator] INVALID config scenarioKey='{}' — bean '{}' method '{}' not found. Fix in Admin Console or DB.",
                    config.getScenarioKey(), beanName, methodName);
            return false;
        }

        return true;
    }
}
