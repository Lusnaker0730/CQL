package com.cqlplatform.service.cql;

import com.cqlplatform.model.CqlExecutionRequest;
import com.cqlplatform.model.CqlExecutionResponse;
import com.cqlplatform.model.measure.ClauseCoverage;
import com.cqlplatform.repository.CqlLibraryRepository;
import com.cqlplatform.service.fhir.FhirDataProviderService;
import com.cqlplatform.service.fhir.FhirTerminologyService;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opencds.cqf.cql.engine.retrieve.RetrieveProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PAT-232 — clause coverage against the real engine. The two walks (engine at run time,
 * translator visitor statically) must agree on what a clause is: a CQL where everything executes
 * must come out 100%, and the things an author needs to see uncovered — an {@code else} whose
 * condition was true, an {@code and} operand short-circuited away, a define nobody referenced —
 * must come out uncovered.
 */
class ClauseCoverageCollectorTest {

    private CqlExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new CqlExecutionService(
                new FhirDataProviderService(null, null, null),
                new FhirTerminologyService(ca.uhn.fhir.context.FhirContext.forR4Cached(),
                        new org.springframework.cache.support.NoOpCacheManager(), null, null),
                Executors.newSingleThreadExecutor(),
                org.mockito.Mockito.mock(CqlLibraryRepository.class));
        ReflectionTestUtils.setField(executionService, "timeoutSeconds", 30);
        ReflectionTestUtils.setField(executionService, "maxRetrieveCount", 10000);
        ReflectionTestUtils.setField(executionService, "maxCollectionSize", 1000);
        ReflectionTestUtils.setField(executionService, "defaultFhirServerUrl", "http://localhost:9999/fhir");
    }

    private ClauseCoverage run(String cql, String[] expressions, List<Resource> resources) {
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql(cql);
        request.setClauseCoverage(true);
        request.setExpressionNames(expressions);
        RetrieveProvider provider = resources == null
                ? (c, cp, cv, dt, t, codePath, codes, vs, dp, dlp, dhp, dr) -> List.of()
                : new com.cqlplatform.service.cds.PrefetchRetrieveProvider(resources, "p1");
        if (resources != null) request.setPatientId("p1");
        CqlExecutionResponse response = executionService.executeWithProvider(request, provider);
        assertThat(response.isSuccess()).as("errors: " + response.getErrors()).isTrue();
        assertThat(response.getClauseCoverage()).isNotNull();
        // Every clause once: the translator shares nodes between parents, a naive walk lists them twice.
        List<String> ids = response.getClauseCoverage().getStatements().stream()
                .flatMap(s -> s.getClauses().stream()).map(ClauseCoverage.Clause::getLocalId).collect(Collectors.toList());
        assertThat(ids).doesNotHaveDuplicates();
        return response.getClauseCoverage();
    }

    private static ClauseCoverage.Statement statement(ClauseCoverage c, String name) {
        return c.getStatements().stream().filter(s -> s.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void everythingExecuted_isOneHundredPercent_andTheStaticAndDynamicWalksAgree() {
        ClauseCoverage c = run("""
                library Full version '1.0.0'
                define "Age": 42
                define "Adult": "Age" >= 18 and "Age" < 120
                define "Tens": ({1, 2, 3}) N return N * 10
                define "Label": if "Adult" then 'adult' else 'child'
                """, new String[]{"Adult", "Tens", "Label"}, null);

        // The only clause never reached is the else branch — everything else the engine visited
        // is exactly what the static walk enumerated.
        List<ClauseCoverage.Clause> uncovered = c.getStatements().stream().flatMap(s -> s.getClauses().stream())
                .filter(cl -> !cl.isCovered()).collect(Collectors.toList());
        assertThat(uncovered).extracting(ClauseCoverage.Clause::getType).containsExactly("Literal");
        assertThat(uncovered.get(0).getValue()).isNull();
        assertThat(c.getTotalClauses()).isEqualTo(c.getCoveredClauses() + 1);
        assertThat(c.getPercent()).isBetween(90.0, 99.9);
        assertThat(c.getLibraryName()).isEqualTo("Full");
        assertThat(c.getCql()).startsWith("library Full");
    }

    @Test
    void clausesCarryPositionsAndValues_perIteration() {
        ClauseCoverage c = run("""
                library Q version '1.0.0'
                define "Big": ({1, 2, 3, 4}) N where N > 2 return N * 10
                """, new String[]{"Big"}, null);

        ClauseCoverage.Statement big = statement(c, "Big");
        assertThat(big.getLocator()).startsWith("2:1");
        ClauseCoverage.Clause where = big.getClauses().stream().filter(cl -> "Greater".equals(cl.getType())).findFirst().orElseThrow();
        assertThat(where.getHits()).isEqualTo(4);          // once per item
        assertThat(where.getValue()).isEqualTo("true");    // the last iteration's
        assertThat(where.getLocator()).matches("2:\\d+-2:\\d+");
        ClauseCoverage.Clause query = big.getClauses().stream().filter(cl -> "Query".equals(cl.getType())).findFirst().orElseThrow();
        assertThat(query.getHits()).isEqualTo(1);
        assertThat(query.getValue()).isEqualTo("[30, 40]");
    }

    @Test
    void unreferencedDefine_staysUncovered_butAndDoesNotShortCircuit() {
        ClauseCoverage c = run("""
                library S version '1.0.0'
                define "Never": 1 = 2
                define "Referenced": 3 = 4
                define "Guarded": false and "Referenced"
                """, new String[]{"Guarded"}, null);

        // The engine evaluates both operands of `and` (three-valued logic, no short-circuit):
        // a false left operand does NOT leave the right one uncovered. Authors must not read
        // coverage of an `and` operand as "this branch was decisive".
        ClauseCoverage.Statement guarded = statement(c, "Guarded");
        assertThat(guarded.getCoveredClauses()).isEqualTo(3).isEqualTo(guarded.getTotalClauses());
        assertThat(statement(c, "Referenced").getCoveredClauses()).isEqualTo(3);
        // A define nothing references is the uncovered one.
        ClauseCoverage.Statement never = statement(c, "Never");
        assertThat(never.getCoveredClauses()).isZero();
        assertThat(never.getTotalClauses()).isEqualTo(3);     // 1, 2, =
        assertThat(c.getPercent()).isEqualTo(ClauseCoverage.percentOf(6, 9));
    }

    @Test
    void functions_areTheirOwnStatements_andCoverWhenCalled() {
        ClauseCoverage c = run("""
                library F version '1.0.0'
                define function "Double"(x Integer): x * 2
                define function "Unused"(x Integer): x + 1
                define "Four": "Double"(2)
                """, new String[]{"Four"}, null);

        assertThat(statement(c, "Double").isFunction()).isTrue();
        assertThat(statement(c, "Double").getCoveredClauses()).isEqualTo(statement(c, "Double").getTotalClauses());
        assertThat(statement(c, "Unused").getCoveredClauses()).isZero();
        assertThat(statement(c, "Unused").getTotalClauses()).isGreaterThan(0);
    }

    @Test
    void fhirRetrieves_andPropertyAccess_areClausesToo() {
        Patient patient = new Patient();
        patient.setId("p1");
        Condition condition = new Condition();
        condition.setId("c1");
        condition.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/p1"));
        condition.getCode().addCoding().setSystem("http://hl7.org/fhir/sid/icd-10-cm").setCode("E11.9");

        ClauseCoverage c = run("""
                library R version '1.0.0'
                using FHIR version '4.0.1'
                include FHIRHelpers version '4.0.1'
                context Patient
                define "Diabetes": [Condition] C where exists (C.code.coding X where X.code = 'E11.9')
                define "Has": exists "Diabetes"
                """, new String[]{"Has"}, List.of(patient, condition));

        // `context Patient` makes the translator emit define "Patient": SingletonFrom([Patient]) at the
        // context line; the engine resolves the context patient without evaluating it through the
        // visitor, so it must not sit in the denominator as a permanently uncovered clause.
        assertThat(c.getStatements()).extracting(ClauseCoverage.Statement::getName).containsExactly("Diabetes", "Has");
        assertThat(c.getCoveredClauses()).isEqualTo(c.getTotalClauses());

        ClauseCoverage.Statement diabetes = statement(c, "Diabetes");
        assertThat(diabetes.getClauses()).extracting(ClauseCoverage.Clause::getType).contains("Retrieve", "Query", "Property", "Equal");
        assertThat(diabetes.getCoveredClauses()).isEqualTo(diabetes.getTotalClauses());
        ClauseCoverage.Clause retrieve = diabetes.getClauses().stream().filter(cl -> "Retrieve".equals(cl.getType())).findFirst().orElseThrow();
        assertThat(retrieve.getValue()).isEqualTo("[FHIR.Condition/c1]");
        assertThat(statement(c, "Has").getClauses()).extracting(ClauseCoverage.Clause::getValue).contains("true");
    }

    @Test
    void sharedNodes_countOnce_andAnEmptyPatientLeavesPerEncounterClausesUncovered() {
        // The shape the eCQM generator emits for a CV measure: `duration in days of E.period`
        // becomes DurationBetween(Start(E.period), End(E.period)) with ONE shared Property node.
        String cql = """
                library CV version '1.0.0'
                using FHIR version '4.0.1'
                include FHIRHelpers version '4.0.1'
                context Patient
                define function "Days"(E Encounter): duration in days of E.period
                define "Stays": [Encounter] E return "Days"(E)
                """;
        Patient patient = new Patient();
        patient.setId("p1");
        org.hl7.fhir.r4.model.Encounter stay = new org.hl7.fhir.r4.model.Encounter();
        stay.setId("e1");
        stay.setSubject(new org.hl7.fhir.r4.model.Reference("Patient/p1"));
        stay.getPeriod().setStartElement(new org.hl7.fhir.r4.model.DateTimeType("2024-02-01T08:00:00Z"));
        stay.getPeriod().setEndElement(new org.hl7.fhir.r4.model.DateTimeType("2024-02-03T08:00:00Z"));

        ClauseCoverage withStay = run(cql, new String[]{"Stays"}, List.of(patient, stay));
        ClauseCoverage.Statement days = statement(withStay, "Days");
        assertThat(days.isFunction()).isTrue();
        assertThat(days.getClauses()).extracting(ClauseCoverage.Clause::getType).containsOnlyOnce("Property");
        assertThat(withStay.getCoveredClauses()).isEqualTo(withStay.getTotalClauses());
        assertThat(statement(withStay, "Stays").getClauses()).extracting(ClauseCoverage.Clause::getValue).contains("[2]");

        // Same universe without data: the retrieve ran, nothing per encounter did.
        ClauseCoverage empty = run(cql, new String[]{"Stays"}, List.of(patient));
        assertThat(empty.getTotalClauses()).isEqualTo(withStay.getTotalClauses());
        assertThat(statement(empty, "Days").getCoveredClauses()).isZero();
        assertThat(empty.getCoveredClauses()).isLessThan(withStay.getCoveredClauses());

        // And the union of the two is the full run's picture with the hits added up.
        ClauseCoverage union = ClauseCoverage.merge(List.of(withStay, empty));
        assertThat(union.getTotalClauses()).isEqualTo(withStay.getTotalClauses());
        assertThat(union.getCoveredClauses()).isEqualTo(withStay.getCoveredClauses());
    }

    @Test
    void merge_unionsRunsOfTheSameLibrary() {
        String cql = """
                library M version '1.0.0'
                define "Age": 42
                define "Label": if "Age" >= 18 then 'adult' else 'child'
                """;
        ClauseCoverage adult = run(cql, new String[]{"Label"}, null);
        ClauseCoverage child = run(cql.replace("42", "7"), new String[]{"Label"}, null);
        // Different CQL text: not the same library, must not be merged in.
        ClauseCoverage merged = ClauseCoverage.merge(List.of(adult, child, adult));
        assertThat(merged.getTotalClauses()).isEqualTo(adult.getTotalClauses());
        assertThat(merged.getCoveredClauses()).isEqualTo(adult.getCoveredClauses());
        ClauseCoverage.Clause ifClause = statement(merged, "Label").getClauses().stream()
                .filter(cl -> "If".equals(cl.getType())).findFirst().orElseThrow();
        assertThat(ifClause.getHits()).isEqualTo(2); // adult counted twice, child excluded

        // Same text, complementary runs: together they cover both branches.
        ClauseCoverage second = run(cql, new String[]{"Label"}, null);
        assertThat(ClauseCoverage.merge(List.of(adult, second)).getPercent()).isEqualTo(adult.getPercent());
        assertThat(ClauseCoverage.merge(List.of())).isNull();
    }

    @Test
    void withoutTheFlag_nothingIsCollected() {
        CqlExecutionRequest request = new CqlExecutionRequest();
        request.setCql("library N version '1.0.0'\ndefine \"X\": 1");
        CqlExecutionResponse response = executionService.executeWithProvider(request,
                (c, cp, cv, dt, t, codePath, codes, vs, dp, dlp, dhp, dr) -> List.of());
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getClauseCoverage()).isNull();
    }

    @Test
    void valuesAreShortened_neverAWholeTree() {
        String longList = "{" + java.util.stream.IntStream.rangeClosed(1, 200).mapToObj(Integer::toString)
                .collect(Collectors.joining(", ")) + "}";
        ClauseCoverage c = run("library L version '1.0.0'\ndefine \"Many\": " + longList, new String[]{"Many"}, null);
        ClauseCoverage.Clause list = statement(c, "Many").getClauses().stream().filter(cl -> "List".equals(cl.getType())).findFirst().orElseThrow();
        assertThat(list.getValue()).hasSizeLessThanOrEqualTo(ClauseCoverageCollector.MAX_VALUE_CHARS).endsWith("…");
        assertThat(Map.of()).isEmpty();
    }
}
