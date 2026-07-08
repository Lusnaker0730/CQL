package com.cqlplatform.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET = "TestSecretKeyForJWTAuthenticationMustBeAtLeast256BitsLong!!TestExtra";
    private static final long EXPIRATION_MS = 3600000; // 1 hour
    private static final long REFRESH_EXPIRATION_MS = 604800000L;
    private static final long ABSOLUTE_SESSION_MS = 2592000000L;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS, REFRESH_EXPIRATION_MS, ABSOLUTE_SESSION_MS);
    }

    @Test
    void generateToken_shouldReturnNonNullToken() {
        String token = jwtTokenProvider.generateToken("testuser", "USER");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void getUsername_shouldExtractCorrectUsername() {
        String token = jwtTokenProvider.generateToken("admin", "ADMIN");
        assertThat(jwtTokenProvider.getUsername(token)).isEqualTo("admin");
    }

    @Test
    void getRole_shouldExtractCorrectRole() {
        String token = jwtTokenProvider.generateToken("testuser", "ADMIN");
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo("ADMIN");
    }

    @Test
    void getTenantId_shouldRoundTripTheTenantClaim() {
        String token = jwtTokenProvider.generateToken("clinicUser", "USER", "cardiology", 7L, 0);
        assertThat(jwtTokenProvider.getTenantId(token)).isEqualTo(7L);
    }

    @Test
    void getTenantId_shouldBeNullWhenNoTenant() {
        String token = jwtTokenProvider.generateToken("u", "USER");
        assertThat(jwtTokenProvider.getTenantId(token)).isNull();
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        String token = jwtTokenProvider.generateToken("testuser", "USER");
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1000, REFRESH_EXPIRATION_MS, ABSOLUTE_SESSION_MS);
        String token = shortLived.generateToken("testuser", "USER");
        assertThat(shortLived.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForTamperedToken() {
        String token = jwtTokenProvider.generateToken("testuser", "USER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtTokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForNullToken() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
    }

    @Test
    void validateToken_shouldReturnFalseForEmptyToken() {
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }

    @Test
    void getExpirationMs_shouldReturnConfiguredValue() {
        assertThat(jwtTokenProvider.getExpirationMs()).isEqualTo(EXPIRATION_MS);
    }

    @Test
    void generateToken_shouldContainIssuerAndAudience() {
        String token = jwtTokenProvider.generateToken("testuser", "USER");
        SecretKey k = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        var claims = Jwts.parser().verifyWith(k).build().parseSignedClaims(token).getPayload();
        assertThat(claims.getIssuer()).isEqualTo("cql-platform");
        assertThat(claims.getAudience()).contains("cql-platform");
    }

    @Test
    void validateToken_shouldRejectTokenWithWrongIssuer() {
        // Build a token with a different issuer using the same key
        SecretKey k = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String badToken = Jwts.builder()
                .issuer("other-service")
                .audience().add("cql-platform").and()
                .subject("testuser")
                .signWith(k)
                .compact();
        assertThat(jwtTokenProvider.validateToken(badToken)).isFalse();
    }

    @Test
    void differentSecrets_shouldNotValidateCrossTokens() {
        JwtTokenProvider other = new JwtTokenProvider(
                "AnotherSecretKeyForJWTAuthenticationMustBeAtLeast256BitsLong!!Extra",
                EXPIRATION_MS, REFRESH_EXPIRATION_MS, ABSOLUTE_SESSION_MS);
        String token = jwtTokenProvider.generateToken("testuser", "USER");
        assertThat(other.validateToken(token)).isFalse();
    }
}
