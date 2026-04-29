package com.uip.backend.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SecretRotationListener {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String vaultEnabled = System.getenv("VAULT_ENABLED");
        if ("true".equals(vaultEnabled)) {
            log.info("[VAULT] Secret rotation listener active — Vault-managed secrets will refresh via agent");
        } else {
            log.info("[VAULT] Using environment variables for secrets — rotation requires restart");
        }
    }
}
