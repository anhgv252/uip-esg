package com.uip.backend.auth.service;

import com.uip.backend.auth.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    /**
     * JJWT 0.12.3 uses a lenient base64url decoder: a trailing extra character
     * (e.g. appending "x" to a signature) gets treated as padding, decoding to
     * the same bytes and passing HMAC verification.
     * Fix: pre-validate that the signature part has the exact base64url length
     * for the HMAC algorithm chosen by our signing key (HS256/HS384/HS512).
     *   HS256 → 32-byte sig → 43 chars
     *   HS384 → 48-byte sig → 64 chars
     *   HS512 → 64-byte sig → 86 chars
     */
    private int expectedSignatureLengthB64Url() {
        return switch (getSigningKey().getAlgorithm()) {
            case "HmacSHA384" -> 64;
            case "HmacSHA512" -> 86;
            default          -> 43; // HmacSHA256
        };
    }

    private final JwtProperties jwtProperties;

    public JwtProperties getJwtProperties() {
        return jwtProperties;
    }

    public String generateAccessToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return buildToken(userDetails.getUsername(), Map.of("roles", roles), jwtProperties.getExpirationMs());
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(userDetails.getUsername(), Map.of("type", "refresh"), jwtProperties.getRefreshExpirationMs());
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public long extractExpirationMs(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            Claims claims = parseClaims(token);
            boolean isRefreshToken = "refresh".equals(claims.get("type", String.class));
            return username != null
                    && username.equals(userDetails.getUsername())
                    && !isTokenExpired(token)
                    && !isRefreshToken;
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            Claims claims = parseClaims(token);
            boolean isRefreshToken = "refresh".equals(claims.get("type", String.class));
            return username != null
                    && username.equals(userDetails.getUsername())
                    && !isTokenExpired(token)
                    && isRefreshToken;
        } catch (Exception e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private String buildToken(String subject, Map<String, Object> extraClaims, long expirationMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(subject)
                .claims(extraClaims)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        // Pre-validate JWT structure. JJWT 0.12.3's lenient base64url decoder
        // treats a trailing extra char as padding (e.g. "sig+x" decodes to the
        // same bytes as "sig"), allowing tampered tokens to pass HMAC verification.
        // We reject any token whose signature part != the expected base64url length.
        int lastDot = token == null ? -1 : token.lastIndexOf('.');
        int expectedSigLen = expectedSignatureLengthB64Url();
        if (lastDot < 0 || (token.length() - lastDot - 1) != expectedSigLen) {
            throw new MalformedJwtException("JWT has invalid signature length");
        }
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
