package com.cqlplatform.service;

import com.cqlplatform.entity.ClinicApplicationEntity;
import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.repository.ClinicApplicationRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.PlatformOperatorGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicApplicationServiceTest {

    @Mock
    private ClinicApplicationRepository applicationRepository;
    @Mock
    private TenantRepository tenantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantService tenantService;
    @Mock
    private PasswordResetService passwordResetService;
    @Mock
    private EmailService emailService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PlatformOperatorGuard platformOperatorGuard;

    @InjectMocks
    private ClinicApplicationService service;

    private ClinicApplicationEntity pending() {
        return ClinicApplicationEntity.builder()
                .id(5L)
                .clinicName("仁心診所")
                .tenantCode("clinic-b")
                .adminUsername("drwang")
                .adminEmail("dr.wang@example.com")
                .status(ClinicApplicationEntity.STATUS_PENDING)
                .build();
    }

    // ===== submit =====

    @Test
    void submit_savesPendingApplication() {
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.submit("仁心診所", "clinic-b", "drwang", "dr.wang@example.com");

        ArgumentCaptor<ClinicApplicationEntity> captor =
                ArgumentCaptor.forClass(ClinicApplicationEntity.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
        assertThat(captor.getValue().getTenantCode()).isEqualTo("clinic-b");
        // Submission is public — no guard involved.
        verifyNoInteractions(platformOperatorGuard);
    }

    // ===== guard =====

    @Test
    void reviewOps_requirePlatformOperator() {
        doThrow(new AccessDeniedException("no")).when(platformOperatorGuard).require();

        assertThatThrownBy(() -> service.list(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.approve(5L, "admin", ""))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.reject(5L, "dup", "admin"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tenantService);
        verify(applicationRepository, never()).save(any());
    }

    // ===== approve =====

    @Test
    void approve_provisionsTenantAndAdminAndReturnsSetupLink() {
        ClinicApplicationEntity app = pending();
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.existsByUsername("drwang")).thenReturn(false);
        when(tenantService.createTenant("clinic-b", "仁心診所"))
                .thenReturn(TenantEntity.builder().id(9L).code("clinic-b").name("仁心診所").active(true).build());
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(userRepository.save(any())).thenAnswer(i -> {
            UserEntity u = i.getArgument(0);
            u.setId(77L);
            return u;
        });
        when(passwordResetService.generateSetupToken(any())).thenReturn("RAWTOKEN");

        var result = service.approve(5L, "admin", "https://twcql.com");

        assertThat(result.setupLink()).isEqualTo("https://twcql.com/reset-password?token=RAWTOKEN");
        assertThat(result.application().getStatus()).isEqualTo("approved");
        assertThat(result.application().getCreatedTenantId()).isEqualTo(9L);
        assertThat(result.application().getCreatedUserId()).isEqualTo(77L);
        assertThat(result.application().getReviewedBy()).isEqualTo("admin");

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());
        UserEntity created = userCaptor.getValue();
        assertThat(created.getUsername()).isEqualTo("drwang");
        assertThat(created.getRole()).isEqualTo(UserEntity.Role.ADMIN);
        assertThat(created.getTenantId()).isEqualTo(9L);           // clinic admin, NOT platform
        assertThat(created.getForcePasswordChange()).isTrue();     // throwaway password guarded
        assertThat(created.getPassword()).isEqualTo("bcrypt-hash");
    }

    @Test
    void approve_usernameTaken_rejectedBeforeProvisioning() {
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(pending()));
        when(userRepository.existsByUsername("drwang")).thenReturn(true);

        assertThatThrownBy(() -> service.approve(5L, "admin", ""))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(tenantService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void approve_alreadyReviewed_rejected() {
        ClinicApplicationEntity reviewed = pending();
        reviewed.setStatus(ClinicApplicationEntity.STATUS_APPROVED);
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(reviewed));

        assertThatThrownBy(() -> service.approve(5L, "admin", ""))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(tenantService);
    }

    // ===== reject =====

    @Test
    void reject_setsStatusAndAudit() {
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(pending()));
        when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ClinicApplicationEntity rejected = service.reject(5L, "duplicate application", "admin");

        assertThat(rejected.getStatus()).isEqualTo("rejected");
        assertThat(rejected.getRejectionReason()).isEqualTo("duplicate application");
        assertThat(rejected.getReviewedBy()).isEqualTo("admin");
        assertThat(rejected.getReviewedAt()).isNotNull();
    }
}
