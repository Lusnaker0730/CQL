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

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        cdsHooksService = new CdsHooksService(repository, objectMapper, invocationService, tupleStrategy,
                java.util.Optional.empty(), java.util.Optional.empty());
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
}
