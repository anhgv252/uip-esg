package com.uip.backend.auth.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * S6-M03 — Public endpoint returning Keycloak configuration for mobile clients.
 * No auth required — called before login to obtain PKCE parameters.
 */
@RestController
@RequestMapping("/api/v1/mobile/auth")
@Tag(name = "Mobile Auth", description = "Mobile authentication configuration")
public class MobileAuthConfigController {

    @Value("${keycloak.auth-server-url:http://localhost:8080/realms/uip}")
    private String issuer;

    @Value("${keycloak.resource:uip-mobile}")
    private String clientId;

    @Value("${keycloak.scope:openid profile email}")
    private String scopes;

    @Value("${app.mobile.redirect-uri:uipmobile://callback}")
    private String redirectUri;

    @GetMapping("/config")
    @Operation(summary = "Get Keycloak auth config for mobile PKCE login")
    public ResponseEntity<Map<String, String>> getConfig(
            @RequestParam(required = false, defaultValue = "hcm") String tenantId) {

        // Build issuer with tenant-specific realm if needed
        String tenantIssuer = issuer;
        if (!issuer.contains("/realms/")) {
            tenantIssuer = issuer + "/realms/" + tenantId;
        }

        return ResponseEntity.ok(Map.of(
                "issuer", tenantIssuer,
                "clientId", clientId,
                "scopes", scopes,
                "redirectUri", redirectUri
        ));
    }
}
