package com.uip.backend.workflow.trigger.strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ScheduledQueryStrategyRegistry {

    private final Map<String, ScheduledQueryStrategy> strategies;

    public ScheduledQueryStrategyRegistry(List<ScheduledQueryStrategy> strategies) {
        this.strategies = strategies.stream()
            .collect(Collectors.toUnmodifiableMap(
                ScheduledQueryStrategy::queryBeanRef,
                s -> s
            ));
    }

    public Optional<ScheduledQueryStrategy> find(String queryBeanRef) {
        return Optional.ofNullable(strategies.get(queryBeanRef));
    }

    public boolean contains(String queryBeanRef) {
        return strategies.containsKey(queryBeanRef);
    }
}
