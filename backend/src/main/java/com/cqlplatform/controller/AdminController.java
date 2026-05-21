package com.cqlplatform.controller;

import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.auth.*;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.service.PasswordResetService;
import com.cqlplatform.service.RefreshTokenService;
import com.cqlplatform.service.TokenVersionService;
import com.cqlplatform.service.UserApiKeyService;
import com.cqlplatform.service.cds.CdsRecentInvocationsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * PAT-145 — class-level {@code @PreAuthorize} as defense-in-depth on top of the
 * URL-path rule in {@code SecurityConfig:191} ({@code /api/admin/**}). If the
 * path matcher is ever weakened or the controller's {@code @RequestMapping}
 * moved outside {@code /api/admin/}, this annotation still guards every public
 * endpoint. Keep this annotation in sync with the path-based rule.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final PasswordEncoder passwordEncoder;
    private final UserApiKeyService userApiKeyService;
    private final TokenVersionService tokenVersionService;
    private final RefreshTokenService refreshTokenService;
    private final CdsRecentInvocationsService cdsRecentInvocationsService;

    @GetMapping("/cds/recent-invocations")
    public ResponseEntity<List<CdsRecentInvocationsService.InvocationRecord>> recentCdsInvocations(
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(cdsRecentInvocationsService.recent(Math.min(Math.max(1, limit), 100)));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserSummary>> listUsers() {
        List<UserSummary> users = userRepository.findAll().stream()
                .map(this::toUserSummary)
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping("/users")
    public ResponseEntity<UserSummary> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }

        UserEntity user = UserEntity.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserEntity.Role.valueOf(request.getRole()))
                .build();

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmailWithHash(request.getEmail());
        }

        UserEntity saved = userRepository.save(user);
        return ResponseEntity.ok(toUserSummary(saved));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<UserSummary> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody RoleUpdateRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getUsername().equals(currentUsername)) {
            throw new ValidationException("Cannot modify your own account");
        }

        user.setRole(UserEntity.Role.valueOf(request.getRole()));
        UserEntity saved = userRepository.save(user);

        // Invalidate existing access tokens — the old role is stale
        tokenVersionService.bumpVersion(user.getUsername());
        refreshTokenService.revokeAllForUser(user.getId());

        return ResponseEntity.ok(toUserSummary(saved));
    }

    @PutMapping("/users/{userId}/enabled")
    public ResponseEntity<UserSummary> updateUserEnabled(
            @PathVariable Long userId,
            @Valid @RequestBody EnabledUpdateRequest request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getUsername().equals(currentUsername)) {
            throw new ValidationException("Cannot modify your own account");
        }

        user.setEnabled(request.getEnabled());
        UserEntity saved = userRepository.save(user);

        // When disabling a user, immediately invalidate all sessions
        if (Boolean.FALSE.equals(request.getEnabled())) {
            tokenVersionService.bumpVersion(user.getUsername());
            refreshTokenService.revokeAllForUser(user.getId());
            userApiKeyService.deactivateAllKeys(user.getUsername());
        }

        return ResponseEntity.ok(toUserSummary(saved));
    }

    @PostMapping("/users/{userId}/reset-password")
    public ResponseEntity<?> resetUserPassword(@PathVariable Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (user.getAuthProvider() == UserEntity.AuthProvider.OKTA) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "Cannot reset password for SSO users"));
        }

        // Security (H8): adminResetPassword no longer returns the plaintext password.
        // The temporary password is delivered to the user via email only.
        passwordResetService.adminResetPassword(userId);

        return ResponseEntity.ok()
                // Prevent any intermediate proxy or browser from caching this response.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AdminResetPasswordResponse.builder()
                        .username(user.getUsername())
                        .message("Temporary password has been sent to the user's registered email address. " +
                                 "User will be required to change it on next login.")
                        .build());
    }

    /**
     * Admin unlock: clears the failed-login counter and lockout timestamp, letting
     * the user log in immediately instead of waiting for the lockout window to expire.
     * Intended for support flows where the user phoned in and identified themselves.
     *
     * <p>Always returns success even if the user wasn't locked — avoids leaking
     * account state to admins who shouldn't need to know the exact lock/unlock state.
     * Admin-only endpoint per existing SecurityConfig `/api/admin/**` matcher.
     */
    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<?> unlockUser(@PathVariable Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setFailedLoginAttempts(0);
        user.setLockoutUntil(null);
        userRepository.save(user);
        return ResponseEntity.ok(java.util.Map.of(
                "username", user.getUsername(),
                "message", "Account unlocked. User may sign in immediately."));
    }

    private UserSummary toUserSummary(UserEntity user) {
        return UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail() != null ? user.getEmail() : "")
                .role(user.getRole().name())
                .enabled(user.getEnabled())
                .forcePasswordChange(Boolean.TRUE.equals(user.getForcePasswordChange()))
                .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : "LOCAL")
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : "")
                .build();
    }
}
