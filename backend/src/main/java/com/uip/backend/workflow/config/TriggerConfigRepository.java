package com.uip.backend.workflow.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TriggerConfigRepository extends JpaRepository<TriggerConfig, Long> {
    List<TriggerConfig> findByTriggerTypeAndEnabled(String triggerType, Boolean enabled);
    List<TriggerConfig> findByTriggerTypeAndKafkaTopicAndEnabled(String triggerType, String kafkaTopic, Boolean enabled);
    Optional<TriggerConfig> findByScenarioKey(String scenarioKey);
    List<TriggerConfig> findByEnabled(Boolean enabled);
}
