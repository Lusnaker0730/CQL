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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantUserServiceTest {

    private static final Long CLINIC_TENANT = 5L;
    private static final Long DEFAULT_TENANT = 1L;
    private static final Long OTHER_TENANT = 9L;

    @Mock private UserRepository userRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenVersionService tokenVersionService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserApiKeyService userApiKeyService;
    @Mock private PasswordResetService passwordResetService;

    @InjectMocks private TenantUserService service;

    @BeforeEach
    void setUp() {
        // Caller is a clinic ADMIN whose tenant is 5 (never the default tenant).
        TenantContext.setCurrentTenantId(CLINIC_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubDefaultTenant() {
        lenient().when(tenantRepository.findByCode("default"))
                .thenReturn(Optional.of(TenantEntity.builder().id(DEFAULT_TENANT).code("default").build()));
    }

    private UserEntity user(Long id, String username, Long tenantId) {
        return UserEntity.builder()
                .id(id).username(username).tenantId(tenantId)
                .role(UserEntity.Role.USER).enabled(true)
                .build();
    }

    @Test
    void listUsers_returnsOnlyOwnTenant_noNullMergeForClinic() {
        stubDefaultTenant();
        when(userRepository.findByTenantId(CLINIC_TENANT))
                .thenReturn(List.of(user(1L, "alice", CLINIC_TENANT), user(2L, "bob", CLINIC_TENANT)));

        List<UserEntity> result = service.listUsers();

        assertThat(result).extracting(UserEntity::getUsername).containsExactly("alice", "bob");
        // A clinic tenant must NOT absorb legacy NULL-tenant users.
        verify(userRepository, never()).findByTenantIdIsNull();
    }

    @Test
    void createUser_stampsCallerTenant_forcesPasswordChange() {
        when(userRepository.existsByUsername("carol")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("ENC");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserEntity created = service.createUser("carol", "Passw0rd", null, "DEPARTMENT_ADMIN");

        assertThat(created.getTenantId()).isEqualTo(CLINIC_TENANT);
        assertThat(created.getRole()).isEqualTo(UserEntity.Role.DEPARTMENT_ADMIN);
        assertThat(created.getForcePasswordChange()).isTrue();
        assertThat(created.getPassword()).isEqualTo("ENC");
    }

    @Test
    void createUser_duplicateUsername_throws() {
        when(userRepository.existsByUsername("dupe")).thenReturn(true);

        assertThatThrownBy(() -> service.createUser("dupe", "Passw0rd", null, "USER"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRole_crossTenant_reportedAsNotFound() {
        stubDefaultTenant();
        when(userRepository.findById(77L)).thenReturn(Optional.of(user(77L, "victim", OTHER_TENANT)));

        assertThatThrownBy(() -> service.updateRole(77L, "ADMIN", "admin"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(userRepository, never()).save(any());
        verify(tokenVersionService, never()).bumpVersion(anyString());
    }

    @Test
    void updateRole_self_throws() {
        stubDefaultTenant();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", CLINIC_TENANT)));

        assertThatThrownBy(() -> service.updateRole(1L, "USER", "admin"))
                .isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateRole_sameTenant_bumpsVersionAndRevokes() {
        stubDefaultTenant();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bob", CLINIC_TENANT)));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UserEntity saved = service.updateRole(2L, "DEPARTMENT_ADMIN", "admin");

        assertThat(saved.getRole()).isEqualTo(UserEntity.Role.DEPARTMENT_ADMIN);
        verify(tokenVersionService).bumpVersion("bob");
        verify(refreshTokenService).revokeAllForUser(2L);
    }

    @Test
    void setEnabled_disable_cutsOffSessionsAndKeys() {
        stubDefaultTenant();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user(2L, "bob", CLINIC_TENANT)));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service.setEnabled(2L, false, "admin");

        verify(tokenVersionService).bumpVersion("bob");
        verify(refreshTokenService).revokeAllForUser(2L);
        verify(userApiKeyService).deactivateAllKeys("bob");
    }

    @Test
    void setEnabled_self_throws() {
        stubDefaultTenant();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "admin", CLINIC_TENANT)));

        assertThatThrownBy(() -> service.setEnabled(1L, false, "admin"))
                .isInstanceOf(ValidationException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_sameTenant_returnsSetupLink() {
        stubDefaultTenant();
        UserEntity target = user(2L, "bob", CLINIC_TENANT);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(passwordResetService.generateSetupToken(target)).thenReturn("tok123");

        String link = service.resetPassword(2L, "https://twcql.com");

        assertThat(link).isEqualTo("https://twcql.com/reset-password?token=tok123");
    }

    @Test
    void resetPassword_crossTenant_reportedAsNotFound() {
        stubDefaultTenant();
        when(userRepository.findById(88L)).thenReturn(Optional.of(user(88L, "victim", OTHER_TENANT)));

        assertThatThrownBy(() -> service.resetPassword(88L, "https://twcql.com"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(passwordResetService, never()).generateSetupToken(any());
    }
}
