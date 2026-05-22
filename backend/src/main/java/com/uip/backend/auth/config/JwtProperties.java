package com.uip.backend.auth.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class JwtProperties {

    @Value("${security.jwt.secret:changeme_jwt_secret_must_be_at_least_256_bits_long_for_hmac_sha256}")
    private String secret;

    @Value("${security.jwt.expiration-ms:3600000}")
    private long expirationMs;

    @Value("${security.jwt.refresh-expiration-ms:86400000}")
    private long refreshExpirationMs;

    @Value("${security.jwt.hmac-issuer:uip-legacy}")
    private String hmacIssuer;

    @Value("${security.jwt.keycloak-issuer:http://localhost:8085/realms/uip}")
    private String keycloakIssuer;

    @Value("${security.jwt.keycloak-jwk-set-uri:http://localhost:8085/realms/uip/protocol/openid-connect/certs}")
    private String keycloakJwkSetUri;
}
