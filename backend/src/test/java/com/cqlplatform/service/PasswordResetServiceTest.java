package com.cqlplatform.service;

import com.cqlplatform.entity.PasswordResetTokenEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.PasswordResetTokenRepository;
import com.cqlplatform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PasswordResetService service;

    // ===== requestPasswordReset =====

    @Test
    void requestPasswordReset_validEmail_shouldGenerateTokenAndSendEmail() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encoded")
                .build();

        String emailHash = UserEntity.computeEmailHash("test@example.com");
        when(userRepository.findByEmailHash(emailHash)).thenReturn(Optional.of(user));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestPasswordReset("test@example.com", "http://localhost:3000");

        verify(tokenRepository).deleteByUserId(1L);
        verify(tokenRepository).save(any(PasswordResetTokenEntity.class));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), eq("testuser"), contains("reset-password?token="));
    }

    @Test
    void requestPasswordReset_unknownEmail_shouldNotThrow() {
        String emailHash = UserEntity.computeEmailHash("unknown@example.com");
        when(userRepository.findByEmailHash(emailHash)).thenReturn(Optional.empty());

        // Should not throw (prevents email enumeration)
        assertThatNoException().isThrownBy(
                () -> service.requestPasswordReset("unknown@example.com", "http://localhost:3000"));

        verify(emailService, never()).sendPasswordResetEmail(any(), any(), any());
    }

    // ===== resetPassword =====

    @Test
    void resetPassword_validToken_shouldSucceed() {
        PasswordResetTokenEntity token = PasswordResetTokenEntity.builder()
                .id(1L)
                .userId(1L)
                .tokenHash("dummy")
                .used(false)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        UserEntity user = UserEntity.builder()
                .id(1L)
                .username("testuser")
                .password("oldEncoded")
                .build();

        // We need to match the sha256 hash — use any() since we can't predict the hash
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123")).thenReturn("newEncoded");
        when(userRepository.save(any())).thenReturn(user);
        when(tokenRepository.save(any())).thenReturn(token);

        boolean result = service.resetPassword("someRawToken", "newPassword123");

        assertThat(result).isTrue();
        verify(passwordEncoder).encode("newPassword123");
        verify(userRepository).save(any());
        assertThat(token.getUsed()).isTrue();
    }

    @Test
    void resetPassword_invalidToken_shouldReturnFalse() {
        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        boolean result = service.resetPassword("invalidToken", "newPassword");

        assertThat(result).isFalse();
    }

    @Test
    void resetPassword_expiredToken_shouldReturnFalse() {
        PasswordResetTokenEntity token = PasswordResetTokenEntity.builder()
                .id(1L)
                .userId(1L)
                .tokenHash("dummy")
                .used(false)
                .expiresAt(LocalDateTime.now().minusMinutes(5)) // expired
                .build();

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        boolean result = service.resetPassword("someToken", "newPassword");

        assertThat(result).isFalse();
    }

    @Test
    void resetPassword_usedToken_shouldReturnFalse() {
        PasswordResetTokenEntity token = PasswordResetTokenEntity.builder()
                .id(1L)
                .userId(1L)
                .tokenHash("dummy")
                .used(true)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.of(token));

        boolean result = service.resetPassword("someToken", "newPassword");

        assertThat(result).isFalse();
    }

    // ===== changePassword =====

    @Test
    void changePassword_correctCurrent_shouldSucceed() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .username("testuser")
                .password("encodedOld")
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldPass", "encodedOld")).thenReturn(true);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNew");
        when(userRepository.save(any())).thenReturn(user);

        boolean result = service.changePassword("testuser", "oldPass", "newPass");

        assertThat(result).isTrue();
        verify(passwordEncoder).encode("newPass");
    }

    @Test
    void changePassword_wrongCurrent_shouldReturnFalse() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .username("testuser")
                .password("encodedOld")
                .build();

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongPass", "encodedOld")).thenReturn(false);

        boolean result = service.changePassword("testuser", "wrongPass", "newPass");

        assertThat(result).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_userNotFound_shouldThrow() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword("unknown", "old", "new"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }

    // ===== adminResetPassword =====

    @Test
    void adminResetPassword_shouldReturnTempPassword() {
        UserEntity user = UserEntity.builder()
                .id(1L)
                .username("testuser")
                .password("old")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any())).thenReturn("encodedTemp");
        when(userRepository.save(any())).thenReturn(user);

        String tempPass = service.adminResetPassword(1L);

        assertThat(tempPass).isNotNull().isNotEmpty();
        assertThat(user.getForcePasswordChange()).isTrue();
        verify(passwordEncoder).encode(tempPass);
    }

    @Test
    void adminResetPassword_notFound_shouldThrow() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.adminResetPassword(999L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found");
    }
}
