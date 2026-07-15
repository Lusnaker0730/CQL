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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;


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
    private final TokenVersionService tokenVersionService;
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

        // Send email after transaction commits (avoid holding DB connection during SMTP I/O)
        String resetLink = baseUrl + "/reset-password?token=" + rawToken;
        String userEmail = user.getEmail();
        String username = user.getUsername();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    emailService.sendPasswordResetEmail(userEmail, username, resetLink);
                } catch (Exception e) {
                    log.warn("Failed to send password reset email for user: {}", username, e);
                }
            }
        });

        log.info("Password reset token generated for user: {}", user.getUsername());
    }

    /**
     * Create a password-setup token for a freshly provisioned account (#700 clinic
     * onboarding) and return the RAW token so the caller can build a setup link.
     *
     * <p>Deliberate deviation from the H8 "never return secrets" rule, scoped tightly:
     * the caller is the platform operator who just CREATED this account — the link
     * grants nothing the operator doesn't already have. It lets onboarding work when
     * SMTP is not configured (the link is handed over out-of-band); when mail IS
     * configured the same link is also emailed. Expiry is longer than the reset flow
     * (7 days) to survive out-of-band handover.
     */
    @Transactional
    public String generateSetupToken(UserEntity user) {
        tokenRepository.deleteByUserId(user.getId());

        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        tokenRepository.save(PasswordResetTokenEntity.builder()
                .userId(user.getId())
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .used(false)
                .build());
        return rawToken;
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

        // Invalidate all existing sessions after password reset
        tokenVersionService.bumpVersion(user.getUsername());

        log.info("Password reset successful for user: {}", user.getUsername());
        return true;
    }

    /**
     * Admin resets a user's password, generating a temporary one.
     * The temporary password is sent to the user's registered email address and is
     * never returned in the API response to prevent plaintext exposure.
     */
    @Transactional
    public void adminResetPassword(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Generate temporary password
        byte[] tempBytes = new byte[12];
        secureRandom.nextBytes(tempBytes);
        String temporaryPassword = Base64.getUrlEncoder().withoutPadding().encodeToString(tempBytes);

        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setForcePasswordChange(true);
        userRepository.save(user);

        // Invalidate all existing sessions after admin password reset
        tokenVersionService.bumpVersion(user.getUsername());

        log.info("Admin reset password for user: {}", user.getUsername());

        // Deliver the temporary password via email after the transaction commits so that
        // the plaintext credential is never returned through the API (H8 fix).
        String userEmail = user.getEmail();
        String username = user.getUsername();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (userEmail != null && !userEmail.isBlank()) {
                    try {
                        emailService.sendTemporaryPasswordEmail(userEmail, username, temporaryPassword);
                    } catch (Exception e) {
                        log.warn("Failed to send temporary password email for user: {}", username, e);
                    }
                } else {
                    log.warn("Admin reset password for user {} who has no registered email; " +
                             "temporary password could not be delivered.", username);
                }
            }
        });
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

        // Invalidate all other sessions after password change
        tokenVersionService.bumpVersion(username);

        log.info("Password changed for user: {}", username);
        return true;
    }

    private String sha256(String input) {
        return com.cqlplatform.util.DigestUtils.sha256Hex(input);
    }
}
