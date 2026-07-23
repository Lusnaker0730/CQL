package com.cqlplatform.service.cds;

import com.cqlplatform.exception.ValidationException;
import com.cqlplatform.model.cds.*;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CdsHooksServiceTest {

    @Mock
    private CdsServiceConfigRepository repository;
    @Mock
    private CdsInvocationService invocationService;
    @Mock
    private CqlTupleCardStrategy tupleStrategy;

    private CdsHooksService cdsHooksService;

    private static final Long TENANT = 7L;

    @org.junit.jupiter.api.AfterEach
    void clearContext() {
        com.cqlplatform.security.TenantContext.clear();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        com.cqlplatform.security.TenantContext.setCurrentTenantId(TENANT);
        // BUG-139: invocation now requires an authenticated caller (anonymous invoke retired).
        // Shared configs in these tests carry no tenantId, so an authenticated caller suffices
        // for the downstream-logic tests (hook validation, delegation) to proceed.
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "test-caller", null, java.util.List.of()));
        cdsHooksService = new CdsHooksService(repository, objectMapper, invocationService, tupleStrategy,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
    }

    @Test
    void getServiceDefinitions_empty_shouldReturnEmptyList() {
        List<CdsServiceDefinition> definitions = cdsHooksService.getServiceDefinitions();
        assertThat(definitions).isEmpty();
    }

    @Test
    void getServiceDefinitions_withRegistered_shouldReturnThem() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("custom-service")
                .hook("patient-view")
                .title("Custom Service")
                .description("A custom CDS service")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();
        cdsHooksService.registerService(config);

        List<CdsServiceDefinition> definitions = cdsHooksService.getServiceDefinitions();
        assertThat(definitions).extracting("id").contains("custom-service");
    }

    @Test
    void registerService_shouldAddToConfigs() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("new-service")
                .hook("order-select")
                .title("New Service")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();

        cdsHooksService.registerService(config);

        List<CdsServiceDefinition> definitions = cdsHooksService.getServiceDefinitions();
        assertThat(definitions).extracting("id").contains("new-service");
    }

    @Test
    void unregisterService_shouldRemoveFromConfigs() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("temp-service")
                .hook("patient-view")
                .title("Temp")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();
        cdsHooksService.registerService(config);
        cdsHooksService.unregisterService("temp-service");

        List<CdsServiceDefinition> definitions = cdsHooksService.getServiceDefinitions();
        assertThat(definitions).extracting("id").doesNotContain("temp-service");
    }

    @Test
    void invokeService_unknownService_shouldReturnNotFoundCard() {
        CdsResponse.Card notFoundCard = CdsResponse.Card.builder()
                .summary("Service not found")
                .detail("The requested CDS service 'nonexistent' is not available.")
                .indicator("info")
                .build();
        when(tupleStrategy.createInfoCard(eq("Service not found"), contains("nonexistent")))
                .thenReturn(notFoundCard);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("nonexistent", request);

        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards().get(0).getSummary()).contains("not found");
    }

    @Test
    void invokeService_registeredService_shouldDelegateToInvocationService() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-cql-service")
                .hook("patient-view")
                .title("Test CQL Service")
                .cqlContent("library Test version '1.0'\ndefine IsTrue: true")
                .defaultIndicator("warning")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();
        cdsHooksService.registerService(config);

        CdsResponse expectedResponse = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder()
                        .summary("Test CQL Service")
                        .detail("**IsTrue**: Yes")
                        .indicator("warning")
                        .build()))
                .build();
        when(invocationService.invoke(any(), any())).thenReturn(expectedResponse);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("test-cql-service", request);

        assertThat(response.getCards()).isNotEmpty();
        verify(invocationService).invoke(any(), any());
    }

    @Test
    void invokeService_nullContext_shouldHandleGracefully() {
        CdsResponse.Card notFoundCard = CdsResponse.Card.builder()
                .summary("Service not found")
                .detail("The requested CDS service 'nonexistent' is not available.")
                .indicator("info")
                .build();
        when(tupleStrategy.createInfoCard(eq("Service not found"), contains("nonexistent")))
                .thenReturn(notFoundCard);

        CdsRequest request = new CdsRequest();
        request.setContext(null);

        CdsResponse response = cdsHooksService.invokeService("nonexistent", request);
        assertThat(response.getCards()).isNotEmpty();
    }

    @Test
    void invokeService_hookMismatch_shouldThrow() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("patient-view-service")
                .hook("patient-view")
                .title("Patient View Service")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();
        cdsHooksService.registerService(config);

        CdsRequest request = new CdsRequest();
        request.setHook("order-select"); // mismatch
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        assertThatThrownBy(() -> cdsHooksService.invokeService("patient-view-service", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hook type mismatch");
    }

    @Test
    void invokeService_patientView_missingUserId_shouldThrowValidationException() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("pv-service")
                .hook("patient-view")
                .title("Patient View")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();
        cdsHooksService.registerService(config);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        // userId intentionally missing
        request.setContext(ctx);

        assertThatThrownBy(() -> cdsHooksService.invokeService("pv-service", request))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("userId"));
                });
    }

    @Test
    void invokeService_orderSelect_missingDraftOrders_shouldThrowValidationException() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("os-service")
                .hook("order-select")
                .title("Order Select")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();
        cdsHooksService.registerService(config);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");
        // selections and draftOrders intentionally missing
        request.setContext(ctx);

        assertThatThrownBy(() -> cdsHooksService.invokeService("os-service", request))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> {
                    ValidationException ve = (ValidationException) e;
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("selections"));
                    assertThat(ve.getDetails()).anyMatch(d -> d.contains("draftOrders"));
                });
    }

    @Test
    void invokeService_encounterStart_withAllRequired_shouldProceed() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("es-service")
                .hook("encounter-start")
                .title("Encounter Start")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .shared(true)  // BUG-139: shared = invocable by same-tenant authenticated callers
                .build();
        cdsHooksService.registerService(config);

        CdsResponse expectedResponse = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder()
                        .summary("OK").indicator("info").build()))
                .build();
        when(invocationService.invoke(any(), any())).thenReturn(expectedResponse);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/123");
        ctx.setPatientId("p1");
        ctx.setEncounterId("enc-1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("es-service", request);
        assertThat(response.getCards()).isNotEmpty();
        verify(invocationService).invoke(any(), any());
    }

    @Test
    void createService_invalidHookType_shouldThrow() {
        CdsServiceConfigRequest request =
                CdsServiceConfigRequest.builder()
                        .id("bad-hook-svc")
                        .hook("invalid-hook")
                        .title("Bad Hook Service")
                        .build();

        assertThatThrownBy(() -> cdsHooksService.createService(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hook type");
    }

    // ===== PR-C2 (option A): invoke authorization gate =====

    @Test
    void invokeService_privateService_anonymousCaller_returnsNotFoundCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("private-svc")
                .hook("patient-view")
                .ownerUsername("alice")
                // shared deliberately NOT set — private service
                .build();
        cdsHooksService.registerService(config);

        CdsResponse.Card notFoundCard = CdsResponse.Card.builder()
                .summary("Service not found")
                .detail("The requested CDS service 'private-svc' is not available.")
                .indicator("info")
                .build();
        when(tupleStrategy.createInfoCard(eq("Service not found"), contains("private-svc")))
                .thenReturn(notFoundCard);

        CdsRequest request = new CdsRequest();
        CdsResponse response = cdsHooksService.invokeService("private-svc", request);

        // Same card as a missing service — private ids are not confirmable by probing
        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getSummary()).isEqualTo("Service not found");
        verify(invocationService, never()).invoke(any(), any());
    }

    @Test
    void invokeService_privateService_ownerAuthenticated_delegates() {
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "alice", null, List.of()));
        try {
            CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                    .id("alice-private")
                    .hook("patient-view")
                    .cqlContent("library T version '1.0' define X: true")
                    .ownerUsername("alice")
                    .build();
            cdsHooksService.registerService(config);
            when(invocationService.invoke(any(), any())).thenReturn(CdsResponse.builder()
                    .cards(List.of(CdsResponse.Card.builder().summary("ok").build())).build());

            CdsRequest request = new CdsRequest();
            CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
            ctx.setUserId("Practitioner/1");
            ctx.setPatientId("p1");
            request.setContext(ctx);

            CdsResponse response = cdsHooksService.invokeService("alice-private", request);

            assertThat(response.getCards()).hasSize(1);
            verify(invocationService).invoke(any(), any());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // ===== BUG-142: share-toggle cache consistency + owner-always-invoke =====

    @Test
    void toggleShared_shouldRefreshInMemoryConfig_soInvokeSeesNewValue() {
        // Toggling shared must update the in-memory registry, not just the DB row —
        // otherwise invokeService keeps authorizing against the stale shared flag until
        // the next restart (so "make private"/"make shared" had no effect on invocation).
        var entity = com.cqlplatform.entity.CdsServiceConfigEntity.builder()
                .id("svc").hook("patient-view").title("T")
                .cqlContent("library T version '1.0' define X: true")
                .ownerUsername("alice").tenantId(TENANT).enabled(true).shared(false)
                .build();
        when(repository.findByIdAndTenantIdWithPrefetch("svc", TENANT))
                .thenReturn(java.util.Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Publish it (shared=true) — the fix must push the refreshed config into the map.
        cdsHooksService.toggleShared("svc", true);

        // setUp's caller "test-caller" is in TENANT but is NOT the owner. It can invoke
        // "svc" only if the map now holds shared=true (same-tenant rule). Before the fix
        // the map held no config for it and this delegation never happened.
        when(invocationService.invoke(any(), any())).thenReturn(CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder().summary("ok").build())).build());
        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setUserId("Practitioner/1");
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("svc", request);

        assertThat(response.getCards()).hasSize(1);
        verify(invocationService).invoke(any(), any());
    }

    @Test
    void invokeService_ownerOfSharedServiceWithMismatchedTenant_delegates() {
        // The owner can ALWAYS invoke their own service — even shared and stamped to a
        // tenant the caller is not in (e.g. a null-tenant admin who owns a tenant-tagged
        // service). Before the fix the shared→same-tenant check locked the owner out.
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "owner-admin", null, List.of()));
        com.cqlplatform.security.TenantContext.clear(); // caller has no tenant
        try {
            CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                    .id("owned-shared").hook("patient-view")
                    .cqlContent("library T version '1.0' define X: true")
                    .ownerUsername("owner-admin").shared(true).tenantId(99L)
                    .build();
            cdsHooksService.registerService(config);
            when(invocationService.invoke(any(), any())).thenReturn(CdsResponse.builder()
                    .cards(List.of(CdsResponse.Card.builder().summary("ok").build())).build());
            CdsRequest request = new CdsRequest();
            CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
            ctx.setUserId("Practitioner/1");
            ctx.setPatientId("p1");
            request.setContext(ctx);

            CdsResponse response = cdsHooksService.invokeService("owned-shared", request);

            assertThat(response.getCards()).hasSize(1);
            verify(invocationService).invoke(any(), any());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    // ===== Tenant boundary (BUG-137 / BUG-139) =====
    //
    // BUG-139 collapsed `shared` to within-tenant (Option A reversed): reads AND mutations are
    // now strictly the caller's own tenant. A shared service in another tenant is simply not
    // found — there is no cross-tenant read surface any more.

    @Test
    void getService_shouldUseStrictTenantLookup() {
        when(repository.findByIdAndTenantIdWithPrefetch("svc-1", TENANT))
                .thenReturn(java.util.Optional.of(com.cqlplatform.entity.CdsServiceConfigEntity.builder()
                        .id("svc-1").hook("patient-view").title("T").tenantId(TENANT).build()));

        cdsHooksService.getService("svc-1");

        // BUG-139: even reads use the strict tenant lookup — no cross-tenant shared read.
        verify(repository).findByIdAndTenantIdWithPrefetch("svc-1", TENANT);
    }

    @Test
    void toggleShared_shouldUseStrictTenantLookupNotTheSharedSurface() {
        // A service that is merely visible (shared, other tenant) must not be togglable:
        // the strict lookup returns empty, so the caller gets not-found rather than
        // silently republishing someone else's service.
        when(repository.findByIdAndTenantIdWithPrefetch("foreign-svc", TENANT))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> cdsHooksService.toggleShared("foreign-svc", true))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository).findByIdAndTenantIdWithPrefetch("foreign-svc", TENANT);
        verify(repository, never()).save(any());
    }

    @Test
    void getSharedServiceDefinitions_shouldReturnEmpty_anonymousSurfaceRetired() {
        // BUG-139: the anonymous, tenant-agnostic discovery surface is retired. It must never
        // enumerate services across tenants — it returns empty.
        assertThat(cdsHooksService.getSharedServiceDefinitions()).isEmpty();
    }

    @Test
    void getServicesForUser_shouldScopeSharedToCallersTenant() {
        // The list surface binds `shared` to the caller's tenant (no cross-tenant shared).
        when(repository.findByTenantIdAndOwnerUsernameOrSharedTrue(TENANT, "bob"))
                .thenReturn(java.util.List.of());

        cdsHooksService.getServicesForUser("bob");

        verify(repository).findByTenantIdAndOwnerUsernameOrSharedTrue(TENANT, "bob");
    }

    @Test
    void getServiceVersions_shouldScopeToCallersTenant() {
        when(repository.findByTenantIdAndServiceNameOrderByVersionDesc(TENANT, "my-service"))
                .thenReturn(java.util.List.of());

        cdsHooksService.getServiceVersions("my-service");

        // Version history carries each version's cqlContent — it stays in the owning tenant
        // even for a shared service.
        verify(repository).findByTenantIdAndServiceNameOrderByVersionDesc(TENANT, "my-service");
    }
}
