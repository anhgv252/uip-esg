package com.uip.backend.alert.repository;

import com.uip.backend.alert.domain.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRuleRepository extends JpaRepository<AlertRule, UUID> {

    List<AlertRule> findByActiveTrueOrderByModuleAsc();

    List<AlertRule> findByModuleAndActiveTrue(String module);
}
