package com.cqlplatform.service;

import com.cqlplatform.entity.RefreshTokenEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.RefreshTokenRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.JwtTokenProvider;
import com.cqlplatform.util.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = UserEntity.builder()
                .id(1L)
                .username("testuser")
                .role(UserEntity.Role.USER)
                .enabled(true)
                .build();
    }

    @Test
    void createTokenPair_shouldReturnValidPairAndStoreHash() {
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAbsoluteSessionMs()).thenReturn(2592000000L);
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateToken("testuser", "USER")).thenReturn("access-jwt");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.TokenPair pair = refreshTokenService.createTokenPair(testUser);

        assertThat(pair.accessToken()).isEqualTo("access-jwt");
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(pair.accessExpiresIn()).isEqualTo(900000L);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshTokenEntity saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(DigestUtils.sha256Hex(pair.refreshToken()));
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getFamilyId()).isNotBlank();
        assertThat(saved.getRevoked()).isFalse();
    }

    @Test
    void refreshTokens_validToken_shouldRotate() {
        String rawToken = "test-raw-token";
        String hash = DigestUtils.sha256Hex(rawToken);

        RefreshTokenEntity existing = RefreshTokenEntity.builder()
                .id(10L)
                .tokenHash(hash)
                .userId(1L)
                .familyId("family-1")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .absoluteExpiresAt(LocalDateTime.now().plusDays(30))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateToken("testuser", "USER")).thenReturn("new-access-jwt");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.TokenPair pair = refreshTokenService.refreshTokens(rawToken);

        assertThat(pair.accessToken()).isEqualTo("new-access-jwt");
        assertThat(pair.refreshToken()).isNotBlank();
        assertThat(pair.refreshToken()).isNotEqualTo(rawToken);

        // Old token should be revoked
        assertThat(existing.getRevoked()).isTrue();

        // New token saved with same family
        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshTokenEntity newEntity = captor.getAllValues().get(1);
        assertThat(newEntity.getFamilyId()).isEqualTo("family-1");
        assertThat(newEntity.getRevoked()).isFalse();
        assertThat(newEntity.getAbsoluteExpiresAt()).isEqualTo(existing.getAbsoluteExpiresAt());
    }

    @Test
    void refreshTokens_revokedToken_shouldRevokeEntireFamilyAndThrow() {
        String rawToken = "reused-token";
        String hash = DigestUtils.sha256Hex(rawToken);

        RefreshTokenEntity revokedToken = RefreshTokenEntity.builder()
                .id(10L)
                .tokenHash(hash)
                .userId(1L)
                .familyId("family-2")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .absoluteExpiresAt(LocalDateTime.now().plusDays(30))
                .revoked(true)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(rawToken))
                .isInstanceOf(RefreshTokenService.RefreshTokenReuseException.class)
                .hasMessageContaining("reuse detected");

        verify(refreshTokenRepository).revokeByFamilyId("family-2");
    }

    @Test
    void refreshTokens_expiredToken_shouldThrow() {
        String rawToken = "expired-token";
        String hash = DigestUtils.sha256Hex(rawToken);

        RefreshTokenEntity expired = RefreshTokenEntity.builder()
                .id(10L)
                .tokenHash(hash)
                .userId(1L)
                .familyId("family-3")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .absoluteExpiresAt(LocalDateTime.now().plusDays(30))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(rawToken))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refreshTokens_absoluteExpiryExceeded_shouldThrow() {
        String rawToken = "abs-expired-token";
        String hash = DigestUtils.sha256Hex(rawToken);

        RefreshTokenEntity absExpired = RefreshTokenEntity.builder()
                .id(10L)
                .tokenHash(hash)
                .userId(1L)
                .familyId("family-4")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .absoluteExpiresAt(LocalDateTime.now().minusMinutes(1))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(absExpired));

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(rawToken))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void refreshTokens_disabledUser_shouldRevokeFamilyAndThrow() {
        String rawToken = "disabled-user-token";
        String hash = DigestUtils.sha256Hex(rawToken);

        UserEntity disabledUser = UserEntity.builder()
                .id(2L)
                .username("disabled")
                .role(UserEntity.Role.USER)
                .enabled(false)
                .build();

        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .id(10L)
                .tokenHash(hash)
                .userId(2L)
                .familyId("family-5")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .absoluteExpiresAt(LocalDateTime.now().plusDays(30))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(userRepository.findById(2L)).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(rawToken))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                .hasMessageContaining("disabled");

        verify(refreshTokenRepository).revokeByFamilyId("family-5");
    }

    @Test
    void refreshTokens_slidingWindowCappedAtAbsoluteExpiry() {
        String rawToken = "near-abs-token";
        String hash = DigestUtils.sha256Hex(rawToken);

        // Absolute expires in 2 days, but sliding window is 7 days — should be capped
        LocalDateTime absoluteExpiry = LocalDateTime.now().plusDays(2);

        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .id(10L)
                .tokenHash(hash)
                .userId(1L)
                .familyId("family-6")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .absoluteExpiresAt(absoluteExpiry)
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(jwtTokenProvider.getRefreshExpirationMs()).thenReturn(604800000L); // 7 days
        when(jwtTokenProvider.getAccessExpirationMs()).thenReturn(900000L);
        when(jwtTokenProvider.generateToken("testuser", "USER")).thenReturn("jwt");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        refreshTokenService.refreshTokens(rawToken);

        ArgumentCaptor<RefreshTokenEntity> captor = ArgumentCaptor.forClass(RefreshTokenEntity.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        RefreshTokenEntity newEntity = captor.getAllValues().get(1);
        // New sliding expiry should equal absolute expiry since it would otherwise exceed it
        assertThat(newEntity.getExpiresAt()).isEqualTo(absoluteExpiry);
    }

    @Test
    void refreshTokens_notFound_shouldThrow() {
        String rawToken = "unknown-token";
        String hash = DigestUtils.sha256Hex(rawToken);

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.refreshTokens(rawToken))
                .isInstanceOf(RefreshTokenService.InvalidRefreshTokenException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void revokeByToken_shouldRevokeFamilyIfFound() {
        String rawToken = "token-to-revoke";
        String hash = DigestUtils.sha256Hex(rawToken);

        RefreshTokenEntity token = RefreshTokenEntity.builder()
                .id(10L)
                .familyId("family-7")
                .build();

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        refreshTokenService.revokeByToken(rawToken);

        verify(refreshTokenRepository).revokeByFamilyId("family-7");
    }

    @Test
    void revokeByToken_notFound_shouldNotThrow() {
        String rawToken = "nonexistent";
        String hash = DigestUtils.sha256Hex(rawToken);

        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThatCode(() -> refreshTokenService.revokeByToken(rawToken)).doesNotThrowAnyException();
    }

    @Test
    void revokeAllForUser_shouldDelegateToRepository() {
        when(refreshTokenRepository.revokeByUserId(1L)).thenReturn(5);

        refreshTokenService.revokeAllForUser(1L);

        verify(refreshTokenRepository).revokeByUserId(1L);
    }

    @Test
    void cleanupExpiredTokens_shouldDelegateToRepository() {
        when(refreshTokenRepository.deleteExpiredOrRevoked(any())).thenReturn(10);

        refreshTokenService.cleanupExpiredTokens();

        verify(refreshTokenRepository).deleteExpiredOrRevoked(any(LocalDateTime.class));
    }
}
