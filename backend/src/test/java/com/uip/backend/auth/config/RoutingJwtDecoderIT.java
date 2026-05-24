package com.uip.backend.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Keycloak RSA authentication via RoutingJwtDecoder.
 *
 * Tests 9 scenarios covering HMAC fallback, RSA token validation, security
 * edge cases (expired, invalid, alg=none), issuer-based routing, and key rotation.
 *
 * Uses JDK built-in HttpServer to mock the JWK set endpoint for RSA tests --
 * zero additional dependencies required.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("RoutingJwtDecoder IT — Keycloak RSA Authentication")
class RoutingJwtDecoderIT {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String HMAC_SECRET = "test-secret-for-integration-tests-only-32chars";
    private static final String HMAC_ISSUER = "uip-legacy";
    private static final String KC_ISSUER = "http://localhost:18085/realms/uip";
    private static final String KID_1 = "test-rsa-key-1";
    private static final String KID_2 = "test-rsa-key-2";

    // ── Testcontainers PostgreSQL ─────────────────────────────────────────────

    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("uip_test")
            .withUsername("test")
            .withPassword("test");

    // ── Mock JWK endpoint (JDK HttpServer) ────────────────────────────────────

    private com.sun.net.httpserver.HttpServer jwkServer;
    private int jwkPort;
    private KeyPair rsaKeyPair1;
    private KeyPair rsaKeyPair2;

    // ── Mocked infrastructure ─────────────────────────────────────────────────

    @MockBean @SuppressWarnings("unused")
    RedisConnectionFactory redisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
    @MockBean @SuppressWarnings("unused")
    StringRedisTemplate stringRedisTemplate;
    @MockBean @SuppressWarnings("unused")
    RedisMessageListenerContainer redisMessageListenerContainer;
    @MockBean @SuppressWarnings("unused")
    KafkaTemplate<String, Object> kafkaTemplate;

    // ── Autowired test targets ────────────────────────────────────────────────

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutingJwtDecoder routingJwtDecoder;

    @Autowired
    private JwtProperties jwtProperties;

    // ── Dynamic properties ────────────────────────────────────────────────────

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        postgres.start();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:29092");
        registry.add("security.jwt.secret", () -> HMAC_SECRET);
        registry.add("security.jwt.hmac-issuer", () -> HMAC_ISSUER);
        registry.add("security.jwt.keycloak-issuer", () -> KC_ISSUER);
        // Placeholder — updated in @BeforeAll after mock JWK server starts
        registry.add("security.jwt.keycloak-jwk-set-uri", () -> KC_ISSUER + "/protocol/openid-connect/certs");
    }

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeAll
    void startJwkServer() throws Exception {
        rsaKeyPair1 = generateRsaKeyPair();
        rsaKeyPair2 = generateRsaKeyPair();

        jwkServer = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        jwkPort = jwkServer.getAddress().getPort();

        jwkServer.createContext("/realms/uip/protocol/openid-connect/certs", exchange -> {
            try {
                String jwkJson = buildJwkSetJson(rsaKeyPair1, KID_1);
                byte[] response = jwkJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        });

        jwkServer.setExecutor(null);
        jwkServer.start();

        // Override JwtProperties fields to point to the mock JWK server
        String jwkUri = "http://127.0.0.1:" + jwkPort + "/realms/uip/protocol/openid-connect/certs";
        setField(jwtProperties, "keycloakJwkSetUri", jwkUri);
        setField(jwtProperties, "keycloakIssuer", KC_ISSUER);
        setField(jwtProperties, "secret", HMAC_SECRET);
        setField(jwtProperties, "hmacIssuer", HMAC_ISSUER);

        // Force RoutingJwtDecoder to re-init decoders with updated properties
        resetDecoderCache();
    }

    @AfterAll
    void stopJwkServer() {
        if (jwkServer != null) {
            jwkServer.stop(0);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-01: RSA JWT decode — iss claim equals Keycloak URL
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("KC-IT-01: RSA JWT decode — iss claim equals Keycloak URL")
    void rsaJwt_decode_issMatchesKeycloakUrl() throws Exception {
        String token = buildRsaToken(rsaKeyPair1, KID_1, "admin", KC_ISSUER, 60000);

        Jwt jwt = routingJwtDecoder.decode(token);

        assertThat(jwt.getClaimAsString("iss")).isEqualTo(KC_ISSUER);
        assertThat(jwt.getSubject()).isEqualTo("admin");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-02: RSA JWT -> API call -> 200
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("KC-IT-02: RSA JWT -> protected API -> 200")
    void rsaJwt_protectedEndpoint_returns200() throws Exception {
        String token = buildRsaToken(rsaKeyPair1, KID_1, "admin", KC_ISSUER, 60000);

        mockMvc.perform(get("/api/v1/health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-03: HMAC JWT -> API call -> 200 (fallback/grace period)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("KC-IT-03: HMAC JWT -> protected API -> 200 (fallback)")
    void hmacJwt_protectedEndpoint_returns200() throws Exception {
        String token = buildHmacToken("admin", HMAC_ISSUER, 60000);

        mockMvc.perform(get("/api/v1/health")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-04: Expired RSA JWT -> API call -> 401
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("KC-IT-04: Expired RSA JWT -> protected API -> 401")
    void expiredRsaJwt_protectedEndpoint_returns401() throws Exception {
        String token = buildRsaToken(rsaKeyPair1, KID_1, "admin", KC_ISSUER, -60000);

        mockMvc.perform(get("/api/v1/environment/sensors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-05: Invalid RSA JWT (wrong key) -> API call -> 401
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("KC-IT-05: RSA JWT signed with unknown key -> protected API -> 401")
    void invalidRsaJwt_protectedEndpoint_returns401() throws Exception {
        KeyPair unknownKeyPair = generateRsaKeyPair();
        String token = buildRsaToken(unknownKeyPair, "unknown-key-id", "admin", KC_ISSUER, 60000);

        mockMvc.perform(get("/api/v1/environment/sensors")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-06: alg=none JWT -> API call -> 401
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("KC-IT-06: alg=none JWT -> rejected by decoder")
    void algNoneJwt_protectedEndpoint_returns401() throws Exception {
        String token = buildUnsignedToken("admin", KC_ISSUER);

        assertThatThrownBy(() -> routingJwtDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-07: RoutingJwtDecoder routes by iss claim
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("KC-IT-07: Issuer-based routing")
    @Order(7)
    class IssuerRoutingTests {

        @Test
        @DisplayName("HMAC issuer -> HMAC decoder path")
        void hmacIssuer_routesToHmacDecoder() throws Exception {
            String token = buildHmacToken("testuser", HMAC_ISSUER, 60000);

            Jwt jwt = routingJwtDecoder.decode(token);

            assertThat(jwt.getSubject()).isEqualTo("testuser");
            assertThat(jwt.getClaimAsString("iss")).isEqualTo(HMAC_ISSUER);
        }

        @Test
        @DisplayName("Keycloak issuer -> RSA decoder path")
        void keycloakIssuer_routesToRsaDecoder() throws Exception {
            String token = buildRsaToken(rsaKeyPair1, KID_1, "testuser", KC_ISSUER, 60000);

            Jwt jwt = routingJwtDecoder.decode(token);

            assertThat(jwt.getSubject()).isEqualTo("testuser");
            assertThat(jwt.getClaimAsString("iss")).isEqualTo(KC_ISSUER);
        }

        @Test
        @DisplayName("Unknown issuer -> JwtException")
        void unknownIssuer_throwsJwtException() throws Exception {
            String token = buildHmacToken("testuser", "unknown-issuer", 60000);

            assertThatThrownBy(() -> routingJwtDecoder.decode(token))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("Unknown JWT issuer");
        }

        @Test
        @DisplayName("Missing issuer -> JwtException")
        void missingIssuer_throwsJwtException() throws Exception {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("testuser")
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 60000))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new com.nimbusds.jose.crypto.MACSigner(
                    HMAC_SECRET.getBytes(StandardCharsets.UTF_8)));

            assertThatThrownBy(() -> routingJwtDecoder.decode(jwt.serialize()))
                    .isInstanceOf(JwtException.class)
                    .hasMessageContaining("missing issuer");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-08: HS256 token with RSA issuer -> rejected
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(8)
    @DisplayName("KC-IT-08: HS256 token with RSA issuer -> rejected")
    void hs256TokenWithRsaIssuer_rejected() throws Exception {
        // HS256-signed token with Keycloak issuer -> routes to RSA decoder
        // RSA decoder cannot verify HMAC signature -> JwtException
        String token = buildHmacToken("attacker", KC_ISSUER, 60000);

        assertThatThrownBy(() -> routingJwtDecoder.decode(token))
                .isInstanceOf(JwtException.class);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // KC-IT-09: JWK key rotation (new kid -> still valid) [STRETCH]
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(9)
    @DisplayName("KC-IT-09: JWK key rotation — new kid in JWK set, old tokens still valid")
    void jwkKeyRotation_newKidInSet_oldTokensStillValid() throws Exception {
        // 1. Token signed with key-1 should decode fine
        String token1 = buildRsaToken(rsaKeyPair1, KID_1, "admin", KC_ISSUER, 60000);
        Jwt jwt1 = routingJwtDecoder.decode(token1);
        assertThat(jwt1.getSubject()).isEqualTo("admin");

        // 2. Add rotated JWK endpoint serving BOTH keys
        String rotatedUri = "http://127.0.0.1:" + jwkPort
                + "/realms/uip/protocol/openid-connect/certs-rotated";
        setField(jwtProperties, "keycloakJwkSetUri", rotatedUri);
        jwkServer.createContext("/realms/uip/protocol/openid-connect/certs-rotated", exchange -> {
            try {
                String jwkJson = buildJwkSetJson(rsaKeyPair1, KID_1, rsaKeyPair2, KID_2);
                byte[] resp = jwkJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, 0);
                exchange.close();
            }
        });
        resetDecoderCache();

        // 3. Token signed with new key-2 decodes successfully
        String token2 = buildRsaToken(rsaKeyPair2, KID_2, "admin", KC_ISSUER, 60000);
        Jwt jwt2 = routingJwtDecoder.decode(token2);
        assertThat(jwt2.getSubject()).isEqualTo("admin");

        // 4. Old token (key-1) still works — both keys present in JWK set
        Jwt jwt1Again = routingJwtDecoder.decode(token1);
        assertThat(jwt1Again.getSubject()).isEqualTo("admin");
    }

    // ── Token builders ────────────────────────────────────────────────────────

    private String buildRsaToken(KeyPair keyPair, String kid, String subject,
                                 String issuer, long expirationOffsetMs) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + expirationOffsetMs))
                .claim("tenant_id", "tenant-a")
                .claim("scope", "esg:write")
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(kid)
                        .build(),
                claims);
        signedJWT.sign(new RSASSASigner(keyPair.getPrivate()));
        return signedJWT.serialize();
    }

    private String buildHmacToken(String subject, String issuer, long expirationOffsetMs) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer(issuer)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + expirationOffsetMs))
                .claim("tenant_id", "tenant-a")
                .claim("roles", List.of("ADMIN"))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJWT.sign(new com.nimbusds.jose.crypto.MACSigner(
                HMAC_SECRET.getBytes(StandardCharsets.UTF_8)));
        return signedJWT.serialize();
    }

    /**
     * Build an unsigned JWT (alg=none) for security testing.
     */
    private String buildUnsignedToken(String subject, String issuer) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String headerJson = mapper.writeValueAsString(Map.of("alg", "none", "typ", "JWT"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", subject);
        payload.put("iss", issuer);
        payload.put("iat", System.currentTimeMillis() / 1000);
        payload.put("exp", (System.currentTimeMillis() / 1000) + 3600);
        String payloadJson = mapper.writeValueAsString(payload);

        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        return headerB64 + "." + payloadB64 + ".";
    }

    // ── JWK set builders ──────────────────────────────────────────────────────

    private String buildJwkSetJson(KeyPair keyPair, String kid) throws Exception {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .keyID(kid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
        return new JWKSet(List.of(rsaKey)).toPublicJWKSet().toString();
    }

    private String buildJwkSetJson(KeyPair keyPair1, String kid1,
                                   KeyPair keyPair2, String kid2) throws Exception {
        RSAKey rsaKey1 = new RSAKey.Builder((RSAPublicKey) keyPair1.getPublic())
                .keyID(kid1).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        RSAKey rsaKey2 = new RSAKey.Builder((RSAPublicKey) keyPair2.getPublic())
                .keyID(kid2).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256).build();
        return new JWKSet(List.of(rsaKey1, rsaKey2)).toPublicJWKSet().toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        return kpg.generateKeyPair();
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void resetDecoderCache() throws Exception {
        java.lang.reflect.Field hmacField = RoutingJwtDecoder.class.getDeclaredField("hmacDecoder");
        hmacField.setAccessible(true);
        hmacField.set(routingJwtDecoder, null);

        java.lang.reflect.Field rsaField = RoutingJwtDecoder.class.getDeclaredField("rsaDecoder");
        rsaField.setAccessible(true);
        rsaField.set(routingJwtDecoder, null);
    }
}
