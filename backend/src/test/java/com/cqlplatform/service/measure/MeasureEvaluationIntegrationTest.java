package com.cqlplatform.service.measure;

import ca.uhn.fhir.context.FhirContext;
import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.CqlExecutionResponse.ExpressionResult;
import com.cqlplatform.model.measure.*;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.cds.PrefetchRetrieveProvider;
import com.cqlplatform.service.cql.CqlExecutionService;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
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
 * End-to-end integration test for measure evaluation.
 *
 * Uses the real CQL Engine to execute measure CQL against golden patient data,
 * then feeds results through PopulationEvaluator and MeasureScoreCalculator
 * to verify the entire pipeline produces correct measure scores.
 *
 * Scenario: Diabetes HbA1c Screening Rate (Proportion Measure)
 * - Initial Population: patients with diabetes diagnosis
 * - Denominator: same as IP
 * - Numerator: diabetes patients with HbA1c test on file
 *
 * Golden data (3 patients):
 *   patient-001: diabetic + HbA1c → IP=1, D=1, N=1
 *   patient-002: diabetic, no HbA1c → IP=1, D=1, N=0
 *   patient-003: healthy → IP=0, D=0, N=0
 *
 * Expected: score = 1/2 * 100 = 50.0%
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Measure Evaluation Integration Tests (Real Engine → Score)")
class MeasureEvaluationIntegrationTest {

    private CqlExecutionService executionService;
    private PopulationEvaluator populationEvaluator;
    private MeasureScoreCalculator scoreCalculator;

    @Mock private FhirDataProviderService dataProviderService;
    @Mock private FhirTerminologyService terminologyService;
    @Mock private CqlLibraryRepository libraryRepository;

    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();

    private static final String DIABETES_SCREENING_CQL = """
            library DiabetesScreening version '1.0'
            using FHIR version '4.0.1'
            include FHIRHelpers version '4.0.1'
            context Patient

            define "Initial Population":
              exists(
                [Condition] C
                  where C.code.coding[0].code.value = 'E11.9'
              )

            define "Denominator": "Initial Population"

            define "Numerator":
              "Initial Population"
                and exists(
                  [Observation] O
                    where O.code.coding[0].code.value = '4548-4'
                      and O.status.value = 'final'
                )

            define "Denominator Exclusions": false
            define "Denominator Exceptions": false
            define "Numerator Exclusions": false
            """;

    /** Patient bundles paired with expected population results. */
    private static final List<GoldenPatient> GOLDEN_PATIENTS = List.of(
            new GoldenPatient("patient-001", "golden/patient-diabetic-screened.json",
                    true, true, true),
            new GoldenPatient("patient-002", "golden/patient-diabetic-not-screened.json",
                    true, true, false),
            new GoldenPatient("patient-003", "golden/patient-healthy.json",
                    false, false, false)
    );

    record GoldenPatient(String id, String bundlePath,
                          boolean expectedIP, boolean expectedDenom, boolean expectedNumer) {}

    @BeforeEach
    void setUp() {
        executionService = new CqlExecutionService(
                dataProviderService, terminologyService,
                Executors.newSingleThreadExecutor(), libraryRepository);
        setField(executionService, "timeoutSeconds", 30);
        setField(executionService, "defaultFhirServerUrl", "http://localhost:9999/fhir");

        populationEvaluator = new PopulationEvaluator();
        scoreCalculator = new MeasureScoreCalculator();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 1. Per-Patient Population Verification
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Each golden patient produces expected population memberships")
    void eachPatient_shouldMatchExpectedPopulations() {
        for (GoldenPatient gp : GOLDEN_PATIENTS) {
            List<Resource> resources = loadBundle(gp.bundlePath());
            CqlExecutionResponse response = executeCql(gp.id(), resources);

            assertThat(response.isSuccess())
                    .as("Patient %s execution should succeed", gp.id())
                    .isTrue();

            assertPopulation(response, gp.id(), "Initial Population", gp.expectedIP());
            assertPopulation(response, gp.id(), "Denominator", gp.expectedDenom());
            assertPopulation(response, gp.id(), "Numerator", gp.expectedNumer());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. Full Aggregation Pipeline
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Population counts aggregate correctly across 3 patients")
    void aggregation_shouldProduceCorrectCounts() {
        Map<String, Integer> counts = populationEvaluator.initializePopulationCounts();

        for (GoldenPatient gp : GOLDEN_PATIENTS) {
            List<Resource> resources = loadBundle(gp.bundlePath());
            CqlExecutionResponse response = executeCql(gp.id(), resources);
            populationEvaluator.aggregatePatientResults(counts, response.getResults());
        }

        // 2 patients in IP (patient-001, patient-002)
        assertThat(counts.get("Initial Population"))
                .as("Initial Population count").isEqualTo(2);
        // Same 2 in denominator
        assertThat(counts.get("Denominator"))
                .as("Denominator count").isEqualTo(2);
        // Only patient-001 has HbA1c
        assertThat(counts.get("Numerator"))
                .as("Numerator count").isEqualTo(1);
        // No exclusions
        assertThat(counts.get("Denominator Exclusions"))
                .as("Denominator Exclusions").isEqualTo(0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. Measure Score Calculation
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Proportion score = 50.0% (1 numerator / 2 denominator)")
    void measureScore_shouldBeFiftyPercent() {
        Map<String, Integer> counts = populationEvaluator.initializePopulationCounts();

        for (GoldenPatient gp : GOLDEN_PATIENTS) {
            List<Resource> resources = loadBundle(gp.bundlePath());
            CqlExecutionResponse response = executeCql(gp.id(), resources);
            populationEvaluator.aggregatePatientResults(counts, response.getResults());
        }

        Double score = scoreCalculator.calculateProportionScore(
                counts.get("Denominator"),
                counts.get("Denominator Exclusions"),
                counts.get("Numerator"));

        assertThat(score)
                .as("Measure score should be 50.0%%")
                .isEqualTo(50.0);
    }

    @Test
    @DisplayName("Ratio scoring for same data = 50.0%")
    void ratioScore_shouldAlsoWork() {
        Map<String, Integer> counts = populationEvaluator.initializePopulationCounts();
        for (GoldenPatient gp : GOLDEN_PATIENTS) {
            List<Resource> resources = loadBundle(gp.bundlePath());
            CqlExecutionResponse response = executeCql(gp.id(), resources);
            populationEvaluator.aggregatePatientResults(counts, response.getResults());
        }

        Double ratioScore = scoreCalculator.calculateScore("ratio",
                counts.get("Denominator"), counts.get("Denominator Exclusions"),
                counts.get("Numerator"));

        // Ratio = numerator/denominator * 100 = 1/2 * 100 = 50.0
        assertThat(ratioScore).isEqualTo(50.0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. Edge Cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Single qualifying patient → score = 100%")
    void singleQualifyingPatient_scoreShouldBe100() {
        List<Resource> resources = loadBundle("golden/patient-diabetic-screened.json");
        CqlExecutionResponse response = executeCql("patient-001", resources);

        Map<String, Integer> counts = populationEvaluator.initializePopulationCounts();
        populationEvaluator.aggregatePatientResults(counts, response.getResults());

        Double score = scoreCalculator.calculateProportionScore(
                counts.get("Denominator"),
                counts.get("Denominator Exclusions"),
                counts.get("Numerator"));

        assertThat(score).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Only non-qualifying patients → denominator=0, score=null")
    void noQualifyingPatients_scoreShouldBeNull() {
        List<Resource> resources = loadBundle("golden/patient-healthy.json");
        CqlExecutionResponse response = executeCql("patient-003", resources);

        Map<String, Integer> counts = populationEvaluator.initializePopulationCounts();
        populationEvaluator.aggregatePatientResults(counts, response.getResults());

        assertThat(counts.get("Initial Population")).isEqualTo(0);
        assertThat(counts.get("Denominator")).isEqualTo(0);

        Double score = scoreCalculator.calculateProportionScore(
                counts.get("Denominator"),
                counts.get("Denominator Exclusions"),
                counts.get("Numerator"));

        assertThat(score).as("Score should be null when denominator is 0").isNull();
    }

    @Test
    @DisplayName("Denominator-only patient → score = 0%")
    void denominatorOnlyPatient_scoreShouldBeZero() {
        List<Resource> resources = loadBundle("golden/patient-diabetic-not-screened.json");
        CqlExecutionResponse response = executeCql("patient-002", resources);

        Map<String, Integer> counts = populationEvaluator.initializePopulationCounts();
        populationEvaluator.aggregatePatientResults(counts, response.getResults());

        Double score = scoreCalculator.calculateProportionScore(
                counts.get("Denominator"),
                counts.get("Denominator Exclusions"),
                counts.get("Numerator"));

        assertThat(score).isEqualTo(0.0);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private CqlExecutionResponse executeCql(String patientId, List<Resource> resources) {
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(DIABETES_SCREENING_CQL);
        request.setPatientId(patientId);
        PrefetchRetrieveProvider provider = new PrefetchRetrieveProvider(resources, patientId);
        return executionService.executeWithProvider(request, provider);
    }

    private static void assertPopulation(CqlExecutionResponse response, String patientId,
                                          String population, boolean expected) {
        ExpressionResult result = response.getResults().get(population);
        assertThat(result)
                .as("Patient %s should have result for '%s'", patientId, population)
                .isNotNull();
        assertThat(result.getValue())
                .as("Patient %s: '%s' should be %s", patientId, population, expected)
                .isEqualTo(expected);
    }

    private static List<Resource> loadBundle(String classpathPath) {
        try (InputStream is = MeasureEvaluationIntegrationTest.class.getClassLoader()
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
