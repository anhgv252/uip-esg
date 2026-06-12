package com.uip.backend.tenant.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.uip.backend.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantContextFilter — covers all branches:
 * - Authorization header (Bearer) path
 * - Cookie (access_token) fallback path
 * - No token → default tenant
 * - JWT with tenant_id claim → sets tenant
 * - JWT without tenant_id → default
 * - JWT with blank tenant_id → default
 * - Malformed JWT → default
 * - Context cleared in finally block
 */
@DisplayName("TenantContextFilter — branch coverage")
class TenantContextFilterTest {

    private static final byte[] HMAC_KEY =
            "uip-tenant-filter-test-key-must-be-32-bytes!!"
                    .getBytes(StandardCharsets.UTF_8);

    private TenantContextFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        filter = new TenantContextFilter(objectMapper);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Authorization header (Bearer) ───────────────────────────────────────

    @Test
    @DisplayName("Bearer token with tenant_id claim → TenantContext set to claim value")
    void bearerToken_withTenantId_setsTenant() throws Exception {
        String jwt = buildJwt("testuser", "hcm");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo("hcm");
    }

    @Test
    @DisplayName("Bearer token without tenant_id claim → TenantContext set to default")
    void bearerToken_noTenantId_usesDefault() throws Exception {
        String jwt = buildJwtNoTenant("testuser");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.getDefaultTenant());
    }

    @Test
    @DisplayName("Bearer token with blank tenant_id claim → TenantContext set to default")
    void bearerToken_blankTenantId_usesDefault() throws Exception {
        String jwt = buildJwtWithTenantValue("testuser", "   ");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        // blank tenant_id falls back to default
        assertThat(captured[0]).isEqualTo(TenantContext.getDefaultTenant());
    }

    // ─── Cookie path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("access_token cookie with tenant_id → TenantContext set from cookie JWT")
    void cookieToken_withTenantId_setsTenant() throws Exception {
        String jwt = buildJwt("cookieuser", "dn");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", jwt));
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo("dn");
    }

    @Test
    @DisplayName("Cookie array with other cookies before access_token → still resolves correctly")
    void cookieArray_otherCookiesPresent_resolvesAccessToken() throws Exception {
        String jwt = buildJwt("cookieuser", "sg");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new jakarta.servlet.http.Cookie("session_id", "abc"),
                new jakarta.servlet.http.Cookie("access_token", jwt),
                new jakarta.servlet.http.Cookie("pref", "dark")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo("sg");
    }

    // ─── No token path ────────────────────────────────────────────────────────

    @Test
    @DisplayName("No Authorization header, no cookies → TenantContext set to default")
    void noToken_noHeader_noCookies_usesDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.getDefaultTenant());
    }

    @Test
    @DisplayName("Cookie array has no access_token → default tenant")
    void cookiesPresent_butNoAccessToken_usesDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new jakarta.servlet.http.Cookie("session_id", "abc"),
                new jakarta.servlet.http.Cookie("lang", "vi")
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.getDefaultTenant());
    }

    // ─── Malformed JWT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("JWT with fewer than 2 parts (no dot) → default tenant, no exception thrown")
    void malformedJwt_noDot_usesDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer not-a-jwt");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.getDefaultTenant());
    }

    @Test
    @DisplayName("JWT payload is invalid base64 → default tenant, no exception thrown")
    void malformedJwt_invalidBase64Payload_usesDefault() throws Exception {
        // header.INVALID_BASE64.sig — middle part is not valid base64url
        String malformed = "eyJhbGciOiJIUzI1NiJ9.!!!NOT_BASE64!!!.some_sig";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + malformed);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);
        final String[] captured = {null};
        doAnswer(inv -> {
            captured[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilterInternal(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.getDefaultTenant());
    }

    // ─── Context cleanup ──────────────────────────────────────────────────────

    @Test
    @DisplayName("TenantContext is cleared after filter chain executes (finally block)")
    void contextClearedAfterFilter() throws Exception {
        String jwt = buildJwt("alice", "hcm");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, resp) -> {
            // tenant is set during doFilter
            assertThat(TenantContext.getCurrentTenant()).isEqualTo("hcm");
        };

        filter.doFilterInternal(request, response, chain);

        // after filter, context must be cleared (TenantContext.clear() → CURRENT_TENANT.remove() → null)
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    @DisplayName("TenantContext is cleared even when filter chain throws exception")
    void contextClearedEvenOnException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain chain = (req, resp) -> {
            throw new jakarta.servlet.ServletException("downstream error");
        };

        try {
            filter.doFilterInternal(request, response, chain);
        } catch (jakarta.servlet.ServletException ignored) {
            // expected
        }

        // clear() calls CURRENT_TENANT.remove() → null, not "default"
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Build a signed JWT with tenant_id claim. */
    private String buildJwt(String subject, String tenantId) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("uip-test")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .claim("tenant_id", tenantId)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_KEY));
        return jwt.serialize();
    }

    /** Build a signed JWT WITHOUT tenant_id claim. */
    private String buildJwtNoTenant(String subject) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("uip-test")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_KEY));
        return jwt.serialize();
    }

    /** Build a signed JWT with a specific (possibly blank) tenant_id value. */
    private String buildJwtWithTenantValue(String subject, String tenantValue) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("uip-test")
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .claim("tenant_id", tenantValue)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(HMAC_KEY));
        return jwt.serialize();
    }
}
