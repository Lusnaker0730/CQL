package com.cqlplatform.security;

import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformOperatorGuardTest {

    @Mock
    private TenantRepository tenantRepository;

    @InjectMocks
    private PlatformOperatorGuard guard;

    @BeforeEach
    void defaultTenant() {
        lenient().when(tenantRepository.findByCode("default")).thenReturn(
                Optional.of(TenantEntity.builder().id(1L).code("default").build()));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void defaultTenantCaller_allowed() {
        TenantContext.setCurrentTenantId(1L);
        assertThatCode(guard::require).doesNotThrowAnyException();
    }

    @Test
    void legacyCallerWithoutClaim_allowed() {
        TenantContext.clear();
        assertThatCode(guard::require).doesNotThrowAnyException();
    }

    @Test
    void clinicTenantCaller_denied() {
        TenantContext.setCurrentTenantId(42L);
        assertThatThrownBy(guard::require).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void missingDefaultTenant_failsLoudly() {
        when(tenantRepository.findByCode("default")).thenReturn(Optional.empty());
        TenantContext.setCurrentTenantId(1L);
        assertThatThrownBy(guard::require).isInstanceOf(IllegalStateException.class);
    }

    // ===== isPlatformOperator(tenantId) — used by the login response (BUG-131) =====

    @Test
    void isPlatformOperator_nullTenant_true() {
        // Legacy/platform account (tenant_id NULL) — never queries the tenant table.
        assertThat(guard.isPlatformOperator(null)).isTrue();
    }

    @Test
    void isPlatformOperator_defaultTenant_true() {
        assertThat(guard.isPlatformOperator(1L)).isTrue();
    }

    @Test
    void isPlatformOperator_clinicTenant_false() {
        assertThat(guard.isPlatformOperator(42L)).isFalse();
    }
}
