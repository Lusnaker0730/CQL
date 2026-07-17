package com.cqlplatform.service;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Tenant-scoped staff management for a clinic ADMIN (PAT-214).
 *
 * <p>BUG-131 locked all platform user management ({@code AdminController}, {@code TenantService})
 * behind {@link com.cqlplatform.security.PlatformOperatorGuard}, correctly stopping a clinic
 * ADMIN from managing platform-wide users — but left clinic admins with no way to manage the
 * staff of their OWN clinic. This service fills that gap: every operation is confined to the
 * caller's {@link #effectiveTenantId()} and every target user is re-checked to belong to that
 * tenant ({@link #requireSameTenant}), so a clinic admin can never reach another tenant's users.
 *
 * <p>A clinic ADMIN may assign any of the three roles (ADMIN / DEPARTMENT_ADMIN / USER) within
 * their tenant — they are the tenant owner. They cannot disable or demote themselves (would let
 * a clinic lock out its own last admin, and would invalidate the very session performing the op).
 * Cross-tenant reassignment stays platform-operator only ({@code TenantService.assignUser}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenVersionService tokenVersionService;
    private final RefreshTokenService refreshTokenService;
    private final UserApiKeyService userApiKeyService;
    private final PasswordResetService passwordResetService;

    /** Caller's tenant ?? default — canonical effectiveTenantId pattern. */
    private Long effectiveTenantId() {
        Long tenantId = TenantContext.getCurrentTenantId();
        if (tenantId != null) {
            return tenantId;
        }
        return defaultTenantId();
    }

    private Long defaultTenantId() {
        return tenantRepository.findByCode(TenantService.DEFAULT_TENANT_CODE)
                .map(TenantEntity::getId)
                .orElseThrow(() -> new IllegalStateException("Default tenant missing"));
    }

    /** Staff of the caller's own tenant. */
    @Transactional(readOnly = true)
    public List<UserEntity> listUsers() {
        Long tenantId = effectiveTenantId();
        List<UserEntity> users = new ArrayList<>(userRepository.findByTenantId(tenantId));
        // Only the default tenant absorbs legacy NULL-tenant users (effectiveTenantId
        // semantics). A clinic tenant never does — its list is exactly findByTenantId.
        if (tenantId.equals(defaultTenantId())) {
            users.addAll(userRepository.findByTenantIdIsNull());
        }
        return users;
    }

    /** Create a staff account inside the caller's tenant. */
    @Transactional
    public UserEntity createUser(String username, String rawPassword, String email, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateResourceException("User", "username", username);
        }
        UserEntity user = UserEntity.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(parseRole(role))
                .enabled(true)
                // New staff must set their own password on first login — the admin-chosen
                // one is a bootstrap secret, not a durable credential.
                .forcePasswordChange(true)
                .tenantId(effectiveTenantId())
                .build();
        if (email != null && !email.isBlank()) {
            user.setEmailWithHash(email);
        }
        UserEntity saved = userRepository.save(user);
        log.info("Tenant {} created staff user '{}' (role {})", effectiveTenantId(), username, role);
        return saved;
    }

    /** Change a staff member's role within the caller's tenant. */
    @Transactional
    public UserEntity updateRole(Long userId, String role, String callerUsername) {
        UserEntity user = requireSameTenant(userId);
        requireNotSelf(user, callerUsername);
        user.setRole(parseRole(role));
        UserEntity saved = userRepository.save(user);
        // The JWT carries the role; invalidate existing tokens so the change takes effect.
        tokenVersionService.bumpVersion(user.getUsername());
        refreshTokenService.revokeAllForUser(user.getId());
        return saved;
    }

    /** Enable / disable a staff member within the caller's tenant. */
    @Transactional
    public UserEntity setEnabled(Long userId, boolean enabled, String callerUsername) {
        UserEntity user = requireSameTenant(userId);
        requireNotSelf(user, callerUsername);
        user.setEnabled(enabled);
        UserEntity saved = userRepository.save(user);
        if (!enabled) {
            // Immediately cut off a disabled account's sessions and API keys.
            tokenVersionService.bumpVersion(user.getUsername());
            refreshTokenService.revokeAllForUser(user.getId());
            userApiKeyService.deactivateAllKeys(user.getUsername());
        }
        return saved;
    }

    /**
     * Reset a staff member's password. Returns a one-time setup link (also delivered by
     * {@code generateSetupToken}'s convention) so it works even where SMTP is not configured —
     * the admin relays the link. Mirrors the clinic-onboarding approval flow.
     */
    @Transactional
    public String resetPassword(Long userId, String baseUrl) {
        UserEntity user = requireSameTenant(userId);
        if (user.getAuthProvider() == UserEntity.AuthProvider.OKTA) {
            throw new ValidationException("Cannot reset password for SSO users");
        }
        String token = passwordResetService.generateSetupToken(user);
        log.info("Tenant {} reset password for staff user '{}'", effectiveTenantId(), user.getUsername());
        return baseUrl + "/reset-password?token=" + token;
    }

    private void requireNotSelf(UserEntity user, String callerUsername) {
        if (user.getUsername().equals(callerUsername)) {
            // Prevents a clinic locking out its own last admin, and avoids invalidating
            // the session performing the operation.
            throw new ValidationException("Cannot modify your own account");
        }
    }

    /**
     * The tenant boundary: the target user must belong to the caller's tenant. A user in
     * another tenant is reported as not-found (never "forbidden") so a clinic admin cannot
     * probe the existence of accounts outside their tenant by enumerating ids.
     */
    private UserEntity requireSameTenant(Long userId) {
        Long myTenant = effectiveTenantId();
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Long userTenant = user.getTenantId() != null ? user.getTenantId() : defaultTenantId();
        if (!myTenant.equals(userTenant)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return user;
    }

    private UserEntity.Role parseRole(String role) {
        try {
            return UserEntity.Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid role: " + role);
        }
    }
}
