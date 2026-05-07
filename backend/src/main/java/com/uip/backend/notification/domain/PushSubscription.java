package com.uip.backend.notification.domain;

import com.uip.backend.tenant.hibernate.TenantEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_subscriptions")
@EntityListeners(TenantEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false)
    @Builder.Default
    private String platform = "web";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String endpoint;

    @Column(name = "p256dh")
    private String p256dh;

    @Column(name = "auth_key")
    private String authKey;

    @Column(name = "device_token")
    private String deviceToken;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
