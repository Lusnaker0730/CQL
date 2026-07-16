package com.cqlplatform.service.cds;

import com.cqlplatform.entity.CdsServiceConfigEntity;
import com.cqlplatform.model.cds.CdsServiceConfigRequest;
import com.cqlplatform.model.cds.CdsServiceConfigResponse;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CdsServiceVersioningTest {

    @Mock
    private CdsServiceConfigRepository repository;
    @Mock
    private CdsInvocationService invocationService;
    @Mock
    private CqlTupleCardStrategy tupleStrategy;

    private CdsHooksService cdsHooksService;

    private static final Long TENANT = 7L;

    @org.junit.jupiter.api.AfterEach
    void clearTenant() {
        com.cqlplatform.security.TenantContext.clear();
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        cdsHooksService = new CdsHooksService(repository, objectMapper, invocationService, tupleStrategy,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        // BUG-137: version queries are tenant-scoped. tenantRepository is Optional.empty() in
        // this wiring, so TenantContext must be set for effectiveTenantId() to resolve.
        com.cqlplatform.security.TenantContext.setCurrentTenantId(TENANT);
    }

    @Test
    void createService_firstVersion_shouldUseVersion1() {
        when(repository.findMaxVersionByServiceName("my-service")).thenReturn(Optional.empty());
        when(repository.existsById("my-service")).thenReturn(false);

        CdsServiceConfigEntity savedEntity = CdsServiceConfigEntity.builder()
                .id("my-service").hook("patient-view").title("My Service")
                .enabled(true).version(1).serviceName("my-service")
                .prefetchItems(new ArrayList<>()).build();
        when(repository.save(any())).thenReturn(savedEntity);

        CdsServiceConfigRequest request = CdsServiceConfigRequest.builder()
                .id("my-service").hook("patient-view").title("My Service").build();

        CdsServiceConfigResponse response = cdsHooksService.createService(request);

        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getServiceName()).isEqualTo("my-service");
    }

    @Test
    void createService_secondVersion_shouldIncrementVersion() {
        when(repository.findMaxVersionByServiceName("my-service")).thenReturn(Optional.of(1));
        when(repository.existsById("my-service-v2")).thenReturn(false);

        CdsServiceConfigEntity savedEntity = CdsServiceConfigEntity.builder()
                .id("my-service-v2").hook("patient-view").title("My Service v2")
                .enabled(true).version(2).serviceName("my-service")
                .prefetchItems(new ArrayList<>()).build();
        when(repository.save(any())).thenReturn(savedEntity);

        CdsServiceConfigRequest request = CdsServiceConfigRequest.builder()
                .id("my-service").hook("patient-view").title("My Service v2").build();

        CdsServiceConfigResponse response = cdsHooksService.createService(request);

        assertThat(response.getVersion()).isEqualTo(2);
        assertThat(response.getId()).isEqualTo("my-service-v2");
    }

    @Test
    void getServiceVersions_shouldReturnAllVersions() {
        CdsServiceConfigEntity v1 = CdsServiceConfigEntity.builder()
                .id("my-service").hook("patient-view").title("V1")
                .enabled(false).version(1).serviceName("my-service")
                .prefetchItems(new ArrayList<>()).build();
        CdsServiceConfigEntity v2 = CdsServiceConfigEntity.builder()
                .id("my-service-v2").hook("patient-view").title("V2")
                .enabled(true).version(2).serviceName("my-service")
                .prefetchItems(new ArrayList<>()).build();

        when(repository.findByTenantIdAndServiceNameOrderByVersionDesc(TENANT, "my-service"))
                .thenReturn(List.of(v2, v1));

        List<CdsServiceConfigResponse> versions = cdsHooksService.getServiceVersions("my-service");

        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVersion()).isEqualTo(2);
        assertThat(versions.get(1).getVersion()).isEqualTo(1);
    }

    @Test
    void rollbackService_shouldDisableAllAndEnableTarget() {
        CdsServiceConfigEntity v1 = CdsServiceConfigEntity.builder()
                .id("my-service").hook("patient-view").title("V1")
                .enabled(false).version(1).serviceName("my-service")
                .prefetchItems(new ArrayList<>()).build();
        CdsServiceConfigEntity v2 = CdsServiceConfigEntity.builder()
                .id("my-service-v2").hook("patient-view").title("V2")
                .enabled(true).version(2).serviceName("my-service")
                .prefetchItems(new ArrayList<>()).build();

        when(repository.findByTenantIdAndServiceNameOrderByVersionDesc(TENANT, "my-service"))
                .thenReturn(List.of(v2, v1));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CdsServiceConfigResponse response = cdsHooksService.rollbackService("my-service", 1);

        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getEnabled()).isTrue();

        ArgumentCaptor<CdsServiceConfigEntity> captor = ArgumentCaptor.forClass(CdsServiceConfigEntity.class);
        verify(repository, atLeast(3)).save(captor.capture());
    }

    @Test
    void rollbackService_nonexistentService_shouldThrow() {
        when(repository.findByTenantIdAndServiceNameOrderByVersionDesc(TENANT, "nonexistent"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> cdsHooksService.rollbackService("nonexistent", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No service found");
    }

    @Test
    void rollbackService_nonexistentVersion_shouldThrow() {
        CdsServiceConfigEntity v1 = CdsServiceConfigEntity.builder()
                .id("my-service").hook("patient-view").title("V1")
                .enabled(true).version(1).serviceName("my-service")
                .prefetchItems(new ArrayList<>()).build();

        when(repository.findByTenantIdAndServiceNameOrderByVersionDesc(TENANT, "my-service"))
                .thenReturn(List.of(v1));

        assertThatThrownBy(() -> cdsHooksService.rollbackService("my-service", 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Version 99 not found");
    }
}
