package com.uip.backend.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VaultHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            String vaultEnabled = System.getenv("VAULT_ENABLED");
            if (!"true".equals(vaultEnabled)) {
                return Health.up().withDetail("mode", "env-vars (Vault not enabled)").build();
            }
            return Health.up().withDetail("mode", "vault-agent").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
