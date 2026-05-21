package com.cqlplatform.service.cds;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.cds.CdsRequest;
import com.cqlplatform.model.cds.CdsResponse;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CdsInvocationServiceTest {

    @Mock
    private CqlExecutionService executionService;
    @Mock
    private CqlTupleCardStrategy tupleStrategy;
    @Mock
    private PlanDefinitionCardStrategy planDefinitionStrategy;

    private CdsInvocationService invocationService;

    @BeforeEach
    void setUp() {
        FhirContext fhirContext = FhirContext.forR4();
        ObjectMapper objectMapper = new ObjectMapper();
        invocationService = new CdsInvocationService(
                executionService, tupleStrategy, planDefinitionStrategy, fhirContext, objectMapper,
                Optional.empty(),  // cdsInvocationTimer
                Optional.empty(),  // cdsInvocationCounter
                Optional.empty(),  // cdsInvocationErrorCounter
                Optional.empty(),  // analyticsService
                Optional.empty(),  // prefetchResolver
                Optional.empty()); // recentInvocationsService
    }

    @Test
    void invoke_cqlTupleMode_shouldUseTupleStrategy() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Test Service")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .cardGenerationMode("cql_tuple")
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("Check", ExpressionResult.builder()
                .name("Check").value(true).valueType("Boolean").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true).results(results).build();
        when(executionService.execute(any())).thenReturn(execResponse);

        CdsResponse expectedResponse = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder().summary("Test").build()))
                .build();
        when(tupleStrategy.buildResponse(any(), any())).thenReturn(expectedResponse);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getCards()).isNotEmpty();
        verify(tupleStrategy).buildResponse(any(), any());
        verify(planDefinitionStrategy, never()).buildResponse(any(), any());
    }

    @Test
    void invoke_planDefinitionMode_shouldUsePlanDefinitionStrategy() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("PlanDef Service")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .cardGenerationMode("plan_definition")
                .planDefinitionJson("{\"resourceType\":\"PlanDefinition\"}")
                .build();

        Map<String, ExpressionResult> results = new LinkedHashMap<>();
        results.put("Check", ExpressionResult.builder()
                .name("Check").value(true).valueType("Boolean").build());

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true).results(results).build();
        when(executionService.execute(any())).thenReturn(execResponse);

        CdsResponse expectedResponse = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder().summary("PlanDef Card").build()))
                .build();
        when(planDefinitionStrategy.buildResponse(any(), any())).thenReturn(expectedResponse);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getCards()).isNotEmpty();
        verify(planDefinitionStrategy).buildResponse(any(), any());
        verify(tupleStrategy, never()).buildResponse(any(), any());
    }

    @Test
    void invoke_nullMode_shouldDefaultToTupleStrategy() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Default Service")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .cardGenerationMode(null)
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true).results(new LinkedHashMap<>()).build();
        when(executionService.execute(any())).thenReturn(execResponse);

        CdsResponse expectedResponse = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder().summary("Default").build()))
                .build();
        when(tupleStrategy.buildResponse(any(), any())).thenReturn(expectedResponse);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        invocationService.invoke(config, request);

        verify(tupleStrategy).buildResponse(any(), any());
    }

    @Test
    void invoke_executionError_shouldReturnErrorCard() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Failing Service")
                .cqlContent("bad cql")
                .build();

        when(executionService.execute(any())).thenThrow(new RuntimeException("CQL failed"));

        CdsResponse.Card errorCard = CdsResponse.Card.builder()
                .summary("CDS Service Error")
                .detail("An error occurred: CQL failed")
                .indicator("warning")
                .build();
        when(tupleStrategy.createErrorCard("CQL failed")).thenReturn(errorCard);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getCards().get(0).getSummary()).contains("Error");
        verify(tupleStrategy).createErrorCard("CQL failed");
    }

    @Test
    void invoke_debugModeFalse_shouldNotAttachDebug() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc").title("Svc").cqlContent("library Test version '1.0'\ndefine X: true")
                .build();

        when(executionService.execute(any())).thenReturn(CqlExecutionResponse.builder()
                .success(true).results(new LinkedHashMap<>()).build());
        when(tupleStrategy.buildResponse(any(), any())).thenReturn(CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder().summary("OK").build())).build());

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);
        request.setDebugMode(false);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getDebug()).isNull();
    }

    @Test
    void invoke_debugModeTrue_success_shouldAttachDebugTraceAndContext() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("svc-1").hook("patient-view").title("Svc")
                .cqlContent("library Test version '1.0'\ndefine X: true")
                .build();

        CqlExecutionResponse.DebugTrace trace = CqlExecutionResponse.DebugTrace.builder()
                .totalTimeMs(42).build();
        when(executionService.execute(any())).thenReturn(CqlExecutionResponse.builder()
                .success(true).results(new LinkedHashMap<>()).debugTrace(trace).build());
        when(tupleStrategy.buildResponse(any(), any())).thenReturn(CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder().summary("OK").build())).build());

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        ctx.setUserId("u1");
        request.setContext(ctx);
        request.setDebugMode(true);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getDebug()).isNotNull();
        assertThat(response.getDebug().getDebugTrace()).isSameAs(trace);
        assertThat(response.getDebug().getInvocationContext()).containsEntry("serviceId", "svc-1");
        assertThat(response.getDebug().getInvocationContext()).containsEntry("hook", "patient-view");
        assertThat(response.getDebug().getInvocationContext()).containsEntry("patientId", "p1");
        assertThat(response.getDebug().getError()).isNull();
    }

    @Test
    void invoke_debugModeTrue_executionFails_shouldAttachErrorInfoWithPhase() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("svc-err").title("Err Svc").cqlContent("library Test version '1.0'\ndefine X: true")
                .build();

        when(executionService.execute(any())).thenThrow(new RuntimeException("boom"));
        when(tupleStrategy.createErrorCard("boom")).thenReturn(CdsResponse.Card.builder()
                .summary("CDS Service Error").detail("boom").indicator("warning").build());

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);
        request.setDebugMode(true);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getCards()).hasSize(1);
        assertThat(response.getDebug()).isNotNull();
        assertThat(response.getDebug().getError()).isNotNull();
        assertThat(response.getDebug().getError().getPhase()).isEqualTo("cql_execution");
        assertThat(response.getDebug().getError().getErrorType()).isEqualTo("RuntimeException");
        assertThat(response.getDebug().getError().getMessage()).isEqualTo("boom");
    }

    @Test
    void invoke_debugModeTrue_translatorErrorWrappedInExecutionException_shouldClassifyAsTranslation() {
        // BUG-115: translator failures bubble up as CqlExecutionException with message
        // "Execution failed: CQL translation failed with N error(s): ...". Prior
        // heuristic only checked top-level class name → misclassified as cql_execution.
        // Fix walks cause chain AND scans message content.
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("svc-xlate-err").title("Xlate Err").cqlContent("broken cql").build();

        RuntimeException wrapped = new RuntimeException(
                "Execution failed: CQL translation failed with 1 error(s): Could not resolve call");
        when(executionService.execute(any())).thenThrow(wrapped);
        when(tupleStrategy.createErrorCard(any())).thenReturn(CdsResponse.Card.builder()
                .summary("err").indicator("warning").build());

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);
        request.setDebugMode(true);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getDebug().getError().getPhase()).isEqualTo("cql_translation");
    }

    @Test
    void invoke_debugModeTrue_translatorErrorAsNestedCause_shouldClassifyAsTranslation() {
        // Second path: translator exception class appears deeper in cause chain.
        // `CompilerException` class-name substring match triggers the translation verdict
        // even if top-level is a generic wrapper.
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("svc-nested").title("Nested").cqlContent("broken").build();

        Throwable translatorCause = new RuntimeException("inner") {
            @Override
            public String toString() { return "CqlCompilerException: inner"; }
        };
        // Use a class whose simple name contains "Compiler"
        @SuppressWarnings("serial")
        class CqlCompilerException extends RuntimeException {
            CqlCompilerException(String m) { super(m); }
        }
        RuntimeException wrapped = new RuntimeException("outer", new CqlCompilerException("bad syntax"));
        when(executionService.execute(any())).thenThrow(wrapped);
        when(tupleStrategy.createErrorCard(any())).thenReturn(CdsResponse.Card.builder()
                .summary("err").indicator("warning").build());

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);
        request.setDebugMode(true);

        CdsResponse response = invocationService.invoke(config, request);

        assertThat(response.getDebug().getError().getPhase()).isEqualTo("cql_translation");
    }

    @Test
    void invoke_planDefinitionModeWithNullJson_shouldFallbackToTupleStrategy() {
        CdsHooksService.CdsServiceConfig config = CdsHooksService.CdsServiceConfig.builder()
                .id("test-svc")
                .title("Missing PlanDef")
                .cqlContent("library Test version '1.0'\ndefine Check: true")
                .cardGenerationMode("plan_definition")
                .planDefinitionJson(null)  // null JSON should fallback
                .build();

        CqlExecutionResponse execResponse = CqlExecutionResponse.builder()
                .success(true).results(new LinkedHashMap<>()).build();
        when(executionService.execute(any())).thenReturn(execResponse);

        CdsResponse expectedResponse = CdsResponse.builder()
                .cards(List.of(CdsResponse.Card.builder().summary("Fallback").build()))
                .build();
        when(tupleStrategy.buildResponse(any(), any())).thenReturn(expectedResponse);

        CdsRequest request = new CdsRequest();
        CdsRequest.CdsContext ctx = new CdsRequest.CdsContext();
        ctx.setPatientId("p1");
        request.setContext(ctx);

        invocationService.invoke(config, request);

        verify(tupleStrategy).buildResponse(any(), any());
    }
}
