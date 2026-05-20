package com.uip.backend.auth.config;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

import javax.crypto.spec.SecretKeySpec;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoutingJwtDecoder implements JwtDecoder {

    private final JwtProperties jwtProperties;

    private volatile JwtDecoder hmacDecoder;
    private volatile JwtDecoder rsaDecoder;

    @Override
    public Jwt decode(String token) throws JwtException {
        String issuer = extractIssuerUnverified(token);

        if (issuer == null || issuer.isBlank()) {
            throw new JwtException("JWT missing issuer (iss) claim — cannot route to decoder");
        }

        if (issuer.equals(jwtProperties.getHmacIssuer())) {
            return getOrCreateHmacDecoder().decode(token);
        }

        if (issuer.equals(jwtProperties.getKeycloakIssuer())) {
            return getOrCreateRsaDecoder().decode(token);
        }

        throw new JwtException("Unknown JWT issuer: " + issuer);
    }

    private String extractIssuerUnverified(String token) {
        try {
            JWT jwt = JWTParser.parse(token);
            return jwt.getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            throw new JwtException("Failed to parse JWT for issuer routing", e);
        }
    }

    private JwtDecoder getOrCreateHmacDecoder() {
        if (hmacDecoder == null) {
            synchronized (this) {
                if (hmacDecoder == null) {
                    byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
                    SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
                    hmacDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                            .macAlgorithm(MacAlgorithm.HS256)
                            .build();
                    log.info("HMAC JwtDecoder initialized for issuer={}", jwtProperties.getHmacIssuer());
                }
            }
        }
        return hmacDecoder;
    }

    private JwtDecoder getOrCreateRsaDecoder() {
        if (rsaDecoder == null) {
            synchronized (this) {
                if (rsaDecoder == null) {
                    rsaDecoder = NimbusJwtDecoder.withJwkSetUri(jwtProperties.getKeycloakJwkSetUri())
                            .build();
                    log.info("RSA JwtDecoder initialized for issuer={} jwkUri={}",
                            jwtProperties.getKeycloakIssuer(), jwtProperties.getKeycloakJwkSetUri());
                }
            }
        }
        return rsaDecoder;
    }
}
