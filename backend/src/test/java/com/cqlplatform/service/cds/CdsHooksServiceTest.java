package com.cqlplatform.service.cds;

import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.cds.*;
import com.cqlplatform.repository.CdsServiceConfigRepository;
import com.cqlplatform.service.cql.CqlExecutionService;
import ca.uhn.fhir.context.FhirContext;
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
    private CqlExecutionService executionService;
    @Mock
    private CdsServiceConfigRepository repository;

    private CdsHooksService cdsHooksService;

    @BeforeEach
    void setUp() {
        FhirContext fhirContext = FhirContext.forR4();
        ObjectMapper objectMapper = new ObjectMapper();
        cdsHooksService = new CdsHooksService(executionService, repository, fhirContext, objectMapper);
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

        // After unregister, the temp service should not be in definitions
        // (may still show defaults if empty)
        List<CdsServiceDefinition> definitions = cdsHooksService.getServiceDefinitions();
        assertThat(definitions).extracting("id").doesNotContain("temp-service");
    }

    @Test
    void invokeService_unknownService_shouldReturnNotFoundCard() {
        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("nonexistent", request);

        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards().get(0).getSummary()).contains("not found");
    }

    @Test
    void invokeService_registeredService_shouldExecuteCql() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-cql-service")
                .hook("patient-view")
                .title("Test CQL Service")
                .cqlContent("library Test version '1.0'\ndefine IsTrue: true")
                .defaultIndicator("warning")
                .build();
        cdsHooksService.registerService(config);

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("IsTrue", ExpressionResult.builder()
                .name("IsTrue").value(true).valueType("Boolean").build());

        when(executionService.execute(any())).thenReturn(
                CqlExecutionResponse.builder()
                        .success(true)
                        .results(results)
                        .build());

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("test-cql-service", request);

        assertThat(response.getCards()).isNotEmpty();
        verify(executionService).execute(any());
    }

    @Test
    void invokeService_cqlExecutionFails_shouldReturnErrorCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("failing-service")
                .hook("patient-view")
                .title("Failing Service")
                .cqlContent("bad cql")
                .build();
        cdsHooksService.registerService(config);

        when(executionService.execute(any())).thenThrow(new RuntimeException("CQL failed"));

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("failing-service", request);

        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards().get(0).getSummary()).contains("Error");
    }

    @Test
    void invokeService_noTrueResults_shouldReturnInfoCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("no-results-service")
                .hook("patient-view")
                .title("No Results")
                .cqlContent("library Test version '1.0'\ndefine Check: false")
                .build();
        cdsHooksService.registerService(config);

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("Check", ExpressionResult.builder()
                .name("Check").value(false).valueType("Boolean").build());

        when(executionService.execute(any())).thenReturn(
                CqlExecutionResponse.builder()
                        .success(true)
                        .results(results)
                        .build());

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = cdsHooksService.invokeService("no-results-service", request);

        assertThat(response.getCards()).isNotEmpty();
        assertThat(response.getCards().get(0).getDetail()).contains("No recommendations");
    }

    @Test
    void invokeService_nullContext_shouldHandleGracefully() {
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
    void createService_invalidHookType_shouldThrow() {
        com.cqlplatform.model.cds.CdsServiceConfigRequest request =
                com.cqlplatform.model.cds.CdsServiceConfigRequest.builder()
                        .id("bad-hook-svc")
                        .hook("invalid-hook")
                        .title("Bad Hook Service")
                        .build();

        assertThatThrownBy(() -> cdsHooksService.createService(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid hook type");
    }
}
