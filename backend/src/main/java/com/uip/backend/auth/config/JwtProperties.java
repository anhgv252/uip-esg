package com.uip.backend.auth.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class JwtProperties {

    @Value("${security.jwt.secret}")
    private String secret;

    @Value("${security.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${security.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${security.jwt.hmac-issuer:uip-legacy}")
    private String hmacIssuer;

    @Value("${security.jwt.keycloak-issuer:http://localhost:8085/realms/uip}")
    private String keycloakIssuer;

    @Value("${security.jwt.keycloak-jwk-set-uri:http://localhost:8085/realms/uip/protocol/openid-connect/certs}")
    private String keycloakJwkSetUri;
}
