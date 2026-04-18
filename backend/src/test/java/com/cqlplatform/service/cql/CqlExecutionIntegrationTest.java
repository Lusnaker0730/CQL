package com.cqlplatform.service.cql;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.engine.runtime.Code;
import org.opencds.cqf.cql.engine.runtime.Interval;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CqlExecutionService using the real HAPI CQL Engine.
 *
 * These tests feed actual CQL expressions into the real CQL-to-ELM translator
 * and CQL Engine, executing against in-memory FHIR resources (golden test data).
 * No external FHIR server is required — all data is served from PrefetchRetrieveProvider.
 *
 * This validates that CQL logic actually produces correct clinical results,
 * not just that methods are called correctly.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CQL Execution Integration Tests (Real Engine)")
class CqlExecutionIntegrationTest {

    private CqlExecutionService executionService;

    @Mock
    private FhirDataProviderService dataProviderService;

    @Mock
    private FhirTerminologyService terminologyService;

    @Mock
    private CqlLibraryRepository libraryRepository;

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    // ── Golden CQL: Pure logic (no FHIR Retrieve) ──────────────────────────

    private static final String ARITHMETIC_CQL = """
            library ArithmeticTest version '1.0'
            define Sum: 2 + 3
            define Product: 6 * 7
            define IsPositive: 10 > 0
            define ListCount: Count({1, 2, 3, 4, 5})
            define Greeting: 'Hello CQL'
            """;

    private static final String DATE_CQL = """
            library DateTest version '1.0'
            define Today: Today()
            define BirthDate: @1990-01-15
            define AgeCheck: years between @1990-01-15 and Today() >= 30
            """;

    private static final String INTERVAL_CQL = """
            library IntervalTest version '1.0'
            define MeasurementPeriod: Interval[@2025-01-01, @2025-12-31]
            define InPeriod: @2025-06-15 in Interval[@2025-01-01, @2025-12-31]
            define NotInPeriod: @2024-06-15 in Interval[@2025-01-01, @2025-12-31]
            """;

    private static final String CONDITIONAL_CQL = """
            library ConditionalTest version '1.0'
            define Score: 85
            define Grade:
              if Score >= 90 then 'A'
              else if Score >= 80 then 'B'
              else if Score >= 70 then 'C'
              else 'F'
            define PassFail: if Score >= 60 then true else false
            """;

    private static final String NULL_HANDLING_CQL = """
            library NullTest version '1.0'
            define NullValue: null
            define NullCoalesce: Coalesce(null, null, 42)
            define IsNullCheck: 5 is not null
            define NullIsNull: null is null
            """;

    // ── Golden CQL: FHIR Retrieve (uses patient data) ───────────────────────

    private static final String PATIENT_DEMOGRAPHICS_CQL = """
            library PatientDemographics version '1.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1'
            context Patient

            define PatientGender: Patient.gender.value
            define PatientBirthDate: Patient.birthDate
            define HasBirthDate: Patient.birthDate is not null
            """;

    private static final String CONDITION_RETRIEVE_CQL = """
            library ConditionRetrieve version '1.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1'
            context Patient

            define AllConditions: [Condition]
            define HasConditions: exists [Condition]
            define ConditionCount: Count([Condition])
            """;

    private static final String OBSERVATION_RETRIEVE_CQL = """
            library ObservationRetrieve version '1.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1'
            context Patient

            define AllObservations: [Observation]
            define HasObservations: exists [Observation]
            define ObservationCount: Count([Observation])
            """;

    // ── Golden CQL: Measure-like population logic ───────────────────────────

    private static final String DIABETES_SCREENING_CQL = """
            library DiabetesScreening version '1.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1'
            context Patient

            define "Has Diabetes":
              exists(
                [Condition] C
                  where C.code.coding[0].code.value = 'E11.9'
              )

            define "Has HbA1c Test":
              exists(
                [Observation] O
                  where O.code.coding[0].code.value = '4548-4'
                    and O.status.value = 'final'
              )

            define "Latest HbA1c Value":
              Last(
                [Observation] O
                  where O.code.coding[0].code.value = '4548-4'
                    and O.status.value = 'final'
                  sort by effective
              ).value as FHIR.Quantity

            define "Initial Population": "Has Diabetes"
            define "Denominator": "Initial Population"
            define "Numerator": "Has Diabetes" and "Has HbA1c Test"
            """;

    @BeforeEach
    void setUp() {
        executionService = new CqlExecutionService(
                dataProviderService,
                terminologyService,
                Executors.newSingleThreadExecutor(),
                libraryRepository);

        // Set timeout and limits via reflection (normally injected by Spring)
        setField(executionService, "timeoutSeconds", 30);
        setField(executionService, "maxRetrieveCount", 10000);
        setField(executionService, "maxCollectionSize", 1000);
        setField(executionService, "defaultFhirServerUrl", "http://localhost:9999/fhir");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Pure CQL Logic Tests (no FHIR data)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Pure CQL Logic (no FHIR data)")
    class PureCqlLogicTests {

        @Test
        @DisplayName("Arithmetic operations produce correct results")
        void arithmetic_shouldProduceCorrectResults() {
            CqlExecutionResponse response = executeWithNoData(ARITHMETIC_CQL);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "Sum", 5);
            assertExpressionEquals(response, "Product", 42);
            assertExpressionEquals(response, "IsPositive", true);
            assertExpressionEquals(response, "ListCount", 5);
            assertExpressionEquals(response, "Greeting", "Hello CQL");
        }

        @Test
        @DisplayName("Date operations produce correct results")
        void dates_shouldProduceCorrectResults() {
            CqlExecutionResponse response = executeWithNoData(DATE_CQL);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionNotNull(response, "Today");
            assertExpressionNotNull(response, "BirthDate");
            assertExpressionEquals(response, "AgeCheck", true);
        }

        @Test
        @DisplayName("Interval contains/not-contains logic works")
        void intervals_shouldComputeContainment() {
            CqlExecutionResponse response = executeWithNoData(INTERVAL_CQL);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionNotNull(response, "MeasurementPeriod");
            assertExpressionEquals(response, "InPeriod", true);
            assertExpressionEquals(response, "NotInPeriod", false);
        }

        @Test
        @DisplayName("Conditional (if/then/else) logic works")
        void conditionals_shouldEvaluateCorrectly() {
            CqlExecutionResponse response = executeWithNoData(CONDITIONAL_CQL);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "Score", 85);
            assertExpressionEquals(response, "Grade", "B");
            assertExpressionEquals(response, "PassFail", true);
        }

        @Test
        @DisplayName("Null handling (Coalesce, is null) works")
        void nullHandling_shouldWorkCorrectly() {
            CqlExecutionResponse response = executeWithNoData(NULL_HANDLING_CQL);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionValue(response, "NullValue", null);
            assertExpressionEquals(response, "NullCoalesce", 42);
            assertExpressionEquals(response, "IsNullCheck", true);
            assertExpressionEquals(response, "NullIsNull", true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. FHIR Retrieve Tests (uses golden patient data)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. FHIR Retrieve against golden patient data")
    class FhirRetrieveTests {

        @Test
        @DisplayName("Patient demographics retrieved from diabetic patient bundle")
        void patientDemographics_fromDiabeticPatient() {
            List<Resource> resources = loadBundle("golden/patient-diabetic-screened.json");
            CqlExecutionResponse response = executeWithData(
                    PATIENT_DEMOGRAPHICS_CQL, "patient-001", resources);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "PatientGender", "male");
            assertExpressionEquals(response, "HasBirthDate", true);
        }

        @Test
        @DisplayName("Condition retrieval finds diabetes condition")
        void conditionRetrieve_findsDiabetes() {
            List<Resource> resources = loadBundle("golden/patient-diabetic-screened.json");
            CqlExecutionResponse response = executeWithData(
                    CONDITION_RETRIEVE_CQL, "patient-001", resources);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "HasConditions", true);
            assertExpressionEquals(response, "ConditionCount", 1);
        }

        @Test
        @DisplayName("Observation retrieval finds HbA1c observation")
        void observationRetrieve_findsHbA1c() {
            List<Resource> resources = loadBundle("golden/patient-diabetic-screened.json");
            CqlExecutionResponse response = executeWithData(
                    OBSERVATION_RETRIEVE_CQL, "patient-001", resources);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "HasObservations", true);
            assertExpressionEquals(response, "ObservationCount", 1);
        }

        @Test
        @DisplayName("Healthy patient has no conditions or observations")
        void healthyPatient_hasNoConditions() {
            List<Resource> resources = loadBundle("golden/patient-healthy.json");

            CqlExecutionResponse condResponse = executeWithData(
                    CONDITION_RETRIEVE_CQL, "patient-003", resources);
            assertThat(condResponse.isSuccess()).isTrue();
            assertExpressionEquals(condResponse, "HasConditions", false);
            assertExpressionEquals(condResponse, "ConditionCount", 0);

            CqlExecutionResponse obsResponse = executeWithData(
                    OBSERVATION_RETRIEVE_CQL, "patient-003", resources);
            assertThat(obsResponse.isSuccess()).isTrue();
            assertExpressionEquals(obsResponse, "HasObservations", false);
            assertExpressionEquals(obsResponse, "ObservationCount", 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Golden Data: Diabetes Screening Measure Logic
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Diabetes Screening Measure (Golden Data)")
    class DiabetesScreeningGoldenTests {

        @Test
        @DisplayName("Diabetic patient WITH HbA1c → IP=true, Denom=true, Numer=true")
        void diabeticScreened_shouldBeInNumerator() {
            List<Resource> resources = loadBundle("golden/patient-diabetic-screened.json");
            CqlExecutionResponse response = executeWithData(
                    DIABETES_SCREENING_CQL, "patient-001", resources);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "Has Diabetes", true);
            assertExpressionEquals(response, "Has HbA1c Test", true);
            assertExpressionEquals(response, "Initial Population", true);
            assertExpressionEquals(response, "Denominator", true);
            assertExpressionEquals(response, "Numerator", true);
        }

        @Test
        @DisplayName("Diabetic patient WITHOUT HbA1c → IP=true, Denom=true, Numer=false")
        void diabeticNotScreened_shouldNotBeInNumerator() {
            List<Resource> resources = loadBundle("golden/patient-diabetic-not-screened.json");
            CqlExecutionResponse response = executeWithData(
                    DIABETES_SCREENING_CQL, "patient-002", resources);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "Has Diabetes", true);
            assertExpressionEquals(response, "Has HbA1c Test", false);
            assertExpressionEquals(response, "Initial Population", true);
            assertExpressionEquals(response, "Denominator", true);
            assertExpressionEquals(response, "Numerator", false);
        }

        @Test
        @DisplayName("Healthy patient → IP=false, Denom=false, Numer=false")
        void healthyPatient_shouldNotBeInPopulation() {
            List<Resource> resources = loadBundle("golden/patient-healthy.json");
            CqlExecutionResponse response = executeWithData(
                    DIABETES_SCREENING_CQL, "patient-003", resources);

            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "Has Diabetes", false);
            assertExpressionEquals(response, "Has HbA1c Test", false);
            assertExpressionEquals(response, "Initial Population", false);
            assertExpressionEquals(response, "Denominator", false);
            assertExpressionEquals(response, "Numerator", false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Debug Mode Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Debug Mode (expression traces)")
    class DebugModeTests {

        @Test
        @DisplayName("Debug mode produces expression traces with timing")
        void debugMode_shouldProduceTraces() {
            CqlExecutionRequest request = new CqlExecutionRequest();
            request.setCql(ARITHMETIC_CQL);
            request.setDebugMode(true);

            CqlExecutionResponse response = executionService.executeWithProvider(
                    request, emptyRetrieveProvider());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getDebugTrace()).isNotNull();
            assertThat(response.getDebugTrace().getExpressionTraces()).isNotEmpty();
            assertThat(response.getDebugTrace().getTotalTimeMs()).isGreaterThanOrEqualTo(0);

            // Each expression should have timing info
            response.getDebugTrace().getExpressionTraces().forEach(trace -> {
                assertThat(trace.getName()).isNotBlank();
                assertThat(trace.getEvaluationTimeMs()).isGreaterThanOrEqualTo(0);
            });
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. Error Handling Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Invalid CQL syntax throws exception or returns error results")
        void invalidCql_shouldFailGracefully() {
            CqlExecutionRequest request = new CqlExecutionRequest();
            request.setCql("""
                    library BadLib version '1.0'
                    using FHIR version '4.0.1'
                    context Patient
                    define Broken: [NonExistentResource]
                    """);

            // The engine may throw CqlExecutionException or return error/null expression results
            try {
                CqlExecutionResponse response = executionService.executeWithProvider(
                        request, emptyRetrieveProvider());
                // If it returns instead of throwing, check for error indicators
                if (response.getResults() != null && response.getResults().containsKey("Broken")) {
                    ExpressionResult broken = response.getResults().get("Broken");
                    // Engine may return null valueType or "Error" — both indicate graceful failure
                    assertThat(broken.getValueType()).isIn("Error", "null", null);
                }
            } catch (com.cqlplatform.exception.CqlExecutionException e) {
                // Expected — invalid CQL can throw during translation or execution
                assertThat(e.getMessage()).isNotBlank();
            }
        }

        @Test
        @DisplayName("Specific expression filter only evaluates requested expressions")
        void expressionFilter_shouldLimitEvaluation() {
            CqlExecutionRequest request = new CqlExecutionRequest();
            request.setCql(ARITHMETIC_CQL);
            request.setExpressionNames(new String[]{"Sum", "Product"});

            CqlExecutionResponse response = executionService.executeWithProvider(
                    request, emptyRetrieveProvider());

            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getResults()).containsOnlyKeys("Sum", "Product");
            assertExpressionEquals(response, "Sum", 5);
            assertExpressionEquals(response, "Product", 42);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Regression guard for BUG-106 (fresh CQL vs DB stored version)
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Library resolution — fresh CQL must win over DB-stored version")
    class LibraryResolutionRegressionTest {

        @Test
        @DisplayName("Editor CQL is executed even when DB has a stale version with same name+version")
        void freshCql_shouldOverrideSavedDbVersion() {
            // Arrange: simulate a library saved in the DB earlier with a contradicting value.
            // If the engine accidentally re-compiled the DB source instead of using the fresh
            // translation, the result would be 1 and the test fails.
            String staleCqlInDb = "library ScratchLib version '1.0.0'\n"
                    + "define \"Answer\": 1\n";
            String freshCqlFromEditor = "library ScratchLib version '1.0.0'\n"
                    + "define \"Answer\": 2\n";

            com.cqlplatform.entity.CqlLibraryEntity staleEntity = new com.cqlplatform.entity.CqlLibraryEntity();
            staleEntity.setName("ScratchLib");
            staleEntity.setVersion("1.0.0");
            staleEntity.setCqlContent(staleCqlInDb);
            // Lenient: these stubs represent what the DB WOULD return if the bug regresses.
            // Strict mode marks them unused on green — which is the whole point: fresh CQL
            // wins, engine never falls back to DatabaseLibrarySourceProvider. If either
            // stub starts being called, the bug is back and the Answer assertion will fail.
            lenient().when(libraryRepository.findByNameAndVersion("ScratchLib", "1.0.0"))
                    .thenReturn(Optional.of(staleEntity));
            lenient().when(libraryRepository.findByName("ScratchLib"))
                    .thenReturn(List.of(staleEntity));

            // Act
            CqlExecutionResponse response = executeWithNoData(freshCqlFromEditor);

            // Assert: result must reflect the editor's CQL, not the DB version.
            assertThat(response.isSuccess()).isTrue();
            assertExpressionEquals(response, "Answer", 2);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private CqlExecutionResponse executeWithNoData(String cql) {
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(cql);
        return executionService.executeWithProvider(request, emptyRetrieveProvider());
    }

    private CqlExecutionResponse executeWithData(String cql, String patientId, List<Resource> resources) {
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(cql);
        request.setPatientId(patientId);

        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(resources, patientId);
        return executionService.executeWithProvider(request, provider);
    }

    private static RetrieveProvider emptyRetrieveProvider() {
        return (context, contextPath, contextValue, dataType, templateId,
                codePath, codes, valueSet, datePath, dateLowPath, dateHighPath, dateRange)
                -> Collections.emptyList();
    }

    private static List<Resource> loadBundle(String classpathPath) {
        try (InputStream is = CqlExecutionIntegrationTest.class.getClassLoader()
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

    private static void assertExpressionEquals(CqlExecutionResponse response, String name, Object expected) {
        ExpressionResult result = response.getResults().get(name);
        assertThat(result)
                .as("Expression '%s' should exist in results", name)
                .isNotNull();
        assertThat(result.getValue())
                .as("Expression '%s' should equal %s", name, expected)
                .isEqualTo(expected);
    }

    private static void assertExpressionNotNull(CqlExecutionResponse response, String name) {
        ExpressionResult result = response.getResults().get(name);
        assertThat(result)
                .as("Expression '%s' should exist in results", name)
                .isNotNull();
        assertThat(result.getValue())
                .as("Expression '%s' should not be null", name)
                .isNotNull();
    }

    private static void assertExpressionValue(CqlExecutionResponse response, String name, Object expected) {
        ExpressionResult result = response.getResults().get(name);
        assertThat(result)
                .as("Expression '%s' should exist in results", name)
                .isNotNull();
        if (expected == null) {
            assertThat(result.getValue()).as("Expression '%s' should be null", name).isNull();
        } else {
            assertThat(result.getValue()).as("Expression '%s'", name).isEqualTo(expected);
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
