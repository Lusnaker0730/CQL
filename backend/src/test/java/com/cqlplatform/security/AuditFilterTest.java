package com.cqlplatform.security;

import com.cqlplatform.entity.AuditLogEntity;
import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.repository.AuditLogRepository;
import com.cqlplatform.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Write-path tenant assignment for the audit trail (Phase 2 — #698). The filter runs
 * synchronously on the request thread, nested inside JwtAuthenticationFilter's
 * try/finally, so a set TenantContext is directly readable; the default-tenant fallback
 * covers anonymous/SSE/API-key/legacy-JWT paths that never set one.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditFilter — tenant assignment on the audit write path")
class AuditFilterTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private TenantRepository tenantRepository;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private AuditLogEntity runFilterAndCaptureSave() throws Exception {
        AuditFilter filter = new AuditFilter(auditLogRepository, tenantRepository);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/measures/1");
        request.setRequestURI("/api/measures/1");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<AuditLogEntity> captor = ArgumentCaptor.forClass(AuditLogEntity.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("JWT path: TenantContext is set — the audit row carries that tenant")
    void tenantFromContext() throws Exception {
        TenantContext.setCurrentTenantId(42L);

        AuditLogEntity saved = runFilterAndCaptureSave();

        assertThat(saved.getTenantId()).isEqualTo(42L);
        verifyNoInteractions(tenantRepository); // no fallback lookup needed
    }

    @Test
    @DisplayName("no TenantContext (anonymous/SSE/API-key/legacy JWT) — falls back to the default tenant")
    void tenantFallsBackToDefault() throws Exception {
        when(tenantRepository.findByCode("default"))
                .thenReturn(Optional.of(TenantEntity.builder().id(9L).code("default").build()));

        AuditLogEntity saved = runFilterAndCaptureSave();

        assertThat(saved.getTenantId()).isEqualTo(9L);
    }

    @Test
    @DisplayName("default tenant unresolvable — audit write still succeeds (null tenant, no throw)")
    void missingDefaultTenant_doesNotBreakAudit() {
        when(tenantRepository.findByCode("default")).thenReturn(Optional.empty());

        assertThatCode(() -> {
            AuditLogEntity saved = runFilterAndCaptureSave();
            assertThat(saved.getTenantId()).isNull();
        }).doesNotThrowAnyException();
    }
}
