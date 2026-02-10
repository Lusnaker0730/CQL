package com.cqlplatform.service;

import com.cqlplatform.entity.PasswordResetTokenEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.repository.PasswordResetTokenRepository;
import com.cqlplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_EXPIRY_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Request a password reset. Always returns void (no email enumeration).
     */
    @Transactional
    public void requestPasswordReset(String email, String baseUrl) {
        String emailHash = UserEntity.computeEmailHash(email);
        Optional<UserEntity> userOpt = userRepository.findByEmailHash(emailHash);

        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for non-existent email hash");
            return; // Silent - prevent email enumeration
        }

        UserEntity user = userOpt.get();

        // Invalidate any existing tokens for this user
        tokenRepository.deleteByUserId(user.getId());

        // Generate secure token
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String tokenHash = sha256(rawToken);

        // Store hashed token
        PasswordResetTokenEntity tokenEntity = PasswordResetTokenEntity.builder()
                .userId(user.getId())
                .tokenHash(tokenHash)
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES))
                .used(false)
                .build();
        tokenRepository.save(tokenEntity);

        // Send email with raw token
        String resetLink = baseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), resetLink);

        log.info("Password reset token generated for user: {}", user.getUsername());
    }

    /**
     * Reset password using a token from the email link.
     */
    @Transactional
    public boolean resetPassword(String rawToken, String newPassword) {
        String tokenHash = sha256(rawToken);
        Optional<PasswordResetTokenEntity> tokenOpt = tokenRepository.findByTokenHash(tokenHash);

        if (tokenOpt.isEmpty()) {
            log.warn("Password reset attempted with invalid token");
            return false;
        }

        PasswordResetTokenEntity tokenEntity = tokenOpt.get();

        if (tokenEntity.getUsed()) {
            log.warn("Password reset attempted with already-used token");
            return false;
        }

        if (tokenEntity.isExpired()) {
            log.warn("Password reset attempted with expired token");
            return false;
        }

        Optional<UserEntity> userOpt = userRepository.findById(tokenEntity.getUserId());
        if (userOpt.isEmpty()) {
            log.warn("Password reset token references non-existent user");
            return false;
        }

        UserEntity user = userOpt.get();
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);

        // Mark token as used
        tokenEntity.setUsed(true);
        tokenRepository.save(tokenEntity);

        log.info("Password reset successful for user: {}", user.getUsername());
        return true;
    }

    /**
     * Admin resets a user's password, generating a temporary one.
     */
    @Transactional
    public String adminResetPassword(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate temporary password
        byte[] tempBytes = new byte[12];
        secureRandom.nextBytes(tempBytes);
        String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(tempBytes);

        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setForcePasswordChange(true);
        userRepository.save(user);

        log.info("Admin reset password for user: {}", user.getUsername());
        return temporaryPassword;
    }

    /**
     * Change password (for force-change flow or voluntary change).
     */
    @Transactional
    public boolean changePassword(String username, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setForcePasswordChange(false);
        userRepository.save(user);

        log.info("Password changed for user: {}", username);
        return true;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
