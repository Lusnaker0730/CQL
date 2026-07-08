package com.cqlplatform.service.fhir;

import com.cqlplatform.entity.EhrConnectionEntity;
import com.cqlplatform.entity.TenantEntity;
import com.cqlplatform.repository.EhrConnectionRepository;
import com.cqlplatform.repository.TenantRepository;
import com.cqlplatform.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Phase 2 — EhrConnection MANAGEMENT operations are tenant-scoped. A caller only sees /
 * manages connections in their own tenant; legacy callers with no tenant fall back to the
 * default tenant (keeps the single-clinic deployment working).
 */
@ExtendWith(MockitoExtension.class)
class EhrConnectionServiceTest {

    @Mock private EhrConnectionRepository repository;
    @Mock private FhirClientFactory fhirClientFactory;
    @Mock private SmartBackendTokenService smartBackendTokenService;
    @Mock private TenantRepository tenantRepository;

    @InjectMocks private EhrConnectionService service;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getById_scopesToCallerTenant() {
        TenantContext.setCurrentTenantId(5L);
        EhrConnectionEntity conn = new EhrConnectionEntity();
        conn.setId(9L);
        conn.setTenantId(5L);
        when(repository.findByIdAndTenantId(9L, 5L)).thenReturn(Optional.of(conn));

        assertThat(service.getById(9L)).isSameAs(conn);
    }

    @Test
    void getById_crossTenant_readsAsNotFound() {
        TenantContext.setCurrentTenantId(5L);
        when(repository.findByIdAndTenantId(9L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(9L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void list_scopesToCallerTenant() {
        TenantContext.setCurrentTenantId(5L);
        when(repository.findByTenantIdAndActiveTrue(5L)).thenReturn(List.of(new EhrConnectionEntity()));

        assertThat(service.list(null)).hasSize(1);
        verify(repository).findByTenantIdAndActiveTrue(5L);
        verify(repository, never()).findByActiveTrue();
    }

    @Test
    void nullTenant_fallsBackToDefaultTenant() {
        TenantEntity def = TenantEntity.builder().code("default").name("Default Tenant").build();
        def.setId(1L);
        when(tenantRepository.findByCode("default")).thenReturn(Optional.of(def));
        when(repository.findByTenantIdAndActiveTrue(1L)).thenReturn(List.of());

        service.list(null);

        verify(repository).findByTenantIdAndActiveTrue(1L);
    }

    @Test
    void getByIdUnscoped_ignoresTenant() {
        EhrConnectionEntity conn = new EhrConnectionEntity();
        conn.setId(9L);
        when(repository.findById(9L)).thenReturn(Optional.of(conn));

        assertThat(service.getByIdUnscoped(9L)).isSameAs(conn);
        verifyNoInteractions(tenantRepository);
    }
}
