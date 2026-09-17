package com.cqlplatform.service;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Tenant (clinic) management — the platform operator provisions a tenant per clinic
 * and assigns users to it (PAT-201 / #699).
 *
 * <h2>Authorization model (transition)</h2>
 * A dedicated SUPER_ADMIN role does not exist yet; introducing one would break the
 * scattered manual {@code isAdmin} authority checks. Until the self-registration work
 * revisits the role model, the platform operator is defined as <b>an ADMIN whose own
 * tenant is the DEFAULT tenant</b>: the controller enforces {@code hasRole('ADMIN')}
 * and {@link #requirePlatformOperator()} enforces the tenant boundary here — a clinic
 * tenant's ADMIN gets 403 on every tenant-management operation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantService {

    public static final String DEFAULT_TENANT_CODE = "default";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final com.cqlplatform.security.PlatformOperatorGuard platformOperatorGuard;
    private final TokenVersionService tokenVersionService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public TenantEntity createTenant(String code, String name) {
        requirePlatformOperator();
        if (tenantRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Tenant with code already exists: " + code);
        }
        TenantEntity created = tenantRepository.save(
                TenantEntity.builder().code(code).name(name).active(true).build());
        log.info("Created tenant '{}' ({})", code, name);
        return created;
    }

    @Transactional(readOnly = true)
    public List<TenantEntity> listActive() {
        return tenantRepository.findByActiveTrue();
    }

    /** Management view — all tenants, active and deactivated. Platform operator only. */
    @Transactional(readOnly = true)
    public List<TenantEntity> listAll() {
        requirePlatformOperator();
        return tenantRepository.findAll();
    }

    @Transactional(readOnly = true)
    public TenantEntity getByCode(String code) {
        return tenantRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + code));
    }

    @Transactional(readOnly = true)
    public TenantEntity getById(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + id));
    }

    /**
     * Activate / deactivate a tenant. Deactivation blocks the clinic's users at login
     * (enforced by the auth path once tenant-active checks land with self-registration);
     * it never deletes data. The default tenant cannot be deactivated.
     */
    @Transactional
    public TenantEntity setActive(Long id, boolean active) {
        requirePlatformOperator();
        TenantEntity tenant = getById(id);
        if (!active && DEFAULT_TENANT_CODE.equals(tenant.getCode())) {
            throw new ValidationException("The default tenant cannot be deactivated");
        }
        tenant.setActive(active);
        TenantEntity saved = tenantRepository.save(tenant);
        log.info("Tenant '{}' set active={}", tenant.getCode(), active);
        return saved;
    }

    /**
     * Users belonging to a tenant. For the default tenant this includes legacy users
     * whose tenant_id is still NULL — they resolve to the default tenant everywhere
     * else (effectiveTenantId semantics), so the management view must match.
     */
    @Transactional(readOnly = true)
    public List<UserEntity> listUsers(Long tenantId) {
        requirePlatformOperator();
        TenantEntity tenant = getById(tenantId);
        List<UserEntity> users = new ArrayList<>(userRepository.findByTenantId(tenantId));
        if (DEFAULT_TENANT_CODE.equals(tenant.getCode())) {
            users.addAll(userRepository.findByTenantIdIsNull());
        }
        return users;
    }

    /**
     * Assign a user to a tenant. The JWT carries the tenant claim, so existing tokens
     * are invalidated immediately (same pattern as a role change): version bump kills
     * access tokens within the 30s cache window and refresh tokens are revoked — the
     * next login mints a token with the new tenant.
     */
    @Transactional
    public UserEntity assignUser(Long userId, Long tenantId, String callerUsername) {
        requirePlatformOperator();
        TenantEntity tenant = getById(tenantId);
        if (!Boolean.TRUE.equals(tenant.getActive())) {
            throw new ValidationException("Cannot assign users to a deactivated tenant: " + tenant.getCode());
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getUsername().equals(callerUsername)) {
            // Same convention as role/enabled changes — and reassigning yourself would
            // invalidate the very session performing the operation.
            throw new ValidationException("Cannot modify your own account");
        }

        user.setTenantId(tenantId);
        UserEntity saved = userRepository.save(user);

        tokenVersionService.bumpVersion(user.getUsername());
        refreshTokenService.revokeAllForUser(user.getId());

        log.info("Assigned user '{}' to tenant '{}'", user.getUsername(), tenant.getCode());
        return saved;
    }

    /** See the class javadoc — delegated to the shared PlatformOperatorGuard (#700 refactor). */
    private void requirePlatformOperator() {
        platformOperatorGuard.require();
    }
}
