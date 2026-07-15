package com.cqlplatform.service;

import com.cqlplatform.entity.ClinicApplicationEntity;
import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.repository.ClinicApplicationRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.PlatformOperatorGuard;
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
import java.util.List;

/**
 * Clinic self-service application flow (#700 PR-1).
 *
 * <p>Public side: anyone may submit an application (rate-limited, uniform response —
 * nothing about existing tenants/usernames is revealed). Operator side: the platform
 * operator reviews pending applications; approval provisions the tenant (via the #699
 * machinery) plus its first tenant-admin account, and yields a one-time password-setup
 * link. The link is ALSO emailed best-effort — but it is returned to the operator so
 * onboarding works when SMTP is not configured (the pilot VM has none).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClinicApplicationService {

    private final ClinicApplicationRepository applicationRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final TenantService tenantService;
    private final PasswordResetService passwordResetService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final PlatformOperatorGuard platformOperatorGuard;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Public submission. Deliberately does NOT validate tenant-code/username
     * availability — that would let an anonymous caller enumerate existing tenants
     * and accounts. Conflicts surface at review time instead.
     */
    @Transactional
    public void submit(String clinicName, String tenantCode, String adminUsername, String adminEmail) {
        applicationRepository.save(ClinicApplicationEntity.builder()
                .clinicName(clinicName)
                .tenantCode(tenantCode)
                .adminUsername(adminUsername)
                .adminEmail(adminEmail)
                .status(ClinicApplicationEntity.STATUS_PENDING)
                .build());
        log.info("Clinic application received for tenant code '{}'", tenantCode);
    }

    @Transactional(readOnly = true)
    public List<ClinicApplicationEntity> list(String status) {
        platformOperatorGuard.require();
        if (status != null && !status.isBlank()) {
            return applicationRepository.findByStatusOrderByCreatedAtDesc(status);
        }
        return applicationRepository.findAllByOrderByCreatedAtDesc();
    }

    public record ApprovalResult(ClinicApplicationEntity application, String setupLink) {}

    /**
     * Approve: provision tenant + first tenant-admin, return the setup link.
     * Conflicts (tenant code / username already taken) throw ValidationException —
     * the operator sees the reason and can reject the application instead.
     */
    @Transactional
    public ApprovalResult approve(Long id, String reviewer, String baseUrl) {
        platformOperatorGuard.require();
        ClinicApplicationEntity application = getPending(id);

        if (userRepository.existsByUsername(application.getAdminUsername())) {
            throw new ValidationException(
                    "Username already taken: " + application.getAdminUsername());
        }

        // Tenant-code conflicts are rejected inside createTenant (DuplicateResourceException).
        TenantEntity tenant = tenantService.createTenant(
                application.getTenantCode(), application.getClinicName());

        // Random throwaway password — never revealed; the account is activated through
        // the setup link (forcePasswordChange guards the unlikely case it leaks).
        byte[] pw = new byte[24];
        secureRandom.nextBytes(pw);
        UserEntity admin = UserEntity.builder()
                .username(application.getAdminUsername())
                .password(passwordEncoder.encode(Base64.getUrlEncoder().withoutPadding().encodeToString(pw)))
                .role(UserEntity.Role.ADMIN)
                .enabled(true)
                .forcePasswordChange(true)
                .tenantId(tenant.getId())
                .build();
        admin.setEmailWithHash(application.getAdminEmail());
        admin = userRepository.save(admin);

        String setupLink = baseUrl + "/reset-password?token="
                + passwordResetService.generateSetupToken(admin);

        application.setStatus(ClinicApplicationEntity.STATUS_APPROVED);
        application.setReviewedBy(reviewer);
        application.setReviewedAt(LocalDateTime.now());
        application.setCreatedTenantId(tenant.getId());
        application.setCreatedUserId(admin.getId());
        applicationRepository.save(application);

        // Best-effort email after commit (PasswordResetService convention) — absence of
        // SMTP must not fail the approval; the operator holds the link either way.
        String email = application.getAdminEmail();
        String username = admin.getUsername();
        Runnable sendOnboardingEmail = () -> {
            try {
                emailService.sendPasswordResetEmail(email, username, setupLink);
            } catch (Exception e) {
                log.warn("Failed to send clinic onboarding email for '{}' (link was returned to the operator)",
                        username, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendOnboardingEmail.run();
                }
            });
        } else {
            // No active transaction (plain unit tests) — send inline, still best-effort.
            sendOnboardingEmail.run();
        }

        log.info("Clinic application {} approved by {}: tenant '{}' + admin '{}' provisioned",
                id, reviewer, tenant.getCode(), username);
        return new ApprovalResult(application, setupLink);
    }

    @Transactional
    public ClinicApplicationEntity reject(Long id, String reason, String reviewer) {
        platformOperatorGuard.require();
        ClinicApplicationEntity application = getPending(id);
        application.setStatus(ClinicApplicationEntity.STATUS_REJECTED);
        application.setRejectionReason(reason);
        application.setReviewedBy(reviewer);
        application.setReviewedAt(LocalDateTime.now());
        log.info("Clinic application {} rejected by {}", id, reviewer);
        return applicationRepository.save(application);
    }

    private ClinicApplicationEntity getPending(Long id) {
        ClinicApplicationEntity application = applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic application not found: " + id));
        if (!ClinicApplicationEntity.STATUS_PENDING.equals(application.getStatus())) {
            throw new ValidationException("Application already reviewed: " + application.getStatus());
        }
        return application;
    }
}
