package com.uip.backend.auth.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingJwtDecoderTest {

    private JwtProperties jwtProperties;
    private RoutingJwtDecoder decoder;

    private static final String HMAC_ISSUER = "uip-legacy";
    private static final String KC_ISSUER = "http://localhost:8085/realms/uip";
    private static final byte[] HMAC_KEY = "changeme_jwt_secret_must_be_at_least_256_bits_long_for_hmac_sha256"
            .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        jwtProperties = mock(JwtProperties.class);
        lenient().when(jwtProperties.getHmacIssuer()).thenReturn(HMAC_ISSUER);
        lenient().when(jwtProperties.getKeycloakIssuer()).thenReturn(KC_ISSUER);
        lenient().when(jwtProperties.getKeycloakJwkSetUri()).thenReturn("http://localhost:8085/realms/uip/protocol/openid-connect/certs");
        lenient().when(jwtProperties.getSecret()).thenReturn(
                new String(HMAC_KEY, StandardCharsets.UTF_8));
        decoder = new RoutingJwtDecoder(jwtProperties);
    }

    private String buildHmacToken(String subject, String issuer, List<String> roles, String tenantId, long expirationOffsetMs) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + expirationOffsetMs));
        if (roles != null) claims.claim("roles", roles);
        if (tenantId != null) claims.claim("tenant_id", tenantId);

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        jwt.sign(new MACSigner(HMAC_KEY));
        return jwt.serialize();
    }

    @Test
    @DisplayName("HMAC token with correct issuer → decode success")
    void hmacToken_correctIssuer_decodes() throws Exception {
        String token = buildHmacToken("testuser", HMAC_ISSUER, List.of("OPERATOR"), null, 60000);

        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo("testuser");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(HMAC_ISSUER);
    }

    @Test
    @DisplayName("HMAC token with roles and tenant_id claims extracted")
    void hmacToken_rolesAndTenant_extracted() throws Exception {
        String token = buildHmacToken("operator1", HMAC_ISSUER, List.of("OPERATOR", "ADMIN"), "hcm", 60000);

        Jwt jwt = decoder.decode(token);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("OPERATOR", "ADMIN");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("hcm");
    }

    @Test
    @DisplayName("Expired HMAC token → rejected with JwtException")
    void expiredHmacToken_rejected() throws Exception {
        String token = buildHmacToken("testuser", HMAC_ISSUER, null, null, -60000);

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Token with unknown issuer → rejected")
    void unknownIssuer_rejected() throws Exception {
        String token = buildHmacToken("testuser", "unknown-issuer", null, null, 60000);

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("Unknown JWT issuer");
    }

    @Test
    @DisplayName("Token missing issuer → rejected")
    void missingIssuer_rejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("testuser")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_KEY));

        assertThatThrownBy(() -> decoder.decode(jwt.serialize()))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("missing issuer");
    }

    @Test
    @DisplayName("Tampered HMAC token → rejected")
    void tamperedHmacToken_rejected() throws Exception {
        String token = buildHmacToken("testuser", HMAC_ISSUER, null, null, 60000);
        String tampered = token.substring(0, 20) + "X" + token.substring(21);

        assertThatThrownBy(() -> decoder.decode(tampered))
                .isInstanceOf(JwtException.class);
    }
}
