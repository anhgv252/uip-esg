package com.uip.backend.notification.repository;

import com.uip.backend.notification.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    List<PushSubscription> findByUserIdAndActiveTrue(UUID userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findByTenantIdAndActiveTrue(String tenantId);

    long countByUserIdAndActiveTrue(UUID userId);
}
