package com.cqlplatform.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private static final String ISSUER = "cql-platform";
    private static final String AUDIENCE = "cql-platform";

    private final SecretKey key;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;
    private final long absoluteSessionMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration-ms:${jwt.expiration-ms:900000}}") long accessExpirationMs,
            @Value("${jwt.refresh-expiration-ms:604800000}") long refreshExpirationMs,
            @Value("${jwt.absolute-session-ms:2592000000}") long absoluteSessionMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable is required. "
                    + "Set a secret with at least 256 bits (32+ characters) via: export JWT_SECRET=\"your-secret\"");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters (256 bits) for HMAC-SHA security.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
        this.absoluteSessionMs = absoluteSessionMs;
    }

    public String generateToken(String username, String role) {
        return generateToken(username, role, null);
    }

    public String generateToken(String username, String role, String department) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);

        var builder = Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry);

        if (department != null) {
            builder.claim("department", department);
        }

        return builder.signWith(key).compact();
    }

    public String getUsername(String token) {
        return getClaims(token).getPayload().getSubject();
    }

    public String getRole(String token) {
        return getClaims(token).getPayload().get("role", String.class);
    }

    public String getDepartment(String token) {
        return getClaims(token).getPayload().get("department", String.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return accessExpirationMs;
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public long getAbsoluteSessionMs() {
        return absoluteSessionMs;
    }

    private Jws<Claims> getClaims(String token) {
        return Jwts.parser()
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }
}
