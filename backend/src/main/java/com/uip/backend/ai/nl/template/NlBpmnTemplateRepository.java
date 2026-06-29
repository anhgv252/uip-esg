package com.uip.backend.ai.nl.template;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface NlBpmnTemplateRepository extends JpaRepository<NlBpmnTemplate, Long> {

    Optional<NlBpmnTemplate> findByIntentAndIsActiveTrue(String intent);

    List<NlBpmnTemplate> findAllByIsActiveTrueOrderByIntent();

    boolean existsByIntent(String intent);
}
