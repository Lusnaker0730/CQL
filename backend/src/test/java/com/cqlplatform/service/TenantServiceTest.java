package com.cqlplatform.service;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.entity.UserEntity;
import com.cqlplatform.exception.DuplicateResourceException;
import com.cqlplatform.exception.ResourceNotFoundException;
import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.repository.UserRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    private static final TenantEntity DEFAULT_TENANT =
            TenantEntity.builder().id(1L).code("default").name("Default Tenant").active(true).build();

    @Mock
    private TenantRepository repo;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenVersionService tokenVersionService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private TenantService service;

    @BeforeEach
    void platformOperatorContext() {
        // Platform operator = caller in the default tenant (id 1). Individual tests
        // override TenantContext to exercise the guard.
        lenient().when(repo.findByCode("default")).thenReturn(Optional.of(DEFAULT_TENANT));
        TenantContext.setCurrentTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ===== platform-operator guard =====

    @Test
    void managementOps_clinicTenantAdmin_denied() {
        TenantContext.setCurrentTenantId(42L);  // a clinic tenant, not the platform

        assertThatThrownBy(() -> service.createTenant("clinic-x", "X"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listAll())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.setActive(2L, false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.listUsers(2L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.assignUser(9L, 2L, "admin"))
                .isInstanceOf(AccessDeniedException.class);
        verify(repo, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void managementOps_legacyPlatformAdminWithoutClaim_allowed() {
        TenantContext.clear();  // legacy admin: no tenant claim -> resolves to default
        when(repo.existsByCode("clinic-a")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        TenantEntity t = service.createTenant("clinic-a", "Clinic A");
        assertThat(t.getCode()).isEqualTo("clinic-a");
    }

    // ===== createTenant =====

    @Test
    void createTenant_savesNewWhenCodeFree() {
        when(repo.existsByCode("clinic-a")).thenReturn(false);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        TenantEntity t = service.createTenant("clinic-a", "Clinic A");

        assertThat(t.getCode()).isEqualTo("clinic-a");
        assertThat(t.getName()).isEqualTo("Clinic A");
        assertThat(t.getActive()).isTrue();
        verify(repo).save(any());
    }

    @Test
    void createTenant_rejectsDuplicateCode() {
        when(repo.existsByCode("dup")).thenReturn(true);

        assertThatThrownBy(() -> service.createTenant("dup", "X"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void getByCode_notFound_throws() {
        when(repo.findByCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByCode("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ===== setActive =====

    @Test
    void setActive_deactivatesClinicTenant() {
        TenantEntity clinic = TenantEntity.builder().id(2L).code("clinic-b").name("B").active(true).build();
        when(repo.findById(2L)).thenReturn(Optional.of(clinic));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));

        TenantEntity saved = service.setActive(2L, false);

        assertThat(saved.getActive()).isFalse();
    }

    @Test
    void setActive_defaultTenantCannotBeDeactivated() {
        when(repo.findById(1L)).thenReturn(Optional.of(DEFAULT_TENANT));

        assertThatThrownBy(() -> service.setActive(1L, false))
                .isInstanceOf(ValidationException.class);
        verify(repo, never()).save(any());
    }

    // ===== listUsers =====

    @Test
    void listUsers_defaultTenant_includesLegacyNullTenantUsers() {
        when(repo.findById(1L)).thenReturn(Optional.of(DEFAULT_TENANT));
        UserEntity assigned = UserEntity.builder().id(10L).username("a").tenantId(1L).build();
        UserEntity legacy = UserEntity.builder().id(11L).username("b").build();
        when(userRepository.findByTenantId(1L)).thenReturn(List.of(assigned));
        when(userRepository.findByTenantIdIsNull()).thenReturn(List.of(legacy));

        List<UserEntity> users = service.listUsers(1L);

        assertThat(users).containsExactly(assigned, legacy);
    }

    @Test
    void listUsers_clinicTenant_onlyAssignedUsers() {
        TenantEntity clinic = TenantEntity.builder().id(2L).code("clinic-b").name("B").active(true).build();
        when(repo.findById(2L)).thenReturn(Optional.of(clinic));
        when(userRepository.findByTenantId(2L)).thenReturn(List.of());

        assertThat(service.listUsers(2L)).isEmpty();
        verify(userRepository, never()).findByTenantIdIsNull();
    }

    // ===== assignUser =====

    @Test
    void assignUser_setsTenantAndInvalidatesSessions() {
        TenantEntity clinic = TenantEntity.builder().id(2L).code("clinic-b").name("B").active(true).build();
        when(repo.findById(2L)).thenReturn(Optional.of(clinic));
        UserEntity user = UserEntity.builder().id(9L).username("alice").build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserEntity saved = service.assignUser(9L, 2L, "admin");

        assertThat(saved.getTenantId()).isEqualTo(2L);
        // The JWT tenant claim is stale — sessions must die immediately.
        verify(tokenVersionService).bumpVersion("alice");
        verify(refreshTokenService).revokeAllForUser(9L);
    }

    @Test
    void assignUser_toDeactivatedTenant_rejected() {
        TenantEntity inactive = TenantEntity.builder().id(2L).code("clinic-b").name("B").active(false).build();
        when(repo.findById(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.assignUser(9L, 2L, "admin"))
                .isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void assignUser_self_rejected() {
        TenantEntity clinic = TenantEntity.builder().id(2L).code("clinic-b").name("B").active(true).build();
        when(repo.findById(2L)).thenReturn(Optional.of(clinic));
        UserEntity self = UserEntity.builder().id(9L).username("admin").build();
        when(userRepository.findById(9L)).thenReturn(Optional.of(self));

        assertThatThrownBy(() -> service.assignUser(9L, 2L, "admin"))
                .isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
        verify(tokenVersionService, never()).bumpVersion(any());
    }
}
