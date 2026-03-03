package com.cqlplatform.service;

import com.cqlplatform.entity.RefreshTokenEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.RefreshTokenRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.JwtTokenProvider;
import com.cqlplatform.util.DigestUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    public record TokenPair(String accessToken, String refreshToken, long accessExpiresIn) {}

    public static class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException(String message) {
            super(message);
        }
    }

    public static class RefreshTokenReuseException extends RuntimeException {
        public RefreshTokenReuseException(String message) {
            super(message);
        }
    }

    @Transactional
    public TokenPair createTokenPair(UserEntity user) {
        String familyId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        long refreshMs = jwtTokenProvider.getRefreshExpirationMs();
        long absoluteMs = jwtTokenProvider.getAbsoluteSessionMs();

        LocalDateTime slidingExpiry = now.plus(refreshMs, ChronoUnit.MILLIS);
        LocalDateTime absoluteExpiry = now.plus(absoluteMs, ChronoUnit.MILLIS);

        String rawToken = generateRawToken();
        String tokenHash = DigestUtils.sha256Hex(rawToken);

        RefreshTokenEntity entity = RefreshTokenEntity.builder()
                .tokenHash(tokenHash)
                .userId(user.getId())
                .familyId(familyId)
                .expiresAt(slidingExpiry)
                .absoluteExpiresAt(absoluteExpiry)
                .revoked(false)
                .build();
        refreshTokenRepository.save(entity);

        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        log.info("Created refresh token family {} for user {}", familyId, user.getUsername());
        return new TokenPair(accessToken, rawToken, jwtTokenProvider.getAccessExpirationMs());
    }

    @Transactional
    public TokenPair refreshTokens(String oldRawToken) {
        String oldHash = DigestUtils.sha256Hex(oldRawToken);

        RefreshTokenEntity oldToken = refreshTokenRepository.findByTokenHash(oldHash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token not found"));

        // Reuse detection: if token is already revoked, someone replayed it
        if (Boolean.TRUE.equals(oldToken.getRevoked())) {
            log.warn("Refresh token reuse detected for family {}! Revoking entire family.", oldToken.getFamilyId());
            refreshTokenRepository.revokeByFamilyId(oldToken.getFamilyId());
            throw new RefreshTokenReuseException(
                    "Refresh token reuse detected — entire session family revoked");
        }

        // Check expiry
        if (oldToken.isExpired()) {
            log.warn("Expired refresh token presented for family {}", oldToken.getFamilyId());
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        // Look up user and check enabled
        UserEntity user = userRepository.findById(oldToken.getUserId())
                .orElseThrow(() -> {
                    refreshTokenRepository.revokeByFamilyId(oldToken.getFamilyId());
                    return new InvalidRefreshTokenException("User not found");
                });

        if (!Boolean.TRUE.equals(user.getEnabled())) {
            log.warn("Disabled user {} attempted token refresh, revoking family {}", user.getUsername(), oldToken.getFamilyId());
            refreshTokenRepository.revokeByFamilyId(oldToken.getFamilyId());
            throw new InvalidRefreshTokenException("User account is disabled");
        }

        // Revoke old token
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        // Issue new token in the same family
        LocalDateTime now = LocalDateTime.now();
        long refreshMs = jwtTokenProvider.getRefreshExpirationMs();
        LocalDateTime slidingExpiry = now.plus(refreshMs, ChronoUnit.MILLIS);

        // Cap sliding window at absolute expiry
        LocalDateTime absoluteExpiry = oldToken.getAbsoluteExpiresAt();
        if (slidingExpiry.isAfter(absoluteExpiry)) {
            slidingExpiry = absoluteExpiry;
        }

        String newRawToken = generateRawToken();
        String newHash = DigestUtils.sha256Hex(newRawToken);

        RefreshTokenEntity newEntity = RefreshTokenEntity.builder()
                .tokenHash(newHash)
                .userId(user.getId())
                .familyId(oldToken.getFamilyId())
                .expiresAt(slidingExpiry)
                .absoluteExpiresAt(absoluteExpiry)
                .revoked(false)
                .build();
        refreshTokenRepository.save(newEntity);

        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        log.debug("Rotated refresh token for family {} user {}", oldToken.getFamilyId(), user.getUsername());
        return new TokenPair(accessToken, newRawToken, jwtTokenProvider.getAccessExpirationMs());
    }

    @Transactional
    public void revokeByToken(String rawToken) {
        String hash = DigestUtils.sha256Hex(rawToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            refreshTokenRepository.revokeByFamilyId(token.getFamilyId());
            log.info("Revoked refresh token family {} via single-device logout", token.getFamilyId());
        });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        int count = refreshTokenRepository.revokeByUserId(userId);
        log.info("Revoked {} refresh tokens for user {}", count, userId);
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        LocalDateTime cutoff = LocalDateTime.now();
        int deleted = refreshTokenRepository.deleteExpiredOrRevoked(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired/revoked refresh tokens", deleted);
        }
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }
}
