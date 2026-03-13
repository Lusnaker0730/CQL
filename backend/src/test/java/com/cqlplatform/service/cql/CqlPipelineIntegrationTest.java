package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlTranslationRequest;
import com.cqlplatform.model.CqlTranslationResponse;
import com.cqlplatform.model.authoring.CqlBuildResult;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.authoring.CqlArtifactBuilder;
import com.cqlplatform.service.authoring.CqlTemplateEngine;
import com.cqlplatform.service.authoring.ExpressionCqlEngine;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end pipeline integration test:
 *   CqlArtifactBuilder (generate CQL) → CqlTranslationService (translate to ELM)
 *   → CqlExecutionService (execute against real FHIR data)
 *
 * This validates the complete authoring-to-execution pipeline using the real
 * FreeMarker template engine, real CQL translator, and real CQL execution engine.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CQL Pipeline Integration Tests (Generate → Translate → Execute)")
class CqlPipelineIntegrationTest {

    private CqlArtifactBuilder artifactBuilder;
    private CqlTranslationService translationService;
    private CqlExecutionService executionService;

    @Mock private FhirDataProviderService dataProviderService;
    @Mock private FhirTerminologyService terminologyService;
    @Mock private CqlLibraryRepository libraryRepository;

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    @BeforeEach
    void setUp() {
        // Real CQL generation engine (FreeMarker templates)
        CqlTemplateEngine templateEngine = new CqlTemplateEngine();
        ExpressionCqlEngine expressionEngine = new ExpressionCqlEngine(templateEngine);
        artifactBuilder = new CqlArtifactBuilder(expressionEngine, templateEngine);

        // Real translation service
        translationService = new CqlTranslationService();
        setField(translationService, "translationTimeoutSeconds", 30);

        // Real execution service
        executionService = new CqlExecutionService(
                dataProviderService, terminologyService,
                Executors.newSingleThreadExecutor(), libraryRepository);
        setField(executionService, "timeoutSeconds", 30);
        setField(executionService, "maxRetrieveCount", 10000);
        setField(executionService, "maxCollectionSize", 1000);
        setField(executionService, "defaultFhirServerUrl", "http://localhost:9999/fhir");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Generated CQL compiles successfully
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CqlArtifactBuilder output compiles without translation errors")
    void generatedCql_shouldTranslateSuccessfully() {
        CqlBuildResult buildResult = buildSimpleArtifact();

        assertThat(buildResult.cql()).isNotBlank();
        assertThat(buildResult.cql()).contains("library");

        // Translate generated CQL
        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(buildResult.cql());
        CqlTranslationResponse response = translationService.translate(request);

        assertThat(response.isSuccess())
                .as("Generated CQL should translate without errors. Errors: %s", response.getErrors())
                .isTrue();
        assertThat(response.getElmJson()).isNotBlank();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Hand-written CQL: translate → execute → validate results
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Translate + Execute: hand-written CQL produces correct clinical results")
    void translateThenExecute_shouldProduceCorrectResults() {
        String cql = """
                library ScreeningCheck version '1.0'
                using FHIR version '4.0.1'
                include FHIRHelpers version '4.0.1'
                context Patient

                define "Has Diabetes":
                  exists([Condition] C where C.code.coding[0].code.value = 'E11.9')

                define "Has Lab Result":
                  exists([Observation] O where O.status.value = 'final')

                define "Needs Screening":
                  "Has Diabetes" and not "Has Lab Result"
                """;

        // Step 1: Translate
        CqlTranslationRequest translateReq = new CqlTranslationRequest();
        translateReq.setCql(cql);
        CqlTranslationResponse translateResp = translationService.translate(translateReq);
        assertThat(translateResp.isSuccess()).isTrue();

        // Step 2: Execute against patient-002 (diabetic, no HbA1c)
        List<Resource> resources = loadBundle("golden/patient-diabetic-not-screened.json");
        CqlExecutionRequest execReq = new CqlExecutionRequest();
        execReq.setCql(cql);
        execReq.setPatientId("patient-002");

        CqlExecutionResponse execResp = executionService.executeWithProvider(
                execReq, new PrefetchRetrieveProvider(resources, "patient-002"));

        assertThat(execResp.isSuccess()).isTrue();
        assertExpressionEquals(execResp, "Has Diabetes", true);
        assertExpressionEquals(execResp, "Has Lab Result", false);
        assertExpressionEquals(execResp, "Needs Screening", true);

        // Step 3: Execute against patient-001 (diabetic, has HbA1c)
        List<Resource> resources2 = loadBundle("golden/patient-diabetic-screened.json");
        execReq.setPatientId("patient-001");

        CqlExecutionResponse execResp2 = executionService.executeWithProvider(
                execReq, new PrefetchRetrieveProvider(resources2, "patient-001"));

        assertThat(execResp2.isSuccess()).isTrue();
        assertExpressionEquals(execResp2, "Has Diabetes", true);
        assertExpressionEquals(execResp2, "Has Lab Result", true);
        assertExpressionEquals(execResp2, "Needs Screening", false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Generated CQL executes correctly
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Generated CQL from ArtifactBuilder executes against patient data")
    void generatedCql_shouldExecuteSuccessfully() {
        CqlBuildResult buildResult = buildSimpleArtifact();
        String generatedCql = buildResult.cql();

        // Execute generated CQL (no patient context needed for empty tree)
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(generatedCql);

        CqlExecutionResponse response = executionService.executeWithProvider(
                request, emptyRetrieveProvider());

        assertThat(response.isSuccess()).isTrue();
        // MeetsInclusionCriteria and MeetsExclusionCriteria are the standard outputs
        // from an empty artifact — they should exist and evaluate
        assertThat(response.getResults()).isNotEmpty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Translation metadata extraction
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Translation extracts expression metadata from CQL")
    void translation_shouldExtractMetadata() {
        String cql = """
                library MetadataTest version '2.0'
                using FHIR version '4.0.1'
                include FHIRHelpers version '4.0.1'
                context Patient

                define "Age Over 18": AgeInYears() >= 18
                define "Is Female": Patient.gender.value = 'female'
                """;

        CqlTranslationRequest request = new CqlTranslationRequest();
        request.setCql(cql);
        CqlTranslationResponse response = translationService.translate(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMetadata()).isNotNull();
        assertThat(response.getMetadata().getLibraryId()).isEqualTo("MetadataTest");
        assertThat(response.getMetadata().getLibraryVersion()).isEqualTo("2.0");
        assertThat(response.getMetadata().getExpressions())
                .extracting("name")
                .contains("Age Over 18", "Is Female");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private CqlBuildResult buildSimpleArtifact() {
        Map<String, Object> emptyTree = new LinkedHashMap<>();
        emptyTree.put("id", "And");
        emptyTree.put("name", "And");
        emptyTree.put("conjunction", true);
        emptyTree.put("returnType", "boolean");
        emptyTree.put("childInstances", new ArrayList<>());

        return artifactBuilder.buildCql(
                "TestArtifact", "1.0.0",
                emptyTree, emptyTree,
                List.of(), List.of(), List.of(),
                null, List.of(), "R4");
    }

    private static org.opencds.cqf.cql.engine.retrieve.RetrieveProvider emptyRetrieveProvider() {
        return (context, contextPath, contextValue, dataType, templateId,
                codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange)
                -> Collections.emptyList();
    }

    private static void assertExpressionEquals(CqlExecutionResponse response, String name, Object expected) {
        CqlExecutionResponse.ExpressionResult result = response.getResults().get(name);
        assertThat(result).as("Expression '%s' should exist", name).isNotNull();
        assertThat(result.getValue()).as("Expression '%s'", name).isEqualTo(expected);
    }

    private static List<Resource> loadBundle(String classpathPath) {
        try (InputStream is = CqlPipelineIntegrationTest.class.getClassLoader()
                .getResourceAsStream(classpathPath)) {
            if (is == null) throw new IllegalStateException("Test resource not found: " + classpathPath);
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            Bundle bundle = (Bundle) FHIR_CONTEXT.newJsonParser().parseResource(json);
            List<Resource> resources = new ArrayList<>();
            for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
                resources.add(entry.getResource());
            }
            return resources;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load test bundle: " + classpathPath, e);
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }
}
