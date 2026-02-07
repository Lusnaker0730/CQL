package com.cqlplatform.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private static final String SECRET = "TestSecretKeyForJWTAuthenticationMustBeAtLeast256BitsLong!!TestExtra";
    private static final long EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, EXPIRATION_MS);
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
    void validateToken_shouldReturnTrueForValidToken() {
        String token = jwtTokenProvider.generateToken("testuser", "USER");
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, -1000);
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
    void differentSecrets_shouldNotValidateCrossTokens() {
        JwtTokenProvider other = new JwtTokenProvider(
                "AnotherSecretKeyForJWTAuthenticationMustBeAtLeast256BitsLong!!Extra", EXPIRATION_MS);
        String token = jwtTokenProvider.generateToken("testuser", "USER");
        assertThat(other.validateToken(token)).isFalse();
    }
}
